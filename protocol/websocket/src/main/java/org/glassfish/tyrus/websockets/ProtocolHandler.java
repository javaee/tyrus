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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.websocket.WebSocketContainer;

import org.glassfish.tyrus.websockets.draft06.ClosingFrame;
import org.glassfish.tyrus.websockets.frametypes.BinaryFrameType;
import org.glassfish.tyrus.websockets.frametypes.TextFrameType;

public abstract class ProtocolHandler {

    private final Charset utf8 = new StrictUtf8();
    private final CharsetDecoder currentDecoder = utf8.newDecoder();
    private WebSocket webSocket;
    private byte outFragmentedType;
    private ByteBuffer remainder;
    private long writeTimeoutMs = -1;
    private WebSocketContainer container;

    protected final boolean maskData;
    protected Connection connection;
    protected byte inFragmentedType;
    protected boolean processingFragment;

    protected ProtocolHandler(boolean maskData) {
        this.maskData = maskData;
    }

    public HandShake handshake(Connection connection, WebSocketApplication app, WebSocketRequest request) {
        final HandShake handshake = createHandShake(request);
        handshake.respond(connection, app/*, ((WebSocketRequest) request.getHttpHeader()).getResponse()*/);
        return handshake;
    }

    public final Future<DataFrame> send(DataFrame frame, boolean useTimeout) {
        return send(frame, null, useTimeout);
    }

    public final Future<DataFrame> send(DataFrame frame) {
        return send(frame, null, true);
    }

    public Future<DataFrame> send(DataFrame frame,
                                  Connection.CompletionHandler<DataFrame> completionHandler, Boolean useTimeout) {
        return write(frame, completionHandler, useTimeout);
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection handler) {
        this.connection = handler;
    }

    public WebSocket getWebSocket() {
        return webSocket;
    }

    public void setWebSocket(WebSocket webSocket) {
        this.webSocket = webSocket;
    }

    public boolean isMaskData() {
        return maskData;
    }

    public abstract byte[] frame(DataFrame frame);

    /**
     * Create {@link HandShake} on server side.
     *
     * @param webSocketRequest representation of received initial HTTP request.
     * @return new {@link HandShake} instance.
     */
    protected abstract HandShake createHandShake(WebSocketRequest webSocketRequest);

    /**
     * Create {@link HandShake} on client side.
     *
     * @param webSocketRequest representation of HTTP request to be sent.
     * @return new {@link HandShake} instance.
     */
    public abstract HandShake createClientHandShake(WebSocketRequest webSocketRequest, boolean client);

    public Future<DataFrame> send(byte[] data) {
        return send(new DataFrame(new BinaryFrameType(), data));
    }

    public Future<DataFrame> send(String data) {
        return send(new DataFrame(new TextFrameType(), data));
    }

    public Future<DataFrame> stream(boolean last, byte[] bytes, int off, int len) {
        return send(new DataFrame(new BinaryFrameType(), bytes, last));
    }

    public Future<DataFrame> stream(boolean last, String fragment) {
        return send(new DataFrame(new TextFrameType(), fragment, last));
    }

    public Future<DataFrame> close(int code, String reason) {
        final ClosingFrame closingFrame = new ClosingFrame(code, reason);

        return send(closingFrame,
                new Connection.CompletionHandler<DataFrame>() {

                    @Override
                    public void failed(final Throwable throwable) {
                        if (webSocket != null) {
                            webSocket.onClose(closingFrame);
                        }
                    }

                    @Override
                    public void completed(DataFrame result) {
                        if (!maskData && (webSocket != null)) {
                            webSocket.onClose(closingFrame);
                        }
                    }
                }, false);
    }

    @SuppressWarnings({"unchecked"})
    private Future<DataFrame> write(final DataFrame frame,
                                    final Connection.CompletionHandler<DataFrame> completionHandler, boolean useTimeout) {
        final Connection localConnection = connection;
        final WriteFuture<DataFrame> future = new WriteFuture<DataFrame>();

        if (localConnection == null) {
            throw new IllegalStateException("Connection is null");
        }


        if (writeTimeoutMs > 0 && container instanceof ExecutorServiceProvider) {
            ExecutorService executor = ((ExecutorServiceProvider) container).getExecutorService();
            try {
                executor.submit(new Runnable() {

                    @Override
                    public void run() {
                        Future<DataFrame> result = localConnection.write(frame, completionHandler);
                        try {
                            result.get();
                        } catch (InterruptedException e) {
                            future.setFailure(e);
                        } catch (ExecutionException e) {
                            future.setFailure(e);
                        }
                        future.setResult(frame);
                    }
                }).get(writeTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                future.setFailure(e);
            } catch (ExecutionException e) {
                future.setFailure(e);
            } catch (TimeoutException e) {
                future.setFailure(e);
            }

            return future;
        } else {
            return localConnection.write(frame, completionHandler);
        }
    }

    public DataFrame unframe(ByteBuffer buffer) {
        return parse(buffer);
    }

    protected abstract DataFrame parse(ByteBuffer buffer);

    /**
     * Convert a byte[] to a long. Used for rebuilding payload length.
     *
     * @param bytes byte array to be converted.
     * @return converted byte array.
     */
    protected long decodeLength(byte[] bytes) {
        return WebSocketEngine.toLong(bytes, 0, bytes.length);
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
    protected byte[] encodeLength(final long length) {
        byte[] lengthBytes;
        if (length <= 125) {
            lengthBytes = new byte[1];
            lengthBytes[0] = (byte) length;
        } else {
            byte[] b = WebSocketEngine.toArray(length);
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

    protected abstract boolean isControlFrame(byte opcode);

    protected byte checkForLastFrame(DataFrame frame, byte opcode) {
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
        final Connection localConnection = connection;
        if (localConnection == null) {
            throw new IllegalStateException("Connection is null");
        }

        localConnection.closeSilently();
    }

    protected void utf8Decode(boolean finalFragment, byte[] data, DataFrame dataFrame) {
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

//    public static class WriteConnectionHandler<DataFrame> extends Connection.CompletionHandler<DataFrame> {
//
//        WriteFuture<DataFrame> future;
//
//        public WriteConnectionHandler(WriteFuture<DataFrame> future) {
//            this.future = future;
//        }
//
//        @Override
//        public void completed(DataFrame result) {
//            future.setResult(result);
//        }
//
//        @Override
//        public void failed(Throwable throwable) {
//            future.setFailure(throwable);
//        }
//    }

}
