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

package org.glassfish.tyrus.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.CloseReason;
import javax.websocket.Decoder;
import javax.websocket.DeploymentException;
import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Extension;
import javax.websocket.PongMessage;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import javax.websocket.server.ServerEndpointConfig;

import org.glassfish.tyrus.spi.SPIEndpoint;
import org.glassfish.tyrus.spi.SPIHandshakeRequest;
import org.glassfish.tyrus.spi.SPIRemoteEndpoint;
import org.glassfish.tyrus.websockets.HandshakeException;

/**
 * Wraps the registered application class.
 * There is one {@link EndpointWrapper} for each application class, which handles all the methods.
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Martin Matula (martin.matula at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class EndpointWrapper extends SPIEndpoint {

    private final static Logger LOGGER = Logger.getLogger(EndpointWrapper.class.getName());

    /**
     * The container for this session.
     */
    final BaseContainer container;
    private final String contextPath;

    private final List<CoderWrapper<Decoder>> decoders = new ArrayList<CoderWrapper<Decoder>>();
    private final List<CoderWrapper<Encoder>> encoders = new ArrayList<CoderWrapper<Encoder>>();

    private final EndpointConfig configuration;
    private final Class<?> endpointClass;
    private final Endpoint endpoint;
    private final Map<SPIRemoteEndpoint, SessionImpl> remoteEndpointToSession =
            new ConcurrentHashMap<SPIRemoteEndpoint, SessionImpl>();
    private final ErrorCollector collector;
    private final ComponentProviderService componentProvider;
    private final ServerEndpointConfig.Configurator configurator;

    // the following is set during the handshake
    private String uri;
    private Principal principal;
    private final Map<String, String> templateValues = new HashMap<String, String>();
    private boolean isSecure;
    private String queryString;
    private Map<String, List<String>> requestParameterMap;

    /**
     * Create {@link EndpointWrapper} for class that extends {@link Endpoint}.
     *
     * @param endpointClass     endpoint class for which the wrapper is created.
     * @param configuration     endpoint configuration.
     * @param componentProvider component provider.
     * @param container         container where the wrapper is running.
     * @param contextPath       context path.
     * @param collector         error collector.
     */
    public EndpointWrapper(Class<?> endpointClass, EndpointConfig configuration,
                           ComponentProviderService componentProvider, BaseContainer container,
                           String contextPath, ErrorCollector collector, ServerEndpointConfig.Configurator configurator) {
        this(null, endpointClass, configuration, componentProvider, container, contextPath, collector, configurator);
    }

    /**
     * Create {@link EndpointWrapper} for {@link Endpoint} instance or {@link AnnotatedEndpoint} instance.
     *
     * @param endpoint          endpoint instance for which the wrapper is created.
     * @param configuration     endpoint configuration.
     * @param componentProvider component provider.
     * @param container         container where the wrapper is running.
     * @param contextPath       context path.
     * @param collector         error collector.
     */
    public EndpointWrapper(Endpoint endpoint, EndpointConfig configuration, ComponentProviderService componentProvider, BaseContainer container,
                           String contextPath, ErrorCollector collector, ServerEndpointConfig.Configurator configurator) {
        this(endpoint, null, configuration, componentProvider, container, contextPath, collector, configurator);
    }

    private EndpointWrapper(Endpoint endpoint, Class<?> endpointClass, EndpointConfig configuration,
                            ComponentProviderService componentProvider, BaseContainer container,
                            String contextPath, ErrorCollector collector, final ServerEndpointConfig.Configurator configurator) {
        this.endpointClass = endpointClass;
        this.endpoint = endpoint;
        this.container = container;
        this.contextPath = contextPath;
        // Uri is re-set in checkHandshake method; this value will be used only in scenarios
        // when checkHandshake is not called, like using EndpointWrapper on the client side.
        // this.uri is then used for creating SessionImpl and used as a return value in Session.getRequestURI() method.
        this.uri = contextPath;
        this.collector = collector;
        this.configurator = configurator;
        this.componentProvider = configurator == null ? componentProvider : new ComponentProviderService(componentProvider) {
            @Override
            public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
                return configurator.getEndpointInstance(endpointClass);
            }
        };

        this.configuration = configuration == null ? new EndpointConfig() {

            private final Map<String, Object> properties = new HashMap<String, Object>();

            @Override
            public List<Class<? extends Encoder>> getEncoders() {
                return Collections.emptyList();
            }

            @Override
            public List<Class<? extends Decoder>> getDecoders() {
                return Collections.emptyList();
            }

            @Override
            public Map<String, Object> getUserProperties() {
                return properties;
            }
        } : configuration;

        for (Class<? extends Decoder> decoderClass : this.configuration.getDecoders()) {
            Class<?> type = getDecoderClassType(decoderClass);
            decoders.add(new CoderWrapper<Decoder>(decoderClass, type));
        }

        //this wrapper represents endpoint which is not annotated endpoint
        if (endpoint == null || !(endpoint instanceof AnnotatedEndpoint)) {
            for (Class<? extends Decoder> decoderClass : getDefaultDecoders()) {
                Class<?> type = getDecoderClassType(decoderClass);
                decoders.add(new CoderWrapper<Decoder>(decoderClass, type));
            }
        }

        for (Class<? extends Encoder> encoderClass : this.configuration.getEncoders()) {
            Class<?> type = getEncoderClassType(encoderClass);
            encoders.add(new CoderWrapper<Encoder>(encoderClass, type));
        }

        encoders.add(new CoderWrapper<Encoder>(NoOpTextCoder.class, String.class));
        encoders.add(new CoderWrapper<Encoder>(NoOpByteBufferCoder.class, ByteBuffer.class));
        encoders.add(new CoderWrapper<Encoder>(NoOpByteArrayCoder.class, byte[].class));
        encoders.add(new CoderWrapper<Encoder>(ToStringEncoder.class, Object.class));
    }

    @Override
    public boolean checkHandshake(SPIHandshakeRequest hr) {
        if (!(configuration instanceof ServerEndpointConfig)) {
            return false;
        }

        uri = hr.getRequestUri();
        queryString = hr.getQueryString();
        isSecure = hr.isSecure();
        principal = hr.getUserPrincipal();
        requestParameterMap = hr.getParameterMap();

        this.templateValues.clear();
        for (Map.Entry<String, List<String>> entry : hr.getParameterMap().entrySet()) {
            this.templateValues.put(entry.getKey(), entry.getValue().get(0));
        }

        if (configurator.checkOrigin(hr.getHeader("Origin"))) {
            return true;
        } else {
            throw new HandshakeException(403, "Origin not verified.");
        }
    }

    static List<Class<? extends Decoder>> getDefaultDecoders() {
        final List<Class<? extends Decoder>> classList = new ArrayList<Class<? extends Decoder>>();
        classList.addAll(PrimitiveDecoders.ALL);
        classList.add(NoOpTextCoder.class);
        classList.add(NoOpByteBufferCoder.class);
        classList.add(NoOpByteArrayCoder.class);
        classList.add(ReaderDecoder.class);
        classList.add(InputStreamDecoder.class);
        return classList;
    }

    public String getEndpointPath() {
        if (configuration instanceof ServerEndpointConfig) {
            String relativePath = ((ServerEndpointConfig) configuration).getPath();
            return (contextPath.endsWith("/") ? contextPath.substring(0, contextPath.length() - 1) : contextPath)
                    + "/" + (relativePath.startsWith("/") ? relativePath.substring(1) : relativePath);
        }

        return null;
    }

    @Override
    public WebSocketContainer getWebSocketContainer() {
        return container;
    }

    private <T> T getCoderInstance(Session session, CoderWrapper<T> wrapper) {
        final T coder = wrapper.getCoder();
        if (coder == null) {
            return this.componentProvider.getCoderInstance(wrapper.getCoderClass(), session, getEndpointConfig(), collector);
        }

        return coder;
    }

    Object decodeCompleteMessage(Session session, Object message, Class<?> type, CoderWrapper<Decoder> selectedDecoder) {
        try {
            final Class<? extends Decoder> decoderClass = selectedDecoder.getCoderClass();

            if (Decoder.Text.class.isAssignableFrom(decoderClass)) {
                if (type != null && type.isAssignableFrom(selectedDecoder.getType())) {
                    final Decoder.Text decoder = (Decoder.Text) getCoderInstance(session, selectedDecoder);

                    // TYRUS-210: willDecode was already called
                    return decoder.decode((String) message);
                }
            } else if (Decoder.Binary.class.isAssignableFrom(decoderClass)) {
                if (type != null && type.isAssignableFrom(selectedDecoder.getType())) {
                    final Decoder.Binary decoder = (Decoder.Binary) getCoderInstance(session, selectedDecoder);

                    // TYRUS-210: willDecode was already called
                    return decoder.decode((ByteBuffer) message);
                }
            } else if (Decoder.TextStream.class.isAssignableFrom(decoderClass)) {
                if (type != null && type.isAssignableFrom(selectedDecoder.getType())) {
                    return ((Decoder.TextStream) getCoderInstance(session, selectedDecoder)).decode(new StringReader((String) message));
                }
            } else if (Decoder.BinaryStream.class.isAssignableFrom(decoderClass)) {
                if (type != null && type.isAssignableFrom(selectedDecoder.getType())) {
                    byte[] array = ((ByteBuffer) message).array();
                    return ((Decoder.BinaryStream) getCoderInstance(session, selectedDecoder)).decode(new ByteArrayInputStream(array));
                }
            }
        } catch (Exception e) {
            collector.addException(e);
        }
        return null;
    }

    private ArrayList<CoderWrapper<Decoder>> findApplicableDecoders(Session session, Object message, boolean isString) {
        ArrayList<CoderWrapper<Decoder>> result = new ArrayList<CoderWrapper<Decoder>>();

        for (CoderWrapper<Decoder> dec : decoders) {
            if (isString && (Decoder.Text.class.isAssignableFrom(dec.getCoderClass()))) {
                final Decoder.Text decoder = (Decoder.Text) getCoderInstance(session, dec);

                if (decoder.willDecode((String) message)) {
                    result.add(dec);
                }
            } else if (!isString && (Decoder.Binary.class.isAssignableFrom(dec.getCoderClass()))) {
                final Decoder.Binary decoder = (Decoder.Binary) getCoderInstance(session, dec);

                if (decoder.willDecode((ByteBuffer) message)) {
                    result.add(dec);
                }
            } else if (isString && (Decoder.TextStream.class.isAssignableFrom(dec.getCoderClass()))) {
                result.add(dec);
            } else if (!isString && (Decoder.BinaryStream.class.isAssignableFrom(dec.getCoderClass()))) {
                result.add(dec);
            }
        }

        return result;
    }

    Object doEncode(Session session, Object message) throws EncodeException {
        for (CoderWrapper<Encoder> enc : encoders) {
            try {
                final Class<? extends Encoder> encoderClass = enc.getCoderClass();

                if (Encoder.Binary.class.isAssignableFrom(encoderClass)) {
                    if (enc.getType().isAssignableFrom(message.getClass())) {
                        final Encoder.Binary encoder = (Encoder.Binary) getCoderInstance(session, enc);

                        return encoder.encode(message);
                    }
                } else if (Encoder.Text.class.isAssignableFrom(encoderClass)) {
                    if (enc.getType().isAssignableFrom(message.getClass())) {
                        final Encoder.Text encoder = (Encoder.Text) getCoderInstance(session, enc);

                        return encoder.encode(message);
                    }
                } else if (Encoder.BinaryStream.class.isAssignableFrom(encoderClass)) {
                    if (enc.getType().isAssignableFrom(message.getClass())) {
                        final ByteArrayOutputStream stream = new ByteArrayOutputStream();
                        final Encoder.BinaryStream encoder = (Encoder.BinaryStream) getCoderInstance(session, enc);

                        encoder.encode(message, stream);
                        return stream;
                    }
                } else if (Encoder.TextStream.class.isAssignableFrom(encoderClass)) {
                    if (enc.getType().isAssignableFrom(message.getClass())) {
                        final Writer writer = new StringWriter();
                        final Encoder.TextStream encoder = (Encoder.TextStream) getCoderInstance(session, enc);

                        encoder.encode(message, writer);
                        return writer;
                    }
                }
            } catch (EncodeException ee) {
                throw ee;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        throw new EncodeException(message, "Unable to encode ");
    }

    @Override
    public List<Extension> getNegotiatedExtensions(List<Extension> clientExtensions) {
        if (configuration instanceof ServerEndpointConfig) {
            return configurator.getNegotiatedExtensions(((ServerEndpointConfig) configuration).getExtensions(), clientExtensions);
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public String getNegotiatedProtocol(List<String> clientProtocols) {
        if (configuration instanceof ServerEndpointConfig) {
            return configurator.getNegotiatedSubprotocol(((ServerEndpointConfig) configuration).getSubprotocols(), clientProtocols);
        } else {
            return null;
        }
    }

    @Override
    public Set<Session> getOpenSessions() {
        Set<Session> result = new HashSet<Session>();

        for (Session session : remoteEndpointToSession.values()) {
            if (session.isOpen()) {
                result.add(session);
            }
        }

        return Collections.unmodifiableSet(result);
    }

    @Override
    public Session createSessionForRemoteEndpoint(SPIRemoteEndpoint re, String subprotocol, List<Extension> extensions) {
        synchronized (remoteEndpointToSession) {
            final SessionImpl session = new SessionImpl(container, re, this, subprotocol, extensions, isSecure,
                    getURI(uri, queryString), queryString, templateValues, principal, requestParameterMap);
            remoteEndpointToSession.put(re, session);
            return session;
        }
    }

    @Override
    public void remove() {
        // TODO: disconnect the endpoint?
    }

    private SessionImpl getSession(SPIRemoteEndpoint gs) {
        synchronized (remoteEndpointToSession) {
            return remoteEndpointToSession.get(gs);
        }
    }

    @Override
    public void onConnect(SPIRemoteEndpoint gs, String subprotocol, List<Extension> extensions) {
        synchronized (remoteEndpointToSession) {
            SessionImpl session = remoteEndpointToSession.get(gs);
            if (session == null) {
                // create a new session
                session = new SessionImpl(container, gs, this, subprotocol, extensions, isSecure,
                        getURI(uri, queryString), queryString, templateValues, principal, requestParameterMap);
                remoteEndpointToSession.put(gs, session);
            }
            // Session was already created in WebSocketContainer#connectToServer call
            // we need to update extensions and subprotocols
            session.setNegotiatedExtensions(extensions);
            session.setNegotiatedSubprotocol(subprotocol);

            final Endpoint toCall = endpoint != null ? endpoint :
                    (Endpoint) componentProvider.getInstance(endpointClass, session, collector);
            try {
                toCall.onOpen(session, configuration);
            } catch (Throwable t) {
                if (toCall != null) {
                    toCall.onError(session, t);
                } else {
                    collector.addException(new DeploymentException(t.getMessage(), t));
                }
            }
        }
    }

    @Override
    public void onMessage(SPIRemoteEndpoint gs, ByteBuffer messageBytes) {
        SessionImpl session = getSession(gs);

        try {
            session.restartIdleTimeoutExecutor();
            session.setState(SessionImpl.State.RUNNING);
            if (session.isWholeBinaryHandlerPresent()) {
                session.notifyMessageHandlers(messageBytes, findApplicableDecoders(session, messageBytes, false));
            } else if (session.isPartialBinaryHandlerPresent()) {
                session.notifyMessageHandlers(messageBytes, true);
            } else {
                throw new IllegalStateException(String.format("Binary messageHandler not found. Session: '%s'.", session));
            }
        } catch (Throwable t) {
            if (!processThrowable(t, session)) {
                final Endpoint toCall = endpoint != null ? endpoint :
                        (Endpoint) componentProvider.getInstance(endpointClass, session, collector);
                if (toCall != null) {
                    toCall.onError(session, t);
                }
            }
        }
    }

    @Override
    public void onMessage(SPIRemoteEndpoint gs, String messageString) {
        SessionImpl session = getSession(gs);

        if (session == null) {
            LOGGER.log(Level.FINE, "Message received on already closed connection.");
            return;
        }

        try {
            session.restartIdleTimeoutExecutor();
            session.setState(SessionImpl.State.RUNNING);
            if (session.isWholeTextHandlerPresent()) {
                session.notifyMessageHandlers(messageString, findApplicableDecoders(session, messageString, true));
            } else if (session.isPartialTextHandlerPresent()) {
                session.notifyMessageHandlers(messageString, true);
            } else {
                throw new IllegalStateException(String.format("Text messageHandler not found. Session: '%s'.", session));
            }
        } catch (Throwable t) {
            if (!processThrowable(t, session)) {
                final Endpoint toCall = endpoint != null ? endpoint :
                        (Endpoint) componentProvider.getInstance(endpointClass, session, collector);
                if (toCall != null) {
                    toCall.onError(session, t);
                }
            }
        }
    }

    @Override
    public void onPartialMessage(SPIRemoteEndpoint gs, String partialString, boolean last) {
        SessionImpl session = getSession(gs);

        try {
            session.restartIdleTimeoutExecutor();
            if (session.isPartialTextHandlerPresent()) {
                session.notifyMessageHandlers(partialString, last);
                session.setState(SessionImpl.State.RUNNING);
            } else if (session.isReaderHandlerPresent()) {
                ReaderBuffer buffer = session.getReaderBuffer();
                switch (session.getState()) {
                    case RUNNING:
                        if (buffer == null) {
                            buffer = new ReaderBuffer(container.getExecutorService());
                            session.setReaderBuffer(buffer);
                        }
                        buffer.resetBuffer(session.getMaxTextMessageBufferSize());
                        buffer.setMessageHandler((session.getMessageHandler(Reader.class)));
                        buffer.appendMessagePart(partialString, last);
                        session.setState(SessionImpl.State.RECEIVING_TEXT);
                        break;
                    case RECEIVING_TEXT:
                        buffer.appendMessagePart(partialString, last);
                        if (last) {
                            session.setState(SessionImpl.State.RUNNING);
                        }
                        break;
                    default:
                        session.setState(SessionImpl.State.RUNNING);
                        throw new IllegalStateException(String.format("Partial text message received out of order. Session: '%s'.", session));
                }
            } else if (session.isWholeTextHandlerPresent()) {
                switch (session.getState()) {
                    case RUNNING:
                        session.getTextBuffer().resetBuffer(session.getMaxTextMessageBufferSize());
                        session.getTextBuffer().appendMessagePart(partialString);
                        session.setState(SessionImpl.State.RECEIVING_TEXT);
                        break;
                    case RECEIVING_TEXT:
                        session.getTextBuffer().appendMessagePart(partialString);
                        if (last) {
                            final String message = session.getTextBuffer().getBufferedContent();
                            session.notifyMessageHandlers(message, findApplicableDecoders(session, message, true));
                            session.setState(SessionImpl.State.RUNNING);
                        }
                        break;
                    default:
                        session.setState(SessionImpl.State.RUNNING);
                        throw new IllegalStateException(String.format("Text message received out of order. Session: '%s'.", session));
                }
            }
        } catch (Throwable t) {
            if (!processThrowable(t, session)) {
                final Endpoint toCall = endpoint != null ? endpoint :
                        (Endpoint) componentProvider.getInstance(endpointClass, session, collector);
                if (toCall != null) {
                    toCall.onError(session, t);
                }
            }
        }
    }

    @Override
    public void onPartialMessage(SPIRemoteEndpoint gs, ByteBuffer partialBytes, boolean last) {
        SessionImpl session = getSession(gs);

        try {
            session.restartIdleTimeoutExecutor();
            if (session.isPartialBinaryHandlerPresent()) {
                session.notifyMessageHandlers(partialBytes, last);
                session.setState(SessionImpl.State.RUNNING);
            } else if (session.isInputStreamHandlerPresent()) {
                InputStreamBuffer buffer = session.getInputStreamBuffer();
                switch (session.getState()) {
                    case RUNNING:
                        if (buffer == null) {
                            buffer = new InputStreamBuffer(container.getExecutorService());
                            session.setInputStreamBuffer(buffer);
                        }
                        buffer.resetBuffer(session.getMaxBinaryMessageBufferSize());
                        buffer.setMessageHandler((session.getMessageHandler(InputStream.class)));
                        buffer.appendMessagePart(partialBytes, last);
                        session.setState(SessionImpl.State.RECEIVING_BINARY);
                        break;
                    case RECEIVING_BINARY:
                        buffer.appendMessagePart(partialBytes, last);
                        if (last) {
                            session.setState(SessionImpl.State.RUNNING);
                        }
                        break;
                    default:
                        session.setState(SessionImpl.State.RUNNING);
                        throw new IllegalStateException(String.format("Partial binary message received out of order. Session: '%s'.", session));
                }
            } else if (session.isWholeBinaryHandlerPresent()) {
                switch (session.getState()) {
                    case RUNNING:
                        session.getBinaryBuffer().resetBuffer(session.getMaxBinaryMessageBufferSize());
                        session.getBinaryBuffer().appendMessagePart(partialBytes);
                        session.setState(SessionImpl.State.RECEIVING_BINARY);
                        break;
                    case RECEIVING_BINARY:
                        session.getBinaryBuffer().appendMessagePart(partialBytes);
                        if (last) {
                            ByteBuffer bb = session.getBinaryBuffer().getBufferedContent();
                            session.notifyMessageHandlers(bb, findApplicableDecoders(session, bb, false));
                            session.setState(SessionImpl.State.RUNNING);
                        }
                        break;
                    default:
                        session.setState(SessionImpl.State.RUNNING);
                        throw new IllegalStateException(String.format("Binary message received out of order. Session: '%s'.", session));
                }
            }
        } catch (Throwable t) {
            if (!processThrowable(t, session)) {
                final Endpoint toCall = endpoint != null ? endpoint :
                        (Endpoint) componentProvider.getInstance(endpointClass, session, collector);
                if (toCall != null) {
                    toCall.onError(session, t);
                }
            }
        }
    }

    /**
     * Check {@link Throwable} produced during {@link javax.websocket.OnMessage} annotated method call.
     *
     * @param throwable thrown {@link Throwable}.
     * @param session   {@link Session} related to {@link Throwable}.
     * @return {@code true} when exception is handled within this method (framework produced it), {@code false}
     *         otherwise.
     */
    private boolean processThrowable(Throwable throwable, Session session) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, String.format("Exception thrown while processing message. Session: '%session'.", session), throwable);
        }

        if (throwable instanceof MessageTooBigException) {
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.TOO_BIG, "Message too big."));
                return false;
            } catch (IOException e) {
                // we don't care.
            }
        }

        return false;
    }

    @Override
    public void onPong(SPIRemoteEndpoint gs, final ByteBuffer bytes) {
        SessionImpl session = getSession(gs);
        session.restartIdleTimeoutExecutor();

        if (session.isPongHandlerPreset()) {
            session.notifyPongHandler(new PongMessage() {
                @Override
                public ByteBuffer getApplicationData() {
                    return bytes;
                }
            });
        } else {
            LOGGER.log(Level.FINE, String.format("Unhandled pong message. Session: '%s'", session));
        }
    }

    // the endpoint needs to respond as soon as possible (see the websocket RFC)
    // no involvement from application layer, there is no ping listener
    @Override
    public void onPing(SPIRemoteEndpoint gs, ByteBuffer bytes) {
        SessionImpl session = getSession(gs);
        session.restartIdleTimeoutExecutor();
        try {
            session.getBasicRemote().sendPong(bytes);
        } catch (IOException e) {
            // do nothing.
            // we might consider calling onError, but there should be better defined exception.
        }
    }

    @Override
    public void onClose(SPIRemoteEndpoint gs, CloseReason closeReason) {
        SessionImpl session = getSession(gs);

        if (session == null) {
            return;
        }

        session.setState(SessionImpl.State.CLOSING);
        final Endpoint toCall = endpoint != null ? endpoint :
                (Endpoint) componentProvider.getInstance(endpointClass, session, collector);

        try {
            toCall.onClose(session, closeReason);
            session.setState(SessionImpl.State.CLOSED);
        } catch (Throwable t) {
            if (toCall != null) {
                toCall.onError(session, t);
            } else {
                collector.addException(new DeploymentException(t.getMessage(), t));
            }
        }

        synchronized (remoteEndpointToSession) {
            remoteEndpointToSession.remove(gs);
            componentProvider.removeSession(session);
        }
    }

    @Override
    public EndpointConfig getEndpointConfig() {
        return configuration;
    }

    boolean isOpen(SessionImpl session) {
        return remoteEndpointToSession.values().contains(session);
    }

    /**
     * Registered {@link Decoder}s.
     *
     * @return {@link List} of registered {@link Decoder}s.
     */
    List<Decoder> getDecoders() {
        return (List<Decoder>) (List<?>) decoders;
    }

    private Class<?> getEncoderClassType(Class<?> encoderClass) {
        if (Encoder.Binary.class.isAssignableFrom(encoderClass)) {
            return ReflectionHelper.getClassType(encoderClass, Encoder.Binary.class);
        } else if (Encoder.Text.class.isAssignableFrom(encoderClass)) {
            return ReflectionHelper.getClassType(encoderClass, Encoder.Text.class);
        } else if (Encoder.BinaryStream.class.isAssignableFrom(encoderClass)) {
            return ReflectionHelper.getClassType(encoderClass, Encoder.BinaryStream.class);
        } else if (Encoder.TextStream.class.isAssignableFrom(encoderClass)) {
            return ReflectionHelper.getClassType(encoderClass, Encoder.TextStream.class);
        } else {
            return null;
        }
    }

    private Class<?> getDecoderClassType(Class<?> decoderClass) {
        if (Decoder.Binary.class.isAssignableFrom(decoderClass)) {
            return ReflectionHelper.getClassType(decoderClass, Decoder.Binary.class);
        } else if (Decoder.Text.class.isAssignableFrom(decoderClass)) {
            return ReflectionHelper.getClassType(decoderClass, Decoder.Text.class);
        } else if (Decoder.BinaryStream.class.isAssignableFrom(decoderClass)) {
            return ReflectionHelper.getClassType(decoderClass, Decoder.BinaryStream.class);
        } else if (Decoder.TextStream.class.isAssignableFrom(decoderClass)) {
            return ReflectionHelper.getClassType(decoderClass, Decoder.TextStream.class);
        } else {
            return null;
        }
    }

    private static URI getURI(String uri, String queryString) {
        if (queryString != null && !queryString.isEmpty()) {
            return URI.create(String.format("%s?%s", uri, queryString));
        } else {
            return URI.create(uri);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("EndpointWrapper");
        sb.append("{endpointClass=").append(endpointClass);
        sb.append(", endpoint=").append(endpoint);
        sb.append(", uri='").append(uri).append('\'');
        sb.append(", contextPath='").append(contextPath).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
