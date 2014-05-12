/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2014 Oracle and/or its affiliates. All rights reserved.
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
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.CloseReason;
import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.Extension;
import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.glassfish.tyrus.core.cluster.ClusterContext;
import org.glassfish.tyrus.core.cluster.ClusterSession;
import org.glassfish.tyrus.core.cluster.SessionEventListener;
import org.glassfish.tyrus.core.coder.CoderWrapper;
import org.glassfish.tyrus.core.l10n.LocalizationMessages;

/**
 * Implementation of the {@link Session}.
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Martin Matula (martin.matula at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class TyrusSession implements Session {

    private static final Logger LOGGER = Logger.getLogger(TyrusSession.class.getName());

    private final WebSocketContainer container;
    private final TyrusEndpointWrapper endpointWrapper;
    private final TyrusRemoteEndpoint.Basic basicRemote;
    private final TyrusRemoteEndpoint.Async asyncRemote;
    private final boolean isSecure;
    private final URI requestURI;
    private final String queryString;
    private final Map<String, String> pathParameters;
    private final Principal userPrincipal;
    private final Map<String, List<String>> requestParameterMap;
    private final Object idleTimeoutLock = new Object();
    private final String id;
    private final String connectionId;
    private final Map<String, Object> userProperties;
    private final MessageHandlerManager handlerManager;
    private final AtomicReference<State> state = new AtomicReference<State>(State.RUNNING);
    private final TextBuffer textBuffer = new TextBuffer();
    private final BinaryBuffer binaryBuffer = new BinaryBuffer();
    private final List<Extension> negotiatedExtensions;
    private final String negotiatedSubprotocol;

    private final Map<ClusterSession.DistributedMapKey, Object> distributedPropertyMap;

    private volatile long maxIdleTimeout = 0;
    private volatile ScheduledFuture<?> idleTimeoutFuture = null;
    private int maxBinaryMessageBufferSize = Integer.MAX_VALUE;
    private int maxTextMessageBufferSize = Integer.MAX_VALUE;
    private ScheduledExecutorService service;
    private ReaderBuffer readerBuffer;
    private InputStreamBuffer inputStreamBuffer;
    private volatile long heartbeatInterval;
    private volatile ScheduledFuture<?> heartbeatTask;

    TyrusSession(WebSocketContainer container, TyrusWebSocket socket, TyrusEndpointWrapper endpointWrapper,
                 String subprotocol, List<Extension> extensions, boolean isSecure,
                 URI requestURI, String queryString, Map<String, String> pathParameters, Principal principal,
                 Map<String, List<String>> requestParameterMap, final ClusterContext clusterContext,
                 String connectionId) {
        this.container = container;
        this.endpointWrapper = endpointWrapper;
        this.negotiatedExtensions = extensions == null ? Collections.<Extension>emptyList() : Collections.unmodifiableList(extensions);
        this.negotiatedSubprotocol = subprotocol == null ? "" : subprotocol;
        this.isSecure = isSecure;
        this.requestURI = requestURI;
        this.queryString = queryString;
        this.pathParameters = pathParameters == null ? Collections.<String, String>emptyMap() : Collections.unmodifiableMap(new HashMap<String, String>(pathParameters));
        this.basicRemote = new TyrusRemoteEndpoint.Basic(this, socket, endpointWrapper);
        this.asyncRemote = new TyrusRemoteEndpoint.Async(this, socket, endpointWrapper);
        this.handlerManager = MessageHandlerManager.fromDecoderInstances(endpointWrapper.getDecoders());
        this.userPrincipal = principal;
        this.requestParameterMap = requestParameterMap == null ? Collections.<String, List<String>>emptyMap() : Collections.unmodifiableMap(new HashMap<String, List<String>>(requestParameterMap));
        this.connectionId = connectionId;

        if (container != null) {
            maxTextMessageBufferSize = container.getDefaultMaxTextMessageBufferSize();
            maxBinaryMessageBufferSize = container.getDefaultMaxBinaryMessageBufferSize();
            service = ((ExecutorServiceProvider) container).getScheduledExecutorService();
            setMaxIdleTimeout(container.getDefaultMaxSessionIdleTimeout());
        }

        // cluster context is always null on client side
        if (clusterContext != null) {
            id = clusterContext.createSessionId();
            distributedPropertyMap = clusterContext.getDistributedSessionProperties(id);
            distributedPropertyMap.put(ClusterSession.DistributedMapKey.NEGOTIATED_SUBPROTOCOL, negotiatedSubprotocol);
            distributedPropertyMap.put(ClusterSession.DistributedMapKey.NEGOTIATED_EXTENSIONS, negotiatedExtensions);
            distributedPropertyMap.put(ClusterSession.DistributedMapKey.SECURE, isSecure);
            distributedPropertyMap.put(ClusterSession.DistributedMapKey.MAX_IDLE_TIMEOUT, maxIdleTimeout);
            distributedPropertyMap.put(ClusterSession.DistributedMapKey.MAX_BINARY_MESSAGE_BUFFER_SIZE, maxBinaryMessageBufferSize);
            distributedPropertyMap.put(ClusterSession.DistributedMapKey.MAX_TEXT_MESSAGE_BUFFER_SIZE, maxTextMessageBufferSize);
            distributedPropertyMap.put(ClusterSession.DistributedMapKey.REQUEST_URI, requestURI);
            distributedPropertyMap.put(ClusterSession.DistributedMapKey.REQUEST_PARAMETER_MAP, requestParameterMap);
            distributedPropertyMap.put(ClusterSession.DistributedMapKey.QUERY_STRING, queryString == null ? "" : queryString);
            distributedPropertyMap.put(ClusterSession.DistributedMapKey.PATH_PARAMETERS, this.pathParameters);
            if (userPrincipal != null) {
                distributedPropertyMap.put(ClusterSession.DistributedMapKey.USER_PRINCIPAL, userPrincipal);
            }

            userProperties = clusterContext.getDistributedUserProperties(connectionId);

            clusterContext.initClusteredSession(id, endpointWrapper.getEndpointPath(), new SessionEventListener(this));
        } else {
            id = UUID.randomUUID().toString();
            userProperties = new HashMap<String, Object>();
            distributedPropertyMap = null;
        }
    }

    /**
     * Web Socket protocol version used.
     *
     * @return protocol version
     */
    @Override
    public String getProtocolVersion() {
        return "13"; // TODO
    }

    @Override
    public String getNegotiatedSubprotocol() {
        return negotiatedSubprotocol;
    }

    @Override
    public javax.websocket.RemoteEndpoint.Async getAsyncRemote() {
        checkConnectionState(State.CLOSED);
        return asyncRemote;
    }

    @Override
    public javax.websocket.RemoteEndpoint.Basic getBasicRemote() {
        checkConnectionState(State.CLOSED);
        return basicRemote;
    }

    @Override
    public boolean isOpen() {
        return state.get() != State.CLOSED;
    }

    @Override
    public void close() throws IOException {
        if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
            heartbeatTask.cancel(true);
        }
        changeStateToClosed();
        basicRemote.close(CloseReasons.NORMAL_CLOSURE.getCloseReason());
    }

    /**
     * Closes the underlying connection this session is based upon.
     */
    @Override
    public void close(CloseReason closeReason) throws IOException {
        if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
            heartbeatTask.cancel(true);
        }
        checkConnectionState(State.CLOSED);
        changeStateToClosed();
        basicRemote.close(closeReason);
    }

    @Override
    public int getMaxBinaryMessageBufferSize() {
        return maxBinaryMessageBufferSize;
    }

    @Override
    public void setMaxBinaryMessageBufferSize(int maxBinaryMessageBufferSize) {
        checkConnectionState(State.CLOSED);
        this.maxBinaryMessageBufferSize = maxBinaryMessageBufferSize;
        if (distributedPropertyMap != null) {
            distributedPropertyMap.put(ClusterSession.DistributedMapKey.MAX_BINARY_MESSAGE_BUFFER_SIZE, maxBinaryMessageBufferSize);
        }
    }

    @Override
    public int getMaxTextMessageBufferSize() {
        return maxTextMessageBufferSize;
    }

    @Override
    public void setMaxTextMessageBufferSize(int maxTextMessageBufferSize) {
        checkConnectionState(State.CLOSED);
        this.maxTextMessageBufferSize = maxTextMessageBufferSize;
        if (distributedPropertyMap != null) {
            distributedPropertyMap.put(ClusterSession.DistributedMapKey.MAX_TEXT_MESSAGE_BUFFER_SIZE, maxTextMessageBufferSize);
        }
    }

    @Override
    public Set<Session> getOpenSessions() {
        return endpointWrapper.getOpenSessions(this);
    }

    @Override
    public List<Extension> getNegotiatedExtensions() {
        return negotiatedExtensions;
    }

    @Override
    public long getMaxIdleTimeout() {
        return maxIdleTimeout;
    }

    @Override
    public void setMaxIdleTimeout(long maxIdleTimeout) {
        checkConnectionState(State.CLOSED);
        this.maxIdleTimeout = maxIdleTimeout;
        restartIdleTimeoutExecutor();
        if (distributedPropertyMap != null) {
            distributedPropertyMap.put(ClusterSession.DistributedMapKey.MAX_IDLE_TIMEOUT, maxIdleTimeout);
        }
    }

    @Override
    public boolean isSecure() {
        return isSecure;
    }

    @Override
    public WebSocketContainer getContainer() {
        return this.container;
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated please use {@link #addMessageHandler(Class, javax.websocket.MessageHandler.Whole)} or
     * {@link #addMessageHandler(Class, javax.websocket.MessageHandler.Partial)}
     */
    @Override
    public void addMessageHandler(MessageHandler handler) {
        checkConnectionState(State.CLOSED);
        synchronized (handlerManager) {
            handlerManager.addMessageHandler(handler);
        }
    }

    /**
     * Register to handle to incoming messages in this conversation. A maximum of one message handler per
     * native websocket message type (text, binary, pong) may be added to each Session. I.e. a maximum
     * of one message handler to handle incoming text messages a maximum of one message handler for
     * handling incoming binary messages, and a maximum of one for handling incoming pong
     * messages. For further details of which message handlers handle which of the native websocket
     * message types please see {@link MessageHandler.Whole} and {@link MessageHandler.Partial}.
     * Adding more than one of any one type will result in a runtime exception.
     * <p/>
     * <p>See {@link javax.websocket.Endpoint} for a usage example.
     *
     * @param clazz   type of the message processed by message handler to be registered.
     * @param handler the MessageHandler to be added.
     * @throws IllegalStateException if there is already a MessageHandler registered for the same native
     *                               websocket message type as this handler.
     */
    public <T> void addMessageHandler(Class<T> clazz, MessageHandler.Whole<T> handler) {
        checkConnectionState(State.CLOSED);
        synchronized (handlerManager) {
            handlerManager.addMessageHandler(clazz, handler);
        }
    }

    /**
     * Register to handle to incoming messages in this conversation. A maximum of one message handler per
     * native websocket message type (text, binary, pong) may be added to each Session. I.e. a maximum
     * of one message handler to handle incoming text messages a maximum of one message handler for
     * handling incoming binary messages, and a maximum of one for handling incoming pong
     * messages. For further details of which message handlers handle which of the native websocket
     * message types please see {@link MessageHandler.Whole} and {@link MessageHandler.Partial}.
     * Adding more than one of any one type will result in a runtime exception.
     * <p/>
     * <p>See {@link javax.websocket.Endpoint} for a usage example.
     *
     * @param clazz   type of the message processed by message handler to be registered.
     * @param handler the MessageHandler to be added.
     * @throws IllegalStateException if there is already a MessageHandler registered for the same native
     *                               websocket message type as this handler.
     */
    public <T> void addMessageHandler(Class<T> clazz, MessageHandler.Partial<T> handler) {
        checkConnectionState(State.CLOSED);
        synchronized (handlerManager) {
            handlerManager.addMessageHandler(clazz, handler);
        }
    }

    @Override
    public Set<MessageHandler> getMessageHandlers() {
        synchronized (handlerManager) {
            return handlerManager.getMessageHandlers();
        }
    }

    @Override
    public void removeMessageHandler(MessageHandler handler) {
        checkConnectionState(State.CLOSED);
        synchronized (handlerManager) {
            handlerManager.removeMessageHandler(handler);
        }
    }

    @Override
    public URI getRequestURI() {
        return requestURI;
    }

    @Override
    public Map<String, List<String>> getRequestParameterMap() {
        return requestParameterMap;
    }

    @Override
    public Map<String, String> getPathParameters() {
        return pathParameters;
    }

    @Override
    public Map<String, Object> getUserProperties() {
        return userProperties;
    }

    @Override
    public String getQueryString() {
        return queryString;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Principal getUserPrincipal() {
        return userPrincipal;
    }

    /**
     * Broadcasts text message to all connected clients.
     *
     * @param message message to be broadcasted.
     * @return map of sessions and futures for user to get the information about status of the message.
     */
    public Map<Session, Future<?>> broadcast(String message) {
        return endpointWrapper.broadcast(message);
    }

    /**
     * Broadcasts binary message to all connected clients.
     *
     * @param message message to be broadcasted.
     * @return map of sessions and futures for user to get the information about status of the message.
     */
    public Map<Session, Future<?>> broadcast(ByteBuffer message) {
        return endpointWrapper.broadcast(message);
    }

    /**
     * Return an interval in milliseconds between scheduled periodic Pong messages.
     * A negative value or 0 means that sending of periodic Pong messages is not turned on.
     *
     * @return heartbeatInterval interval between periodic pong messages in milliseconds.
     */
    public long getHeartbeatInterval() {
        return heartbeatInterval;
    }

    /**
     * Set an interval in milliseconds between scheduled periodic Pong messages.
     * Setting the interval to a negative value or 0 will cancel sending of periodic Pong messages.
     *
     * @param heartbeatInterval interval between periodic Pong messages in milliseconds.
     */
    public void setHeartbeatInterval(long heartbeatInterval) {
        checkConnectionState(State.CLOSED);
        this.heartbeatInterval = heartbeatInterval;
        if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
            heartbeatTask.cancel(true);
        }

        if (heartbeatInterval < 1) {
            return;
        }

        heartbeatTask = service.scheduleAtFixedRate(new HeartbeatCommand(), heartbeatInterval, heartbeatInterval, TimeUnit.MILLISECONDS);
    }

    void restartIdleTimeoutExecutor() {
        if (this.maxIdleTimeout < 1) {
            synchronized (idleTimeoutLock) {
                if (idleTimeoutFuture != null) {
                    idleTimeoutFuture.cancel(true);
                }
                return;
            }
        }

        synchronized (idleTimeoutLock) {
            if (idleTimeoutFuture != null) {
                idleTimeoutFuture.cancel(false);
            }

            idleTimeoutFuture = service.schedule(new IdleTimeoutCommand(), this.getMaxIdleTimeout(), TimeUnit.MILLISECONDS);
        }
    }

    private void checkConnectionState(State... states) {
        final State sessionState = state.get();
        for (State s : states) {
            if (sessionState == s) {
                throw new IllegalStateException(LocalizationMessages.CONNECTION_HAS_BEEN_CLOSED());
            }
        }
    }

    private void checkMessageSize(Object message, long maxMessageSize) {
        if (maxMessageSize != -1) {
            final long messageSize = (message instanceof String ? ((String) message).getBytes(Charset.defaultCharset()).length :
                    ((ByteBuffer) message).remaining());

            if (messageSize > maxMessageSize) {
                throw new MessageTooBigException(LocalizationMessages.MESSAGE_TOO_LONG(maxMessageSize, messageSize));
            }
        }
    }

    void notifyMessageHandlers(Object message, List<CoderWrapper<Decoder>> availableDecoders) throws DecodeException, IOException {
        boolean decoded = false;

        if (availableDecoders.isEmpty()) {
            LOGGER.warning(LocalizationMessages.NO_DECODER_FOUND());
        }

        List<Map.Entry<Class<?>, MessageHandler>> orderedMessageHandlers;
        synchronized (handlerManager) {
            orderedMessageHandlers = handlerManager.getOrderedWholeMessageHandlers();
        }

        for (CoderWrapper<Decoder> decoder : availableDecoders) {
            for (Map.Entry<Class<?>, MessageHandler> entry : orderedMessageHandlers) {
                MessageHandler mh = entry.getValue();

                Class<?> type = entry.getKey();
                if (type.isAssignableFrom(decoder.getType())) {

                    if (mh instanceof BasicMessageHandler) {
                        checkMessageSize(message, ((BasicMessageHandler) mh).getMaxMessageSize());
                    }

                    Object object = endpointWrapper.decodeCompleteMessage(this, message, type, decoder);
                    if (object != null) {
                        final State currentState = state.get();
                        if (currentState != State.CLOSED) {
                            //noinspection unchecked
                            ((MessageHandler.Whole) mh).onMessage(object);
                        }
                        decoded = true;
                        break;
                    }
                }
            }
            if (decoded) {
                break;
            }
        }
    }

    <T> MessageHandler.Whole<T> getMessageHandler(Class<T> c) {
        List<Map.Entry<Class<?>, MessageHandler>> orderedMessageHandlers;
        synchronized (handlerManager) {
            orderedMessageHandlers = handlerManager.getOrderedWholeMessageHandlers();
        }

        for (Map.Entry<Class<?>, MessageHandler> entry : orderedMessageHandlers) {
            if (entry.getKey() == c) {
                return (MessageHandler.Whole<T>) entry.getValue();
            }
        }

        return null;
    }

    void notifyMessageHandlers(Object message, boolean last) {
        boolean handled = false;

        for (MessageHandler handler : getMessageHandlers()) {
            if ((handler instanceof MessageHandler.Partial) &&
                    MessageHandlerManager.getHandlerType(handler).isAssignableFrom(message.getClass())) {

                if (handler instanceof AsyncMessageHandler) {
                    checkMessageSize(message, ((AsyncMessageHandler) handler).getMaxMessageSize());
                }

                final State currentState = state.get();
                if (currentState != State.CLOSED) {
                    //noinspection unchecked
                    ((MessageHandler.Partial) handler).onMessage(message, last);
                }
                handled = true;
                break;
            }
        }

        if (!handled) {
            if (message instanceof ByteBuffer) {
                notifyMessageHandlers(((ByteBuffer) message).array(), last);
            } else {
                LOGGER.warning(LocalizationMessages.UNHANDLED_TEXT_MESSAGE(this));
            }
        }
    }

    void notifyPongHandler(PongMessage pongMessage) {
        final Set<MessageHandler> messageHandlers = getMessageHandlers();
        for (MessageHandler handler : messageHandlers) {
            if (MessageHandlerManager.getHandlerType(handler).equals(PongMessage.class)) {
                ((MessageHandler.Whole<PongMessage>) handler).onMessage(pongMessage);
            }
        }
    }

    boolean isWholeTextHandlerPresent() {
        return handlerManager.isWholeTextHandlerPresent();
    }

    boolean isWholeBinaryHandlerPresent() {
        return handlerManager.isWholeBinaryHandlerPresent();
    }

    boolean isPartialTextHandlerPresent() {
        return handlerManager.isPartialTextHandlerPresent();
    }

    boolean isPartialBinaryHandlerPresent() {
        return handlerManager.isPartialBinaryHandlerPresent();
    }

    boolean isReaderHandlerPresent() {
        return handlerManager.isReaderHandlerPresent();
    }

    boolean isInputStreamHandlerPresent() {
        return handlerManager.isInputStreamHandlerPresent();
    }

    boolean isPongHandlerPreset() {
        return handlerManager.isPongHandlerPresent();
    }

    State getState() {
        return state.get();
    }

    String getConnectionId() {
        return connectionId;
    }

    /**
     * Set the state of the {@link Session}.
     *
     * @param state the newly set state.
     */
    void setState(State state) {
        if (!state.equals(this.state.get())) {
            checkConnectionState(State.CLOSED);
            this.state.set(state);
        }
    }

    TextBuffer getTextBuffer() {
        return textBuffer;
    }

    BinaryBuffer getBinaryBuffer() {
        return binaryBuffer;
    }

    ReaderBuffer getReaderBuffer() {
        return readerBuffer;
    }

    void setReaderBuffer(ReaderBuffer readerBuffer) {
        this.readerBuffer = readerBuffer;
    }

    InputStreamBuffer getInputStreamBuffer() {
        return inputStreamBuffer;
    }

    void setInputStreamBuffer(InputStreamBuffer inputStreamBuffer) {
        this.inputStreamBuffer = inputStreamBuffer;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("TyrusSession");
        sb.append("{uri=").append(requestURI);
        sb.append(", id='").append(id).append('\'');
        sb.append(", endpointWrapper=").append(endpointWrapper);
        sb.append('}');
        return sb.toString();
    }

    private void changeStateToClosed() {
        state.compareAndSet(State.RUNNING, State.CLOSED);
        state.compareAndSet(State.RECEIVING_BINARY, State.CLOSED);
        state.compareAndSet(State.RECEIVING_TEXT, State.CLOSED);
    }

    /**
     * Session state.
     */
    enum State {

        /**
         * {@link Session} is running and is not receiving partial messages on registered {@link MessageHandler.Whole}.
         */
        RUNNING,

        /**
         * {@link Session} is currently receiving text partial message on registered {@link MessageHandler.Whole}.
         */
        RECEIVING_TEXT,

        /**
         * {@link Session} is currently receiving binary partial message on registered {@link MessageHandler.Whole}.
         */
        RECEIVING_BINARY,

        /**
         * {@link Session} has been already closed.
         */
        CLOSED
    }

    private class IdleTimeoutCommand implements Runnable {

        @Override
        public void run() {
            TyrusSession session = TyrusSession.this;

            // condition is required because scheduled task can be (for some reason) run even when it is cancelled.
            if (session.getMaxIdleTimeout() > 0 && session.isOpen()) {
                try {
                    session.close(new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, LocalizationMessages.SESSION_CLOSED_IDLE_TIMEOUT()));
                } catch (IOException e) {
                    LOGGER.log(Level.FINE, "Session could not been closed. " + e.getMessage());
                }
            }
        }
    }

    private class HeartbeatCommand implements Runnable {

        @Override
        public void run() {
            TyrusSession session = TyrusSession.this;
            if (session.isOpen() && session.getHeartbeatInterval() > 0) {
                try {
                    session.getBasicRemote().sendPong(null);
                } catch (IOException e) {
                    LOGGER.log(Level.FINE, "Pong could not have been sent " + e.getMessage());
                }
            }
        }
    }
}