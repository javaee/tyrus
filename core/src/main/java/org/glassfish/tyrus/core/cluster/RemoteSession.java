/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2016 Oracle and/or its affiliates. All rights reserved.
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
 * Remote session represents session originating from another node.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class RemoteSession implements Session, DistributedSession {

    private static final Integer SYNC_SEND_TIMEOUT = 30;

    private final RemoteEndpoint.Basic basicRemote;
    private final RemoteEndpoint.Async asyncRemote;
    private final String sessionId;
    private final String connectionId;
    private final ClusterContext clusterContext;
    private final Map<DistributedMapKey, Object> distributedPropertyMap;
    private final TyrusEndpointWrapper endpointWrapper;

    public static enum DistributedMapKey implements Serializable {
        /**
         * Negotiated subprotocol.
         * <p>
         * Value must be {@link String}.
         *
         * @see javax.websocket.Session#getNegotiatedSubprotocol()
         */
        NEGOTIATED_SUBPROTOCOL("negotiatedSubprotocol"),
        /**
         * Negotiated extensions.
         * <p>
         * Value must be {@link List}&lt;{@link Extension}&gt;.
         *
         * @see javax.websocket.Session#getNegotiatedExtensions()
         */
        NEGOTIATED_EXTENSIONS("negotiatedExtensions"),
        /**
         * Secure flag.
         * <p>
         * Value must be {@code boolean} or {@link java.lang.Boolean}.
         *
         * @see javax.websocket.Session#isSecure()
         */
        SECURE("secure"),
        /**
         * Max idle timeout.
         * <p>
         * Value must be {@code long} or {@link java.lang.Long}.
         *
         * @see javax.websocket.Session#getMaxIdleTimeout()
         */
        MAX_IDLE_TIMEOUT("maxIdleTimeout"),
        /**
         * Max binary buffer size.
         * <p>
         * Value must be {@code int} or {@link java.lang.Integer}.
         *
         * @see javax.websocket.Session#getMaxBinaryMessageBufferSize()
         */
        MAX_BINARY_MESSAGE_BUFFER_SIZE("maxBinaryBufferSize"),
        /**
         * Max text buffer size.
         * <p>
         * Value must be {@code int} or {@link java.lang.Integer}.
         *
         * @see javax.websocket.Session#getMaxTextMessageBufferSize()
         */
        MAX_TEXT_MESSAGE_BUFFER_SIZE("maxTextBufferSize"),
        /**
         * Request URI.
         * <p>
         * Value must be {@link URI}.
         *
         * @see javax.websocket.Session#getRequestURI()
         */
        REQUEST_URI("requestURI"),
        /**
         * Request Parameter map.
         * <p>
         * Value must be {@link java.util.Map}&lt;{@link String}, {@link java.util.List}&lt;{@link String}&gt;&gt;.
         *
         * @see javax.websocket.Session#getRequestParameterMap()
         */
        REQUEST_PARAMETER_MAP("requestParameterMap"),
        /**
         * Query string.
         * <p>
         * Value must be {@link String}.
         *
         * @see javax.websocket.Session#getQueryString()
         */
        QUERY_STRING("queryString"),
        /**
         * Path parameters.
         * <p>
         * Value must be {@link java.util.Map}&lt;{@link String}, {@link String}&gt;.
         *
         * @see javax.websocket.Session#getPathParameters()
         */
        PATH_PARAMETERS("pathParameters"),
        /**
         * User principal.
         * <p>
         * Value must be {@link java.security.Principal}.
         *
         * @see javax.websocket.Session#getUserPrincipal()
         */
        USER_PRINCIPAL("userPrincipal"),
        /**
         * Cluster connection Id. (internal property).
         * <p>
         * Value must be {@link String}.
         */
        CONNECTION_ID("connectionId");

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
     * @param sessionId              session id.
     * @param clusterContext         cluster context.
     * @param distributedPropertyMap distributed property map.
     * @param endpointWrapper        used just to get encoders/decoders.
     * @param session                used just to get encoders/decoders.
     */
    public RemoteSession(final String sessionId,
                         final ClusterContext clusterContext,
                         final Map<DistributedMapKey, Object> distributedPropertyMap,
                         final TyrusEndpointWrapper endpointWrapper,
                         final Session session) {

        this.sessionId = sessionId;
        this.clusterContext = clusterContext;
        this.distributedPropertyMap = distributedPropertyMap;
        this.endpointWrapper = endpointWrapper;

        this.connectionId = distributedPropertyMap.get(DistributedMapKey.CONNECTION_ID).toString();

        this.basicRemote = new RemoteEndpoint.Basic() {
            @Override
            public void sendText(String text) throws IOException {
                checkNotNull(text, "text");
                final Future<?> future = clusterContext.sendText(sessionId, text);
                processFuture(future);
            }

            @Override
            public void sendBinary(ByteBuffer data) throws IOException {
                checkNotNull(data, "data");
                final Future<?> future = clusterContext.sendBinary(sessionId, Utils.getRemainingArray(data));
                processFuture(future);
            }

            @Override
            public void sendText(String partialMessage, boolean isLast) throws IOException {
                checkNotNull(partialMessage, "partialMessage");
                final Future<?> future = clusterContext.sendText(sessionId, partialMessage, isLast);
                processFuture(future);
            }

            @Override
            public void sendBinary(ByteBuffer partialByte, boolean isLast) throws IOException {
                checkNotNull(partialByte, "partialByte");
                final Future<?> future =
                        clusterContext.sendBinary(sessionId, Utils.getRemainingArray(partialByte), isLast);
                processFuture(future);
            }

            /**
             * Wait for the future to be completed.
             * <p>
             * {@link java.util.concurrent.Future#get()} will be invoked and exception processed (if thrown).
             *
             * @param future to be processed.
             * @throws IOException when {@link java.io.IOException} is the cause of thrown {@link java.util
             * .concurrent.ExecutionException}
             *                     it will be extracted and rethrown. Otherwise whole ExecutionException will be
             *                     rethrown wrapped in {@link java.io.IOException}.
             */
            private void processFuture(Future<?> future) throws IOException {
                try {
                    future.get(SYNC_SEND_TIMEOUT, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof IOException) {
                        throw (IOException) e.getCause();
                    } else {
                        throw new IOException(e.getCause());
                    }
                } catch (TimeoutException e) {
                    throw new IOException(e.getCause());
                }
            }

            @Override
            public void sendPing(ByteBuffer applicationData) throws IOException, IllegalArgumentException {
                if (applicationData != null && applicationData.remaining() > 125) {
                    throw new IllegalArgumentException(
                            "Ping applicationData exceeded the maximum allowed payload of 125 bytes.");
                }
                clusterContext.sendPing(sessionId, Utils.getRemainingArray(applicationData));
            }

            @Override
            public void sendPong(ByteBuffer applicationData) throws IOException, IllegalArgumentException {
                if (applicationData != null && applicationData.remaining() > 125) {
                    throw new IllegalArgumentException(
                            "Pong applicationData exceeded the maximum allowed payload of 125 bytes.");
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

                checkNotNull(data, "data");

                final Future<Void> future;
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
                    return;
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
                        } else if ((off < 0) || (off > b.length) || (len < 0)
                                || ((off + len) > b.length) || ((off + len) < 0)) {
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
                checkNotNull(text, "text");
                checkNotNull(handler, "handler");
                clusterContext.sendText(sessionId, text, handler);
            }

            @Override
            public Future<Void> sendText(String text) {
                checkNotNull(text, "text");
                return clusterContext.sendText(sessionId, text);
            }

            @Override
            public Future<Void> sendBinary(ByteBuffer data) {
                checkNotNull(data, "data");
                return clusterContext.sendBinary(sessionId, Utils.getRemainingArray(data));
            }

            @Override
            public void sendBinary(ByteBuffer data, SendHandler handler) {
                checkNotNull(data, "data");
                checkNotNull(handler, "handler");
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

                checkNotNull(data, "data");

                final Future<Void> future;
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
                        public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException,
                                TimeoutException {
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

                checkNotNull(data, "data");

                if (data instanceof String) {
                    clusterContext.sendText(sessionId, (String) data, handler);
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
                    throw new IllegalArgumentException(
                            "Ping applicationData exceeded the maximum allowed payload of 125 bytes.");
                }
                clusterContext.sendPing(sessionId, Utils.getRemainingArray(applicationData));
            }

            @Override
            public void sendPong(ByteBuffer applicationData) throws IOException, IllegalArgumentException {
                if (applicationData != null && applicationData.remaining() > 125) {
                    throw new IllegalArgumentException(
                            "Pong applicationData exceeded the maximum allowed payload of 125 bytes.");
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
     * Get the version of the websocket protocol currently being used. This is taken as the value of the
     * Sec-WebSocket-Version header used in the opening handshake. i.e. "13".
     *
     * @return the protocol version.
     */
    @Override
    public String getProtocolVersion() {
        return "13";
    }

    /**
     * Get the sub protocol agreed during the websocket handshake for this conversation.
     *
     * @return the negotiated subprotocol, or the empty string if there isn't one.
     */
    @Override
    public String getNegotiatedSubprotocol() {
        return (String) distributedPropertyMap.get(DistributedMapKey.NEGOTIATED_SUBPROTOCOL);
    }

    /**
     * Get the list of extensions currently in use for this conversation.
     *
     * @return the negotiated extensions.
     */
    @Override
    public List<Extension> getNegotiatedExtensions() {
        //noinspection unchecked
        return (List<Extension>) distributedPropertyMap.get(DistributedMapKey.NEGOTIATED_EXTENSIONS);
    }

    /**
     * Get the information about secure transport.
     *
     * @return {@code true} when the underlying socket is using a secure transport, {@code false} otherwise.
     */
    @Override
    public boolean isSecure() {
        //noinspection unchecked
        return (Boolean) distributedPropertyMap.get(DistributedMapKey.SECURE);
    }

    /**
     * Get the information about session state.
     *
     * @return {@code true} when the underlying socket is open, {@code false} otherwise.
     */
    @Override
    public boolean isOpen() {
        return clusterContext.isSessionOpen(sessionId, endpointWrapper.getEndpointPath());
    }

    /**
     * Get the number of milliseconds before this conversation may be closed by the
     * container if it is inactive, i.e. no messages are either sent or received in that time.
     *
     * @return the timeout in milliseconds.
     */
    @Override
    public long getMaxIdleTimeout() {
        //noinspection unchecked
        return (Long) distributedPropertyMap.get(DistributedMapKey.MAX_IDLE_TIMEOUT);
    }

    /**
     * Get the maximum length of incoming binary messages that this Session can buffer. If
     * the implementation receives a binary message that it cannot buffer because it
     * is too large, it must close the session with a close code of {@link CloseReason.CloseCodes#TOO_BIG}.
     *
     * @return the maximum binary message size that can be buffered.
     */
    @Override
    public int getMaxBinaryMessageBufferSize() {
        //noinspection unchecked
        return (Integer) distributedPropertyMap.get(DistributedMapKey.MAX_BINARY_MESSAGE_BUFFER_SIZE);
    }

    /**
     * Get the maximum length of incoming text messages that this Session can buffer. If
     * the implementation receives a text message that it cannot buffer because it
     * is too large, it must close the session with a close code of {@link CloseReason.CloseCodes#TOO_BIG}.
     *
     * @return the maximum text message size that can be buffered.
     */
    @Override
    public int getMaxTextMessageBufferSize() {
        //noinspection unchecked
        return (Integer) distributedPropertyMap.get(DistributedMapKey.MAX_TEXT_MESSAGE_BUFFER_SIZE);
    }

    /**
     * Get a reference a {@link RemoteEndpoint.Async} object representing the peer of this conversation
     * that is able to send messages asynchronously to the peer.
     *
     * @return the remote endpoint representation.
     */
    @Override
    public RemoteEndpoint.Async getAsyncRemote() {
        return asyncRemote;
    }

    /**
     * Get a reference a {@link RemoteEndpoint.Basic} object representing the peer of this conversation
     * that is able to send messages synchronously to the peer.
     *
     * @return the remote endpoint representation.
     */
    @Override
    public RemoteEndpoint.Basic getBasicRemote() {
        return basicRemote;
    }

    /**
     * Get a string containing the unique identifier assigned to this session.
     * The identifier is assigned by the web socket implementation and is implementation dependent.
     *
     * @return the unique identifier for this session instance.
     */
    @Override
    public String getId() {
        return sessionId;
    }

    /**
     * Close the current conversation with a normal status code and no reason phrase.
     *
     * @throws IOException if there was a connection error closing the connection.
     */
    @Override
    public void close() throws IOException {
        clusterContext.close(sessionId);
    }

    /**
     * Close the current conversation, giving a reason for the closure. The close
     * call causes the implementation to attempt notify the client of the close as
     * soon as it can. This may cause the sending of unsent messages immediately
     * prior to the close notification. After the close notification has been sent
     * the implementation notifies the endpoint's onClose method. Note the websocket
     * specification defines the acceptable uses of status codes and reason phrases.
     * If the application cannot determine a suitable close code to use for the closeReason,
     * it is recommended to use {@link CloseReason.CloseCodes#NO_STATUS_CODE}.
     *
     * @param closeReason the reason for the closure.
     * @throws IOException if there was a connection error closing the connection.
     */
    @Override
    public void close(CloseReason closeReason) throws IOException {
        clusterContext.close(sessionId, closeReason);
    }

    /**
     * Get the {@link URI} under which this session was opened, including
     * the query string if there is one.
     *
     * @return the request URI.
     */
    @Override
    public URI getRequestURI() {
        //noinspection unchecked
        return (URI) distributedPropertyMap.get(DistributedMapKey.REQUEST_URI);
    }

    /**
     * Get the request parameters associated with the request this session
     * was opened under.
     *
     * @return the unmodifiable map of the request parameters.
     */
    @Override
    public Map<String, List<String>> getRequestParameterMap() {
        //noinspection unchecked
        return (Map<String, List<String>>) distributedPropertyMap.get(DistributedMapKey.REQUEST_PARAMETER_MAP);
    }

    /**
     * Get the query string associated with the request this session
     * was opened under.
     *
     * @return the query string.
     */
    @Override
    public String getQueryString() {
        return (String) distributedPropertyMap.get(DistributedMapKey.QUERY_STRING);
    }

    /**
     * Get a map of the path parameter names and values used associated with the
     * request this session was opened under.
     *
     * @return the unmodifiable map of path parameters. The key of the map is the parameter name,
     * the values in the map are the parameter values.
     */
    @Override
    public Map<String, String> getPathParameters() {
        //noinspection unchecked
        return (Map<String, String>) distributedPropertyMap.get(DistributedMapKey.PATH_PARAMETERS);
    }

    /**
     * This method is not supported on {@link RemoteSession}. Each invocation will throw an {@link
     * UnsupportedOperationException}.
     *
     * @return nothing.
     * @see #getDistributedProperties()
     */
    @Override
    public Map<String, Object> getUserProperties() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, Object> getDistributedProperties() {
        return clusterContext.getDistributedUserProperties(connectionId);
    }

    /**
     * Get the authenticated user for this session or {@code null} if no user is authenticated for this session.
     *
     * @return the user principal.
     */
    @Override
    public Principal getUserPrincipal() {
        //noinspection unchecked
        return (Principal) distributedPropertyMap.get(DistributedMapKey.USER_PRINCIPAL);
    }

    @Override
    public String toString() {
        return "RemoteSession{sessionId='" + sessionId + '\'' + ", clusterContext=" + clusterContext + '}';
    }

    /**
     * This method is not supported on {@link RemoteSession}. Each invocation will throw an {@link
     * UnsupportedOperationException}.
     *
     * @return nothing.
     */
    @Override
    public WebSocketContainer getContainer() {
        throw new UnsupportedOperationException();
    }

    /**
     * This method is not supported on {@link RemoteSession}. Each invocation will throw an {@link
     * UnsupportedOperationException}.
     *
     * @param handler nothing.
     */
    @Override
    public void addMessageHandler(MessageHandler handler) throws IllegalStateException {
        throw new UnsupportedOperationException();
    }

    /**
     * This method is not supported on {@link RemoteSession}. Each invocation will throw an {@link
     * UnsupportedOperationException}.
     *
     * @param clazz   nothing.
     * @param handler nothing.
     */
    @Override
    public <T> void addMessageHandler(Class<T> clazz, MessageHandler.Whole<T> handler) {
        throw new UnsupportedOperationException();
    }

    /**
     * This method is not supported on {@link RemoteSession}. Each invocation will throw an {@link
     * UnsupportedOperationException}.
     *
     * @param clazz   nothing.
     * @param handler nothing.
     */
    @Override
    public <T> void addMessageHandler(Class<T> clazz, MessageHandler.Partial<T> handler) {
        throw new UnsupportedOperationException();
    }

    /**
     * This method is not supported on {@link RemoteSession}. Each invocation will throw an {@link
     * UnsupportedOperationException}.
     *
     * @return nothing.
     */
    @Override
    public Set<MessageHandler> getMessageHandlers() {
        throw new UnsupportedOperationException();
    }

    /**
     * This method is not supported on {@link RemoteSession}. Each invocation will throw an {@link
     * UnsupportedOperationException}.
     *
     * @param handler nothing.
     */
    @Override
    public void removeMessageHandler(MessageHandler handler) {
        throw new UnsupportedOperationException();
    }

    /**
     * This method is not supported on {@link RemoteSession}. Each invocation will throw an {@link
     * UnsupportedOperationException}.
     *
     * @param milliseconds nothing.
     */
    @Override
    public void setMaxIdleTimeout(long milliseconds) {
        throw new UnsupportedOperationException();
    }

    /**
     * This method is not supported on {@link RemoteSession}. Each invocation will throw an {@link
     * UnsupportedOperationException}.
     *
     * @param length nothing.
     */
    @Override
    public void setMaxBinaryMessageBufferSize(int length) {
        throw new UnsupportedOperationException();
    }

    /**
     * This method is not supported on {@link RemoteSession}. Each invocation will throw an {@link
     * UnsupportedOperationException}.
     *
     * @param length nothing.
     */
    @Override
    public void setMaxTextMessageBufferSize(int length) {
        throw new UnsupportedOperationException();
    }

    /**
     * This method is not supported on {@link RemoteSession}. Each invocation will throw an {@link
     * UnsupportedOperationException}.
     *
     * @return nothing.
     */
    @Override
    public Set<Session> getOpenSessions() {
        throw new UnsupportedOperationException();
    }
}
