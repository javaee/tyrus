/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2016 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.tyrus.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.CloseReason;
import javax.websocket.Extension;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import javax.websocket.server.HandshakeRequest;

import org.glassfish.tyrus.core.extension.ExtendedExtension;
import org.glassfish.tyrus.core.frame.BinaryFrame;
import org.glassfish.tyrus.core.frame.CloseFrame;
import org.glassfish.tyrus.core.frame.Frame;
import org.glassfish.tyrus.core.frame.TextFrame;
import org.glassfish.tyrus.core.frame.TyrusFrame;
import org.glassfish.tyrus.core.l10n.LocalizationMessages;
import org.glassfish.tyrus.core.monitoring.MessageEventListener;
import org.glassfish.tyrus.spi.CompletionHandler;
import org.glassfish.tyrus.spi.UpgradeRequest;
import org.glassfish.tyrus.spi.UpgradeResponse;
import org.glassfish.tyrus.spi.Writer;

/**
 * Tyrus protocol handler.
 * <p>
 * Responsible for framing and unframing raw websocket frames. Tyrus creates exactly one instance per Session.
 */
public final class ProtocolHandler {

    /**
     * RFC 6455
     */
    public static final int MASK_SIZE = 4;

    private static final Logger LOGGER = Logger.getLogger(ProtocolHandler.class.getName());
    private static final int SEND_TIMEOUT = 3000; // millis.

    private final boolean client;
    private final MaskingKeyGenerator maskingKeyGenerator;
    private final ParsingState parsingState = new ParsingState();

    private volatile TyrusWebSocket webSocket;
    private volatile byte outFragmentedType;
    private volatile Writer writer;
    private volatile byte inFragmentedType;
    private volatile boolean processingFragment;
    private volatile String subProtocol = null;
    private volatile List<Extension> extensions;
    private volatile ExtendedExtension.ExtensionContext extensionContext;
    private volatile ByteBuffer remainder = null;
    private volatile boolean hasExtensions = false;
    private volatile MessageEventListener messageEventListener = MessageEventListener.NO_OP;
    private volatile SendingFragmentState sendingFragment = SendingFragmentState.IDLE;

    /**
     * Synchronizes all public send* (including stream variants) methods.
     * <p>
     * The reason for this lock is that we need to have consistent value in {#sendingFragment} field to be able to
     * determine the sending state of this particular instance/session.
     */
    private final Lock lock = new ReentrantLock();

    /**
     * If partial message is being send and we want to send partial message with different type or other whole message,
     * we need to wait until "idleCondition" is signalled.
     */
    private final Condition idleCondition = lock.newCondition();

    /**
     * Sending state.
     */
    private static enum SendingFragmentState {

        /**
         * Session is idle - no partial message in progress.
         */
        IDLE,

        /**
         * Sending partial text message - final frame was not yet sent.
         */
        SENDING_TEXT,

        /**
         * Sending partial binary message - final frame was not yet sent.
         */
        SENDING_BINARY
    }

    /**
     * Constructor.
     *
     * @param client              {@code true} when this instance is on client side, {@code false} when on server side.
     * @param maskingKeyGenerator random number generator that will be used for generating masking keys. Masking keys
     *                            are required only on the client side and {@code maskingKeyGenerator} should be {@code
     *                            null} on the server side. If {@code null} on the client side, {@link
     *                            java.security.SecureRandom} will be used by default.
     */
    ProtocolHandler(boolean client, MaskingKeyGenerator maskingKeyGenerator) {
        this.client = client;

        if (client) {
            if (maskingKeyGenerator != null) {
                this.maskingKeyGenerator = maskingKeyGenerator;
            } else {
                this.maskingKeyGenerator = new MaskingKeyGenerator() {

                    private final SecureRandom secureRandom = new SecureRandom();

                    @Override
                    public int nextInt() {
                        return secureRandom.nextInt();
                    }
                };
            }
        } else {
            // masking key is not used on the server
            this.maskingKeyGenerator = null;
        }
    }

    /**
     * Set {@link Writer} instance.
     * <p>
     * The set instance is used for "sending" all outgoing WebSocket frames.
     *
     * @param writer {@link Writer} to be set.
     */
    public void setWriter(Writer writer) {
        this.writer = writer;
    }

    /**
     * Returns true when current connection has some negotiated extension.
     *
     * @return {@code true} if there is at least one negotiated extension associated to this connection, {@code false}
     * otherwise.
     */
    public boolean hasExtensions() {
        return hasExtensions;
    }

    /**
     * Server side handshake processing.
     *
     * @param endpointWrapper  endpoint related to the handshake (path is already matched).
     * @param request          handshake request.
     * @param response         handshake response.
     * @param extensionContext extension context.
     * @return server handshake object.
     * @throws HandshakeException when there is problem with received {@link UpgradeRequest}.
     */
    public Handshake handshake(TyrusEndpointWrapper endpointWrapper, UpgradeRequest request, UpgradeResponse
            response, ExtendedExtension.ExtensionContext extensionContext) throws HandshakeException {
        final Handshake handshake = Handshake.createServerHandshake(request, extensionContext);
        this.extensions = handshake.respond(request, response, endpointWrapper);
        this.subProtocol = response.getFirstHeaderValue(HandshakeRequest.SEC_WEBSOCKET_PROTOCOL);
        this.extensionContext = extensionContext;
        hasExtensions = extensions != null && extensions.size() > 0;
        return handshake;
    }

    /* package */ List<Extension> getExtensions() {
        return extensions;
    }

    /**
     * Client side. Set extensions negotiated for this WebSocket session/connection.
     *
     * @param extensions list of negotiated extensions. Can be {@code null}.
     */
    public void setExtensions(List<Extension> extensions) {
        this.extensions = extensions;
        this.hasExtensions = extensions != null && extensions.size() > 0;
    }

    /* package */ String getSubProtocol() {
        return subProtocol;
    }

    /**
     * Client side. Set WebSocket.
     *
     * @param webSocket client WebSocket connection.
     */
    public void setWebSocket(TyrusWebSocket webSocket) {
        this.webSocket = webSocket;
    }

    /**
     * Client side. Set extension context.
     *
     * @param extensionContext extension context.
     */
    public void setExtensionContext(ExtendedExtension.ExtensionContext extensionContext) {
        this.extensionContext = extensionContext;
    }

    /**
     * Set message event listener.
     *
     * @param messageEventListener message event listener.
     */
    public void setMessageEventListener(MessageEventListener messageEventListener) {
        this.messageEventListener = messageEventListener;
    }

    /**
     * Not message frames - ping/pong/...
     */
    /* package */
    final Future<Frame> send(TyrusFrame frame) {
        return send(frame, null, true);
    }

    private Future<Frame> send(TyrusFrame frame, CompletionHandler<Frame> completionHandler, Boolean useTimeout) {
        return write(frame, completionHandler, useTimeout);
    }

    private Future<Frame> send(ByteBuffer frame, CompletionHandler<Frame> completionHandler, Boolean useTimeout) {
        return write(frame, completionHandler, useTimeout);
    }

    public Future<Frame> send(byte[] data) {
        lock.lock();
        try {
            checkSendingFragment();

            return send(new BinaryFrame(data, false, true), null, true);
        } finally {
            lock.unlock();
        }
    }

    public void send(final byte[] data, final SendHandler handler) {
        lock.lock();

        try {
            checkSendingFragment();

            send(new BinaryFrame(data, false, true), new CompletionHandler<Frame>() {
                @Override
                public void failed(Throwable throwable) {
                    handler.onResult(new SendResult(throwable));
                }

                @Override
                public void completed(Frame result) {
                    handler.onResult(new SendResult());
                }
            }, true);
        } finally {
            lock.unlock();
        }
    }

    public Future<Frame> send(String data) {
        lock.lock();

        try {
            checkSendingFragment();
            return send(new TextFrame(data, false, true));
        } finally {
            lock.unlock();
        }
    }

    public void send(final String data, final SendHandler handler) {
        lock.lock();

        try {
            checkSendingFragment();

            send(new TextFrame(data, false, true), new CompletionHandler<Frame>() {
                @Override
                public void failed(Throwable throwable) {
                    handler.onResult(new SendResult(throwable));
                }

                @Override
                public void completed(Frame result) {
                    handler.onResult(new SendResult());
                }
            }, true);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Raw frame is always whole (not partial).
     *
     * @param data serialized frame.
     * @return send future.
     */
    public Future<Frame> sendRawFrame(ByteBuffer data) {
        lock.lock();

        try {
            checkSendingFragment();

            return send(data, null, true);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Check whether current {@link ProtocolHandler} is sending a partial message.
     * <p>
     * If yes, wait for {@value ProtocolHandler#SEND_TIMEOUT} and if the message still cannot be sent, throw {@link
     * IllegalStateException}.
     */
    private void checkSendingFragment() {
        final long timeout = System.currentTimeMillis() + SEND_TIMEOUT;

        // idleCondition can be signalled but other thread could be scheduled before this one; of that thread starts
        // sending another partial message, we should wait again for the condition to be signalled.
        while (sendingFragment != SendingFragmentState.IDLE) {
            final long currentTimeMillis = System.currentTimeMillis();

            // timeout already reached.
            if (currentTimeMillis >= timeout) {
                throw new IllegalStateException();
            }

            try {
                if (!idleCondition.await(timeout - currentTimeMillis, TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    public Future<Frame> stream(boolean last, byte[] bytes, int off, int len) {
        lock.lock();

        try {
            switch (sendingFragment) {
                case SENDING_BINARY:
                    Future<Frame> frameFuture = send(
                            new BinaryFrame(Arrays.copyOfRange(bytes, off, off + len), true, last));
                    if (last) {
                        sendingFragment = SendingFragmentState.IDLE;
                        idleCondition.signalAll();
                    }
                    return frameFuture;

                case SENDING_TEXT:
                    checkSendingFragment();
                    sendingFragment = (last ? SendingFragmentState.IDLE : SendingFragmentState.SENDING_BINARY);
                    return send(new BinaryFrame(Arrays.copyOfRange(bytes, off, off + len), false, last));

                default:
                    // IDLE
                    sendingFragment = (last ? SendingFragmentState.IDLE : SendingFragmentState.SENDING_BINARY);
                    return send(new BinaryFrame(Arrays.copyOfRange(bytes, off, off + len), false, last));
            }

        } finally {
            lock.unlock();
        }
    }

    public Future<Frame> stream(boolean last, String fragment) {
        lock.lock();

        try {
            switch (sendingFragment) {
                case SENDING_TEXT:
                    Future<Frame> frameFuture = send(new TextFrame(fragment, true, last));
                    if (last) {
                        sendingFragment = SendingFragmentState.IDLE;
                        idleCondition.signalAll();
                    }
                    return frameFuture;

                case SENDING_BINARY:
                    checkSendingFragment();
                    sendingFragment = (last ? SendingFragmentState.IDLE : SendingFragmentState.SENDING_TEXT);
                    return send(new TextFrame(fragment, false, last));

                default:
                    // IDLE
                    sendingFragment = (last ? SendingFragmentState.IDLE : SendingFragmentState.SENDING_TEXT);
                    return send(new TextFrame(fragment, false, last));
            }

        } finally {
            lock.unlock();
        }
    }

    public synchronized Future<Frame> close(final int code, final String reason) {
        final CloseFrame outgoingCloseFrame;
        final CloseReason closeReason = new CloseReason(CloseReason.CloseCodes.getCloseCode(code), reason);

        if (code == CloseReason.CloseCodes.NO_STATUS_CODE.getCode()
                || code == CloseReason.CloseCodes.CLOSED_ABNORMALLY.getCode()
                || code == CloseReason.CloseCodes.TLS_HANDSHAKE_FAILURE.getCode()
                // client side cannot send SERVICE_RESTART or TRY_AGAIN_LATER
                // will be replaced with NORMAL_CLOSURE
                || (client && (code == CloseReason.CloseCodes.SERVICE_RESTART.getCode()
                || code == CloseReason.CloseCodes.TRY_AGAIN_LATER.getCode()))) {

            outgoingCloseFrame = new CloseFrame(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, reason));
        } else {
            outgoingCloseFrame = new CloseFrame(closeReason);
        }

        final Future<Frame> send = send(outgoingCloseFrame, null, false);

        webSocket.onClose(new CloseFrame(closeReason));

        return send;
    }

    private Future<Frame> write(final TyrusFrame frame, final CompletionHandler<Frame> completionHandler,
                                boolean useTimeout) {
        final Writer localWriter = writer;
        final TyrusFuture<Frame> future = new TyrusFuture<Frame>();

        if (localWriter == null) {
            throw new IllegalStateException(LocalizationMessages.CONNECTION_NULL());
        }

        final ByteBuffer byteBuffer = frame(frame);
        localWriter.write(byteBuffer, new CompletionHandlerWrapper(completionHandler, future, frame));
        messageEventListener.onFrameSent(frame.getFrameType(), frame.getPayloadLength());

        return future;
    }

    private Future<Frame> write(final ByteBuffer frame, final CompletionHandler<Frame> completionHandler,
                                boolean useTimeout) {
        final Writer localWriter = writer;
        final TyrusFuture<Frame> future = new TyrusFuture<Frame>();

        if (localWriter == null) {
            throw new IllegalStateException(LocalizationMessages.CONNECTION_NULL());
        }

        localWriter.write(frame, new CompletionHandlerWrapper(completionHandler, future, null));

        return future;
    }

    /**
     * Convert a byte[] to a long. Used for rebuilding payload length.
     *
     * @param bytes byte array to be converted.
     * @return converted byte array.
     */
    private long decodeLength(byte[] bytes) {
        return Utils.toLong(bytes, 0, bytes.length);
    }

    /**
     * Converts the length given to the appropriate framing data: <ol> <li>0-125 one element that is the payload
     * length.
     * <li>up to 0xFFFF, 3 element array starting with 126 with the following 2 bytes interpreted as a 16 bit unsigned
     * integer showing the payload length. <li>else 9 element array starting with 127 with the following 8 bytes
     * interpreted as a 64-bit unsigned integer (the high bit must be 0) showing the payload length. </ol>
     *
     * @param length the payload size
     * @return the array
     */
    private byte[] encodeLength(final long length) {
        byte[] lengthBytes;
        if (length <= 125) {
            lengthBytes = new byte[1];
            lengthBytes[0] = (byte) length;
        } else {
            byte[] b = Utils.toArray(length);
            if (length <= 0xFFFF) {
                lengthBytes = new byte[3];
                lengthBytes[0] = 126;
                System.arraycopy(b, 6, lengthBytes, 1, 2);
            } else {
                lengthBytes = new byte[9];
                lengthBytes[0] = 127;
                System.arraycopy(b, 0, lengthBytes, 1, 8);
            }
        }
        return lengthBytes;
    }

    private void validate(final byte fragmentType, byte opcode) {
        if (opcode != 0 && opcode != fragmentType && !isControlFrame(opcode)) {
            throw new ProtocolException(LocalizationMessages.SEND_MESSAGE_INFRAGMENT());
        }
    }

    private byte checkForLastFrame(Frame frame) {
        byte local = frame.getOpcode();

        if (frame.isControlFrame()) {
            local |= 0x80;
            return local;
        }

        if (!frame.isFin()) {
            if (outFragmentedType != 0) {
                local = 0x00;
            } else {
                outFragmentedType = local;
                local &= 0x7F;
            }
            validate(outFragmentedType, local);
        } else if (outFragmentedType != 0) {
            local = (byte) 0x80;
            outFragmentedType = 0;
        } else {
            local |= 0x80;
        }
        return local;
    }

    /* package */ void doClose() {
        final Writer localWriter = writer;
        if (localWriter == null) {
            throw new IllegalStateException(LocalizationMessages.CONNECTION_NULL());
        }

        try {
            localWriter.close();
        } catch (IOException e) {
            throw new IllegalStateException(LocalizationMessages.IOEXCEPTION_CLOSE(), e);
        }
    }

    /* package */ ByteBuffer frame(Frame frame) {

        if (client) {
            frame = Frame.builder(frame).maskingKey(maskingKeyGenerator.nextInt()).mask(true).build();
        }

        if (extensions != null && extensions.size() > 0) {
            for (Extension extension : extensions) {
                if (extension instanceof ExtendedExtension) {
                    try {
                        frame = ((ExtendedExtension) extension).processOutgoing(extensionContext, frame);
                    } catch (Throwable t) {
                        // TODO: define ExtendedExtension exception handling.
                        LOGGER.log(Level.FINE, LocalizationMessages.EXTENSION_EXCEPTION(extension.getName(), t
                                .getMessage()), t);
                    }
                }
            }
        }

        byte opcode = checkForLastFrame(frame);
        if (frame.isRsv1()) {
            opcode |= 0x40;
        }
        if (frame.isRsv2()) {
            opcode |= 0x20;
        }
        if (frame.isRsv3()) {
            opcode |= 0x10;
        }

        final byte[] bytes = frame.getPayloadData();
        final byte[] lengthBytes = encodeLength(frame.getPayloadLength());

        // TODO - length limited to int, it should be long (see RFC 9788, chapter 5.2)
        // TODO - in that case, we will need to NOT store dataframe inmemory - introduce maskingByteStream or
        // TODO   maskingByteBuffer
        final int payloadLength = (int) frame.getPayloadLength();
        int length = 1 + lengthBytes.length + payloadLength + (client ? MASK_SIZE : 0);
        int payloadStart = 1 + lengthBytes.length + (client ? MASK_SIZE : 0);
        final byte[] packet = new byte[length];
        packet[0] = opcode;
        System.arraycopy(lengthBytes, 0, packet, 1, lengthBytes.length);
        // if client, then we need to mask data.
        if (client) {
            Integer maskingKey = frame.getMaskingKey();
            if (maskingKey == null) {
                // TODO: improve validation/exception handling
                // TODO: related to ExtendedExtension
                throw new ProtocolException("Masking key cannot be null when sending message from client to server.");
            }
            Masker masker = new Masker(maskingKey);
            packet[1] |= 0x80;
            masker.mask(packet, payloadStart, bytes, payloadLength);
            System.arraycopy(masker.getMask(), 0, packet, payloadStart - MASK_SIZE, MASK_SIZE);
        } else {
            System.arraycopy(bytes, 0, packet, payloadStart, payloadLength);
        }
        return ByteBuffer.wrap(packet);
    }

    /**
     * TODO!
     *
     * @param buffer TODO.
     * @return TODO.
     */
    public Frame unframe(ByteBuffer buffer) {

        try {
            // this do { .. } while cycle was forced by findbugs check - complained about missing break statements.
            do {
                switch (parsingState.state.get()) {
                    case 0:
                        if (buffer.remaining() < 2) {
                            // Don't have enough bytes to read opcode and lengthCode
                            return null;
                        }

                        byte opcode = buffer.get();


                        parsingState.finalFragment = isBitSet(opcode, 7);
                        parsingState.controlFrame = isControlFrame(opcode);
                        parsingState.opcode = (byte) (opcode & 0x7f);
                        if (!parsingState.finalFragment && parsingState.controlFrame) {
                            throw new ProtocolException(LocalizationMessages.CONTROL_FRAME_FRAGMENTED());
                        }

                        byte lengthCode = buffer.get();

                        parsingState.masked = (lengthCode & 0x80) == 0x80;
                        parsingState.masker = new Masker(buffer);
                        if (parsingState.masked) {
                            lengthCode ^= 0x80;
                        }
                        parsingState.lengthCode = lengthCode;

                        parsingState.state.incrementAndGet();
                        break;
                    case 1:
                        if (parsingState.lengthCode <= 125) {
                            parsingState.length = parsingState.lengthCode;
                        } else {
                            if (parsingState.controlFrame) {
                                throw new ProtocolException(LocalizationMessages.CONTROL_FRAME_LENGTH());
                            }

                            final int lengthBytes = parsingState.lengthCode == 126 ? 2 : 8;
                            if (buffer.remaining() < lengthBytes) {
                                // Don't have enough bytes to read length
                                return null;
                            }
                            parsingState.masker.setBuffer(buffer);
                            parsingState.length = decodeLength(parsingState.masker.unmask(lengthBytes));
                        }
                        parsingState.state.incrementAndGet();
                        break;
                    case 2:
                        if (parsingState.masked) {
                            if (buffer.remaining() < MASK_SIZE) {
                                // Don't have enough bytes to read mask
                                return null;
                            }
                            parsingState.masker.setBuffer(buffer);
                            parsingState.masker.readMask();
                        }
                        parsingState.state.incrementAndGet();
                        break;
                    case 3:
                        if (buffer.remaining() < parsingState.length) {
                            return null;
                        }

                        parsingState.masker.setBuffer(buffer);
                        final byte[] data = parsingState.masker.unmask((int) parsingState.length);
                        if (data.length != parsingState.length) {
                            throw new ProtocolException(
                                    LocalizationMessages.DATA_UNEXPECTED_LENGTH(data.length, parsingState.length));
                        }

                        final Frame frame = Frame.builder().fin(parsingState.finalFragment)
                                                 .rsv1(isBitSet(parsingState.opcode, 6))
                                                 .rsv2(isBitSet(parsingState.opcode, 5))
                                                 .rsv3(isBitSet(parsingState.opcode, 4))
                                                 .opcode((byte) (parsingState.opcode & 0xf))
                                                 .payloadLength(parsingState.length)
                                                 .payloadData(data)
                                                 .build();

                        parsingState.recycle();

                        return frame;
                    default:
                        // Should never get here
                        throw new IllegalStateException(LocalizationMessages.UNEXPECTED_STATE(parsingState.state));
                }
            } while (true);
        } catch (Exception e) {
            parsingState.recycle();
            throw (RuntimeException) e;
        }
    }

    /**
     * TODO.
     * <p>
     * called after Extension execution.
     * <p>
     * validates frame + processes its content
     *
     * @param frame  TODO.
     * @param socket TODO.
     */
    public void process(Frame frame, TyrusWebSocket socket) {
        if (frame.isRsv1() || frame.isRsv2() || frame.isRsv3()) {
            throw new ProtocolException(LocalizationMessages.RSV_INCORRECTLY_SET());
        }

        final byte opcode = frame.getOpcode();
        final boolean fin = frame.isFin();
        if (!frame.isControlFrame()) {
            final boolean continuationFrame = (opcode == 0);
            if (continuationFrame && !processingFragment) {
                throw new ProtocolException(LocalizationMessages.UNEXPECTED_END_FRAGMENT());
            }
            if (processingFragment && !continuationFrame) {
                throw new ProtocolException(LocalizationMessages.FRAGMENT_INVALID_OPCODE());
            }
            if (!fin && !continuationFrame) {
                processingFragment = true;
            }
            if (!fin) {
                if (inFragmentedType == 0) {
                    inFragmentedType = opcode;
                }
            }
        }

        TyrusFrame tyrusFrame = TyrusFrame.wrap(frame, inFragmentedType, remainder);

        // TODO - utf8 decoder needs this state to be shared among decoded frames.
        // TODO - investigate whether it can be removed; (this effectively denies lazy decoding)
        if (tyrusFrame instanceof TextFrame) {
            remainder = ((TextFrame) tyrusFrame).getRemainder();
        }

        // server should not allow receiving 1012 or 1013 from the client
        // (SERVICE_RESTART and TRY_AGAIN_LATER does not make sense from the client side.
        if (!client) {
            if (tyrusFrame.isControlFrame() && tyrusFrame instanceof CloseFrame) {
                CloseReason.CloseCode closeCode = ((CloseFrame) tyrusFrame).getCloseReason().getCloseCode();
                if (closeCode.equals(CloseReason.CloseCodes.SERVICE_RESTART)
                        || closeCode.equals(CloseReason.CloseCodes.TRY_AGAIN_LATER)) {
                    throw new ProtocolException("Illegal close code: " + closeCode);
                }
            }
        }

        tyrusFrame.respond(socket);

        if (!tyrusFrame.isControlFrame() && fin) {
            inFragmentedType = 0;
            processingFragment = false;
        }
    }

    private boolean isControlFrame(byte opcode) {
        return (opcode & 0x08) == 0x08;
    }

    private boolean isBitSet(final byte b, int bit) {
        return ((b >> bit & 1) != 0);
    }

    /**
     * Handler passed to the {@link org.glassfish.tyrus.spi.Writer}.
     */
    private static class CompletionHandlerWrapper extends CompletionHandler<ByteBuffer> {

        private final CompletionHandler<Frame> frameCompletionHandler;
        private final TyrusFuture<Frame> future;
        private final Frame frame;

        private CompletionHandlerWrapper(CompletionHandler<Frame> frameCompletionHandler, TyrusFuture<Frame> future,
                                         Frame frame) {
            this.frameCompletionHandler = frameCompletionHandler;
            this.future = future;
            this.frame = frame;
        }

        @Override
        public void cancelled() {
            if (frameCompletionHandler != null) {
                frameCompletionHandler.cancelled();
            }

            if (future != null) {
                future.setFailure(new RuntimeException(LocalizationMessages.FRAME_WRITE_CANCELLED()));
            }
        }

        @Override
        public void failed(Throwable throwable) {
            if (frameCompletionHandler != null) {
                frameCompletionHandler.failed(throwable);
            }

            if (future != null) {
                future.setFailure(throwable);
            }
        }

        @Override
        public void completed(ByteBuffer result) {
            if (frameCompletionHandler != null) {
                frameCompletionHandler.completed(frame);
            }

            if (future != null) {
                future.setResult(frame);
            }
        }

        @Override
        public void updated(ByteBuffer result) {
            if (frameCompletionHandler != null) {
                frameCompletionHandler.updated(frame);
            }
        }
    }

    private static class ParsingState {
        final AtomicInteger state = new AtomicInteger(0);
        volatile byte opcode = (byte) -1;
        volatile long length = -1;
        volatile boolean masked;
        volatile Masker masker;
        volatile boolean finalFragment;
        volatile boolean controlFrame;

        private volatile byte lengthCode = -1;

        void recycle() {
            state.set(0);
            opcode = (byte) -1;
            length = -1;
            lengthCode = -1;
            masked = false;
            masker = null;
            finalFragment = false;
            controlFrame = false;
        }
    }
}
