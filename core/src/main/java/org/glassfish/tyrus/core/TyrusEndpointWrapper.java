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
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.CloseReason;
import javax.websocket.DecodeException;
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

import org.glassfish.tyrus.core.frame.BinaryFrame;
import org.glassfish.tyrus.core.frame.TextFrame;
import org.glassfish.tyrus.spi.UpgradeRequest;

/**
 * Wraps the registered application class.
 * There is one {@link TyrusEndpointWrapper} for each application class, which handles all the methods.
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Martin Matula (martin.matula at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class TyrusEndpointWrapper extends EndpointWrapper {

    private final static Logger LOGGER = Logger.getLogger(TyrusEndpointWrapper.class.getName());

    /**
     * The container for this session.
     */
    private final String contextPath;

    private final List<CoderWrapper<Decoder>> decoders = new ArrayList<CoderWrapper<Decoder>>();
    private final List<CoderWrapper<Encoder>> encoders = new ArrayList<CoderWrapper<Encoder>>();

    private final EndpointConfig configuration;
    private final Class<? extends Endpoint> endpointClass;
    private final Endpoint endpoint;
    private final Map<RemoteEndpoint, TyrusSession> remoteEndpointToSession =
            new ConcurrentHashMap<RemoteEndpoint, TyrusSession>();
    private final ComponentProviderService componentProvider;
    private final ServerEndpointConfig.Configurator configurator;
    private final WebSocketContainer container;

    private final Method onOpen;
    private final Method onClose;
    private final Method onError;


    /**
     * Create {@link TyrusEndpointWrapper} for class that extends {@link Endpoint}.
     *
     * @param endpointClass     endpoint class for which the wrapper is created.
     * @param configuration     endpoint configuration.
     * @param componentProvider component provider.
     * @param container         container where the wrapper is running.
     */
    public TyrusEndpointWrapper(Class<? extends Endpoint> endpointClass, EndpointConfig configuration,
                                ComponentProviderService componentProvider, WebSocketContainer container,
                                String contextPath, ServerEndpointConfig.Configurator configurator) throws DeploymentException {
        this(null, endpointClass, configuration, componentProvider, container, contextPath, configurator);
    }

    /**
     * Create {@link TyrusEndpointWrapper} for {@link Endpoint} instance or {@link AnnotatedEndpoint} instance.
     *
     * @param endpoint          endpoint instance for which the wrapper is created.
     * @param configuration     endpoint configuration.
     * @param componentProvider component provider.
     * @param container         container where the wrapper is running.
     */
    public TyrusEndpointWrapper(Endpoint endpoint, EndpointConfig configuration, ComponentProviderService componentProvider, WebSocketContainer container,
                                String contextPath, ServerEndpointConfig.Configurator configurator) throws DeploymentException {
        this(endpoint, null, configuration, componentProvider, container, contextPath, configurator);
    }

    private TyrusEndpointWrapper(Endpoint endpoint, Class<? extends Endpoint> endpointClass, EndpointConfig configuration,
                                 ComponentProviderService componentProvider, WebSocketContainer container,
                                 String contextPath, final ServerEndpointConfig.Configurator configurator) throws DeploymentException {
        this.endpointClass = endpointClass;
        this.endpoint = endpoint;
        this.container = container;
        this.contextPath = contextPath;
        this.configurator = configurator;
        this.componentProvider = configurator == null ? componentProvider : new ComponentProviderService(componentProvider) {
            @Override
            public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
                return configurator.getEndpointInstance(endpointClass);
            }
        };


        {
            final Class<? extends Endpoint> clazz = endpointClass == null ? endpoint.getClass() : endpointClass;
            Method onOpenMethod = null;
            Method onCloseMethod = null;
            Method onErrorMethod = null;

            for (Method m : Endpoint.class.getMethods()) {
                if (m.getName().equals("onOpen")) {
                    onOpenMethod = m;
                } else if (m.getName().equals("onClose")) {
                    onCloseMethod = m;
                } else if (m.getName().equals("onError")) {
                    onErrorMethod = m;
                }
            }

            try {
                // Endpoint class contains all of these.
                assert onOpenMethod != null;
                assert onCloseMethod != null;
                assert onErrorMethod != null;
                onOpenMethod = clazz.getMethod(onOpenMethod.getName(), onOpenMethod.getParameterTypes());
                onCloseMethod = clazz.getMethod(onCloseMethod.getName(), onCloseMethod.getParameterTypes());
                onErrorMethod = clazz.getMethod(onErrorMethod.getName(), onErrorMethod.getParameterTypes());
            } catch (NoSuchMethodException e) {
                throw new DeploymentException(e.getMessage(), e);
            }

            if (endpoint != null) {
                this.onOpen = onOpenMethod;
                this.onClose = onCloseMethod;
                this.onError = onErrorMethod;
            } else {
                this.onOpen = componentProvider.getInvocableMethod(onOpenMethod);
                this.onClose = componentProvider.getInvocableMethod(onCloseMethod);
                this.onError = componentProvider.getInvocableMethod(onErrorMethod);
            }
        }

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
    public boolean checkHandshake(UpgradeRequest hr) {
        if (!(configuration instanceof ServerEndpointConfig)) {
            return false;
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

    @Override
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

    private <T> Object getCoderInstance(Session session, CoderWrapper<T> wrapper) {
        final Object coder = wrapper.getCoder();
        if (coder == null) {
            ErrorCollector collector = new ErrorCollector();
            final Object coderInstance = this.componentProvider.getCoderInstance(wrapper.getCoderClass(), session, getEndpointConfig(), collector);
            if (!collector.isEmpty()) {
                final DeploymentException deploymentException = collector.composeComprehensiveException();
                LOGGER.log(Level.WARNING, deploymentException.getMessage(), deploymentException);
                return null;
            }

            return coderInstance;
        }

        return coder;
    }

    Object decodeCompleteMessage(Session session, Object message, Class<?> type, CoderWrapper<Decoder> selectedDecoder) throws DecodeException, IOException {
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

    Object doEncode(Session session, Object message) throws EncodeException, IOException {
        for (CoderWrapper<Encoder> enc : encoders) {
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
        }

        throw new EncodeException(message, "Encoding failed.");
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
    public Session createSessionForRemoteEndpoint(RemoteEndpoint re, String subprotocol, List<Extension> extensions) {
        synchronized (remoteEndpointToSession) {
            final TyrusSession session = new TyrusSession(container, re, this, subprotocol, extensions, false,
                    getURI(contextPath, null), null, Collections.<String, String>emptyMap(), null, Collections.<String, List<String>>emptyMap());
            remoteEndpointToSession.put(re, session);
            return session;
        }
    }

    private TyrusSession getSession(RemoteEndpoint gs) {
        synchronized (remoteEndpointToSession) {
            return remoteEndpointToSession.get(gs);
        }
    }

    @Override
    public Session onConnect(RemoteEndpoint gs, String subprotocol, List<Extension> extensions, UpgradeRequest upgradeRequest) {
        synchronized (remoteEndpointToSession) {
            TyrusSession session = remoteEndpointToSession.get(gs);
            if (session == null) {
                final Map<String, String> templateValues = new HashMap<String, String>();

                for (Map.Entry<String, List<String>> entry : upgradeRequest.getParameterMap().entrySet()) {
                    templateValues.put(entry.getKey(), entry.getValue().get(0));
                }

                // create a new session
                session = new TyrusSession(container, gs, this, subprotocol, extensions, upgradeRequest.isSecure(),
                        getURI(upgradeRequest.getRequestURI().toString(), upgradeRequest.getQueryString()),
                        upgradeRequest.getQueryString(), templateValues, upgradeRequest.getUserPrincipal(), upgradeRequest.getParameterMap());
                remoteEndpointToSession.put(gs, session);
            }

            ErrorCollector collector = new ErrorCollector();

            final Object toCall = endpoint != null ? endpoint :
                    componentProvider.getInstance(endpointClass, session, collector);
            try {
                if (!collector.isEmpty()) {
                    throw collector.composeComprehensiveException();
                }

                if (endpoint != null) {
                    ((Endpoint) toCall).onOpen(session, configuration);
                } else {
                    onOpen.invoke(toCall, session, configuration);
                }
            } catch (Throwable t) {
                if (toCall != null) {
                    if (endpoint != null) {
                        ((Endpoint) toCall).onError(session, t);
                    } else {
                        try {
                            onError.invoke(toCall, session, t);
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, t.getMessage(), t);
                        }
                    }
                } else {
                    LOGGER.log(Level.WARNING, t.getMessage(), t);
                }
            }

            return session;
        }
    }

    @Override
    public void onMessage(RemoteEndpoint gs, ByteBuffer messageBytes) {
        TyrusSession session = getSession(gs);

        if (session == null) {
            LOGGER.log(Level.FINE, "Message received on already closed connection.");
            return;
        }

        try {
            session.restartIdleTimeoutExecutor();
            final TyrusSession.State state = session.getState();
            if (state == TyrusSession.State.RECEIVING_BINARY || state == TyrusSession.State.RECEIVING_TEXT) {
                session.setState(TyrusSession.State.RUNNING);
            }
            if (session.isWholeBinaryHandlerPresent()) {
                session.notifyMessageHandlers(messageBytes, findApplicableDecoders(session, messageBytes, false));
            } else if (session.isPartialBinaryHandlerPresent()) {
                session.notifyMessageHandlers(messageBytes, true);
            } else {
                throw new IllegalStateException(String.format("Binary messageHandler not found. Session: '%s'.", session));
            }
        } catch (Throwable t) {
            if (!processThrowable(t, session)) {
                ErrorCollector collector = new ErrorCollector();
                final Object toCall = endpoint != null ? endpoint :
                        componentProvider.getInstance(endpointClass, session, collector);
                if (toCall != null) {
                    if (endpoint != null) {
                        ((Endpoint) toCall).onError(session, t);
                    } else {
                        try {
                            onError.invoke(toCall, session, t);
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, t.getMessage(), t);
                        }
                    }
                } else if (!collector.isEmpty()) {
                    final DeploymentException deploymentException = collector.composeComprehensiveException();
                    LOGGER.log(Level.WARNING, deploymentException.getMessage(), deploymentException);
                }
            }
        }
    }

    @Override
    public void onMessage(RemoteEndpoint gs, String messageString) {
        TyrusSession session = getSession(gs);

        if (session == null) {
            LOGGER.log(Level.FINE, "Message received on already closed connection.");
            return;
        }

        try {
            session.restartIdleTimeoutExecutor();
            final TyrusSession.State state = session.getState();
            if (state == TyrusSession.State.RECEIVING_BINARY || state == TyrusSession.State.RECEIVING_TEXT) {
                session.setState(TyrusSession.State.RUNNING);
            }
            if (session.isWholeTextHandlerPresent()) {
                session.notifyMessageHandlers(messageString, findApplicableDecoders(session, messageString, true));
            } else if (session.isPartialTextHandlerPresent()) {
                session.notifyMessageHandlers(messageString, true);
            } else {
                throw new IllegalStateException(String.format("Text messageHandler not found. Session: '%s'.", session));
            }
        } catch (Throwable t) {
            if (!processThrowable(t, session)) {
                ErrorCollector collector = new ErrorCollector();
                final Object toCall = endpoint != null ? endpoint :
                        componentProvider.getInstance(endpointClass, session, collector);
                if (toCall != null) {
                    if (endpoint != null) {
                        ((Endpoint) toCall).onError(session, t);
                    } else {
                        try {
                            onError.invoke(toCall, session, t);
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, t.getMessage(), t);
                        }
                    }
                } else if (!collector.isEmpty()) {
                    final DeploymentException deploymentException = collector.composeComprehensiveException();
                    LOGGER.log(Level.WARNING, deploymentException.getMessage(), deploymentException);
                }
            }
        }
    }

    @Override
    public void onPartialMessage(RemoteEndpoint gs, String partialString, boolean last) {
        TyrusSession session = getSession(gs);

        if (session == null) {
            LOGGER.log(Level.FINE, "Message received on already closed connection.");
            return;
        }

        try {
            session.restartIdleTimeoutExecutor();
            final TyrusSession.State state = session.getState();
            if (session.isPartialTextHandlerPresent()) {
                session.notifyMessageHandlers(partialString, last);
                if (state == TyrusSession.State.RECEIVING_BINARY || state == TyrusSession.State.RECEIVING_TEXT) {
                    session.setState(TyrusSession.State.RUNNING);
                }
            } else if (session.isReaderHandlerPresent()) {
                ReaderBuffer buffer = session.getReaderBuffer();
                switch (state) {
                    case RUNNING:
                        if (buffer == null) {
                            // TODO:
                            buffer = new ReaderBuffer(((BaseContainer) container).getExecutorService());
                            session.setReaderBuffer(buffer);
                        }
                        buffer.resetBuffer(session.getMaxTextMessageBufferSize());
                        buffer.setMessageHandler((session.getMessageHandler(Reader.class)));
                        buffer.appendMessagePart(partialString, last);
                        session.setState(TyrusSession.State.RECEIVING_TEXT);
                        break;
                    case RECEIVING_TEXT:
                        buffer.appendMessagePart(partialString, last);
                        if (last) {
                            session.setState(TyrusSession.State.RUNNING);
                        }
                        break;
                    default:
                        if (state == TyrusSession.State.RECEIVING_BINARY) {
                            session.setState(TyrusSession.State.RUNNING);
                        }
                        throw new IllegalStateException(String.format("Partial text message received out of order. Session: '%s'.", session));
                }
            } else if (session.isWholeTextHandlerPresent()) {
                switch (state) {
                    case RUNNING:
                        session.getTextBuffer().resetBuffer(session.getMaxTextMessageBufferSize());
                        session.getTextBuffer().appendMessagePart(partialString);
                        session.setState(TyrusSession.State.RECEIVING_TEXT);
                        break;
                    case RECEIVING_TEXT:
                        session.getTextBuffer().appendMessagePart(partialString);
                        if (last) {
                            final String message = session.getTextBuffer().getBufferedContent();
                            session.notifyMessageHandlers(message, findApplicableDecoders(session, message, true));
                            session.setState(TyrusSession.State.RUNNING);
                        }
                        break;
                    default:
                        if (state == TyrusSession.State.RECEIVING_BINARY) {
                            session.setState(TyrusSession.State.RUNNING);
                        }
                        throw new IllegalStateException(String.format("Text message received out of order. Session: '%s'.", session));
                }
            }
        } catch (Throwable t) {
            if (!processThrowable(t, session)) {
                ErrorCollector collector = new ErrorCollector();
                final Object toCall = endpoint != null ? endpoint :
                        componentProvider.getInstance(endpointClass, session, collector);
                if (toCall != null) {
                    if (endpoint != null) {
                        ((Endpoint) toCall).onError(session, t);
                    } else {
                        try {
                            onError.invoke(toCall, session, t);
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, t.getMessage(), t);
                        }
                    }
                } else if (!collector.isEmpty()) {
                    final DeploymentException deploymentException = collector.composeComprehensiveException();
                    LOGGER.log(Level.WARNING, deploymentException.getMessage(), deploymentException);
                }
            }
        }
    }

    @Override
    public void onPartialMessage(RemoteEndpoint gs, ByteBuffer partialBytes, boolean last) {
        TyrusSession session = getSession(gs);

        if (session == null) {
            LOGGER.log(Level.FINE, "Message received on already closed connection.");
            return;
        }

        try {
            session.restartIdleTimeoutExecutor();
            final TyrusSession.State state = session.getState();
            if (session.isPartialBinaryHandlerPresent()) {
                session.notifyMessageHandlers(partialBytes, last);
                if (state == TyrusSession.State.RECEIVING_BINARY || state == TyrusSession.State.RECEIVING_TEXT) {
                    session.setState(TyrusSession.State.RUNNING);
                }
            } else if (session.isInputStreamHandlerPresent()) {
                InputStreamBuffer buffer = session.getInputStreamBuffer();
                switch (state) {
                    case RUNNING:
                        if (buffer == null) {
                            // TODO
                            buffer = new InputStreamBuffer(((BaseContainer) container).getExecutorService());
                            session.setInputStreamBuffer(buffer);
                        }
                        buffer.resetBuffer(session.getMaxBinaryMessageBufferSize());
                        buffer.setMessageHandler((session.getMessageHandler(InputStream.class)));
                        buffer.appendMessagePart(partialBytes, last);
                        session.setState(TyrusSession.State.RECEIVING_BINARY);
                        break;
                    case RECEIVING_BINARY:
                        buffer.appendMessagePart(partialBytes, last);
                        if (last) {
                            session.setState(TyrusSession.State.RUNNING);
                        }
                        break;
                    default:
                        if (state == TyrusSession.State.RECEIVING_TEXT) {
                            session.setState(TyrusSession.State.RUNNING);
                        }
                        throw new IllegalStateException(String.format("Partial binary message received out of order. Session: '%s'.", session));
                }
            } else if (session.isWholeBinaryHandlerPresent()) {
                switch (state) {
                    case RUNNING:
                        session.getBinaryBuffer().resetBuffer(session.getMaxBinaryMessageBufferSize());
                        session.getBinaryBuffer().appendMessagePart(partialBytes);
                        session.setState(TyrusSession.State.RECEIVING_BINARY);
                        break;
                    case RECEIVING_BINARY:
                        session.getBinaryBuffer().appendMessagePart(partialBytes);
                        if (last) {
                            ByteBuffer bb = session.getBinaryBuffer().getBufferedContent();
                            session.notifyMessageHandlers(bb, findApplicableDecoders(session, bb, false));
                            session.setState(TyrusSession.State.RUNNING);
                        }
                        break;
                    default:
                        if (state == TyrusSession.State.RECEIVING_TEXT) {
                            session.setState(TyrusSession.State.RUNNING);
                        }
                        throw new IllegalStateException(String.format("Binary message received out of order. Session: '%s'.", session));
                }
            }
        } catch (Throwable t) {
            if (!processThrowable(t, session)) {
                ErrorCollector collector = new ErrorCollector();
                final Object toCall = endpoint != null ? endpoint :
                        componentProvider.getInstance(endpointClass, session, collector);
                if (toCall != null) {
                    if (endpoint != null) {
                        ((Endpoint) toCall).onError(session, t);
                    } else {
                        try {
                            onError.invoke(toCall, session, t);
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, t.getMessage(), t);
                        }
                    }
                } else if (!collector.isEmpty()) {
                    final DeploymentException deploymentException = collector.composeComprehensiveException();
                    LOGGER.log(Level.WARNING, deploymentException.getMessage(), deploymentException);
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
     * otherwise.
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
    public void onPong(RemoteEndpoint gs, final ByteBuffer bytes) {
        TyrusSession session = getSession(gs);

        if (session == null) {
            LOGGER.log(Level.FINE, "Pong received on already closed connection.");
            return;
        }

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
    public void onPing(RemoteEndpoint gs, ByteBuffer bytes) {
        TyrusSession session = getSession(gs);

        if (session == null) {
            LOGGER.log(Level.FINE, "Ping received on already closed connection.");
            return;
        }

        session.restartIdleTimeoutExecutor();
        try {
            session.getBasicRemote().sendPong(bytes);
        } catch (IOException e) {
            // do nothing.
            // we might consider calling onError, but there should be better defined exception.
        }
    }

    @Override
    public void onClose(RemoteEndpoint gs, CloseReason closeReason) {
        TyrusSession session = getSession(gs);

        if (session == null) {
            return;
        }

        session.setState(TyrusSession.State.CLOSING);

        ErrorCollector collector = new ErrorCollector();

        final Object toCall = endpoint != null ? endpoint :
                componentProvider.getInstance(endpointClass, session, collector);

        try {
            if (!collector.isEmpty()) {
                throw collector.composeComprehensiveException();
            }

            if (endpoint != null) {
                ((Endpoint) toCall).onClose(session, closeReason);
            } else {
                onClose.invoke(toCall, session, closeReason);
            }
        } catch (Throwable t) {
            if (toCall != null) {
                if (endpoint != null) {
                    ((Endpoint) toCall).onError(session, t);
                } else {
                    try {
                        onError.invoke(toCall, session, t);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, t.getMessage(), t);
                    }
                }
            } else {
                LOGGER.log(Level.WARNING, t.getMessage(), t);
            }
        } finally {
            session.setState(TyrusSession.State.CLOSED);

            synchronized (remoteEndpointToSession) {
                remoteEndpointToSession.remove(gs);
                componentProvider.removeSession(session);
            }
        }
    }

    @Override
    public EndpointConfig getEndpointConfig() {
        return configuration;
    }

    /**
     * Broadcasts text message to all connected clients.
     *
     * @param message message to be broadcasted.
     * @return map of sessions and futures for user to get the information about status of the message.
     */
    public Map<Session, Future<?>> broadcast(final String message) {

        final Map<Session, Future<?>> futures = new HashMap<Session, Future<?>>();
        byte[] frame = null;

        for (Map.Entry<RemoteEndpoint, TyrusSession> e : remoteEndpointToSession.entrySet()) {
            if (e.getValue().isOpen()) {

                final TyrusRemoteEndpoint remoteEndpoint = (TyrusRemoteEndpoint) e.getKey();

                if (frame == null) {
                    final Frame wrappedFrame = new TextFrame(message, false, true);
                    final ByteBuffer byteBuffer = ((TyrusWebSocket) remoteEndpoint.getSocket()).getProtocolHandler().frame(wrappedFrame);
                    frame = new byte[byteBuffer.remaining()];
                    byteBuffer.get(frame);
                }

                final Future<Frame> frameFuture = remoteEndpoint.sendRawFrame(ByteBuffer.wrap(frame));
                futures.put(e.getValue(), frameFuture);
            }
        }

        return futures;
    }

    /**
     * Broadcasts binary message to all connected clients.
     *
     * @param message message to be broadcasted.
     * @return map of sessions and futures for user to get the information about status of the message.
     */
    public Map<Session, Future<?>> broadcast(final ByteBuffer message) {

        final Map<Session, Future<?>> futures = new HashMap<Session, Future<?>>();
        byte[] frame = null;

        for (Map.Entry<RemoteEndpoint, TyrusSession> e : remoteEndpointToSession.entrySet()) {
            if (e.getValue().isOpen()) {

                final TyrusRemoteEndpoint remoteEndpoint = (TyrusRemoteEndpoint) e.getKey();

                if (frame == null) {
                    byte[] byteArrayMessage = new byte[message.remaining()];
                    message.get(byteArrayMessage);

                    final Frame dataFrame = new BinaryFrame(byteArrayMessage, false, true);
                    final ByteBuffer byteBuffer = ((TyrusWebSocket) remoteEndpoint.getSocket()).getProtocolHandler().frame(dataFrame);
                    frame = new byte[byteBuffer.remaining()];
                    byteBuffer.get(frame);
                }

                final Future<Frame> frameFuture = remoteEndpoint.sendRawFrame(ByteBuffer.wrap(frame));
                futures.put(e.getValue(), frameFuture);
            }
        }

        return futures;
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
        sb.append(", contextPath='").append(contextPath).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
