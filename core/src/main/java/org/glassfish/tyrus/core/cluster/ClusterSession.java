/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.core.cluster;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.websocket.CloseReason;
import javax.websocket.EncodeException;
import javax.websocket.Extension;
import javax.websocket.MessageHandler;
import javax.websocket.RemoteEndpoint;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.glassfish.tyrus.core.TyrusEndpointWrapper;
import org.glassfish.tyrus.core.Utils;
import static org.glassfish.tyrus.core.Utils.checkNotNull;

/**
 * Cluster session represents session present on another node.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class ClusterSession implements Session {

    private final RemoteEndpoint.Basic basicRemote;
    private final RemoteEndpoint.Async asyncRemote;
    private final String sessionId;
    private final ClusterContext clusterContext;
    private final Map<DistributedMapKey, Object> distributedPropertyMap;

    public static enum DistributedMapKey implements Serializable {
        NEGOTIATED_SUBPROTOCOL("negotiatedSubprotocol"),
        NEGOTIATED_EXTENSIONS("negotiatedExtensions"),
        SECURE("secure"),
        MAX_IDLE_TIMEOUT("maxIdleTimeout"),
        MAX_BINARY_MESSAGE_BUFFER_SIZE("maxBinaryBufferSize"),
        MAX_TEXT_MESSAGE_BUFFER_SIZE("maxTextBufferSize"),
        REQUEST_URI("requestURI"),
        REQUEST_PARAMETER_MAP("requestParameterMap"),
        QUERY_STRING("queryString"),
        PATH_PARAMETERS("pathParameters"),
        USER_PROPERTIES("userProperties"),
        USER_PRINCIPAL("userPrincipal");

        private final String key;

        DistributedMapKey(String key) {
            this.key = key;
        }

        @Override
        public String toString() {
            return key;
        }
    }

    /**
     * Constructor.
     *
     * @param sessionId       session id.
     * @param clusterContext  cluster context.
     * @param endpointWrapper used just to get encoders/decoders.
     * @param session         used just to get encoders/decoders.
     */
    public ClusterSession(final String sessionId,
                          final ClusterContext clusterContext,
                          final Map<DistributedMapKey, Object> distributedPropertyMap,
                          final TyrusEndpointWrapper endpointWrapper,
                          final Session session) {

        this.sessionId = sessionId;
        this.clusterContext = clusterContext;
        this.distributedPropertyMap = distributedPropertyMap;

        this.basicRemote = new RemoteEndpoint.Basic() {
            @Override
            public void sendText(String text) throws IOException {
                checkNotNull(text, "Argument 'text' cannot be null.");
                final Future<?> future = clusterContext.sendText(sessionId, text);
                processFuture(future);
            }

            @Override
            public void sendBinary(ByteBuffer data) throws IOException {
                checkNotNull(data, "Argument 'data' cannot be null.");
                final Future<?> future = clusterContext.sendBinary(sessionId, Utils.getRemainingArray(data));
                processFuture(future);
            }

            @Override
            public void sendText(String partialMessage, boolean isLast) throws IOException {
                checkNotNull(partialMessage, "Argument 'partialMessage' cannot be null.");
                final Future<?> future = clusterContext.sendText(sessionId, partialMessage, isLast);
                processFuture(future);
            }

            @Override
            public void sendBinary(ByteBuffer partialByte, boolean isLast) throws IOException {
                checkNotNull(partialByte, "Argument 'partialByte' cannot be null.");
                final Future<?> future = clusterContext.sendBinary(sessionId, Utils.getRemainingArray(partialByte), isLast);
                processFuture(future);
            }

            /**
             * Wait for the future to be completed.
             * <p/>
             * {@link java.util.concurrent.Future#get()} will be invoked and exception processed (if thrown).
             *
             * @param future to be processed.
             * @throws IOException when {@link java.io.IOException} is the cause of thrown {@link java.util.concurrent.ExecutionException}
             *                     it will be extracted and rethrown. Otherwise whole ExecutionException will be rethrown wrapped in {@link java.io.IOException}.
             */
            private void processFuture(Future<?> future) throws IOException {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof IOException) {
                        throw (IOException) e.getCause();
                    } else {
                        throw new IOException(e.getCause());
                    }
                }
            }

            @Override
            public void sendPing(ByteBuffer applicationData) throws IOException, IllegalArgumentException {
                if (applicationData != null && applicationData.remaining() > 125) {
                    throw new IllegalArgumentException("Ping applicationData exceeded the maximum allowed payload of 125 bytes.");
                }
                clusterContext.sendPing(sessionId, Utils.getRemainingArray(applicationData));
            }

            @Override
            public void sendPong(ByteBuffer applicationData) throws IOException, IllegalArgumentException {
                if (applicationData != null && applicationData.remaining() > 125) {
                    throw new IllegalArgumentException("Pong applicationData exceeded the maximum allowed payload of 125 bytes.");
                }
                clusterContext.sendPong(sessionId, Utils.getRemainingArray(applicationData));
            }

            @Override
            public void sendObject(Object data) throws IOException, EncodeException {

                // TODO 1: where should we handle encoding?
                // TODO 1: encoding instances cannot be shared among the cluster
                // TODO 1: would be fair to pass this object to Session owner, but
                // TODO 1: then all passed objects needs to be serializable..

                // TODO 2: using current session encoders and then sending to remote session
                // TODO 2: uncertain what happens when current session is closed anytime during the process

                checkNotNull(data, "Argument 'data' cannot be null.");

                final Future<Void> future;
                if (data instanceof String) {
                    future = clusterContext.sendText(sessionId, (String) data);
                } else {
                    final Object toSend = endpointWrapper.doEncode(session, data);
                    if (toSend instanceof String) {
                        future = clusterContext.sendText(sessionId, (String) toSend);
                    } else if (toSend instanceof ByteBuffer) {
                        future = clusterContext.sendBinary(sessionId, Utils.getRemainingArray((ByteBuffer) toSend));
                    } else if (toSend instanceof StringWriter) {
                        StringWriter writer = (StringWriter) toSend;
                        StringBuffer sb = writer.getBuffer();
                        future = clusterContext.sendText(sessionId, sb.toString());
                    } else if (toSend instanceof ByteArrayOutputStream) {
                        ByteArrayOutputStream baos = (ByteArrayOutputStream) toSend;
                        future = clusterContext.sendBinary(sessionId, baos.toByteArray());
                    } else {
                        // will never happen.
                        future = null;
                    }

                }
                processFuture(future);
            }

            @Override
            public OutputStream getSendStream() throws IOException {
                return new OutputStream() {
                    @Override
                    public void write(byte b[], int off, int len) throws IOException {
                        if (b == null) {
                            throw new NullPointerException();
                        } else if ((off < 0) || (off > b.length) || (len < 0) ||
                                ((off + len) > b.length) || ((off + len) < 0)) {
                            throw new IndexOutOfBoundsException();
                        } else if (len == 0) {
                            return;
                        }

                        byte[] toSend = new byte[len];
                        System.arraycopy(b, off, toSend, 0, len);

                        final Future<?> future = clusterContext.sendBinary(sessionId, toSend, false);
                        try {
                            future.get();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } catch (ExecutionException e) {
                            if (e.getCause() instanceof IOException) {
                                throw (IOException) e.getCause();
                            } else {
                                throw new IOException(e.getCause());
                            }
                        }
                    }

                    @Override
                    public void write(int i) throws IOException {
                        byte[] byteArray = new byte[]{(byte) i};

                        write(byteArray, 0, byteArray.length);
                    }

                    @Override
                    public void flush() throws IOException {
                        // do nothing.
                    }

                    @Override
                    public void close() throws IOException {
                        clusterContext.sendBinary(sessionId, new byte[]{}, true);
                    }
                };
            }

            @Override
            public Writer getSendWriter() throws IOException {
                return new Writer() {
                    private String buffer = null;

                    private void sendBuffer(boolean last) {
                        clusterContext.sendText(sessionId, buffer, last);
                    }

                    @Override
                    public void write(char[] chars, int index, int len) throws IOException {
                        if (buffer != null) {
                            this.sendBuffer(false);
                        }
                        buffer = (new String(chars)).substring(index, index + len);

                    }

                    @Override
                    public void flush() throws IOException {
                        this.sendBuffer(false);
                        buffer = null;
                    }

                    @Override
                    public void close() throws IOException {
                        this.sendBuffer(true);
                    }
                };
            }


            @Override
            public void setBatchingAllowed(boolean allowed) throws IOException {

            }

            @Override
            public boolean getBatchingAllowed() {
                return false;
            }

            @Override
            public void flushBatch() throws IOException {

            }
        };


        this.asyncRemote = new RemoteEndpoint.Async() {
            @Override
            // TODO XXX FIXME
            public long getSendTimeout() {
                return 0;
            }

            @Override
            // TODO XXX FIXME
            public void setSendTimeout(long timeoutmillis) {

            }

            @Override
            public void sendText(String text, SendHandler handler) {
                checkNotNull(text, "Argument 'text' cannot be null.");
                checkNotNull(handler, "Argument 'handler' cannot be null.");
                clusterContext.sendText(sessionId, text, handler);
            }

            @Override
            public Future<Void> sendText(String text) {
                checkNotNull(text, "Argument 'text' cannot be null.");
                return clusterContext.sendText(sessionId, text);
            }

            @Override
            public Future<Void> sendBinary(ByteBuffer data) {
                checkNotNull(data, "Argument 'data' cannot be null.");
                return clusterContext.sendBinary(sessionId, Utils.getRemainingArray(data));
            }

            @Override
            public void sendBinary(ByteBuffer data, SendHandler handler) {
                checkNotNull(data, "Argument 'data' cannot be null.");
                checkNotNull(handler, "Argument 'handler' cannot be null.");
                clusterContext.sendBinary(sessionId, Utils.getRemainingArray(data), handler);
            }

            @Override
            public Future<Void> sendObject(Object data) {

                // TODO 1: where should we handle encoding?
                // TODO 1: encoding instances cannot be shared among the cluster
                // TODO 1: would be fair to pass this object to Session owner, but
                // TODO 1: then all passed objects needs to be serializable..

                // TODO 2: using current session encoders and then sending to remote session
                // TODO 2: uncertain what happens when current session is closed anytime during the process

                checkNotNull(data, "Argument 'data' cannot be null.");

                final Future<Void> future;
                if (data instanceof String) {
                    future = clusterContext.sendText(sessionId, (String) data);
                } else {
                    final Object toSend;
                    try {
                        toSend = endpointWrapper.doEncode(session, data);
                    } catch (final Exception e) {
                        return new Future<Void>() {
                            @Override
                            public boolean cancel(boolean mayInterruptIfRunning) {
                                return false;
                            }

                            @Override
                            public boolean isCancelled() {
                                return false;
                            }

                            @Override
                            public boolean isDone() {
                                return true;
                            }

                            @Override
                            public Void get() throws InterruptedException, ExecutionException {
                                throw new ExecutionException(e);
                            }

                            @Override
                            public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                                throw new ExecutionException(e);
                            }
                        };
                    }
                    if (toSend instanceof String) {
                        future = clusterContext.sendText(sessionId, (String) toSend);
                    } else if (toSend instanceof ByteBuffer) {
                        future = clusterContext.sendBinary(sessionId, Utils.getRemainingArray((ByteBuffer) toSend));
                    } else if (toSend instanceof StringWriter) {
                        StringWriter writer = (StringWriter) toSend;
                        StringBuffer sb = writer.getBuffer();
                        future = clusterContext.sendText(sessionId, sb.toString());
                    } else if (toSend instanceof ByteArrayOutputStream) {
                        ByteArrayOutputStream baos = (ByteArrayOutputStream) toSend;
                        future = clusterContext.sendBinary(sessionId, baos.toByteArray());
                    } else {
                        // will never happen.
                        future = null;
                    }
                }

                return future;
            }

            @Override
            public void sendObject(Object data, SendHandler handler) {

                // TODO 1: where should we handle encoding?
                // TODO 1: encoding instances cannot be shared among the cluster
                // TODO 1: would be fair to pass this object to Session owner, but
                // TODO 1: then all passed objects needs to be serializable..

                // TODO 2: using current session encoders and then sending to remote session
                // TODO 2: uncertain what happens when current session is closed anytime during the process

                checkNotNull(data, "Argument 'data' cannot be null.");

                final Future<Void> future;
                if (data instanceof String) {
                    future = clusterContext.sendText(sessionId, (String) data);
                } else {
                    final Object toSend;
                    try {
                        toSend = endpointWrapper.doEncode(session, data);
                    } catch (final Throwable t) {
                        handler.onResult(new SendResult(t));
                        return;
                    }
                    if (toSend instanceof String) {
                        clusterContext.sendText(sessionId, (String) toSend, handler);
                    } else if (toSend instanceof ByteBuffer) {
                        clusterContext.sendBinary(sessionId, Utils.getRemainingArray((ByteBuffer) toSend), handler);
                    } else if (toSend instanceof StringWriter) {
                        StringWriter writer = (StringWriter) toSend;
                        StringBuffer sb = writer.getBuffer();
                        clusterContext.sendText(sessionId, sb.toString(), handler);
                    } else if (toSend instanceof ByteArrayOutputStream) {
                        ByteArrayOutputStream baos = (ByteArrayOutputStream) toSend;
                        clusterContext.sendBinary(sessionId, baos.toByteArray(), handler);
                    }
                }

            }

            @Override
            public void sendPing(ByteBuffer applicationData) throws IOException, IllegalArgumentException {
                if (applicationData != null && applicationData.remaining() > 125) {
                    throw new IllegalArgumentException("Ping applicationData exceeded the maximum allowed payload of 125 bytes.");
                }
                clusterContext.sendPing(sessionId, Utils.getRemainingArray(applicationData));
            }

            @Override
            public void sendPong(ByteBuffer applicationData) throws IOException, IllegalArgumentException {
                if (applicationData != null && applicationData.remaining() > 125) {
                    throw new IllegalArgumentException("Pong applicationData exceeded the maximum allowed payload of 125 bytes.");
                }
                clusterContext.sendPong(sessionId, Utils.getRemainingArray(applicationData));
            }

            @Override
            public void setBatchingAllowed(boolean allowed) throws IOException {

            }

            @Override
            public boolean getBatchingAllowed() {
                return false;
            }

            @Override
            public void flushBatch() throws IOException {

            }
        };
    }

    /**
     * Not supported (yet?).
     *
     * @return nothing.
     */
    @Override
    public WebSocketContainer getContainer() {
        throw new UnsupportedOperationException();
    }

    /**
     * Not supported (yet?).
     *
     * @param handler nothing.
     * @throws IllegalStateException newer.
     */
    @Override
    public void addMessageHandler(MessageHandler handler) throws IllegalStateException {
        throw new UnsupportedOperationException();
    }

    /**
     * Not supported (yet?).
     *
     * @return nothing.
     */
    @Override
    public Set<MessageHandler> getMessageHandlers() {
        throw new UnsupportedOperationException();
    }

    /**
     * Not supported (yet?).
     *
     * @param handler nothing.
     */
    @Override
    public void removeMessageHandler(MessageHandler handler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getProtocolVersion() {
        return "13";
    }

    @Override
    public String getNegotiatedSubprotocol() {
        return (String) distributedPropertyMap.get(DistributedMapKey.NEGOTIATED_SUBPROTOCOL);
    }

    @Override
    public List<Extension> getNegotiatedExtensions() {
        //noinspection unchecked
        return (List<Extension>) distributedPropertyMap.get(DistributedMapKey.NEGOTIATED_EXTENSIONS);
    }

    @Override
    public boolean isSecure() {
        //noinspection unchecked
        return (Boolean) distributedPropertyMap.get(DistributedMapKey.SECURE);
    }

    @Override
    public boolean isOpen() {
        // unsupportedException? isOpen is not very usable in clustered scenario; the call itself can be invoked on
        // the real session, but the result will be meaningless in the time it gets back to original invoker.
        return true;
    }

    @Override
    public long getMaxIdleTimeout() {
        //noinspection unchecked
        return (Long) distributedPropertyMap.get(DistributedMapKey.MAX_IDLE_TIMEOUT);
    }

    /**
     * Remote setters are not supported (yet?).
     *
     * @param milliseconds nothing.
     */
    @Override
    public void setMaxIdleTimeout(long milliseconds) {
        throw new UnsupportedOperationException();
    }

    /**
     * Remote setters are not supported (yet?).
     *
     * @param length nothing.
     */
    @Override
    public void setMaxBinaryMessageBufferSize(int length) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxBinaryMessageBufferSize() {
        //noinspection unchecked
        return (Integer) distributedPropertyMap.get(DistributedMapKey.MAX_BINARY_MESSAGE_BUFFER_SIZE);
    }

    /**
     * Remote setters are not supported (yet?).
     *
     * @param length nothing.
     */
    @Override
    public void setMaxTextMessageBufferSize(int length) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxTextMessageBufferSize() {
        //noinspection unchecked
        return (Integer) distributedPropertyMap.get(DistributedMapKey.MAX_TEXT_MESSAGE_BUFFER_SIZE);
    }

    @Override
    public RemoteEndpoint.Async getAsyncRemote() {
        return asyncRemote;
    }

    @Override
    public RemoteEndpoint.Basic getBasicRemote() {
        return basicRemote;
    }

    @Override
    public String getId() {
        return sessionId;
    }

    @Override
    public void close() throws IOException {
        clusterContext.close(sessionId);
    }

    @Override
    public void close(CloseReason closeReason) throws IOException {
        clusterContext.close(sessionId, closeReason);
    }

    @Override
    public URI getRequestURI() {
        //noinspection unchecked
        return (URI) distributedPropertyMap.get(DistributedMapKey.REQUEST_URI);
    }

    @Override
    public Map<String, List<String>> getRequestParameterMap() {
        //noinspection unchecked
        return (Map<String, List<String>>) distributedPropertyMap.get(DistributedMapKey.REQUEST_PARAMETER_MAP);
    }

    @Override
    public String getQueryString() {
        return (String) distributedPropertyMap.get(DistributedMapKey.QUERY_STRING);
    }

    @Override
    public Map<String, String> getPathParameters() {
        //noinspection unchecked
        return (Map<String, String>) distributedPropertyMap.get(DistributedMapKey.PATH_PARAMETERS);
    }

    @Override
    public Map<String, Object> getUserProperties() {
        //noinspection unchecked
        return (Map<String, Object>) distributedPropertyMap.get(DistributedMapKey.USER_PROPERTIES);
    }

    @Override
    public Principal getUserPrincipal() {
        //noinspection unchecked
        return (Principal) distributedPropertyMap.get(DistributedMapKey.USER_PRINCIPAL);
    }

    /**
     * Not supported.
     * <p/>
     * Session.getOpenSessions()."get(<remoteIndex>)".getOpenSessions() .. why would anyone try to do that?
     *
     * @return nothing.
     */
    @Override
    public Set<Session> getOpenSessions() {
        // Session.getOpenSessions()."get(<remoteIndex>)".getOpenSessions() .. why would anyone try to do that?
        throw new UnsupportedOperationException();
    }
}
