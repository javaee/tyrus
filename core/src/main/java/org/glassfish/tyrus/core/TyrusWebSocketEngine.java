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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.WebSocketContainer;
import javax.websocket.server.ServerEndpointConfig;

import org.glassfish.tyrus.core.uri.Match;
import org.glassfish.tyrus.spi.Connection;
import org.glassfish.tyrus.spi.ReadHandler;
import org.glassfish.tyrus.spi.UpgradeRequest;
import org.glassfish.tyrus.spi.UpgradeResponse;
import org.glassfish.tyrus.spi.WebSocketEngine;
import org.glassfish.tyrus.spi.Writer;

/**
 * WebSockets engine implementation (singleton), which handles {@link WebSocketApplication}s registration, responsible
 * for client and server handshake validation.
 *
 * @author Alexey Stashok
 * @see WebSocket
 * @see WebSocketApplication
 */
public class TyrusWebSocketEngine implements WebSocketEngine {

    public static final String INCOMING_BUFFER_SIZE = "org.glassfish.tyrus.incomingBufferSize";

    public static final int RESPONSE_CODE_VALUE = 101;
    public static final Version DEFAULT_VERSION = Version.DRAFT17;
    public static final int MASK_SIZE = 4;

    private static final int BUFFER_STEP_SIZE = 256;
    private static final Logger LOGGER = Logger.getLogger(UpgradeRequest.WEBSOCKET);

    private final Set<WebSocketApplication> applications = Collections.newSetFromMap(new ConcurrentHashMap<WebSocketApplication, Boolean>());
    private final ComponentProviderService componentProviderService = ComponentProviderService.create();
    private final WebSocketContainer webSocketContainer;

    private int incomingBufferSize = 4194315; // 4M (payload) + 11 (frame overhead)

    /**
     * Create {@link WebSocketEngine} instance based on passed {@link WebSocketContainer}.
     *
     * @param webSocketContainer used {@link WebSocketContainer} instance.
     */
    public TyrusWebSocketEngine(WebSocketContainer webSocketContainer) {
        this.webSocketContainer = webSocketContainer;
    }

    /**
     * Create {@link WebSocketEngine} instance based on passed {@link WebSocketContainer} and with configured maximal
     * incoming buffer size.
     *
     * @param webSocketContainer used {@link WebSocketContainer} instance.
     * @param incomingBufferSize maximal incoming buffer size (this engine won't be able to process messages bigger
     *                           than this number. If null, default value will be used).
     */
    public TyrusWebSocketEngine(WebSocketContainer webSocketContainer, Integer incomingBufferSize) {
        if (incomingBufferSize != null) {
            this.incomingBufferSize = incomingBufferSize;
        }
        this.webSocketContainer = webSocketContainer;
    }

    public static byte[] toArray(long length) {
        long value = length;
        byte[] b = new byte[8];
        for (int i = 7; i >= 0 && value > 0; i--) {
            b[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return b;
    }

    public static long toLong(byte[] bytes, int start, int end) {
        long value = 0;
        for (int i = start; i < end; i++) {
            value <<= 8;
            value ^= (long) bytes[i] & 0xFF;
        }
        return value;
    }

    public static List<String> toString(byte[] bytes) {
        return toString(bytes, 0, bytes.length);
    }

    private static List<String> toString(byte[] bytes, int start, int end) {
        List<String> list = new ArrayList<String>();
        for (int i = start; i < end; i++) {
            list.add(Integer.toHexString(bytes[i] & 0xFF).toUpperCase(Locale.US));
        }
        return list;
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

    WebSocketApplication getApplication(UpgradeRequest request) {
        if (applications.isEmpty()) {
            return null;
        }

        final String requestPath = request.getRequestUri();

        for (Match m : Match.getAllMatches(requestPath, applications)) {
            final WebSocketApplication webSocketApplication = m.getWebSocketApplication();

            for (String name : m.getParameterNames()) {
                request.getParameterMap().put(name, Arrays.asList(m.getParameterValue(name)));
            }

            if (webSocketApplication.upgrade(request)) {
                return webSocketApplication;
            }
        }

        return null;
    }


    @Override
    public UpgradeInfo upgrade(final UpgradeRequest request, final UpgradeResponse response) {

        try {
            final WebSocketApplication app = getApplication(request);
            if (app != null) {
                final ProtocolHandler protocolHandler = loadHandler(request);
                if (protocolHandler == null) {
                    handleUnsupportedVersion(request, response);
                    return HANDSHAKE_FAILED_UPGRADE_INFO;
                }
                protocolHandler.handshake(app, request, response);
                return new SuccessfulUpgradeInfo(app, protocolHandler, incomingBufferSize, request);
            }
        } catch (HandshakeException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            response.setStatus(e.getCode());
            return HANDSHAKE_FAILED_UPGRADE_INFO;
        }

        response.setStatus(500);
        return NOT_APPLICABLE_UPGRADE_INFO;
    }

    public ReadHandler getReadHandler(WebSocketHolder webSocketHolder) {
        return new TyrusReadHandler(webSocketHolder, incomingBufferSize);
    }

    private static class TyrusReadHandler implements ReadHandler {
        private final WebSocketHolder webSocketHolder;
        private final int incomingBufferSize;

        TyrusReadHandler(final WebSocketHolder webSocketHolder, int incomingBufferSize) {
            this.webSocketHolder = webSocketHolder;
            this.incomingBufferSize = incomingBufferSize;
        }

        @Override
        public void handle(ByteBuffer data) {
            if (webSocketHolder == null) {
                // TODO?
                return;
            }

            try {
                if (data != null && data.hasRemaining()) {

                    if (webSocketHolder.buffer != null) {
                        data = appendBuffers(webSocketHolder.buffer, data);
                    } else {
                        int newSize = data.remaining();
                        if (newSize > incomingBufferSize) {
                            throw new IllegalArgumentException("Buffer overflow.");
                        } else {
                            final int roundedSize = (newSize % BUFFER_STEP_SIZE) > 0 ? ((newSize / BUFFER_STEP_SIZE) + 1) * BUFFER_STEP_SIZE : newSize;
                            final ByteBuffer result = ByteBuffer.allocate(roundedSize > incomingBufferSize ? newSize : roundedSize);
                            result.flip();
                            data = appendBuffers(result, data);
                        }
                    }

                    do {
                        final DataFrame result = webSocketHolder.handler.unframe(data);
                        if (result == null) {
                            webSocketHolder.buffer = data;
                            break;
                        } else {
                            result.respond(webSocketHolder.webSocket);
                        }
                    } while (true);
                }
            } catch (FramingException e) {
                e.printStackTrace();
                webSocketHolder.webSocket.onClose(new CloseReason(CloseReason.CloseCodes.getCloseCode(e.getClosingCode()), e.getMessage()));
            } catch (Exception wse) {
                wse.printStackTrace();

                // client-side only
                if (webSocketHolder.application == null) {
                    webSocketHolder.webSocket.onClose(new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, wse.getMessage()));
                    // server
                } else if (webSocketHolder.application.onError(webSocketHolder.webSocket, wse)) {
                    webSocketHolder.webSocket.onClose(new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, wse.getMessage()));
                }
            }
        }

        /**
         * Concatenates two buffers into one. If buffer given as first argument has enough space for putting
         * the other one, it will be done and the original buffer will be returned. Otherwise new buffer will
         * be created.
         *
         * @param buffer  first buffer.
         * @param buffer1 second buffer.
         * @return concatenation.
         */
        private ByteBuffer appendBuffers(ByteBuffer buffer, ByteBuffer buffer1) {

            final int limit = buffer.limit();
            final int capacity = buffer.capacity();
            final int remaining = buffer.remaining();
            final int len = buffer1.remaining();

            // buffer1 will be appended to buffer
            if (len < (capacity - limit)) {

                buffer.mark();
                buffer.position(limit);
                buffer.limit(capacity);
                buffer.put(buffer1);
                buffer.limit(limit + len);
                buffer.reset();
                return buffer;
                // Remaining data is moved to left. Then new data is appended
            } else if (remaining + len < capacity) {
                buffer.compact();
                buffer.put(buffer1);
                buffer.flip();
                return buffer;
                // create new buffer
            } else {
                int newSize = remaining + len;
                if (newSize > incomingBufferSize) {
                    throw new IllegalArgumentException("Buffer overflow.");
                } else {
                    final int roundedSize = (newSize % BUFFER_STEP_SIZE) > 0 ? ((newSize / BUFFER_STEP_SIZE) + 1) * BUFFER_STEP_SIZE : newSize;
                    final ByteBuffer result = ByteBuffer.allocate(roundedSize > incomingBufferSize ? newSize : roundedSize);
                    result.put(buffer);
                    result.put(buffer1);
                    result.flip();
                    return result;
                }
            }
        }
    }

    public void setIncomingBufferSize(int incomingBufferSize) {
        this.incomingBufferSize = incomingBufferSize;
    }


    /**
     * Registers the specified {@link WebSocketApplication} with the
     * <code>WebSocketEngine</code>.
     *
     * @param app the {@link WebSocketApplication} to register.
     * @throws DeploymentException when added applications responds to same path as some already registered application.
     */
    private void register(WebSocketApplication app) throws DeploymentException {
        checkPath(app);
        applications.add(app);
    }

    @Override
    public void register(Class<?> endpointClass, String contextPath) throws DeploymentException {

        final ErrorCollector collector = new ErrorCollector();

        AnnotatedEndpoint endpoint = AnnotatedEndpoint.fromClass(endpointClass, componentProviderService, true, collector);
        EndpointConfig config = endpoint.getEndpointConfig();

        TyrusEndpointWrapper ew = new TyrusEndpointWrapper(endpoint, config, componentProviderService, webSocketContainer,
                contextPath, config instanceof ServerEndpointConfig ? ((ServerEndpointConfig) config).getConfigurator() : null);

        if (collector.isEmpty()) {
            register(new TyrusEndpoint(ew));
        } else {
            throw collector.composeComprehensiveException();
        }
    }

    @Override
    public void register(ServerEndpointConfig serverConfig, String contextPath) throws DeploymentException {

        TyrusEndpointWrapper ew;

        Class<?> endpointClass = serverConfig.getEndpointClass();
        boolean isEndpointClass = false;

        do {
            endpointClass = endpointClass.getSuperclass();
            if (endpointClass.equals(Endpoint.class)) {
                isEndpointClass = true;
            }
        } while (!endpointClass.equals(Object.class));

        if (isEndpointClass) {
            ew = new TyrusEndpointWrapper(serverConfig.getEndpointClass(),
                    serverConfig, componentProviderService, webSocketContainer, contextPath, serverConfig.getConfigurator());
        } else {
            final ErrorCollector collector = new ErrorCollector();

            final AnnotatedEndpoint endpoint = AnnotatedEndpoint.fromClass(serverConfig.getEndpointClass(), componentProviderService, true, collector);
            final EndpointConfig config = endpoint.getEndpointConfig();

            ew = new TyrusEndpointWrapper(endpoint, config, componentProviderService, webSocketContainer,
                    contextPath, config instanceof ServerEndpointConfig ? ((ServerEndpointConfig) config).getConfigurator() : null);

            if (!collector.isEmpty()) {
                throw collector.composeComprehensiveException();
            }
        }

        register(new TyrusEndpoint(ew));
    }

    private void checkPath(WebSocketApplication app) throws DeploymentException {
        for (WebSocketApplication webSocketApplication : applications) {
            if (Match.isEquivalent(app.getPath(), webSocketApplication.getPath())) {
                throw new DeploymentException(String.format(
                        "Found equivalent paths. Added path: '%s' is equivalent with '%s'.", app.getPath(),
                        webSocketApplication.getPath()));
            }
        }
    }

    /**
     * Un-registers the specified {@link WebSocketApplication} with the
     * <code>WebSocketEngine</code>.
     *
     * @param app the {@link WebSocketApplication} to un-register.
     */
    public void unregister(WebSocketApplication app) {
        applications.remove(app);
    }

    /**
     * WebSocketHolder object, which gets associated with the Grizzly {@link org.glassfish.tyrus.spi.Writer}.
     */
    public final static class WebSocketHolder {
        public final WebSocket webSocket;
        public final ProtocolHandler handler;
        public final Handshake handshake;
        public final WebSocketApplication application;
        public volatile ByteBuffer buffer;

        public WebSocketHolder(final ProtocolHandler handler, final WebSocket socket, final Handshake handshake,
                               final WebSocketApplication application) {
            this.handler = handler;
            this.webSocket = socket;
            this.handshake = handshake;
            this.application = application;
        }
    }

    private static final UpgradeInfo NOT_APPLICABLE_UPGRADE_INFO =
            new NoConnectionUpgradeInfo(UpgradeStatus.NOT_APPLICABLE);

    private static final UpgradeInfo HANDSHAKE_FAILED_UPGRADE_INFO =
            new NoConnectionUpgradeInfo(UpgradeStatus.HANDSHAKE_FAILED);


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

        private final WebSocketApplication app;
        private final ProtocolHandler protocolHandler;
        private final int incomingBufferSize;
        private final UpgradeRequest upgradeRequest;

        SuccessfulUpgradeInfo(WebSocketApplication app, ProtocolHandler protocolHandler, int incomingBufferSize, UpgradeRequest upgradeRequest) {
            this.app = app;
            this.protocolHandler = protocolHandler;
            this.incomingBufferSize = incomingBufferSize;
            this.upgradeRequest = upgradeRequest;
        }

        @Override
        public UpgradeStatus getStatus() {
            return UpgradeStatus.SUCCESS;
        }

        @Override
        public Connection createConnection(Writer writer, Connection.CloseListener closeListener) {
            return new TyrusConnection(app, protocolHandler, incomingBufferSize, writer, closeListener, upgradeRequest);
        }
    }

    static class TyrusConnection implements Connection {

        private final ReadHandler readHandler;
        private final Writer writer;
        private final CloseListener closeListener;
        private final WebSocket socket;

        TyrusConnection(WebSocketApplication app, ProtocolHandler protocolHandler, int incomingBufferSize, Writer writer, Connection.CloseListener closeListener, UpgradeRequest upgradeRequest) {
            protocolHandler.setWriter(writer);
            final WebSocket socket = app.createSocket(protocolHandler, app);
            final WebSocketHolder holder = new WebSocketHolder(protocolHandler, socket, null, app);

            socket.onConnect(upgradeRequest);
            this.socket = socket;
            this.readHandler = new TyrusReadHandler(holder, incomingBufferSize);
            this.writer = writer;
            this.closeListener = closeListener;
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
            socket.close(reason.getCloseCode().getCode(), reason.getReasonPhrase());
        }
    }

}
