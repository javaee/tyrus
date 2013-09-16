/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import javax.websocket.CloseReason;
import javax.websocket.EncodeException;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;

import org.glassfish.tyrus.spi.RemoteEndpoint;
import static org.glassfish.tyrus.websockets.Utils.checkNotNull;

/**
 * Wraps the {@link javax.websocket.RemoteEndpoint} and represents the other side of the websocket connection.
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Martin Matula (martin.matula at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public abstract class RemoteEndpointWrapper implements javax.websocket.RemoteEndpoint {

    protected final RemoteEndpoint remoteEndpoint;
    protected final TyrusSession session;
    protected final TyrusEndpointWrapper tyrusEndpointWrapper;

    private RemoteEndpointWrapper(TyrusSession session, RemoteEndpoint remoteEndpoint, TyrusEndpointWrapper tyrusEndpointWrapper) {
        this.remoteEndpoint = remoteEndpoint;
        this.tyrusEndpointWrapper = tyrusEndpointWrapper;
        this.session = session;
    }

    static class Basic extends RemoteEndpointWrapper implements javax.websocket.RemoteEndpoint.Basic {

        Basic(TyrusSession session, RemoteEndpoint remoteEndpoint, TyrusEndpointWrapper tyrusEndpointWrapper) {
            super(session, remoteEndpoint, tyrusEndpointWrapper);
        }

        @Override
        public void sendText(String text) throws IOException {
            checkNotNull(text, "Argument 'text' cannot be null.");
            super.sendSyncText(text);
            session.restartIdleTimeoutExecutor();
        }

        @Override
        public void sendBinary(ByteBuffer data) throws IOException {
            checkNotNull(data, "Argument 'data' cannot be null.");
            super.sendSyncBinary(data);
            session.restartIdleTimeoutExecutor();
        }

        @Override
        public void sendText(String partialMessage, boolean isLast) throws IOException {
            checkNotNull(partialMessage, "Argument 'partialMessage' cannot be null.");
            remoteEndpoint.sendText(partialMessage, isLast);
            session.restartIdleTimeoutExecutor();
        }

        @Override
        public void sendBinary(ByteBuffer partialByte, boolean isLast) throws IOException {
            checkNotNull(partialByte, "Argument 'partialByte' cannot be null.");
            remoteEndpoint.sendBinary(partialByte, isLast);
            session.restartIdleTimeoutExecutor();
        }

        @Override
        public void sendObject(Object data) throws IOException, EncodeException {
            checkNotNull(data, "Argument 'data' cannot be null.");
            super.sendSyncObject(data);
            session.restartIdleTimeoutExecutor();
        }

        @Override
        public OutputStream getSendStream() throws IOException {
            return new OutputStreamToAsyncBinaryAdapter(remoteEndpoint);
        }

        @Override
        public Writer getSendWriter() throws IOException {
            return new WriterToAsyncTextAdapter(remoteEndpoint);
        }
    }

    static class Async extends RemoteEndpointWrapper implements javax.websocket.RemoteEndpoint.Async {
        private long sendTimeout;

        Async(TyrusSession session, RemoteEndpoint remoteEndpoint, TyrusEndpointWrapper tyrusEndpointWrapper) {
            super(session, remoteEndpoint, tyrusEndpointWrapper);
        }

        @Override
        public void sendText(String text, SendHandler handler) {
            checkNotNull(text, "Argument 'text' cannot be null.");
            checkNotNull(handler, "Argument 'handler' cannot be null.");
            session.restartIdleTimeoutExecutor();
            sendAsync(text, handler, AsyncMessageType.TEXT);
        }

        @Override
        public Future<Void> sendText(String text) {
            checkNotNull(text, "Argument 'text' cannot be null.");
            session.restartIdleTimeoutExecutor();
            return sendAsync(text, null, AsyncMessageType.TEXT);
        }

        @Override
        public Future<Void> sendBinary(ByteBuffer data) {
            checkNotNull(data, "Argument 'data' cannot be null.");
            session.restartIdleTimeoutExecutor();
            return sendAsync(data, null, AsyncMessageType.BINARY);
        }

        @Override
        public void sendBinary(ByteBuffer data, SendHandler handler) {
            checkNotNull(data, "Argument 'data' cannot be null.");
            checkNotNull(handler, "Argument 'handler' cannot be null.");
            session.restartIdleTimeoutExecutor();
            sendAsync(data, handler, AsyncMessageType.BINARY);
        }

        @Override
        public void sendObject(Object data, SendHandler handler) {
            checkNotNull(data, "Argument 'data' cannot be null.");
            checkNotNull(handler, "Argument 'handler' cannot be null.");
            session.restartIdleTimeoutExecutor();
            sendAsync(data, handler, AsyncMessageType.OBJECT);
        }

        @Override
        public Future<Void> sendObject(Object data) {
            checkNotNull(data, "Argument 'data' cannot be null.");
            session.restartIdleTimeoutExecutor();
            return sendAsync(data, null, AsyncMessageType.OBJECT);
        }

        @Override
        public long getSendTimeout() {
            return sendTimeout;
        }

        @Override
        public void setSendTimeout(long timeoutmillis) {
            sendTimeout = timeoutmillis;
            remoteEndpoint.setWriteTimeout(timeoutmillis);
        }

        /**
         * Sends the message asynchronously (from separate {@link Thread}).
         *
         * @param message message to be sent
         * @param handler message sending callback handler
         * @param type    message type
         * @return message sending callback {@link Future}
         */
        private Future<Void> sendAsync(final Object message, final SendHandler handler, final AsyncMessageType type) {
            final FutureSendResult fsr = new FutureSendResult();

            tyrusEndpointWrapper.container.getExecutorService().execute(new Runnable() {

                @Override
                public void run() {
                    Future<?> result = null;
                    SendResult sr = null;

                    try {
                        switch (type) {
                            case TEXT:
                                result = sendSyncText((String) message);
                                break;

                            case BINARY:
                                result = sendSyncBinary((ByteBuffer) message);
                                break;

                            case OBJECT:
                                result = sendSyncObject(message);
                                break;
                        }

                        if (result != null) {
                            result.get();
                        }
                    } catch (Throwable thw) {
                        sr = new SendResult(thw);
                        fsr.setFailure(thw);
                    } finally {
                        if (sr == null) {
                            sr = new SendResult();
                        }
                        if (handler != null) {
                            handler.onResult(sr);
                        }
                        fsr.setDone();
                    }
                }
            });

            return fsr;
        }

        private static enum AsyncMessageType {
            TEXT, // String
            BINARY,  // ByteBuffer
            OBJECT // OBJECT
        }
    }

    protected Future<?> sendSyncText(String data) throws IOException {
        return remoteEndpoint.sendText(data);
    }

    protected Future<?> sendSyncBinary(ByteBuffer buf) throws IOException {
        return remoteEndpoint.sendBinary(buf);
    }

    @SuppressWarnings("unchecked")
    protected Future<?> sendSyncObject(Object o) throws IOException, EncodeException {
        if (o instanceof String) {
            return remoteEndpoint.sendText((String) o);
        } else {
            Object toSend = tyrusEndpointWrapper.doEncode(session, o);

            if (toSend instanceof String) {
                return remoteEndpoint.sendText((String) toSend);
            } else if (toSend instanceof ByteBuffer) {
                return remoteEndpoint.sendBinary((ByteBuffer) toSend);
            } else if (toSend instanceof StringWriter) {
                StringWriter writer = (StringWriter) toSend;
                StringBuffer sb = writer.getBuffer();
                return remoteEndpoint.sendText(sb.toString());
            } else if (toSend instanceof ByteArrayOutputStream) {
                ByteArrayOutputStream baos = (ByteArrayOutputStream) toSend;
                ByteBuffer buffer = ByteBuffer.wrap(baos.toByteArray());
                return remoteEndpoint.sendBinary(buffer);
            }
        }

        return null;
    }

    @Override
    public void sendPing(ByteBuffer applicationData) throws IOException {
        if (applicationData != null && applicationData.remaining() > 125) {
            throw new IllegalArgumentException("Ping applicationData exceeded the maximum allowed payload of 125 bytes.");
        }
        session.restartIdleTimeoutExecutor();
        remoteEndpoint.sendPing(applicationData);
    }

    @Override
    public void sendPong(ByteBuffer applicationData) throws IOException {
        if (applicationData != null && applicationData.remaining() > 125) {
            throw new IllegalArgumentException("Pong applicationData exceeded the maximum allowed payload of 125 bytes.");
        }
        session.restartIdleTimeoutExecutor();
        remoteEndpoint.sendPong(applicationData);
    }

    @Override
    public String toString() {
        return "Wrapped: " + getClass().getSimpleName();
    }

    @Override
    public void setBatchingAllowed(boolean allowed) {
        // TODO: Implement.
    }

    @Override
    public boolean getBatchingAllowed() {
        return false;  // TODO: Implement.
    }

    @Override
    public void flushBatch() {
        // TODO: Implement.
    }


    public void close(CloseReason cr) {
        Logger.getLogger(RemoteEndpointWrapper.class.getName()).fine("Close public void close(CloseReason cr): " + cr);
        remoteEndpoint.close(cr);
    }
}
