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
import org.glassfish.tyrus.core.uri.Match;
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

    public static final String INCOMING_BUFFER_SIZE = "org.glassfish.tyrus.incomingBufferSize";

    private static final int BUFFER_STEP_SIZE = 256;
    private static final Logger LOGGER = Logger.getLogger(UpgradeRequest.WEBSOCKET);

    private static final UpgradeInfo NOT_APPLICABLE_UPGRADE_INFO = new NoConnectionUpgradeInfo(UpgradeStatus.NOT_APPLICABLE);
    private static final UpgradeInfo HANDSHAKE_FAILED_UPGRADE_INFO = new NoConnectionUpgradeInfo(UpgradeStatus.HANDSHAKE_FAILED);

    private final Set<TyrusEndpointWrapper> endpointWrappers = Collections.newSetFromMap(new ConcurrentHashMap<TyrusEndpointWrapper, Boolean>());
    private final ComponentProviderService componentProviderService = ComponentProviderService.create();
    private final WebSocketContainer webSocketContainer;

    private int incomingBufferSize = 4194315; // 4M (payload) + 11 (frame overhead)

    private final ClusterContext clusterContext;

    /**
     * Create {@link WebSocketEngine} instance based on passed {@link WebSocketContainer}.
     *
     * @param webSocketContainer used {@link WebSocketContainer} instance.
     */
    public TyrusWebSocketEngine(WebSocketContainer webSocketContainer) {
        this(webSocketContainer, null, null);
    }

    /**
     * Create {@link WebSocketEngine} instance based on passed {@link WebSocketContainer} and with configured maximal
     * incoming buffer size.
     *
     * @param webSocketContainer used {@link WebSocketContainer} instance.
     * @param incomingBufferSize maximal incoming buffer size (this engine won't be able to process messages bigger
     *                           than this number. If null, default value will be used).
     */
    public TyrusWebSocketEngine(WebSocketContainer webSocketContainer, Integer incomingBufferSize, ClusterContext clusterContext) {
        if (incomingBufferSize != null) {
            this.incomingBufferSize = incomingBufferSize;
        }
        this.webSocketContainer = webSocketContainer;
        this.clusterContext = clusterContext;
    }

    private static ProtocolHandler loadHandler(UpgradeRequest request) {
        for (Version version : Version.values()) {
            if (version.validate(request)) {
                return version.createHandler(false);
            }
        }
        return null;
    }

    private static void handleUnsupportedVersion(final UpgradeRequest request, UpgradeResponse response) {
        response.setStatus(426);
        response.getHeaders().put(UpgradeRequest.SEC_WEBSOCKET_VERSION,
                Arrays.asList(Version.getSupportedWireProtocolVersions()));
    }

    TyrusEndpointWrapper getEndpointWrapper(UpgradeRequest request) {
        if (endpointWrappers.isEmpty()) {
            return null;
        }

        final String requestPath = request.getRequestUri();

        for (Match m : Match.getAllMatches(requestPath, endpointWrappers)) {
            final TyrusEndpointWrapper endpointWrapper = m.getEndpointWrapper();

            for (String name : m.getParameterNames()) {
                request.getParameterMap().put(name, Arrays.asList(m.getParameterValue(name)));
            }

            if (endpointWrapper.upgrade(request)) {
                return endpointWrapper;
            }
        }

        return null;
    }

    @Override
    public UpgradeInfo upgrade(final UpgradeRequest request, final UpgradeResponse response) {

        try {
            final TyrusEndpointWrapper endpointWrapper = getEndpointWrapper(request);
            if (endpointWrapper != null) {
                final ProtocolHandler protocolHandler = loadHandler(request);
                if (protocolHandler == null) {
                    handleUnsupportedVersion(request, response);
                    return HANDSHAKE_FAILED_UPGRADE_INFO;
                }

                final ExtendedExtension.ExtensionContext extensionContext = new ExtendedExtension.ExtensionContext() {

                    private final Map<String, Object> properties = new HashMap<String, Object>();

                    @Override
                    public Map<String, Object> getProperties() {
                        return properties;
                    }
                };

                protocolHandler.handshake(endpointWrapper, request, response, extensionContext);

                if (clusterContext != null && request.getHeaders().get(UpgradeRequest.CLUSTER_CONNECTION_ID_HEADER) == null) {
                    // TODO: we might need to introduce some property to check whether we should put this header into the response.
                    response.getHeaders().put(UpgradeRequest.CLUSTER_CONNECTION_ID_HEADER, Collections.singletonList(clusterContext.createConnectionId()));
                }

                return new SuccessfulUpgradeInfo(endpointWrapper, protocolHandler, incomingBufferSize, request, response, extensionContext);
            }
        } catch (HandshakeException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            response.setStatus(e.getHttpStatusCode());
            return HANDSHAKE_FAILED_UPGRADE_INFO;
        }

        response.setStatus(500);
        return NOT_APPLICABLE_UPGRADE_INFO;
    }

    private static class TyrusReadHandler implements ReadHandler {

        private final ProtocolHandler protocolHandler;
        private final TyrusWebSocket socket;
        private final TyrusEndpointWrapper endpointWrapper;
        private final int incomingBufferSize;
        private final ExtendedExtension.ExtensionContext extensionContext;

        private volatile ByteBuffer buffer;

        private TyrusReadHandler(ProtocolHandler protocolHandler, TyrusWebSocket socket, TyrusEndpointWrapper endpointWrapper, int incomingBufferSize, ExtendedExtension.ExtensionContext extensionContext) {
            this.extensionContext = extensionContext;
            this.protocolHandler = protocolHandler;
            this.socket = socket;
            this.endpointWrapper = endpointWrapper;
            this.incomingBufferSize = incomingBufferSize;
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
                                        LOGGER.log(Level.FINE, String.format("Extension '%s' threw an exception during processIncoming method invocation: \"%s\".", extension.getName(), t.getMessage()), t);
                                    }
                                }
                            }

                            protocolHandler.process(frame, socket);
                        }
                    } while (true);
                }
            } catch (WebSocketException e) {
                LOGGER.log(Level.FINE, e.getMessage(), e);
                socket.onClose(new CloseFrame(e.getCloseReason()));
            } catch (Exception e) {
                String message = e.getMessage();
                LOGGER.log(Level.FINE, message, e);
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
        endpointWrappers.add(endpointWrapper);
    }

    @Override
    public void register(Class<?> endpointClass, String contextPath) throws DeploymentException {

        final ErrorCollector collector = new ErrorCollector();

        AnnotatedEndpoint endpoint = AnnotatedEndpoint.fromClass(endpointClass, componentProviderService, true, incomingBufferSize, collector);
        EndpointConfig config = endpoint.getEndpointConfig();

        TyrusEndpointWrapper endpointWrapper = new TyrusEndpointWrapper(endpoint, config, componentProviderService, webSocketContainer,
                contextPath, config instanceof ServerEndpointConfig ? ((ServerEndpointConfig) config).getConfigurator() : null, null, clusterContext);

        if (collector.isEmpty()) {
            register(endpointWrapper);
        } else {
            throw collector.composeComprehensiveException();
        }
    }

    @Override
    public void register(ServerEndpointConfig serverConfig, String contextPath) throws DeploymentException {

        TyrusEndpointWrapper endpointWrapper;

        Class<?> endpointClass = serverConfig.getEndpointClass();
        boolean isEndpointClass = false;

        do {
            endpointClass = endpointClass.getSuperclass();
            if (endpointClass.equals(Endpoint.class)) {
                isEndpointClass = true;
            }
        } while (!endpointClass.equals(Object.class));

        if (isEndpointClass) {
            // we are pretty sure that endpoint class is javax.websocket.Endpoint descendant.
            //noinspection unchecked
            endpointWrapper = new TyrusEndpointWrapper((Class<? extends Endpoint>) serverConfig.getEndpointClass(),
                    serverConfig, componentProviderService, webSocketContainer, contextPath, serverConfig.getConfigurator());
        } else {
            final ErrorCollector collector = new ErrorCollector();

            final AnnotatedEndpoint endpoint = AnnotatedEndpoint.fromClass(serverConfig.getEndpointClass(), componentProviderService, true, incomingBufferSize, collector);
            final EndpointConfig config = endpoint.getEndpointConfig();

            endpointWrapper = new TyrusEndpointWrapper(endpoint, config, componentProviderService, webSocketContainer,
                    contextPath, config instanceof ServerEndpointConfig ? ((ServerEndpointConfig) config).getConfigurator() : null, null, clusterContext);

            if (!collector.isEmpty()) {
                throw collector.composeComprehensiveException();
            }
        }

        register(endpointWrapper);
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

        SuccessfulUpgradeInfo(TyrusEndpointWrapper endpointWrapper, ProtocolHandler protocolHandler, int incomingBufferSize,
                              UpgradeRequest upgradeRequest, UpgradeResponse upgradeResponse, ExtendedExtension.ExtensionContext extensionContext) {
            this.endpointWrapper = endpointWrapper;
            this.protocolHandler = protocolHandler;
            this.incomingBufferSize = incomingBufferSize;
            this.upgradeRequest = upgradeRequest;
            this.upgradeResponse = upgradeResponse;
            this.extensionContext = extensionContext;
        }

        @Override
        public UpgradeStatus getStatus() {
            return UpgradeStatus.SUCCESS;
        }

        @Override
        public Connection createConnection(Writer writer, Connection.CloseListener closeListener) {
            return new TyrusConnection(endpointWrapper, protocolHandler, incomingBufferSize, writer, closeListener, upgradeRequest, upgradeResponse, extensionContext);
        }
    }

    static class TyrusConnection implements Connection {

        private final ReadHandler readHandler;
        private final Writer writer;
        private final CloseListener closeListener;
        private final TyrusWebSocket socket;
        private final ExtendedExtension.ExtensionContext extensionContext;
        private final List<Extension> extensions;

        TyrusConnection(TyrusEndpointWrapper endpointWrapper, ProtocolHandler protocolHandler, int incomingBufferSize, Writer writer, CloseListener closeListener,
                        UpgradeRequest upgradeRequest, UpgradeResponse upgradeResponse, ExtendedExtension.ExtensionContext extensionContext) {
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

            this.socket.onConnect(upgradeRequest, protocolHandler.getSubProtocol(), extensions, connectionId);

            this.readHandler = new TyrusReadHandler(protocolHandler, socket, endpointWrapper, incomingBufferSize, extensionContext);
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
}
