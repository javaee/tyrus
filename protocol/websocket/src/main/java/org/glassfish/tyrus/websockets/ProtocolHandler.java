/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.websockets;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.websocket.CloseReason;
import javax.websocket.WebSocketContainer;

import org.glassfish.tyrus.spi.HandshakeRequest;
import org.glassfish.tyrus.spi.Writer;
import org.glassfish.tyrus.websockets.frame.BinaryFrame;
import org.glassfish.tyrus.websockets.frame.ClosingFrame;
import org.glassfish.tyrus.websockets.frame.ContinuationFrame;
import org.glassfish.tyrus.websockets.frame.Frame;
import org.glassfish.tyrus.websockets.frame.PingFrame;
import org.glassfish.tyrus.websockets.frame.PongFrame;
import org.glassfish.tyrus.websockets.frame.TextFrame;

public final class ProtocolHandler {

    private final Charset utf8 = new StrictUtf8();
    private final CharsetDecoder currentDecoder = utf8.newDecoder();
    private final AtomicBoolean onClosedCalled = new AtomicBoolean(false);
    private final boolean maskData;
    private final ParsingState state = new ParsingState();
    private WebSocket webSocket;
    private byte outFragmentedType;
    private ByteBuffer remainder;
    private long writeTimeoutMs = -1;
    private WebSocketContainer container;
    private Writer writer;
    private byte inFragmentedType;
    private boolean processingFragment;

    ProtocolHandler(boolean maskData) {
        this.maskData = maskData;
    }

    public Handshake handshake(org.glassfish.tyrus.spi.WebSocketEngine.ResponseWriter writer, WebSocketApplication app, HandshakeRequest request) {
        final Handshake handshake = createHandShake(request);
        handshake.respond(writer, app/*, ((WebSocketRequest) request.getHttpHeader()).getResponse()*/);
        return handshake;
    }

    public void setWriter(Writer handler) {
        this.writer = handler;
    }

    public void setWebSocket(WebSocket webSocket) {
        this.webSocket = webSocket;
    }

    /**
     * Create {@link Handshake} on server side.
     *
     * @param webSocketRequest representation of received initial HTTP request.
     * @return new {@link Handshake} instance.
     */
    Handshake createHandShake(HandshakeRequest webSocketRequest) {
        return Handshake.createServerHandShake(webSocketRequest);
    }

    /**
     * Create {@link Handshake} on client side.
     *
     * @param webSocketRequest representation of HTTP request to be sent.
     * @return new {@link Handshake} instance.
     */
    public Handshake createClientHandShake(WebSocketRequest webSocketRequest) {
        return Handshake.createClientHandShake(webSocketRequest);
    }

    public final Future<DataFrame> send(DataFrame frame, boolean useTimeout) {
        return send(frame, null, useTimeout);
    }

    public final Future<DataFrame> send(DataFrame frame) {
        return send(frame, null, true);
    }

    Future<DataFrame> send(DataFrame frame,
                           Writer.CompletionHandler<DataFrame> completionHandler, Boolean useTimeout) {
        return write(frame, completionHandler, useTimeout);
    }

    Future<DataFrame> send(byte[] frame,
                           Writer.CompletionHandler<DataFrame> completionHandler, Boolean useTimeout) {
        return write(frame, completionHandler, useTimeout);
    }

    public Future<DataFrame> send(byte[] data) {
        return send(new DataFrame(new BinaryFrame(), data), null, true);
    }

    public Future<DataFrame> send(String data) {
        return send(new DataFrame(new TextFrame(), data));
    }

    public Future<DataFrame> sendRawFrame(byte[] data) {
        return send(data, null, true);
    }

    public Future<DataFrame> stream(boolean last, byte[] bytes, int off, int len) {
        return send(new DataFrame(new BinaryFrame(), Arrays.copyOfRange(bytes, off, off + len), last));
    }

    public Future<DataFrame> stream(boolean last, String fragment) {
        return send(new DataFrame(new TextFrame(), fragment, last));
    }

    public Future<DataFrame> close(final int code, final String reason) {
        final ClosingDataFrame outgoingClosingFrame;
        final CloseReason closeReason = new CloseReason(CloseReason.CloseCodes.getCloseCode(code), reason);

        if (code == CloseReason.CloseCodes.NO_STATUS_CODE.getCode() || code == CloseReason.CloseCodes.CLOSED_ABNORMALLY.getCode()
                || code == CloseReason.CloseCodes.TLS_HANDSHAKE_FAILURE.getCode()) {
            outgoingClosingFrame = new ClosingDataFrame(CloseReason.CloseCodes.NORMAL_CLOSURE.getCode(), reason);
        } else {
            outgoingClosingFrame = new ClosingDataFrame(code, reason);
        }

        return send(outgoingClosingFrame, new Writer.CompletionHandler<DataFrame>() {

            @Override
            public void cancelled() {
                if (webSocket != null && !onClosedCalled.getAndSet(true)) {
                    webSocket.onClose(closeReason);
                }
            }

            @Override
            public void failed(final Throwable throwable) {
                if (webSocket != null && !onClosedCalled.getAndSet(true)) {
                    webSocket.onClose(closeReason);
                }
            }

            @Override
            public void completed(DataFrame result) {
                if (!maskData && (webSocket != null) && !onClosedCalled.getAndSet(true)) {
                    webSocket.onClose(closeReason);
                }
            }
        }, false);
    }

    @SuppressWarnings({"unchecked"})
    private Future<DataFrame> write(final DataFrame frame, final Writer.CompletionHandler<DataFrame> completionHandler, boolean useTimeout) {
        final Writer localWriter = writer;
        final WriteFuture<DataFrame> future = new WriteFuture<DataFrame>();

        if (localWriter == null) {
            throw new IllegalStateException("Connection is null");
        }


        if (useTimeout && writeTimeoutMs > 0 && container instanceof ExecutorServiceProvider) {
            ExecutorService executor = ((ExecutorServiceProvider) container).getExecutorService();
            try {
                executor.submit(new Runnable() {

                    @Override
                    public void run() {
                        final byte[] bytes = frame(frame);
                        localWriter.write(bytes, new CompletionHandlerWrapper(completionHandler, future, frame));
                    }
                }).get(writeTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                future.setFailure(e);
            } catch (ExecutionException e) {
                future.setFailure(e);
            } catch (TimeoutException e) {
                future.setFailure(e);
            }
        } else {
            final byte[] bytes = frame(frame);
            localWriter.write(bytes, new CompletionHandlerWrapper(completionHandler, future, frame));
        }

        return future;
    }

    @SuppressWarnings({"unchecked"})
    private Future<DataFrame> write(final byte[] frame, final Writer.CompletionHandler<DataFrame> completionHandler, boolean useTimeout) {
        final Writer localWriter = writer;
        final WriteFuture<DataFrame> future = new WriteFuture<DataFrame>();

        if (localWriter == null) {
            throw new IllegalStateException("Connection is null");
        }


        if (useTimeout && writeTimeoutMs > 0 && container instanceof ExecutorServiceProvider) {
            ExecutorService executor = ((ExecutorServiceProvider) container).getExecutorService();
            try {
                executor.submit(new Runnable() {

                    @Override
                    public void run() {
                        localWriter.write(frame, new CompletionHandlerWrapper(completionHandler, future, null));
                    }
                }).get(writeTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                future.setFailure(e);
            } catch (ExecutionException e) {
                future.setFailure(e);
            } catch (TimeoutException e) {
                future.setFailure(e);
            }
        } else {
            localWriter.write(frame, new CompletionHandlerWrapper(completionHandler, future, null));
        }

        return future;
    }

    public DataFrame unframe(ByteBuffer buffer) {
        return parse(buffer);
    }

    /**
     * Convert a byte[] to a long. Used for rebuilding payload length.
     *
     * @param bytes byte array to be converted.
     * @return converted byte array.
     */
    long decodeLength(byte[] bytes) {
        return TyrusWebSocketEngine.toLong(bytes, 0, bytes.length);
    }

    /**
     * Converts the length given to the appropriate framing data: <ol> <li>0-125 one element that is the payload length.
     * <li>up to 0xFFFF, 3 element array starting with 126 with the following 2 bytes interpreted as a 16 bit unsigned
     * integer showing the payload length. <li>else 9 element array starting with 127 with the following 8 bytes
     * interpreted as a 64-bit unsigned integer (the high bit must be 0) showing the payload length. </ol>
     *
     * @param length the payload size
     * @return the array
     */
    byte[] encodeLength(final long length) {
        byte[] lengthBytes;
        if (length <= 125) {
            lengthBytes = new byte[1];
            lengthBytes[0] = (byte) length;
        } else {
            byte[] b = TyrusWebSocketEngine.toArray(length);
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

    void validate(final byte fragmentType, byte opcode) {
        if (fragmentType != 0 && opcode != fragmentType && !isControlFrame(opcode)) {
            throw new WebSocketException("Attempting to send a message while sending fragments of another");
        }
    }

    byte checkForLastFrame(DataFrame frame, byte opcode) {
        byte local = opcode;
        if (!frame.isLast()) {
            validate(outFragmentedType, local);
            if (outFragmentedType != 0) {
                local = 0x00;
            } else {
                outFragmentedType = local;
                local &= 0x7F;
            }
        } else if (outFragmentedType != 0) {
            local = (byte) 0x80;
            outFragmentedType = 0;
        } else {
            local |= 0x80;
        }
        return local;
    }

    public void doClose() {
        final Writer localWriter = writer;
        if (localWriter == null) {
            throw new IllegalStateException("Connection is null");
        }

        try {
            localWriter.close();
        } catch (IOException e) {
            throw new IllegalStateException("IOException thrown when closing connection", e);
        }
    }

    void utf8Decode(boolean finalFragment, byte[] data, DataFrame dataFrame) {
        final ByteBuffer b = getByteBuffer(data);
        int n = (int) (b.remaining() * currentDecoder.averageCharsPerByte());
        CharBuffer cb = CharBuffer.allocate(n);
        for (; ; ) {
            CoderResult result = currentDecoder.decode(b, cb, finalFragment);
            if (result.isUnderflow()) {
                if (finalFragment) {
                    currentDecoder.flush(cb);
                    if (b.hasRemaining()) {
                        throw new IllegalStateException("Final UTF-8 fragment received, but not all bytes consumed by decode process");
                    }
                    currentDecoder.reset();
                } else {
                    if (b.hasRemaining()) {
                        remainder = b;
                    }
                }
                cb.flip();
                String res = cb.toString();
                dataFrame.setPayload(res);
                dataFrame.setPayload(Utf8Utils.encode(new StrictUtf8(), res));
                break;
            }
            if (result.isOverflow()) {
                CharBuffer tmp = CharBuffer.allocate(2 * n + 1);
                cb.flip();
                tmp.put(cb);
                cb = tmp;
                continue;
            }
            if (result.isError() || result.isMalformed()) {
                throw new Utf8DecodingError("Illegal UTF-8 Sequence");
            }
        }
    }

    ByteBuffer getByteBuffer(final byte[] data) {
        if (remainder == null) {
            return ByteBuffer.wrap(data);
        } else {
            final int rem = remainder.remaining();
            final byte[] orig = remainder.array();
            byte[] b = new byte[rem + data.length];
            System.arraycopy(orig, orig.length - rem, b, 0, rem);
            System.arraycopy(data, 0, b, rem, data.length);
            remainder = null;
            return ByteBuffer.wrap(b);
        }
    }

    /**
     * Sets the timeout for the writing operation.
     *
     * @param timeoutMs timeout in milliseconds.
     */
    public void setWriteTimeout(long timeoutMs) {
        this.writeTimeoutMs = timeoutMs;
    }

    /**
     * Sets the container.
     *
     * @param container container.
     */
    public void setContainer(WebSocketContainer container) {
        this.container = container;
    }

    public byte[] frame(DataFrame frame) {
        byte opcode = checkForLastFrame(frame, getOpcode(frame.getType()));
        final byte[] bytes = frame.getType().getBytes(frame);
        final byte[] lengthBytes = encodeLength(bytes.length);

        int length = 1 + lengthBytes.length + bytes.length + (maskData ? TyrusWebSocketEngine.MASK_SIZE : 0);
        int payloadStart = 1 + lengthBytes.length + (maskData ? TyrusWebSocketEngine.MASK_SIZE : 0);
        final byte[] packet = new byte[length];
        packet[0] = opcode;
        System.arraycopy(lengthBytes, 0, packet, 1, lengthBytes.length);
        if (maskData) {
            Masker masker = new Masker();
            packet[1] |= 0x80;
            masker.mask(packet, payloadStart, bytes);
            System.arraycopy(masker.getMask(), 0, packet, payloadStart - TyrusWebSocketEngine.MASK_SIZE,
                    TyrusWebSocketEngine.MASK_SIZE);
        } else {
            System.arraycopy(bytes, 0, packet, payloadStart, bytes.length);
        }
        return packet;
    }

    DataFrame parse(ByteBuffer buffer) {
        DataFrame dataFrame;

        try {
            switch (state.state) {
                case 0:
                    if (buffer.remaining() < 2) {
                        // Don't have enough bytes to read opcode and lengthCode
                        return null;
                    }

                    byte opcode = buffer.get();
                    boolean rsvBitSet = isBitSet(opcode, 6)
                            || isBitSet(opcode, 5)
                            || isBitSet(opcode, 4);
                    if (rsvBitSet) {
                        throw new ProtocolError("RSV bit(s) incorrectly set.");
                    }
                    state.finalFragment = isBitSet(opcode, 7);
                    state.controlFrame = isControlFrame(opcode);
                    state.opcode = (byte) (opcode & 0x7f);
                    state.frame = valueOf(inFragmentedType, state.opcode);
                    if (!state.finalFragment && state.controlFrame) {
                        throw new ProtocolError("Fragmented control frame");
                    }

                    if (!state.controlFrame) {
                        if (isContinuationFrame(state.opcode) && !processingFragment) {
                            throw new ProtocolError("End fragment sent, but wasn't processing any previous fragments");
                        }
                        if (processingFragment && !isContinuationFrame(state.opcode)) {
                            throw new ProtocolError("Fragment sent but opcode was not 0");
                        }
                        if (!state.finalFragment && !isContinuationFrame(state.opcode)) {
                            processingFragment = true;
                        }
                        if (!state.finalFragment) {
                            if (inFragmentedType == 0) {
                                inFragmentedType = state.opcode;
                            }
                        }
                    }
                    byte lengthCode = buffer.get();

                    state.masked = (lengthCode & 0x80) == 0x80;
                    state.masker = new Masker(buffer);
                    if (state.masked) {
                        lengthCode ^= 0x80;
                    }
                    state.lengthCode = lengthCode;

                    state.state++;

                case 1:
                    if (state.lengthCode <= 125) {
                        state.length = state.lengthCode;
                    } else {
                        if (state.controlFrame) {
                            throw new ProtocolError("Control frame payloads must be no greater than 125 bytes.");
                        }

                        final int lengthBytes = state.lengthCode == 126 ? 2 : 8;
                        if (buffer.remaining() < lengthBytes) {
                            // Don't have enought bytes to read length
                            return null;
                        }
                        state.masker.setBuffer(buffer);
                        state.length = decodeLength(state.masker.unmask(lengthBytes));
                    }
                    state.state++;
                case 2:
                    if (state.masked) {
                        if (buffer.remaining() < TyrusWebSocketEngine.MASK_SIZE) {
                            // Don't have enough bytes to read mask
                            return null;
                        }
                        state.masker.setBuffer(buffer);
                        state.masker.readMask();
                    }
                    state.state++;
                case 3:
                    if (buffer.remaining() < state.length) {
                        return null;
                    }

                    state.masker.setBuffer(buffer);
                    final byte[] data = state.masker.unmask((int) state.length);
                    if (data.length != state.length) {
                        throw new ProtocolError(String.format("Data read (%s) is not the expected" +
                                " size (%s)", data.length, state.length));
                    }
                    dataFrame = state.frame.create(state.finalFragment, data);

                    if (!state.controlFrame && (isTextFrame(state.opcode) || inFragmentedType == 1)) {
                        utf8Decode(state.finalFragment, data, dataFrame);
                    }

                    if (!state.controlFrame && state.finalFragment) {
                        inFragmentedType = 0;
                        processingFragment = false;
                    }
                    state.recycle();

                    break;
                default:
                    // Should never get here
                    throw new IllegalStateException("Unexpected state: " + state.state);
            }
        } catch (Exception e) {
            state.recycle();
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(e);
            }
        }

        return dataFrame;

    }

    boolean isControlFrame(byte opcode) {
        return (opcode & 0x08) == 0x08;
    }

    private boolean isBitSet(final byte b, int bit) {
        return ((b >> bit & 1) != 0);
    }

    private boolean isContinuationFrame(byte opcode) {
        return opcode == 0;
    }

    private boolean isTextFrame(byte opcode) {
        return opcode == 1;
    }

    private byte getOpcode(Frame type) {
        if (type instanceof TextFrame) {
            return 0x01;
        } else if (type instanceof BinaryFrame) {
            return 0x02;
        } else if (type instanceof ClosingFrame) {
            return 0x08;
        } else if (type instanceof PingFrame) {
            return 0x09;
        } else if (type instanceof PongFrame) {
            return 0x0A;
        }

        throw new ProtocolError("Unknown frame type: " + type.getClass().getName());
    }

    private Frame valueOf(byte fragmentType, byte value) {
        final int opcode = value & 0xF;
        switch (opcode) {
            case 0x00:
                return new ContinuationFrame((fragmentType & 0x01) == 0x01);
            case 0x01:
                return new TextFrame();
            case 0x02:
                return new BinaryFrame();
            case 0x08:
                return new ClosingFrame();
            case 0x09:
                return new PingFrame();
            case 0x0A:
                return new PongFrame();
            default:
                throw new ProtocolError(String.format("Unknown frame type: %s, %s",
                        Integer.toHexString(opcode & 0xFF).toUpperCase(Locale.US), writer));
        }
    }

    /**
     * Handler passed to the {@link org.glassfish.tyrus.spi.Writer}.
     */
    private static class CompletionHandlerWrapper extends Writer.CompletionHandler<byte[]> {

        private final Writer.CompletionHandler<DataFrame> frameCompletionHandler;
        private final WriteFuture<DataFrame> future;
        private final DataFrame frame;

        private CompletionHandlerWrapper(Writer.CompletionHandler<DataFrame> frameCompletionHandler, WriteFuture<DataFrame> future, DataFrame frame) {
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
                future.setFailure(new RuntimeException("Frame writing was canceled."));
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
        public void completed(byte[] result) {
            if (frameCompletionHandler != null) {
                frameCompletionHandler.completed(frame);
            }

            if (future != null) {
                future.setResult(frame);
            }
        }

        @Override
        public void updated(byte[] result) {
            if (frameCompletionHandler != null) {
                frameCompletionHandler.updated(frame);
            }
        }
    }

    private static class ParsingState {
        int state = 0;
        byte opcode = (byte) -1;
        long length = -1;
        Frame frame;
        boolean masked;
        Masker masker;
        boolean finalFragment;
        boolean controlFrame;
        private byte lengthCode = -1;

        void recycle() {
            state = 0;
            opcode = (byte) -1;
            length = -1;
            lengthCode = -1;
            masked = false;
            masker = null;
            finalFragment = false;
            controlFrame = false;
            frame = null;
        }
    }
}