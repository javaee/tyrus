/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2014 Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
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
import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;

import org.glassfish.tyrus.core.cluster.BroadcastListener;
import org.glassfish.tyrus.core.cluster.ClusterContext;
import org.glassfish.tyrus.core.cluster.RemoteSession;
import org.glassfish.tyrus.core.coder.CoderWrapper;
import org.glassfish.tyrus.core.coder.InputStreamDecoder;
import org.glassfish.tyrus.core.coder.NoOpByteArrayCoder;
import org.glassfish.tyrus.core.coder.NoOpByteBufferCoder;
import org.glassfish.tyrus.core.coder.NoOpTextCoder;
import org.glassfish.tyrus.core.coder.PrimitiveDecoders;
import org.glassfish.tyrus.core.coder.ReaderDecoder;
import org.glassfish.tyrus.core.coder.ToStringEncoder;
import org.glassfish.tyrus.core.frame.BinaryFrame;
import org.glassfish.tyrus.core.frame.Frame;
import org.glassfish.tyrus.core.frame.TextFrame;
import org.glassfish.tyrus.core.frame.TyrusFrame;
import org.glassfish.tyrus.core.l10n.LocalizationMessages;
import org.glassfish.tyrus.core.monitoring.EndpointEventListener;
import org.glassfish.tyrus.spi.UpgradeRequest;
import org.glassfish.tyrus.spi.UpgradeResponse;

/**
 * Wraps the registered application class.
 * There is one {@link TyrusEndpointWrapper} for each application class, which handles all the methods.
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Martin Matula (martin.matula at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class TyrusEndpointWrapper {

    private final static Logger LOGGER = Logger.getLogger(TyrusEndpointWrapper.class.getName());

    /**
     * Used as threshold for parallel broadcast. When the sessions are divided between threads, number of sessions
     * per thread should not be lower than this constant.
     */
    private final static int MIN_SESSIONS_PER_THREAD = 16;
    /**
     * The container for this session.
     */
    private final WebSocketContainer container;
    private final String contextPath;
    private final String endpointPath;
    private final String serverEndpointPath;
    private final List<CoderWrapper<Decoder>> decoders = new ArrayList<CoderWrapper<Decoder>>();
    private final List<CoderWrapper<Encoder>> encoders = new ArrayList<CoderWrapper<Encoder>>();
    private final EndpointConfig configuration;
    private final Class<? extends Endpoint> endpointClass;
    private final Endpoint endpoint;
    private final Map<TyrusWebSocket, TyrusSession> webSocketToSession =
            new ConcurrentHashMap<TyrusWebSocket, TyrusSession>();
    private final Map<String, RemoteSession> clusteredSessions =
            new ConcurrentHashMap<String, RemoteSession>();
    private final ComponentProviderService componentProvider;
    private final ServerEndpointConfig.Configurator configurator;
    private final Method onOpen;
    private final Method onClose;
    private final Method onError;
    private final SessionListener sessionListener;
    private final EndpointEventListener endpointEventListener;
    private final boolean parallelBroadcastEnabled;
    private final boolean programmaticEndpoint;

    private final ClusterContext clusterContext;

    /**
     * Create {@link TyrusEndpointWrapper} for class that extends {@link Endpoint}.
     *
     * @param endpointClass            endpoint class for which the wrapper is created.
     * @param configuration            endpoint configuration.
     * @param componentProvider        component provider.
     * @param container                container where the wrapper is running.
     * @param clusterContext           cluster context instance. {@code null} indicates standalone mode.
     * @param endpointEventListener    endpoint event listener.
     * @param parallelBroadcastEnabled {@code true} if parallel broadcast should be enabled, {@code true} is default.
     */
    public TyrusEndpointWrapper(Class<? extends Endpoint> endpointClass, EndpointConfig configuration,
                                ComponentProviderService componentProvider, WebSocketContainer container,
                                String contextPath, ServerEndpointConfig.Configurator configurator,
                                SessionListener sessionListener, ClusterContext clusterContext,
                                EndpointEventListener endpointEventListener, Boolean parallelBroadcastEnabled) throws DeploymentException {
        this(null, endpointClass, configuration, componentProvider, container, contextPath, configurator, sessionListener, clusterContext, endpointEventListener, parallelBroadcastEnabled);
    }

    /**
     * Create {@link TyrusEndpointWrapper} for {@link Endpoint} instance or {@link AnnotatedEndpoint} instance.
     *
     * @param endpoint                 endpoint instance for which the wrapper is created.
     * @param configuration            endpoint configuration.
     * @param componentProvider        component provider.
     * @param container                container where the wrapper is running.
     * @param clusterContext           cluster context instance. {@code null} indicates standalone mode.
     * @param endpointEventListener    endpoint event listener.
     * @param parallelBroadcastEnabled {@code true} if parallel broadcast should be enabled, {@code true} is default.
     */
    public TyrusEndpointWrapper(Endpoint endpoint, EndpointConfig configuration, ComponentProviderService componentProvider, WebSocketContainer container,
                                String contextPath, ServerEndpointConfig.Configurator configurator, SessionListener sessionListener, ClusterContext clusterContext,
                                EndpointEventListener endpointEventListener, Boolean parallelBroadcastEnabled) throws DeploymentException {
        this(endpoint, null, configuration, componentProvider, container, contextPath, configurator, sessionListener, clusterContext, endpointEventListener, parallelBroadcastEnabled);
    }

    private TyrusEndpointWrapper(Endpoint endpoint, Class<? extends Endpoint> endpointClass, EndpointConfig configuration,
                                 ComponentProviderService componentProvider, WebSocketContainer container,
                                 String contextPath, final ServerEndpointConfig.Configurator configurator,
                                 SessionListener sessionListener, final ClusterContext clusterContext,
                                 EndpointEventListener endpointEventListener, Boolean parallelBroadcastEnabled) throws DeploymentException {
        this.endpointClass = endpointClass;
        this.endpoint = endpoint;
        this.programmaticEndpoint = (endpoint != null);
        this.container = container;
        this.contextPath = contextPath;
        this.configurator = configurator;
        this.sessionListener = sessionListener;
        this.clusterContext = clusterContext;

        if (endpointEventListener != null) {
            this.endpointEventListener = endpointEventListener;
        } else {
            this.endpointEventListener = EndpointEventListener.NO_OP;
        }

        if (parallelBroadcastEnabled == null) {
            this.parallelBroadcastEnabled = true;
        } else {
            this.parallelBroadcastEnabled = parallelBroadcastEnabled;
        }

        // server-side only
        if (configuration instanceof ServerEndpointConfig) {
            this.serverEndpointPath = ((ServerEndpointConfig) configuration).getPath();
            this.endpointPath = (contextPath.endsWith("/") ? contextPath.substring(0, contextPath.length() - 1) : contextPath)
                    + "/" + (serverEndpointPath.startsWith("/") ? serverEndpointPath.substring(1) : serverEndpointPath);
        } else {
            this.serverEndpointPath = null;
            this.endpointPath = null;
        }

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

            if (programmaticEndpoint) {
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
            if (getDefaultDecoders().contains(decoderClass)) {
                try {
                    decoders.add(new CoderWrapper<Decoder>(ReflectionHelper.getInstance(decoderClass), type));
                } catch (ReflectiveOperationException e) {
                    throw new DeploymentException(e.getMessage(), e);
                }
            } else {
                decoders.add(new CoderWrapper<Decoder>(decoderClass, type));
            }
        }

        //this wrapper represents endpoint which is not annotated endpoint
        if (endpoint == null || !(endpoint instanceof AnnotatedEndpoint)) {
            for (Class<? extends Decoder> decoderClass : getDefaultDecoders()) {
                Class<?> type = getDecoderClassType(decoderClass);
                try {
                    decoders.add(new CoderWrapper<Decoder>(ReflectionHelper.getInstance(decoderClass), type));
                } catch (ReflectiveOperationException e) {
                    throw new DeploymentException(e.getMessage(), e);
                }
            }
        }

        for (Class<? extends Encoder> encoderClass : this.configuration.getEncoders()) {
            Class<?> type = getEncoderClassType(encoderClass);
            encoders.add(new CoderWrapper<Encoder>(encoderClass, type));
        }

        encoders.add(new CoderWrapper<Encoder>(new NoOpTextCoder(), String.class));
        encoders.add(new CoderWrapper<Encoder>(new NoOpByteBufferCoder(), ByteBuffer.class));
        encoders.add(new CoderWrapper<Encoder>(new NoOpByteArrayCoder(), byte[].class));
        encoders.add(new CoderWrapper<Encoder>(new ToStringEncoder(), Object.class));

        // clustered mode
        if (clusterContext != null) {
            clusterContext.registerSessionListener(getEndpointPath(), new org.glassfish.tyrus.core.cluster.SessionListener() {
                @Override
                public void onSessionOpened(String sessionId) {
                    final Map<RemoteSession.DistributedMapKey, Object> distributedSessionProperties = clusterContext.getDistributedSessionProperties(sessionId);
                    clusteredSessions.put(sessionId, new RemoteSession(sessionId, clusterContext, distributedSessionProperties, TyrusEndpointWrapper.this, dummySession));
                }

                @Override
                public void onSessionClosed(String sessionId) {
                    clusteredSessions.remove(sessionId);
                }
            });

            clusterContext.registerBroadcastListener(getEndpointPath(), new BroadcastListener() {
                @Override
                public void onBroadcast(String text) {
                    broadcast(text, true);
                }

                @Override
                public void onBroadcast(byte[] data) {
                    broadcast(ByteBuffer.wrap(data), true);
                }
            });

            for (String sessionId : clusterContext.getRemoteSessionIds(getEndpointPath())) {
                final Map<RemoteSession.DistributedMapKey, Object> distributedSessionProperties = clusterContext.getDistributedSessionProperties(sessionId);
                clusteredSessions.put(sessionId, new RemoteSession(sessionId, clusterContext, distributedSessionProperties, this, dummySession));
            }
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

    private static URI getURI(String uri, String queryString) {
        if (queryString != null && !queryString.isEmpty()) {
            return URI.create(String.format("%s?%s", uri, queryString));
        } else {
            return URI.create(uri);
        }
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

    Object decodeCompleteMessage(TyrusSession session, Object message, Class<?> type, CoderWrapper<Decoder> selectedDecoder) throws DecodeException, IOException {
        final Class<? extends Decoder> decoderClass = selectedDecoder.getCoderClass();

        if (Decoder.Text.class.isAssignableFrom(decoderClass)) {
            if (type != null && type.isAssignableFrom(selectedDecoder.getType())) {
                final Decoder.Text decoder = (Decoder.Text) getCoderInstance(session, selectedDecoder);

                session.getDebugContext().appendLogMessage(LOGGER, Level.FINEST, DebugContext.Type.MESSAGE_IN, "Decoding with ", selectedDecoder);

                // TYRUS-210: willDecode was already called
                return decoder.decode((String) message);
            }
        } else if (Decoder.Binary.class.isAssignableFrom(decoderClass)) {
            if (type != null && type.isAssignableFrom(selectedDecoder.getType())) {
                final Decoder.Binary decoder = (Decoder.Binary) getCoderInstance(session, selectedDecoder);

                session.getDebugContext().appendLogMessage(LOGGER, Level.FINEST, DebugContext.Type.MESSAGE_IN, "Decoding with ", selectedDecoder);
                // TYRUS-210: willDecode was already called
                return decoder.decode((ByteBuffer) message);
            }
        } else if (Decoder.TextStream.class.isAssignableFrom(decoderClass)) {
            if (type != null && type.isAssignableFrom(selectedDecoder.getType())) {

                session.getDebugContext().appendLogMessage(LOGGER, Level.FINEST, DebugContext.Type.MESSAGE_IN, "Decoding with ", selectedDecoder);

                return ((Decoder.TextStream) getCoderInstance(session, selectedDecoder)).decode(new StringReader((String) message));
            }
        } else if (Decoder.BinaryStream.class.isAssignableFrom(decoderClass)) {
            if (type != null && type.isAssignableFrom(selectedDecoder.getType())) {
                byte[] array = ((ByteBuffer) message).array();

                session.getDebugContext().appendLogMessage(LOGGER, Level.FINEST, DebugContext.Type.MESSAGE_IN, "Decoding with ", selectedDecoder);

                return ((Decoder.BinaryStream) getCoderInstance(session, selectedDecoder)).decode(new ByteArrayInputStream(array));
            }
        }

        return null;
    }

    private ArrayList<CoderWrapper<Decoder>> findApplicableDecoders(TyrusSession session, Object message, boolean isString) {
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

        session.getDebugContext().appendLogMessage(LOGGER, Level.FINEST, DebugContext.Type.MESSAGE_IN, "Applicable decoders: ", result);

        return result;
    }

    public Object doEncode(Session session, Object message) throws EncodeException, IOException {
        for (CoderWrapper<Encoder> enc : encoders) {
            final Class<? extends Encoder> encoderClass = enc.getCoderClass();

            if (Encoder.Binary.class.isAssignableFrom(encoderClass)) {
                if (enc.getType().isAssignableFrom(message.getClass())) {
                    final Encoder.Binary encoder = (Encoder.Binary) getCoderInstance(session, enc);

                    logUsedEncoder(enc, session);

                    return encoder.encode(message);
                }
            } else if (Encoder.Text.class.isAssignableFrom(encoderClass)) {
                if (enc.getType().isAssignableFrom(message.getClass())) {
                    final Encoder.Text encoder = (Encoder.Text) getCoderInstance(session, enc);

                    logUsedEncoder(enc, session);

                    return encoder.encode(message);
                }
            } else if (Encoder.BinaryStream.class.isAssignableFrom(encoderClass)) {
                if (enc.getType().isAssignableFrom(message.getClass())) {
                    final ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    final Encoder.BinaryStream encoder = (Encoder.BinaryStream) getCoderInstance(session, enc);

                    logUsedEncoder(enc, session);

                    encoder.encode(message, stream);
                    return stream;
                }
            } else if (Encoder.TextStream.class.isAssignableFrom(encoderClass)) {
                if (enc.getType().isAssignableFrom(message.getClass())) {
                    final Writer writer = new StringWriter();
                    final Encoder.TextStream encoder = (Encoder.TextStream) getCoderInstance(session, enc);

                    logUsedEncoder(enc, session);

                    encoder.encode(message, writer);
                    return writer;
                }
            }
        }

        throw new EncodeException(message, LocalizationMessages.ENCODING_FAILED());
    }

    private void logUsedEncoder(CoderWrapper<Encoder> encoder, Session session) {
        if (LOGGER.isLoggable(Level.FINEST)) {
            if (session instanceof TyrusSession) {
                ((TyrusSession) session).getDebugContext().appendLogMessage(LOGGER, Level.FINEST, DebugContext.Type.MESSAGE_OUT, "Encoding with: ", encoder);
            }
        }
    }

    /**
     * Server-side; Get Endpoint absolute path.
     *
     * @return endpoint absolute path.
     */
    public String getEndpointPath() {
        return endpointPath;
    }

    /**
     * Server-side; Get server endpoint path.
     * <p/>
     * In this context, server endpoint path is exactly what is present in {@link javax.websocket.server.ServerEndpoint}
     * annotation or returned from {@link javax.websocket.server.ServerEndpointConfig#getPath()} method call. Context
     * path is not included.
     *
     * @return server endpoint path.
     * @see javax.websocket.server.ServerEndpoint#value()
     * @see javax.websocket.server.ServerEndpointConfig#getPath()
     */
    String getServerEndpointPath() {
        return serverEndpointPath;
    }

    /**
     * Server-side; Get the negotiated extensions' names based on the extensions supported by client.
     *
     * @param clientExtensions names of the extensions' supported by client.
     * @return names of extensions supported by both client and class that implements this one.
     */
    List<Extension> getNegotiatedExtensions(List<Extension> clientExtensions) {
        if (configuration instanceof ServerEndpointConfig) {
            return configurator.getNegotiatedExtensions(((ServerEndpointConfig) configuration).getExtensions(), clientExtensions);
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Server-side; Compute the sub-protocol which will be used.
     *
     * @param clientProtocols sub-protocols supported by client.
     * @return negotiated sub-protocol, {@code null} if none found.
     */
    String getNegotiatedProtocol(List<String> clientProtocols) {
        if (configuration instanceof ServerEndpointConfig) {
            return configurator.getNegotiatedSubprotocol(((ServerEndpointConfig) configuration).getSubprotocols(), clientProtocols);
        } else {
            return null;
        }
    }

    /**
     * Get the set of open {@link TyrusSession}.
     *
     * @return open sessions.
     */
    Set<TyrusSession> getOpenSessions() {
        Set<TyrusSession> result = new HashSet<TyrusSession>();

        for (TyrusSession session : webSocketToSession.values()) {
            if (session.isOpen()) {
                result.add(session);
            }
        }

        return Collections.unmodifiableSet(result);
    }

    Set<RemoteSession> getRemoteSessions() {
        Set<RemoteSession> result = new HashSet<RemoteSession>();

        // clustered mode
        if (clusterContext != null) {
            result.addAll(clusteredSessions.values());
        }

        return Collections.unmodifiableSet(result);
    }

    /**
     * Creates a Session based on the {@link TyrusWebSocket}, subprotocols and extensions.
     *
     * @param socket       the other end of the connection.
     * @param subprotocol  used.
     * @param extensions   extensions used.
     * @param debugContext debug context.
     * @return {@link Session} representing the connection.
     */
    public Session createSessionForRemoteEndpoint(TyrusWebSocket socket, String subprotocol, List<Extension> extensions, DebugContext debugContext) {
        final TyrusSession session = new TyrusSession(container, socket, this, subprotocol, extensions, false,
                getURI(contextPath, null), null, Collections.<String, String>emptyMap(), null, Collections.<String, List<String>>emptyMap(), null, null, null, debugContext);
        webSocketToSession.put(socket, session);
        return session;
    }

    private TyrusSession getSession(TyrusWebSocket socket) {
        return webSocketToSession.get(socket);
    }

    /**
     * Called by the provider when the web socket connection
     * is established.
     *
     * @param socket         {@link TyrusWebSocket} who has just connected to this web socket endpoint.
     * @param upgradeRequest request associated with accepted connection.
     * @param debugContext   debug context.
     * @return Created {@link Session} instance or {@code null} when session was not created properly (max sessions
     * limit on endpoint or application or issues with endpoint validation).
     */
    Session onConnect(TyrusWebSocket socket, UpgradeRequest upgradeRequest, String subProtocol, List<Extension> extensions, String connectionId, DebugContext debugContext) {
        TyrusSession session = webSocketToSession.get(socket);
        // session is null on Server; client always has session instance at this point.
        if (session == null) {
            final Map<String, String> templateValues = new HashMap<String, String>();

            for (Map.Entry<String, List<String>> entry : upgradeRequest.getParameterMap().entrySet()) {
                templateValues.put(entry.getKey(), entry.getValue().get(0));
            }

            // create a new session
            session = new TyrusSession(container, socket, this, subProtocol, extensions, upgradeRequest.isSecure(),
                    getURI(upgradeRequest.getRequestURI().toString(), upgradeRequest.getQueryString()),
                    upgradeRequest.getQueryString(), templateValues, upgradeRequest.getUserPrincipal(),
                    upgradeRequest.getParameterMap(), clusterContext, connectionId,
                    ((RequestContext) upgradeRequest).getRemoteAddr(), debugContext);
            webSocketToSession.put(socket, session);

            // max open session per endpoint exceeded?
            boolean maxSessionPerEndpointExceeded = configuration instanceof TyrusServerEndpointConfig &&
                    ((TyrusServerEndpointConfig) configuration).getMaxSessions() > 0 &&
                    webSocketToSession.size() > ((TyrusServerEndpointConfig) configuration).getMaxSessions();

            SessionListener.OnOpenResult onOpenResult = sessionListener.onOpen(session);

            // test max open sessions per endpoint and per application
            if (maxSessionPerEndpointExceeded || !onOpenResult.equals(SessionListener.OnOpenResult.SESSION_ALLOWED)) {
                try {
                    webSocketToSession.remove(socket);
                    String refuseDetail;

                    if (maxSessionPerEndpointExceeded) {
                        refuseDetail = LocalizationMessages.MAX_SESSIONS_PER_ENDPOINT_EXCEEDED();
                    } else {
                        switch (onOpenResult) {
                            case MAX_SESSIONS_PER_APP_EXCEEDED:
                                refuseDetail = LocalizationMessages.MAX_SESSIONS_PER_APP_EXCEEDED();
                                break;
                            case MAX_SESSIONS_PER_REMOTE_ADDR_EXCEEDED:
                                refuseDetail = LocalizationMessages.MAX_SESSIONS_PER_REMOTEADDR_EXCEEDED();
                                break;
                            default:
                                // should not happen.
                                refuseDetail = null;
                        }
                    }

                    debugContext.appendLogMessage(LOGGER, Level.FINE, DebugContext.Type.MESSAGE_IN, "Session opening refused: ", refuseDetail);
                    session.close(new CloseReason(CloseReason.CloseCodes.TRY_AGAIN_LATER, refuseDetail));
                } catch (IOException e) {
                    debugContext.appendLogMessageWithThrowable(LOGGER, Level.WARNING, DebugContext.Type.MESSAGE_IN, e, e.getMessage());
                }
                // session was not opened.
                return null;
            }

            socket.setMessageEventListener(endpointEventListener.onSessionOpened(session.getId()));
        }

        ErrorCollector collector = new ErrorCollector();

        final Object toCall = programmaticEndpoint ? endpoint :
                componentProvider.getInstance(endpointClass, session, collector);

        // TYRUS-325: Server do not close session properly if non-instantiable endpoint class is provided
        // programmatic non-instantiable endpoint has toCall null
        if (toCall == null) {
            if (!collector.isEmpty()) {
                Throwable t = collector.composeComprehensiveException();
                debugContext.appendLogMessageWithThrowable(LOGGER, Level.FINE, DebugContext.Type.MESSAGE_IN, t, t.getMessage());
            }
            webSocketToSession.remove(socket);
            sessionListener.onClose(session, CloseReasons.UNEXPECTED_CONDITION.getCloseReason());
            try {
                session.close(CloseReasons.UNEXPECTED_CONDITION.getCloseReason());
            } catch (IOException e) {
                debugContext.appendLogMessageWithThrowable(LOGGER, Level.FINEST, DebugContext.Type.MESSAGE_IN, e, e.getMessage());
            }
            // session was not opened.
            return null;
        }

        try {
            if (!collector.isEmpty()) {
                throw collector.composeComprehensiveException();
            }

            if (programmaticEndpoint) {
                ((Endpoint) toCall).onOpen(session, configuration);
            } else {
                try {
                    onOpen.invoke(toCall, session, configuration);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
        } catch (Throwable t) {
            if (programmaticEndpoint) {
                ((Endpoint) toCall).onError(session, t);
            } else {
                try {
                    onError.invoke(toCall, session, t);
                } catch (Exception e) {
                    debugContext.appendLogMessageWithThrowable(LOGGER, Level.WARNING, DebugContext.Type.MESSAGE_IN, t, t.getMessage());
                }
            }
            endpointEventListener.onError(session.getId(), t);
        }

        return session;
    }

    /**
     * Called by the provider when the web socket connection
     * has an incoming text message from the given remote endpoint.
     *
     * @param socket       {@link TyrusWebSocket} who sent the message.
     * @param messageBytes the message.
     */
    void onMessage(TyrusWebSocket socket, ByteBuffer messageBytes) {
        TyrusSession session = getSession(socket);

        if (session == null) {
            LOGGER.log(Level.FINE, "Message received on already closed connection.");
            return;
        }

        session.getDebugContext().appendLogMessage(LOGGER, Level.FINEST, DebugContext.Type.MESSAGE_IN, "Received binary message");

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
                throw new IllegalStateException(LocalizationMessages.BINARY_MESSAGE_HANDLER_NOT_FOUND(session));
            }
        } catch (Throwable t) {
            if (!processThrowable(t, session)) {
                ErrorCollector collector = new ErrorCollector();
                final Object toCall = programmaticEndpoint ? endpoint :
                        componentProvider.getInstance(endpointClass, session, collector);
                if (toCall != null) {
                    if (programmaticEndpoint) {
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
                endpointEventListener.onError(session.getId(), t);
            }
        }
    }

    /**
     * Called by the provider when the web socket connection
     * has an incoming text message from the given remote endpoint.
     *
     * @param socket        {@link TyrusWebSocket} who sent the message.
     * @param messageString the message.
     */
    void onMessage(TyrusWebSocket socket, String messageString) {
        TyrusSession session = getSession(socket);

        if (session == null) {
            LOGGER.log(Level.FINE, "Message received on already closed connection.");
            return;
        }

        session.getDebugContext().appendLogMessage(LOGGER, Level.FINEST, DebugContext.Type.MESSAGE_IN, "Received text message");

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
                throw new IllegalStateException(LocalizationMessages.TEXT_MESSAGE_HANDLER_NOT_FOUND(session));
            }
        } catch (Throwable t) {
            if (!processThrowable(t, session)) {
                ErrorCollector collector = new ErrorCollector();
                final Object toCall = programmaticEndpoint ? endpoint :
                        componentProvider.getInstance(endpointClass, session, collector);
                if (toCall != null) {
                    if (programmaticEndpoint) {
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
                endpointEventListener.onError(session.getId(), t);
            }
        }
    }

    /**
     * Called by the provider when the web socket connection
     * has an incoming partial text message from the given remote endpoint. Partial
     * text messages are passed in sequential order, one piece at a time. If an implementation
     * does not support streaming, it will need to reconstruct the message here and pass the whole
     * thing along.
     *
     * @param socket        {@link TyrusWebSocket} who sent the message.
     * @param partialString the String message.
     * @param last          to indicate if this is the last partial string in the sequence
     */
    void onPartialMessage(TyrusWebSocket socket, String partialString, boolean last) {
        TyrusSession session = getSession(socket);

        if (session == null) {
            LOGGER.log(Level.FINE, "Message received on already closed connection.");
            return;
        }

        session.getDebugContext().appendLogMessage(LOGGER, Level.FINEST, DebugContext.Type.MESSAGE_IN, "Received partial text message");

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
                        throw new IllegalStateException(LocalizationMessages.PARTIAL_TEXT_MESSAGE_OUT_OF_ORDER(session));
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
                        throw new IllegalStateException(LocalizationMessages.TEXT_MESSAGE_OUT_OF_ORDER(session));
                }
            }
        } catch (Throwable t) {
            if (!processThrowable(t, session)) {
                ErrorCollector collector = new ErrorCollector();
                final Object toCall = programmaticEndpoint ? endpoint :
                        componentProvider.getInstance(endpointClass, session, collector);
                if (toCall != null) {
                    if (programmaticEndpoint) {
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
                endpointEventListener.onError(session.getId(), t);
            }
        }
    }

    /**
     * Called by the provider when the web socket connection
     * has an incoming partial binary message from the given remote endpoint. Partial
     * binary messages are passed in sequential order, one piece at a time. If an implementation
     * does not support streaming, it will need to reconstruct the message here and pass the whole
     * thing along.
     *
     * @param socket       {@link TyrusWebSocket} who sent the message.
     * @param partialBytes the piece of the binary message.
     * @param last         to indicate if this is the last partial byte buffer in the sequence
     */
    void onPartialMessage(TyrusWebSocket socket, ByteBuffer partialBytes, boolean last) {
        TyrusSession session = getSession(socket);

        if (session == null) {
            LOGGER.log(Level.FINE, "Message received on already closed connection.");
            return;
        }

        session.getDebugContext().appendLogMessage(LOGGER, Level.FINEST, DebugContext.Type.MESSAGE_IN, "Received partial binary message");

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
                        throw new IllegalStateException(LocalizationMessages.PARTIAL_BINARY_MESSAGE_OUT_OF_ORDER(session));
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
                        throw new IllegalStateException(LocalizationMessages.BINARY_MESSAGE_OUT_OF_ORDER(session));
                }
            }
        } catch (Throwable t) {
            if (!processThrowable(t, session)) {
                ErrorCollector collector = new ErrorCollector();
                final Object toCall = programmaticEndpoint ? endpoint :
                        componentProvider.getInstance(endpointClass, session, collector);
                if (toCall != null) {
                    if (programmaticEndpoint) {
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
                endpointEventListener.onError(session.getId(), t);
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

        if (throwable instanceof WebSocketException) {
            try {
                session.close(((WebSocketException) throwable).getCloseReason());
                return false;
            } catch (IOException e) {
                // we don't care.
            }
        }

        return false;
    }

    /**
     * Called by the provider when the web socket connection
     * has an incoming pong message from the given remote endpoint.
     *
     * @param socket {@link TyrusWebSocket} who sent the message.
     * @param bytes  the message.
     */
    void onPong(TyrusWebSocket socket, final ByteBuffer bytes) {
        TyrusSession session = getSession(socket);

        if (session == null) {
            LOGGER.log(Level.FINE, "Pong received on already closed connection.");
            return;
        }

        session.getDebugContext().appendLogMessage(LOGGER, Level.FINEST, DebugContext.Type.MESSAGE_IN, "Received pong message");

        session.restartIdleTimeoutExecutor();

        if (session.isPongHandlerPresent()) {
            try {
                session.notifyPongHandler(new PongMessage() {
                    @Override
                    public ByteBuffer getApplicationData() {
                        return bytes;
                    }

                    @Override
                    public String toString() {
                        return "PongMessage: " + bytes;
                    }
                });
            } catch (Throwable t) {
                if (!processThrowable(t, session)) {
                    ErrorCollector collector = new ErrorCollector();
                    final Object toCall = programmaticEndpoint ? endpoint :
                            componentProvider.getInstance(endpointClass, session, collector);
                    if (toCall != null) {
                        if (programmaticEndpoint) {
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
                    endpointEventListener.onError(session.getId(), t);
                }
            }
        } else {
            session.getDebugContext().appendLogMessage(LOGGER, Level.FINEST, DebugContext.Type.MESSAGE_IN, "Unhandled pong message");
        }
    }

    /**
     * Called by the provider when the web socket connection
     * has an incoming ping message from the given remote endpoint.
     * <p/>
     * The endpoint needs to respond as soon as possible (see the websocket RFC).
     * No involvement from application layer, there is no ping listener.
     *
     * @param socket {@link TyrusWebSocket} who sent the message.
     * @param bytes  the message.
     */
    void onPing(TyrusWebSocket socket, ByteBuffer bytes) {
        TyrusSession session = getSession(socket);

        if (session == null) {
            LOGGER.log(Level.FINE, "Ping received on already closed connection.");
            return;
        }

        session.getDebugContext().appendLogMessage(LOGGER, Level.FINEST, DebugContext.Type.MESSAGE_IN, "Received ping message");

        session.restartIdleTimeoutExecutor();
        try {
            session.getBasicRemote().sendPong(bytes);
        } catch (IOException e) {
            // do nothing.
            // we might consider calling onError, but there should be better defined exception.
        }
    }

    /**
     * Called by the provider when the web socket connection
     * to the given remote endpoint has just closed.
     *
     * @param socket {@link TyrusWebSocket} who has just closed the connection.
     */
    void onClose(TyrusWebSocket socket, CloseReason closeReason) {
        TyrusSession session = getSession(socket);

        if (session == null) {
            return;
        }

        session.setState(TyrusSession.State.CLOSED);

        ErrorCollector collector = new ErrorCollector();

        final Object toCall = programmaticEndpoint ? endpoint :
                componentProvider.getInstance(endpointClass, session, collector);

        try {
            if (!collector.isEmpty()) {
                throw collector.composeComprehensiveException();
            }

            if (programmaticEndpoint) {
                ((Endpoint) toCall).onClose(session, closeReason);
            } else {
                try {
                    onClose.invoke(toCall, session, closeReason);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
        } catch (Throwable t) {
            if (toCall != null) {
                if (programmaticEndpoint) {
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
            endpointEventListener.onError(session.getId(), t);
        } finally {
            if (clusterContext != null) {
                clusterContext.removeSession(session.getId(), getEndpointPath());

                // close code is not 1006 = the close was initiated from the user code
                if (!(CloseReason.CloseCodes.CLOSED_ABNORMALLY.equals(closeReason.getCloseCode()) ||
                        CloseReason.CloseCodes.GOING_AWAY.equals(closeReason.getCloseCode()))) {
                    clusterContext.destroyDistributedUserProperties(session.getConnectionId());
                }
            }

            session.setState(TyrusSession.State.CLOSED);

            webSocketToSession.remove(socket);
            endpointEventListener.onSessionClosed(session.getId());
            componentProvider.removeSession(session);
            sessionListener.onClose(session, closeReason);
        }
    }

    /**
     * Get Endpoint configuration.
     *
     * @return configuration.
     */
    public EndpointConfig getEndpointConfig() {
        return configuration;
    }

    /**
     * Broadcasts text message to all connected clients.
     *
     * @param message message to be broadcasted.
     * @return map of sessions and futures for user to get the information about status of the message. Messages send
     * from other cluster nodes are not included.
     */
    Map<Session, Future<?>> broadcast(final String message) {
        return broadcast(message, false);
    }

    private Map<Session, Future<?>> broadcast(final String message, boolean local) {

        if (!local && clusterContext != null) {
            clusterContext.broadcastText(getEndpointPath(), message);
            return new HashMap<Session, Future<?>>();
        } else {

            if (webSocketToSession.isEmpty()) {
                return new HashMap<Session, Future<?>>();
            }

            TyrusWebSocket webSocket = webSocketToSession.keySet().iterator().next();
            final Frame dataFrame = new TextFrame(message, false, true);
            final ByteBuffer byteBuffer = webSocket.getProtocolHandler().frame(dataFrame);
            final byte[] frame = new byte[byteBuffer.remaining()];
            byteBuffer.get(frame);
            final long payloadLength = dataFrame.getPayloadLength();

            SessionCallable broadcastCallable = new SessionCallable() {

                @Override
                public Future<?> call(TyrusWebSocket webSocket, TyrusSession session) {
                    final ProtocolHandler protocolHandler = webSocket.getProtocolHandler();

                    // we need to let protocol handler execute extensions if there are any
                    if (protocolHandler.hasExtensions()) {
                        byte[] tempFrame;

                        final Frame dataFrame = new TextFrame(message, false, true);
                        final ByteBuffer byteBuffer = webSocket.getProtocolHandler().frame(dataFrame);
                        tempFrame = new byte[byteBuffer.remaining()];
                        byteBuffer.get(tempFrame);

                        final Future<Frame> frameFuture = webSocket.sendRawFrame(ByteBuffer.wrap(tempFrame));
                        webSocket.getMessageEventListener().onFrameSent(TyrusFrame.FrameType.TEXT, dataFrame.getPayloadLength());
                        return frameFuture;
                    } else {
                        final Future<Frame> frameFuture = webSocket.sendRawFrame(ByteBuffer.wrap(frame));
                        webSocket.getMessageEventListener().onFrameSent(TyrusFrame.FrameType.TEXT, payloadLength);
                        return frameFuture;
                    }
                }
            };

            if (parallelBroadcastEnabled) {
                return executeInParallel(broadcastCallable);
            }

            Map<Session, Future<?>> futures = new HashMap<Session, Future<?>>();

            for (Map.Entry<TyrusWebSocket, TyrusSession> e : webSocketToSession.entrySet()) {
                if (e.getValue().isOpen()) {
                    Future<?> future = broadcastCallable.call(e.getKey(), e.getValue());
                    futures.put(e.getValue(), future);
                }
            }

            return futures;
        }
    }

    /**
     * Broadcasts binary message to all connected clients.
     *
     * @param message message to be broadcasted.
     * @return map of sessions and futures for user to get the information about status of the message. Messages send
     * from other cluster nodes are not included.
     */
    Map<Session, Future<?>> broadcast(final ByteBuffer message) {
        return broadcast(message, false);
    }

    private Map<Session, Future<?>> broadcast(final ByteBuffer message, boolean local) {

        final byte[] byteArrayMessage = Utils.getRemainingArray(message);

        if (!local && clusterContext != null) {
            clusterContext.broadcastBinary(getEndpointPath(), byteArrayMessage);
            // TODO: fix for cluster case
            return new HashMap<Session, Future<?>>();
        } else {

            if (webSocketToSession.isEmpty()) {
                return new HashMap<Session, Future<?>>();
            }

            TyrusWebSocket webSocket = webSocketToSession.keySet().iterator().next();
            final Frame dataFrame = new BinaryFrame(byteArrayMessage, false, true);
            final ByteBuffer byteBuffer = webSocket.getProtocolHandler().frame(dataFrame);
            final byte[] frame = new byte[byteBuffer.remaining()];
            byteBuffer.get(frame);
            final long payloadLength = dataFrame.getPayloadLength();

            SessionCallable broadcastCallable = new SessionCallable() {

                @Override
                public Future<?> call(TyrusWebSocket webSocket, TyrusSession session) {
                    final ProtocolHandler protocolHandler = webSocket.getProtocolHandler();

                    // we need to let protocol handler execute extensions if there are any
                    if (protocolHandler.hasExtensions()) {
                        byte[] tempFrame;

                        final Frame dataFrame = new BinaryFrame(byteArrayMessage, false, true);
                        final ByteBuffer byteBuffer = webSocket.getProtocolHandler().frame(dataFrame);
                        tempFrame = new byte[byteBuffer.remaining()];
                        byteBuffer.get(tempFrame);

                        final Future<Frame> frameFuture = webSocket.sendRawFrame(ByteBuffer.wrap(tempFrame));
                        webSocket.getMessageEventListener().onFrameSent(TyrusFrame.FrameType.BINARY, dataFrame.getPayloadLength());
                        return frameFuture;
                    } else {
                        final Future<Frame> frameFuture = webSocket.sendRawFrame(ByteBuffer.wrap(frame));
                        webSocket.getMessageEventListener().onFrameSent(TyrusFrame.FrameType.BINARY, payloadLength);
                        return frameFuture;
                    }
                }
            };

            if (parallelBroadcastEnabled) {
                return executeInParallel(broadcastCallable);
            }

            Map<Session, Future<?>> futures = new HashMap<Session, Future<?>>();

            for (Map.Entry<TyrusWebSocket, TyrusSession> e : webSocketToSession.entrySet()) {
                if (e.getValue().isOpen()) {
                    Future<?> future = broadcastCallable.call(e.getKey(), e.getValue());
                    futures.put(e.getValue(), future);
                }
            }

            return futures;
        }
    }

    /**
     * Divides open sessions into subsets on which an operation passed as {@code broadcastCallable} will be executed in parallel.
     *
     * @param broadcastCallable operation to be executed on open sessions.
     * @return futures of the operations executed on each session.
     */
    private Map<Session, Future<?>> executeInParallel(final SessionCallable broadcastCallable) {
        final List<Map.Entry<TyrusWebSocket, TyrusSession>> sessions = new ArrayList<Map.Entry<TyrusWebSocket, TyrusSession>>();

        for (Map.Entry<TyrusWebSocket, TyrusSession> e : webSocketToSession.entrySet()) {
            if (e.getValue().isOpen()) {
                sessions.add(e);
            }
        }

        if (sessions.isEmpty()) {
            return new HashMap<Session, Future<?>>();
        }

        ExecutorService executor = ((BaseContainer) sessions.get(0).getValue().getContainer()).getExecutorService();
        Map<Future<Map<Session, Future<?>>>, int[]> submitFutures = new HashMap<Future<Map<Session, Future<?>>>, int[]>();

        int sessionCount = sessions.size();
        int maxThreadCount = sessionCount / MIN_SESSIONS_PER_THREAD == 0 ? 1 : sessionCount / MIN_SESSIONS_PER_THREAD;
        int threadCount = Math.min(Runtime.getRuntime().availableProcessors(), maxThreadCount);

        for (int i = 0; i < threadCount; i++) {

            final int lowerBound = (sessionCount + threadCount - 1) / threadCount * i;
            final int upperBound = Math.min((sessionCount + threadCount - 1) / threadCount * (i + 1), sessionCount);

            Future<Map<Session, Future<?>>> submitFuture = executor.submit(new Callable<Map<Session, Future<?>>>() {

                @Override
                public Map<Session, Future<?>> call() throws Exception {
                    Map<Session, Future<?>> futures = new HashMap<Session, Future<?>>();

                    for (int j = lowerBound; j < upperBound; j++) {
                        Map.Entry<TyrusWebSocket, TyrusSession> e = sessions.get(j);
                        Future<?> future = broadcastCallable.call(e.getKey(), e.getValue());
                        futures.put(e.getValue(), future);
                    }
                    return futures;
                }
            });

            submitFutures.put(submitFuture, new int[]{lowerBound, upperBound});
        }

        final Map<Session, Future<?>> futures = new HashMap<Session, Future<?>>();

        for (Future<Map<Session, Future<?>>> submitFuture : submitFutures.keySet()) {
            try {
                futures.putAll(submitFuture.get());
            } catch (InterruptedException e) {
                handleSubmitException(futures, sessions, submitFutures.get(submitFuture), e);
            } catch (ExecutionException e) {
                handleSubmitException(futures, sessions, submitFutures.get(submitFuture), e);
            }
        }

        return futures;
    }

    private void handleSubmitException(Map<Session, Future<?>> futures,
                                       List<Map.Entry<TyrusWebSocket, TyrusSession>> sessions,
                                       int[] bounds, Exception e) {

        for (int j = bounds[0]; j < bounds[1]; j++) {
            TyrusFuture<Void> future = new TyrusFuture<Void>();
            future.setFailure(e);
            futures.put(sessions.get(j).getValue(), future);
        }
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

    /**
     * Server side check for protocol specific information to determine whether the request can be upgraded.
     * <p/>
     * The default implementation will check for the presence of the
     * {@code Upgrade} header with a value of {@code WebSocket}.
     *
     * @param request received {@link UpgradeRequest}.
     * @return {@code true} if the request should be upgraded to a WebSocket connection.
     * @throws HandshakeException when origin verification check returns {@code false}.
     */
    final boolean upgrade(UpgradeRequest request) throws HandshakeException {
        final String upgradeHeader = request.getHeader(UpgradeRequest.UPGRADE);
        if (request.getHeaders().get(UpgradeRequest.UPGRADE) != null &&
                // RFC 6455, paragraph 4.2.1.3
                UpgradeRequest.WEBSOCKET.equalsIgnoreCase(upgradeHeader)) {

            if (!(configuration instanceof ServerEndpointConfig)) {
                return false;
            }

            if (configurator.checkOrigin(request.getHeader("Origin"))) {
                return true;
            } else {
                throw new HandshakeException(403, LocalizationMessages.ORIGIN_NOT_VERIFIED());
            }
        }

        return false;
    }

    /**
     * Factory method to create new {@link TyrusWebSocket} instances.  Developers may
     * wish to override this to return customized {@link TyrusWebSocket} implementations.
     *
     * @param handler the {@link ProtocolHandler} to use with the newly created
     *                {@link TyrusWebSocket}.
     * @return TODO
     */
    TyrusWebSocket createSocket(final ProtocolHandler handler) {
        return new TyrusWebSocket(handler, this);
    }

    /**
     * This method will be invoked if an unexpected exception is caught by
     * the WebSocket runtime.
     *
     * @param socket the websocket being processed at the time the
     *               exception occurred.
     * @param t      the unexpected exception.
     * @return {@code true} if the WebSocket should be closed otherwise
     * {@code false}.
     */
    boolean onError(TyrusWebSocket socket, Throwable t) {
        Logger.getLogger(TyrusEndpointWrapper.class.getName()).log(Level.WARNING, LocalizationMessages.UNEXPECTED_ERROR_CONNECTION_CLOSE(), t);
        return true;
    }

    /**
     * Invoked when server side handshake is ready to send response.
     * <p/>
     * Changes in response parameter will be reflected in data sent back to client.
     *
     * @param request  original request which caused this handshake.
     * @param response response to be send.
     */
    void onHandShakeResponse(UpgradeRequest request, UpgradeResponse response) {
        final EndpointConfig configuration = getEndpointConfig();

        if (configuration instanceof ServerEndpointConfig) {

            // http://java.net/jira/browse/TYRUS-62
            final ServerEndpointConfig serverEndpointConfig = (ServerEndpointConfig) configuration;
            serverEndpointConfig.getConfigurator().modifyHandshake(serverEndpointConfig, createHandshakeRequest(request),
                    response);
        }
    }

    private HandshakeRequest createHandshakeRequest(final UpgradeRequest webSocketRequest) {
        if (webSocketRequest instanceof RequestContext) {
            final RequestContext requestContext = (RequestContext) webSocketRequest;
            // TYRUS-208; spec requests headers to be read only when passed to ServerEndpointConfig.Configurator#modifyHandshake.
            // TYRUS-211; spec requests parameterMap to be read only when passed to ServerEndpointConfig.Configurator#modifyHandshake.
            requestContext.lock();
            return requestContext;
        }

        return null;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("TyrusEndpointWrapper");
        sb.append("{endpointClass=").append(endpointClass);
        sb.append(", endpoint=").append(endpoint);
        sb.append(", contextPath='").append(contextPath).append('\'');
        sb.append(", endpointPath=").append(endpointPath);
        sb.append(", encoders=[");
        boolean first = true;
        for (CoderWrapper<Encoder> encoder : encoders) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(encoder);
        }
        sb.append("]");

        sb.append(", decoders=[");
        first = true;
        for (CoderWrapper<Decoder> decoder : decoders) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(decoder);
        }
        sb.append("]");


        sb.append('}');
        return sb.toString();
    }

    /**
     * Session listener.
     * <p/>
     * TODO: rename/consolidate with {@link org.glassfish.tyrus.core.monitoring.EndpointEventListener}?
     */
    public abstract static class SessionListener {

        /**
         * Result of {@link org.glassfish.tyrus.core.TyrusEndpointWrapper.SessionListener#onOpen(TyrusSession)}.
         */
        public enum OnOpenResult {

            /**
             * Session can be opened.
             */
            SESSION_ALLOWED,

            /**
             * Session cannot be opened - the maximal number of open session per application exceeded.
             */
            MAX_SESSIONS_PER_APP_EXCEEDED,

            /**
             * Session cannot be opened - the maximal number of open session per remote address exceeded.
             */
            MAX_SESSIONS_PER_REMOTE_ADDR_EXCEEDED
        }

        /**
         * Invoked before {@link javax.websocket.OnOpen} annotated method
         * or {@link Endpoint#onOpen(javax.websocket.Session, javax.websocket.EndpointConfig)} is invoked.
         * <p/>
         * Default implementation always returns {@link org.glassfish.tyrus.core.TyrusEndpointWrapper.SessionListener.OnOpenResult#SESSION_ALLOWED}.
         *
         * @param session session to be opened.
         * @return {@link org.glassfish.tyrus.core.TyrusEndpointWrapper.SessionListener.OnOpenResult#SESSION_ALLOWED}
         * if session can be opened or reason why not.
         */
        public OnOpenResult onOpen(final TyrusSession session) {
            return OnOpenResult.SESSION_ALLOWED;
        }

        /**
         * Invoked after {@link javax.websocket.OnClose} annotated method
         * or {@link Endpoint#onClose(javax.websocket.Session, javax.websocket.CloseReason)} execution.
         *
         * @param session     closed session.
         * @param closeReason close reason.
         */
        public void onClose(final TyrusSession session, final CloseReason closeReason) {
        }
    }

    /**
     * Used only as a placeholder to get encoder instances from {@link ComponentProvider}.
     */
    private static final Session dummySession = new Session() {
        @Override
        public WebSocketContainer getContainer() {
            return null;
        }

        @Override
        public void addMessageHandler(MessageHandler handler) throws IllegalStateException {
        }

        @Override
        public <T> void addMessageHandler(Class<T> clazz, MessageHandler.Whole<T> handler) {
        }

        @Override
        public <T> void addMessageHandler(Class<T> clazz, MessageHandler.Partial<T> handler) {
        }

        @Override
        public Set<MessageHandler> getMessageHandlers() {
            return null;
        }

        @Override
        public void removeMessageHandler(MessageHandler handler) {
        }

        @Override
        public String getProtocolVersion() {
            return null;
        }

        @Override
        public String getNegotiatedSubprotocol() {
            return null;
        }

        @Override
        public List<Extension> getNegotiatedExtensions() {
            return null;
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public long getMaxIdleTimeout() {
            return 0;
        }

        @Override
        public void setMaxIdleTimeout(long milliseconds) {
        }

        @Override
        public void setMaxBinaryMessageBufferSize(int length) {
        }

        @Override
        public int getMaxBinaryMessageBufferSize() {
            return 0;
        }

        @Override
        public void setMaxTextMessageBufferSize(int length) {
        }

        @Override
        public int getMaxTextMessageBufferSize() {
            return 0;
        }

        @Override
        public RemoteEndpoint.Async getAsyncRemote() {
            return null;
        }

        @Override
        public RemoteEndpoint.Basic getBasicRemote() {
            return null;
        }

        @Override
        public String getId() {
            return null;
        }

        @Override
        public void close() throws IOException {
        }

        @Override
        public void close(CloseReason closeReason) throws IOException {
        }

        @Override
        public URI getRequestURI() {
            return null;
        }

        @Override
        public Map<String, List<String>> getRequestParameterMap() {
            return null;
        }

        @Override
        public String getQueryString() {
            return null;
        }

        @Override
        public Map<String, String> getPathParameters() {
            return null;
        }

        @Override
        public Map<String, Object> getUserProperties() {
            return null;
        }

        @Override
        public Principal getUserPrincipal() {
            return null;
        }

        @Override
        public Set<Session> getOpenSessions() {
            return null;
        }
    };

    private static interface SessionCallable {

        Future<?> call(TyrusWebSocket tyrusWebSocket, TyrusSession session);
    }
}
