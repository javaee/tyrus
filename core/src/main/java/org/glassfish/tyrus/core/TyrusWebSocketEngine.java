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

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Extension;
import javax.websocket.WebSocketContainer;
import javax.websocket.server.ServerEndpointConfig;

import org.glassfish.tyrus.core.cluster.ClusterContext;
import org.glassfish.tyrus.core.extension.ExtendedExtension;
import org.glassfish.tyrus.core.frame.CloseFrame;
import org.glassfish.tyrus.core.frame.Frame;
import org.glassfish.tyrus.core.l10n.LocalizationMessages;
import org.glassfish.tyrus.core.monitoring.ApplicationEventListener;
import org.glassfish.tyrus.core.monitoring.EndpointEventListener;
import org.glassfish.tyrus.core.monitoring.MessageEventListener;
import org.glassfish.tyrus.core.uri.Match;
import org.glassfish.tyrus.core.wsadl.model.Application;
import org.glassfish.tyrus.spi.Connection;
import org.glassfish.tyrus.spi.ReadHandler;
import org.glassfish.tyrus.spi.UpgradeRequest;
import org.glassfish.tyrus.spi.UpgradeResponse;
import org.glassfish.tyrus.spi.WebSocketEngine;
import org.glassfish.tyrus.spi.Writer;

/**
 * {@link WebSocketEngine} implementation, which handles server-side handshake, validation and data processing.
 *
 * @author Alexey Stashok
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 * @see org.glassfish.tyrus.core.TyrusWebSocket
 * @see org.glassfish.tyrus.core.TyrusEndpointWrapper
 */
public class TyrusWebSocketEngine implements WebSocketEngine {

    /**
     * Maximum size of incoming buffer in bytes.
     * <p/>
     * The value must be {@link java.lang.Integer} or its primitive alternative.
     * <p/>
     * Default value is 4194315, which means that TyrusWebSocketEngine is by default
     * capable of processing messages up to 4 MB.
     */
    public static final String INCOMING_BUFFER_SIZE = "org.glassfish.tyrus.incomingBufferSize";

    /**
     * Maximum number of open sessions per server application.
     * <p/>
     * The value must be positive {@link java.lang.Integer} or its primitive alternative. Negative values
     * and zero are ignored.
     * <p/>
     * The number of open sessions per application is not limited by default.
     */
    public static final String MAX_SESSIONS_PER_APP = "org.glassfish.tyrus.maxSessionsPerApp";

    /**
     * Maximum number of open sessions per unique remote address.
     * <p/>
     * The value must be positive {@link java.lang.Integer} or its primitive alternative. Negative values
     * and zero are ignored.
     * <p/>
     * The number of open sessions per remote address is not limited by default.
     */
    public static final String MAX_SESSIONS_PER_REMOTE_ADDR = "org.glassfish.tyrus.maxSessionsPerRemoteAddr";

    /**
     * Property used for configuring the type of tracing supported by the server.
     * <p/>
     * The value is expected to be string value of {@link org.glassfish.tyrus.core.DebugContext.TracingType}.
     * <p/>
     * The default value is {@link org.glassfish.tyrus.core.DebugContext.TracingType#OFF}.
     */
    public static final String TRACING_TYPE = "org.glassfish.tyrus.server.tracingType";

    /**
     * Property used for configuring tracing threshold.
     * <p/>
     * The value is expected to be string value of {@link org.glassfish.tyrus.core.DebugContext.TracingThreshold}.
     * <p/>
     * The default value is {@link org.glassfish.tyrus.core.DebugContext.TracingThreshold#SUMMARY}.
     */
    public static final String TRACING_THRESHOLD = "org.glassfish.tyrus.server.tracingThreshold";

    /**
     * Wsadl support.
     * <p/>
     * Wsadl is experimental feature which exposes endpoint configuration in form of XML file,
     * similarly as Wadl for REST services. Currently generated Wsadl contains only set of
     * endpoints and their endpoint paths. Wsadl is exposed on URI ending by "application.wsadl".
     * <p/>
     * The value must be string, {@code "true"} means that the feature is enable, {@code "false"} that the feature
     * is disabled.
     * <p/>
     * Default value is "false";
     */
    @Beta
    public static final String WSADL_SUPPORT = "org.glassfish.tyrus.server.wsadl";

    /**
     * Parallel broadcast support.
     * <p/>
     * {@link org.glassfish.tyrus.core.TyrusSession#broadcast(String)} and {@link org.glassfish.tyrus.core.TyrusSession#broadcast(java.nio.ByteBuffer)}
     * operations are by default executed in parallel. The parallel execution of broadcast can be disabled by setting
     * this server property to {@code false}.
     * <p/>
     * Expected value is {@code true} or {@code false} and the default value is {@code true}.
     *
     * @see org.glassfish.tyrus.core.TyrusSession#broadcast(String).
     * @see org.glassfish.tyrus.core.TyrusSession#broadcast(java.nio.ByteBuffer).
     */
    public static final String PARALLEL_BROADCAST_ENABLED = "org.glassfish.tyrus.server.parallelBroadcastEnabled";

    private static final int BUFFER_STEP_SIZE = 256;
    private static final Logger LOGGER = Logger.getLogger(TyrusWebSocketEngine.class.getName());

    private static final UpgradeInfo NOT_APPLICABLE_UPGRADE_INFO = new NoConnectionUpgradeInfo(UpgradeStatus.NOT_APPLICABLE);
    private static final UpgradeInfo HANDSHAKE_FAILED_UPGRADE_INFO = new NoConnectionUpgradeInfo(UpgradeStatus.HANDSHAKE_FAILED);
    private static final TyrusEndpointWrapper.SessionListener NO_OP_SESSION_LISTENER = new TyrusEndpointWrapper.SessionListener() {
    };

    private final Set<TyrusEndpointWrapper> endpointWrappers = Collections.newSetFromMap(new ConcurrentHashMap<TyrusEndpointWrapper, Boolean>());
    private final ComponentProviderService componentProviderService = ComponentProviderService.create();
    private final WebSocketContainer webSocketContainer;

    private int incomingBufferSize = 4194315; // 4M (payload) + 11 (frame overhead)

    private final ClusterContext clusterContext;
    private final ApplicationEventListener applicationEventListener;
    private final TyrusEndpointWrapper.SessionListener sessionListener;
    private final Boolean parallelBroadcastEnabled;

    private final DebugContext.TracingType tracingType;
    private final DebugContext.TracingThreshold tracingThreshold;

    /**
     * Create {@link org.glassfish.tyrus.core.TyrusWebSocketEngine.TyrusWebSocketEngineBuilder}
     * instance based on passed {@link WebSocketContainer}.
     *
     * @param webSocketContainer {@link WebSocketContainer} instance. Cannot be {@link null}.
     * @return new builder.
     */
    public static TyrusWebSocketEngineBuilder builder(WebSocketContainer webSocketContainer) {
        return new TyrusWebSocketEngineBuilder(webSocketContainer);
    }

    /**
     * Create {@link WebSocketEngine} instance based on passed {@link WebSocketContainer} and with configured maximal
     * incoming buffer size.
     *
     * @param webSocketContainer       used {@link WebSocketContainer} instance.
     * @param incomingBufferSize       maximal incoming buffer size (this engine won't be able to process messages bigger
     *                                 than this number. If null, default value will be used).
     * @param clusterContext           cluster context instance. {@code null} indicates standalone mode.
     * @param applicationEventListener listener used to collect monitored events.
     * @param maxSessionsPerApp        maximal number of open sessions per application. If {@code null}, no limit is applied.
     * @param maxSessionsPerRemoteAddr maximal number of open sessions per remote address. If {@code null}, no limit is applied.
     * @param tracingType              type of tracing.
     * @param tracingThreshold         tracing threshold.
     * @param parallelBroadcastEnabled {@code true} if parallel broadcast should be enabled, {@code true} is default.
     */
    private TyrusWebSocketEngine(WebSocketContainer webSocketContainer, Integer incomingBufferSize,
                                 ClusterContext clusterContext, ApplicationEventListener applicationEventListener,
                                 final Integer maxSessionsPerApp, final Integer maxSessionsPerRemoteAddr,
                                 DebugContext.TracingType tracingType, DebugContext.TracingThreshold tracingThreshold, Boolean parallelBroadcastEnabled) {
        if (incomingBufferSize != null) {
            this.incomingBufferSize = incomingBufferSize;
        }
        this.webSocketContainer = webSocketContainer;
        this.clusterContext = clusterContext;
        this.parallelBroadcastEnabled = parallelBroadcastEnabled;
        if (applicationEventListener == null) {
            // create dummy instance in order not to have to check null pointer
            this.applicationEventListener = ApplicationEventListener.NO_OP;
        } else {
            LOGGER.config("Application event listener " + applicationEventListener.getClass().getName() + " registered");
            this.applicationEventListener = applicationEventListener;
        }

        LOGGER.config("Incoming buffer size: " + this.incomingBufferSize);
        LOGGER.config("Max sessions per app: " + maxSessionsPerApp);
        LOGGER.config("Max sessions per remote address: " + maxSessionsPerRemoteAddr);
        // parallel broadcast is enabled by default, so null means true
        LOGGER.config("Parallel broadcast enabled: " + (parallelBroadcastEnabled == null || parallelBroadcastEnabled));

        this.tracingType = tracingType;
        this.tracingThreshold = tracingThreshold;

        this.sessionListener = maxSessionsPerApp == null && maxSessionsPerRemoteAddr == null ?
                NO_OP_SESSION_LISTENER : new TyrusEndpointWrapper.SessionListener() {
            // Implementation of {@link org.glassfish.tyrus.core.TyrusEndpointWrapper.SessionListener} counting
            // sessions.

            // limit per application counter
            private final AtomicInteger counter = new AtomicInteger(0);
            private final Object counterLock = new Object();

            // limit per remote address counter
            private final Map<String, AtomicInteger> remoteAddressCounters = new HashMap<String, AtomicInteger>();

            @Override
            public OnOpenResult onOpen(final TyrusSession session) {
                if (maxSessionsPerApp != null) {
                    synchronized (counterLock) {
                        if (counter.get() >= maxSessionsPerApp) {
                            return OnOpenResult.MAX_SESSIONS_PER_APP_EXCEEDED;
                        } else {
                            counter.incrementAndGet();
                        }
                    }
                }

                if (maxSessionsPerRemoteAddr != null) {
                    synchronized (remoteAddressCounters) {
                        AtomicInteger remoteAddressCounter = remoteAddressCounters.get(session.getRemoteAddr());
                        if (remoteAddressCounter == null) {
                            remoteAddressCounter = new AtomicInteger(1);
                            remoteAddressCounters.put(session.getRemoteAddr(), remoteAddressCounter);
                        } else if (remoteAddressCounter.get() >= maxSessionsPerRemoteAddr) {
                            return OnOpenResult.MAX_SESSIONS_PER_REMOTE_ADDR_EXCEEDED;
                        } else {
                            remoteAddressCounter.incrementAndGet();
                        }
                    }
                }

                return OnOpenResult.SESSION_ALLOWED;
            }

            @Override
            public void onClose(final TyrusSession session, final CloseReason closeReason) {
                if (maxSessionsPerApp != null) {
                    synchronized (counterLock) {
                        counter.decrementAndGet();
                    }
                }
                if (maxSessionsPerRemoteAddr != null) {
                    synchronized (remoteAddressCounters) {
                        int remoteAddressCounter = remoteAddressCounters.get(session.getRemoteAddr()).decrementAndGet();
                        if (remoteAddressCounter == 0) {
                            remoteAddressCounters.remove(session.getRemoteAddr());
                        }
                    }
                }
            }
        };
    }

    private static ProtocolHandler loadHandler(UpgradeRequest request) {
        for (Version version : Version.values()) {
            if (version.validate(request)) {
                return version.createHandler(false, null);
            }
        }
        return null;
    }

    private static void handleUnsupportedVersion(final UpgradeRequest request, UpgradeResponse response) {
        response.setStatus(426);
        response.getHeaders().put(UpgradeRequest.SEC_WEBSOCKET_VERSION,
                Arrays.asList(Version.getSupportedWireProtocolVersions()));
    }

    TyrusEndpointWrapper getEndpointWrapper(UpgradeRequest request, DebugContext debugContext) throws HandshakeException {
        if (endpointWrappers.isEmpty()) {
            return null;
        }

        final String requestPath = request.getRequestUri();

        for (Match m : Match.getAllMatches(requestPath, endpointWrappers, debugContext)) {
            final TyrusEndpointWrapper endpointWrapper = m.getEndpointWrapper();

            for (String name : m.getParameterNames()) {
                request.getParameterMap().put(name, Arrays.asList(m.getParameterValue(name)));
            }

            if (endpointWrapper.upgrade(request)) {
                debugContext.appendTraceMessage(LOGGER, Level.FINE, DebugContext.Type.MESSAGE_IN, "Endpoint selected as a match to the handshake URI: ", endpointWrapper.getEndpointPath());
                debugContext.appendLogMessage(LOGGER, Level.FINER, DebugContext.Type.MESSAGE_IN, "Target endpoint: ", endpointWrapper);
                return endpointWrapper;
            }
        }

        return null;
    }

    @Override
    public UpgradeInfo upgrade(final UpgradeRequest request, final UpgradeResponse response) {

        DebugContext debugContext = createDebugContext(request);

        if (LOGGER.isLoggable(Level.FINE)) {
            debugContext.appendLogMessage(LOGGER, Level.FINE, DebugContext.Type.MESSAGE_IN, "Received handshake request:\n" + Utils.stringifyUpgradeRequest(request));
        }

        final TyrusEndpointWrapper endpointWrapper;
        try {
            endpointWrapper = getEndpointWrapper(request, debugContext);
        } catch (HandshakeException e) {
            return handleHandshakeException(e, response);
        }

        if (endpointWrapper != null) {
            final ProtocolHandler protocolHandler = loadHandler(request);
            if (protocolHandler == null) {
                handleUnsupportedVersion(request, response);
                debugContext.appendTraceMessage(LOGGER, Level.FINE, DebugContext.Type.MESSAGE_IN, "Upgrade request contains unsupported version of Websocket protocol");

                if (LOGGER.isLoggable(Level.FINE)) {
                    debugContext.appendLogMessage(LOGGER, Level.FINE, DebugContext.Type.MESSAGE_OUT, "Sending handshake response:\n" + Utils.stringifyUpgradeResponse(response));
                }

                response.getHeaders().putAll(debugContext.getTracingHeaders());
                debugContext.flush();
                return HANDSHAKE_FAILED_UPGRADE_INFO;
            }

            final ExtendedExtension.ExtensionContext extensionContext = new ExtendedExtension.ExtensionContext() {

                private final Map<String, Object> properties = new HashMap<String, Object>();

                @Override
                public Map<String, Object> getProperties() {
                    return properties;
                }
            };

            try {
                protocolHandler.handshake(endpointWrapper, request, response, extensionContext);
            } catch (HandshakeException e) {
                return handleHandshakeException(e, response);
            }

            logExtensionsAndSubprotocol(protocolHandler, debugContext);

            if (clusterContext != null && request.getHeaders().get(UpgradeRequest.CLUSTER_CONNECTION_ID_HEADER) == null) {
                // TODO: we might need to introduce some property to check whether we should put this header into the response.
                String connectionId = clusterContext.createConnectionId();
                response.getHeaders().put(UpgradeRequest.CLUSTER_CONNECTION_ID_HEADER, Collections.singletonList(connectionId));

                debugContext.appendLogMessage(LOGGER, Level.FINE, DebugContext.Type.OTHER, "Connection ID: ", connectionId);
            }

            if (LOGGER.isLoggable(Level.FINE)) {
                debugContext.appendLogMessage(LOGGER, Level.FINE, DebugContext.Type.MESSAGE_OUT, "Sending handshake response:\n" + Utils.stringifyUpgradeResponse(response) + "\n");
            }

            response.getHeaders().putAll(debugContext.getTracingHeaders());
            return new SuccessfulUpgradeInfo(endpointWrapper, protocolHandler, incomingBufferSize, request, response, extensionContext, debugContext);
        }

        response.setStatus(500);
        response.getHeaders().putAll(debugContext.getTracingHeaders());
        debugContext.flush();
        return NOT_APPLICABLE_UPGRADE_INFO;
    }

    private void logExtensionsAndSubprotocol(ProtocolHandler protocolHandler, DebugContext debugContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("Using negotiated extensions: [");
        boolean isFirst = true;
        for (Extension extension : protocolHandler.getExtensions()) {
            if (isFirst) {
                isFirst = false;
            } else {
                sb.append(", ");
            }
            sb.append(extension.getName());
        }
        sb.append("]");

        debugContext.appendLogMessage(LOGGER, Level.FINE, DebugContext.Type.OTHER, "Using negotiated extensions: ", sb);
        debugContext.appendLogMessage(LOGGER, Level.FINE, DebugContext.Type.OTHER, "Using negotiated subprotocol: ", protocolHandler.getSubProtocol());
    }

    private DebugContext createDebugContext(UpgradeRequest upgradeRequest) {
        String thresholdHeader = upgradeRequest.getHeader(UpgradeRequest.TRACING_THRESHOLD);

        DebugContext.TracingThreshold threshold = tracingThreshold;

        Exception thresholdHeaderParsingError = null;
        if (thresholdHeader != null) {
            try {
                threshold = DebugContext.TracingThreshold.valueOf(thresholdHeader);
            } catch (Exception e) {
                thresholdHeaderParsingError = e;
            }
        }

        DebugContext debugContext;
        if (tracingType == DebugContext.TracingType.ALL
                || tracingType == DebugContext.TracingType.ON_DEMAND && upgradeRequest.getHeader(UpgradeRequest.ENABLE_TRACING_HEADER) != null) {
            debugContext = new DebugContext(threshold);
        } else {
            debugContext = new DebugContext();
        }

        if (thresholdHeaderParsingError != null) {
            debugContext.appendTraceMessageWithThrowable(LOGGER, Level.WARNING, DebugContext.Type.MESSAGE_IN, thresholdHeaderParsingError,
                    "An error occurred while parsing ", UpgradeRequest.TRACING_THRESHOLD, " header:", thresholdHeaderParsingError.getMessage());
        }

        return debugContext;
    }

    private UpgradeInfo handleHandshakeException(HandshakeException handshakeException, UpgradeResponse response) {
        LOGGER.log(Level.CONFIG, handshakeException.getMessage(), handshakeException);
        response.setStatus(handshakeException.getHttpStatusCode());
        return HANDSHAKE_FAILED_UPGRADE_INFO;
    }

    private static class TyrusReadHandler implements ReadHandler {

        private final ProtocolHandler protocolHandler;
        private final TyrusWebSocket socket;
        private final TyrusEndpointWrapper endpointWrapper;
        private final int incomingBufferSize;
        private final ExtendedExtension.ExtensionContext extensionContext;
        private final DebugContext debugContext;

        private volatile ByteBuffer buffer;

        private TyrusReadHandler(ProtocolHandler protocolHandler, TyrusWebSocket socket, TyrusEndpointWrapper endpointWrapper, int incomingBufferSize, ExtendedExtension.ExtensionContext extensionContext, DebugContext debugContext) {
            this.extensionContext = extensionContext;
            this.protocolHandler = protocolHandler;
            this.socket = socket;
            this.endpointWrapper = endpointWrapper;
            this.incomingBufferSize = incomingBufferSize;
            this.debugContext = debugContext;
        }

        @Override
        public void handle(ByteBuffer data) {
            try {
                if (data != null && data.hasRemaining()) {

                    if (buffer != null) {
                        data = Utils.appendBuffers(buffer, data, incomingBufferSize, BUFFER_STEP_SIZE);
                    } else {
                        int newSize = data.remaining();
                        if (newSize > incomingBufferSize) {
                            throw new IllegalArgumentException(LocalizationMessages.BUFFER_OVERFLOW());
                        } else {
                            final int roundedSize = (newSize % BUFFER_STEP_SIZE) > 0 ? ((newSize / BUFFER_STEP_SIZE) + 1) * BUFFER_STEP_SIZE : newSize;
                            final ByteBuffer result = ByteBuffer.allocate(roundedSize > incomingBufferSize ? newSize : roundedSize);
                            result.flip();
                            data = Utils.appendBuffers(result, data, incomingBufferSize, BUFFER_STEP_SIZE);
                        }
                    }

                    do {
                        final Frame incomingFrame = protocolHandler.unframe(data);

                        if (incomingFrame == null) {
                            buffer = data;
                            break;
                        } else {
                            Frame frame = incomingFrame;

                            for (Extension extension : protocolHandler.getExtensions()) {
                                if (extension instanceof ExtendedExtension) {
                                    try {
                                        frame = ((ExtendedExtension) extension).processIncoming(extensionContext, frame);
                                    } catch (Throwable t) {
                                        debugContext.appendLogMessageWithThrowable(LOGGER, Level.FINE, DebugContext.Type.MESSAGE_IN, t, "Extension '", extension.getName(), "' threw an exception during processIncoming method invocation: ", t.getMessage());
                                    }
                                }
                            }

                            protocolHandler.process(frame, socket);
                        }
                    } while (true);
                }
            } catch (WebSocketException e) {
                debugContext.appendLogMessageWithThrowable(LOGGER, Level.FINE, DebugContext.Type.MESSAGE_IN, e, e.getMessage());
                socket.onClose(new CloseFrame(e.getCloseReason()));
            } catch (Exception e) {
                String message = e.getMessage();
                debugContext.appendLogMessageWithThrowable(LOGGER, Level.FINE, DebugContext.Type.MESSAGE_IN, e, e.getMessage());
                if (endpointWrapper.onError(socket, e)) {
                    if (message != null && message.length() > 123) {
                        // reason phrase length is limited.
                        message = message.substring(0, 123);
                    }
                    socket.onClose(new CloseFrame(new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, message)));
                }
            }
        }
    }

    /**
     * Set incoming buffer size.
     *
     * @param incomingBufferSize buffer size in bytes.
     * @deprecated Please use {@link org.glassfish.tyrus.core.TyrusWebSocketEngine.TyrusWebSocketEngineBuilder#incomingBufferSize(Integer)} instead.
     */
    public void setIncomingBufferSize(int incomingBufferSize) {
        this.incomingBufferSize = incomingBufferSize;
    }

    /**
     * Registers the specified {@link TyrusEndpointWrapper} with the
     * <code>WebSocketEngine</code>.
     *
     * @param endpointWrapper the {@link TyrusEndpointWrapper} to register.
     * @throws DeploymentException when added endpoint responds to same path as some already registered endpoint.
     */
    private void register(TyrusEndpointWrapper endpointWrapper) throws DeploymentException {
        checkPath(endpointWrapper);
        LOGGER.log(Level.FINER, "Registered endpoint: " + endpointWrapper);
        endpointWrappers.add(endpointWrapper);
    }

    @Override
    public void register(Class<?> endpointClass, String contextPath) throws DeploymentException {

        final ErrorCollector collector = new ErrorCollector();

        EndpointEventListenerWrapper endpointEventListenerWrapper = new EndpointEventListenerWrapper();
        AnnotatedEndpoint endpoint = AnnotatedEndpoint.fromClass(endpointClass, componentProviderService, true, incomingBufferSize, collector, endpointEventListenerWrapper);
        EndpointConfig config = endpoint.getEndpointConfig();

        TyrusEndpointWrapper endpointWrapper = new TyrusEndpointWrapper(endpoint, config, componentProviderService, webSocketContainer,
                contextPath, config instanceof ServerEndpointConfig ? ((ServerEndpointConfig) config).getConfigurator() : null, sessionListener, clusterContext, endpointEventListenerWrapper, parallelBroadcastEnabled);

        if (collector.isEmpty()) {
            register(endpointWrapper);
        } else {
            throw collector.composeComprehensiveException();
        }

        String endpointPath = config instanceof ServerEndpointConfig ? ((ServerEndpointConfig) config).getPath() : null;
        EndpointEventListener endpointEventListener = applicationEventListener.onEndpointRegistered(endpointPath, endpointClass);
        endpointEventListenerWrapper.setEndpointEventListener(endpointEventListener);
    }

    @Override
    public void register(ServerEndpointConfig serverConfig, String contextPath) throws DeploymentException {

        TyrusEndpointWrapper endpointWrapper;

        Class<?> endpointClass = serverConfig.getEndpointClass();
        Class<?> parent = endpointClass;
        boolean isEndpointClass = false;

        do {
            parent = parent.getSuperclass();
            if (parent.equals(Endpoint.class)) {
                isEndpointClass = true;
            }
        } while (!parent.equals(Object.class));

        EndpointEventListenerWrapper endpointEventListenerWrapper = new EndpointEventListenerWrapper();

        if (isEndpointClass) {
            // we are pretty sure that endpoint class is javax.websocket.Endpoint descendant.
            //noinspection unchecked
            endpointWrapper = new TyrusEndpointWrapper((Class<? extends Endpoint>) endpointClass, serverConfig, componentProviderService,
                    webSocketContainer, contextPath, serverConfig.getConfigurator(), sessionListener, clusterContext, endpointEventListenerWrapper, parallelBroadcastEnabled);
        } else {
            final ErrorCollector collector = new ErrorCollector();

            final AnnotatedEndpoint endpoint = AnnotatedEndpoint.fromClass(endpointClass, componentProviderService, true, incomingBufferSize, collector, endpointEventListenerWrapper);
            final EndpointConfig config = endpoint.getEndpointConfig();

            endpointWrapper = new TyrusEndpointWrapper(endpoint, config, componentProviderService, webSocketContainer,
                    contextPath, config instanceof ServerEndpointConfig ? ((ServerEndpointConfig) config).getConfigurator() : null, sessionListener, clusterContext, endpointEventListenerWrapper, parallelBroadcastEnabled);

            if (!collector.isEmpty()) {
                throw collector.composeComprehensiveException();
            }
        }

        register(endpointWrapper);
        EndpointEventListener endpointEventListener = applicationEventListener.onEndpointRegistered(serverConfig.getPath(), endpointClass);
        endpointEventListenerWrapper.setEndpointEventListener(endpointEventListener);
    }

    private void checkPath(TyrusEndpointWrapper endpoint) throws DeploymentException {
        for (TyrusEndpointWrapper endpointWrapper : endpointWrappers) {
            if (Match.isEquivalent(endpoint.getEndpointPath(), endpointWrapper.getEndpointPath())) {
                throw new DeploymentException(LocalizationMessages.EQUIVALENT_PATHS(endpoint.getEndpointPath(),
                        endpointWrapper.getEndpointPath()));
            }
        }
    }

    /**
     * Un-registers the specified {@link TyrusEndpointWrapper} with the
     * <code>WebSocketEngine</code>.
     *
     * @param endpointWrapper the {@link TyrusEndpointWrapper} to un-register.
     */
    public void unregister(TyrusEndpointWrapper endpointWrapper) {
        endpointWrappers.remove(endpointWrapper);
        applicationEventListener.onEndpointUnregistered(endpointWrapper.getEndpointPath());
    }

    private static class NoConnectionUpgradeInfo implements UpgradeInfo {
        private final UpgradeStatus status;

        NoConnectionUpgradeInfo(UpgradeStatus status) {
            this.status = status;
        }

        @Override
        public UpgradeStatus getStatus() {
            return status;
        }

        @Override
        public Connection createConnection(Writer writer, Connection.CloseListener closeListener) {
            return null;
        }
    }

    private static class SuccessfulUpgradeInfo implements UpgradeInfo {

        private final TyrusEndpointWrapper endpointWrapper;
        private final ProtocolHandler protocolHandler;
        private final int incomingBufferSize;
        private final UpgradeRequest upgradeRequest;
        private final UpgradeResponse upgradeResponse;
        private final ExtendedExtension.ExtensionContext extensionContext;
        private final DebugContext debugContext;

        SuccessfulUpgradeInfo(TyrusEndpointWrapper endpointWrapper, ProtocolHandler protocolHandler, int incomingBufferSize,
                              UpgradeRequest upgradeRequest, UpgradeResponse upgradeResponse, ExtendedExtension.ExtensionContext extensionContext, DebugContext debugContext) {
            this.endpointWrapper = endpointWrapper;
            this.protocolHandler = protocolHandler;
            this.incomingBufferSize = incomingBufferSize;
            this.upgradeRequest = upgradeRequest;
            this.upgradeResponse = upgradeResponse;
            this.extensionContext = extensionContext;
            this.debugContext = debugContext;
        }

        @Override
        public UpgradeStatus getStatus() {
            return UpgradeStatus.SUCCESS;
        }

        @Override
        public Connection createConnection(Writer writer, Connection.CloseListener closeListener) {
            TyrusConnection tyrusConnection = new TyrusConnection(endpointWrapper, protocolHandler, incomingBufferSize, writer, closeListener, upgradeRequest, upgradeResponse, extensionContext, debugContext);
            debugContext.flush();
            return tyrusConnection;
        }
    }

    /**
     * Get {@link org.glassfish.tyrus.core.monitoring.ApplicationEventListener} related to current
     * {@link org.glassfish.tyrus.core.TyrusWebSocketEngine} instance.
     *
     * @return listener instance.
     */
    public ApplicationEventListener getApplicationEventListener() {
        return applicationEventListener;
    }

    /**
     * Get {@link org.glassfish.tyrus.core.wsadl.model.Application} representing current set of deployed endpoints.
     *
     * @return application representing current set of deployed endpoints.
     */
    @Beta
    public Application getWsadlApplication() {
        Application application = new Application();
        for (TyrusEndpointWrapper wrapper : endpointWrappers) {
            org.glassfish.tyrus.core.wsadl.model.Endpoint endpoint = new org.glassfish.tyrus.core.wsadl.model.Endpoint();
            endpoint.setPath(wrapper.getServerEndpointPath());
            application.getEndpoint().add(endpoint);
        }

        return application;
    }

    static class TyrusConnection implements Connection {

        private final ReadHandler readHandler;
        private final Writer writer;
        private final CloseListener closeListener;
        private final TyrusWebSocket socket;
        private final ExtendedExtension.ExtensionContext extensionContext;
        private final List<Extension> extensions;

        TyrusConnection(TyrusEndpointWrapper endpointWrapper, ProtocolHandler protocolHandler, int incomingBufferSize, Writer writer, CloseListener closeListener,
                        UpgradeRequest upgradeRequest, UpgradeResponse upgradeResponse, ExtendedExtension.ExtensionContext extensionContext, DebugContext debugContext) {
            protocolHandler.setWriter(writer);
            extensions = protocolHandler.getExtensions();
            this.socket = endpointWrapper.createSocket(protocolHandler);

            // TODO: we might need to introduce some property to check whether we should put this header into the response.
            final List<String> connectionIdHeader = upgradeRequest.getHeaders().get(UpgradeRequest.CLUSTER_CONNECTION_ID_HEADER);
            String connectionId;
            if (connectionIdHeader != null && connectionIdHeader.size() == 1) {
                connectionId = connectionIdHeader.get(0);
            } else {
                connectionId = upgradeResponse.getFirstHeaderValue(UpgradeRequest.CLUSTER_CONNECTION_ID_HEADER);
            }

            this.socket.onConnect(upgradeRequest, protocolHandler.getSubProtocol(), extensions, connectionId, debugContext);

            this.readHandler = new TyrusReadHandler(protocolHandler, socket, endpointWrapper, incomingBufferSize, extensionContext, debugContext);
            this.writer = writer;
            this.closeListener = closeListener;
            this.extensionContext = extensionContext;
        }

        @Override
        public ReadHandler getReadHandler() {
            return readHandler;
        }

        @Override
        public Writer getWriter() {
            return writer;
        }

        @Override
        public CloseListener getCloseListener() {
            return closeListener;
        }

        @Override
        public void close(CloseReason reason) {
            if (!socket.isConnected()) {
                return;
            }

            socket.close(reason.getCloseCode().getCode(), reason.getReasonPhrase());

            for (Extension extension : extensions) {
                if (extension instanceof ExtendedExtension) {
                    try {
                        ((ExtendedExtension) extension).destroy(extensionContext);
                    } catch (Throwable t) {
                        // ignore.
                    }
                }
            }
        }
    }

    /**
     * {@link org.glassfish.tyrus.core.TyrusWebSocketEngine} builder.
     */
    public static class TyrusWebSocketEngineBuilder {

        private final WebSocketContainer webSocketContainer;

        private Integer incomingBufferSize = null;
        private ClusterContext clusterContext = null;
        private ApplicationEventListener applicationEventListener = null;
        private Integer maxSessionsPerApp = null;
        private Integer maxSessionsPerRemoteAddr = null;
        private DebugContext.TracingType tracingType = null;
        private DebugContext.TracingThreshold tracingThreshold = null;
        private Boolean parallelBroadcastEnabled = null;

        /**
         * Create new {@link org.glassfish.tyrus.core.TyrusWebSocketEngine} instance with
         * current set of parameters.
         *
         * @return new {@link org.glassfish.tyrus.core.TyrusWebSocketEngine} instance.
         */
        public TyrusWebSocketEngine build() {
            if (maxSessionsPerApp != null && maxSessionsPerApp <= 0) {
                LOGGER.log(Level.CONFIG, "Invalid configuration value " + MAX_SESSIONS_PER_APP + " (" + maxSessionsPerApp + "), expected value greater than 0.");
                maxSessionsPerApp = null;
            }

            if (maxSessionsPerRemoteAddr != null && maxSessionsPerRemoteAddr <= 0) {
                LOGGER.log(Level.CONFIG, "Invalid configuration value " + MAX_SESSIONS_PER_REMOTE_ADDR + " (" + maxSessionsPerRemoteAddr + "), expected value greater than 0.");
                maxSessionsPerRemoteAddr = null;
            }

            if (maxSessionsPerApp != null && maxSessionsPerRemoteAddr != null && maxSessionsPerApp < maxSessionsPerRemoteAddr) {
                LOGGER.log(Level.FINE, String.format("Invalid configuration - value %s (%d) cannot be greater then %s (%d).",
                        MAX_SESSIONS_PER_REMOTE_ADDR, maxSessionsPerRemoteAddr, MAX_SESSIONS_PER_APP, maxSessionsPerApp));
            }

            return new TyrusWebSocketEngine(webSocketContainer, incomingBufferSize, clusterContext,
                    applicationEventListener, maxSessionsPerApp, maxSessionsPerRemoteAddr, tracingType, tracingThreshold, parallelBroadcastEnabled);
        }

        TyrusWebSocketEngineBuilder(WebSocketContainer webSocketContainer) {
            if (webSocketContainer == null) {
                throw new NullPointerException();
            }

            this.webSocketContainer = webSocketContainer;
        }

        /**
         * Set {@link org.glassfish.tyrus.core.monitoring.ApplicationEventListener}.
         * <p/>
         * Listener can be used for monitoring various events and properties, such as deployed endpoints,
         * ongoing sessions etc...
         *
         * @param applicationEventListener listener instance used for building {@link org.glassfish.tyrus.core.TyrusWebSocketEngine}.
         *                                 Can be {@link null}.
         * @return updated builder.
         */
        public TyrusWebSocketEngineBuilder applicationEventListener(ApplicationEventListener applicationEventListener) {
            this.applicationEventListener = applicationEventListener;
            return this;
        }

        /**
         * Set incoming buffer size.
         *
         * @param incomingBufferSize maximal incoming buffer size (this engine won't be able to process messages bigger
         *                           than this number. If {@code null}, default value will be used).
         * @return updated builder.
         */
        public TyrusWebSocketEngineBuilder incomingBufferSize(Integer incomingBufferSize) {
            this.incomingBufferSize = incomingBufferSize;
            return this;
        }

        /**
         * Set {@link org.glassfish.tyrus.core.cluster.ClusterContext}.
         * <p/>
         * ClusterContext provides clustering functionality.
         *
         * @param clusterContext cluster context instance. {@code null} indicates standalone mode.
         * @return updated builder.
         */
        public TyrusWebSocketEngineBuilder clusterContext(ClusterContext clusterContext) {
            this.clusterContext = clusterContext;
            return this;
        }

        /**
         * Set maximal number of open sessions per server application.
         *
         * @param maxSessionsPerApp maximal number of open sessions. If {@code null}, no limit is applied.
         * @return updated builder.
         */
        public TyrusWebSocketEngineBuilder maxSessionsPerApp(Integer maxSessionsPerApp) {
            this.maxSessionsPerApp = maxSessionsPerApp;
            return this;
        }

        /**
         * Set maximal number of open sessions from remote address.
         *
         * @param maxSessionsPerRemoteAddr maximal number of open sessions from remote address.
         *                                 If {@code null}, no limit is applied.
         * @return updated builder.
         */
        public TyrusWebSocketEngineBuilder maxSessionsPerRemoteAddr(Integer maxSessionsPerRemoteAddr) {
            this.maxSessionsPerRemoteAddr = maxSessionsPerRemoteAddr;
            return this;
        }

        /**
         * Set type of tracing.
         *
         * @param tracingType tracing type.
         * @return updated builder.
         */
        public TyrusWebSocketEngineBuilder tracingType(DebugContext.TracingType tracingType) {
            this.tracingType = tracingType;
            return this;
        }

        /**
         * Set tracing threshold.
         *
         * @param tracingThreshold tracing threshold.
         * @return updated builder.
         */
        public TyrusWebSocketEngineBuilder tracingThreshold(DebugContext.TracingThreshold tracingThreshold) {
            this.tracingThreshold = tracingThreshold;
            return this;
        }

        public TyrusWebSocketEngineBuilder parallelBroadcastEnabled(Boolean parallelBroadcastEnabled) {
            this.parallelBroadcastEnabled = parallelBroadcastEnabled;
            return this;
        }
    }

    /**
     * Endpoint event listener wrapper that allows setting the wrapped endpoint event listener later.
     */
    private static class EndpointEventListenerWrapper implements EndpointEventListener {

        private volatile EndpointEventListener endpointEventListener = EndpointEventListener.NO_OP;

        void setEndpointEventListener(EndpointEventListener endpointEventListener) {
            this.endpointEventListener = endpointEventListener;
        }

        @Override
        public MessageEventListener onSessionOpened(String sessionId) {
            return endpointEventListener.onSessionOpened(sessionId);
        }

        @Override
        public void onSessionClosed(String sessionId) {
            endpointEventListener.onSessionClosed(sessionId);
        }

        @Override
        public void onError(String sessionId, Throwable t) {
            endpointEventListener.onError(sessionId, t);
        }
    }
}
