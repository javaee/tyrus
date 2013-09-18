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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
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

    public static final int RESPONSE_CODE_VALUE = 101;
    public static final Version DEFAULT_VERSION = Version.DRAFT17;
    public static final int MASK_SIZE = 4;

    private static int BUFFER_STEP_SIZE = 256;
    private static final Logger LOGGER = Logger.getLogger(UpgradeRequest.WEBSOCKET);

    private final Set<WebSocketApplication> applications = Collections.newSetFromMap(new ConcurrentHashMap<WebSocketApplication, Boolean>());
    private final Map<Writer, WebSocketHolder> webSocketHolderMap = new ConcurrentHashMap<Writer, WebSocketHolder>();
    private final ComponentProviderService componentProviderService = ComponentProviderService.create();
    private final WebSocketContainer webSocketContainer;

    private int incomingBufferSize = 4194315; // 4M (payload) + 11 (frame overhead)

    public TyrusWebSocketEngine(WebSocketContainer webSocketContainer) {
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

        WebSocket socket = null;

        try {
            final WebSocketApplication app = getApplication(request);
            if (app != null) {
                final ProtocolHandler protocolHandler = loadHandler(request);
                if (protocolHandler == null) {
                    handleUnsupportedVersion(request, response);
                    return new UpgradeInfo() {
                        @Override
                        public UpgradeStatus getStatus() {
                            return UpgradeStatus.HANDSHAKE_FAILED;
                        }

                        @Override
                        public Connection createConnection(Writer writer, Connection.CloseListener cl) {
                            return null;
                        }
                    };
                }

                protocolHandler.handshake(app, request, response);

                return new UpgradeInfo() {
                    @Override
                    public UpgradeStatus getStatus() {
                        return UpgradeStatus.SUCCESS;
                    }

                    @Override
                    public Connection createConnection(final Writer writer, final Connection.CloseListener closeListener) {

                        protocolHandler.setWriter(writer);
                        final WebSocket socket = app.createSocket(protocolHandler, app);
                        setWebSocketHolder(writer, protocolHandler, null, socket, app);

                        // TODO: servlet integration
//                        if (upgradeListener != null) {
//                            upgradeListener.onUpgradeFinished();
//                        } else {
//                            socket.onConnect();
//                        }


                        return new Connection() {
                            @Override
                            public ReadHandler getReadHandler() {
                                return TyrusWebSocketEngine.this.getReadHandler(writer);
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
                            public void open() {
                                socket.onConnect();
                            }

                            @Override
                            public void close(CloseReason reason) {
                                // TODO!
                                TyrusWebSocketEngine.this.close(writer, reason);
                            }
                        };
                    }
                };
            }
        } catch (HandshakeException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            if (socket != null) {
                socket.close();
            }

            response.setStatus(e.getCode());

            return new UpgradeInfo() {
                @Override
                public UpgradeStatus getStatus() {
                    return UpgradeStatus.HANDSHAKE_FAILED;
                }

                @Override
                public Connection createConnection(Writer writer, Connection.CloseListener cl) {
                    return null;
                }
            };
        }

        response.setStatus(500);

        return new UpgradeInfo() {
            @Override
            public UpgradeStatus getStatus() {
                return UpgradeStatus.NOT_APPLICABLE;
            }

            @Override
            public Connection createConnection(Writer writer, Connection.CloseListener cl) {
                return null;
            }
        };
    }

    public ReadHandler getReadHandler(final Writer writer) {
        return new ReadHandler() {
            @Override
            public void handle(ByteBuffer data) {
                TyrusWebSocketEngine.this.processData(writer, data);
            }
        };
    }

    private void processData(Writer writer, ByteBuffer data) {
        final WebSocketHolder holder = getWebSocketHolder(writer);
        try {
            if (data != null && data.hasRemaining()) {

                if (holder.buffer != null) {
                    data = appendBuffers(holder.buffer, data);
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
                    final DataFrame result = holder.handler.unframe(data);
                    if (result == null) {
                        holder.buffer = data;
                        break;
                    } else {
                        result.respond(holder.webSocket);
                    }
                } while (true);
            }
        } catch (FramingException e) {
            holder.webSocket.onClose(new CloseReason(CloseReason.CloseCodes.getCloseCode(e.getClosingCode()), e.getMessage()));
        } catch (Exception wse) {
            if (holder.application.onError(holder.webSocket, wse)) {
                holder.webSocket.onClose(new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, wse.getMessage()));

            }
        }
    }

    //    @Override
    private void onConnect(Writer writer) {
        getWebSocketHolder(writer).webSocket.onConnect();
    }

    private void close(Writer writer, CloseReason closeReason) {
        final WebSocketHolder holder = getWebSocketHolder(writer);
        if (holder != null) {
            holder.webSocket.onClose(closeReason);
            removeConnection(writer);
        }
    }

    public void setIncomingBufferSize(int incomingBufferSize) {
        this.incomingBufferSize = incomingBufferSize;
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
                contextPath, collector, config instanceof ServerEndpointConfig ? ((ServerEndpointConfig) config).getConfigurator() : null);

        if (collector.isEmpty()) {
            register(new TyrusEndpoint(ew));
        } else {
            throw collector.composeComprehensiveException();
        }
    }

    @Override
    public void register(ServerEndpointConfig serverConfig, String contextPath) throws DeploymentException {

        final ErrorCollector collector = new ErrorCollector();

        TyrusEndpointWrapper ew = new TyrusEndpointWrapper(serverConfig.getEndpointClass(),
                serverConfig, componentProviderService, webSocketContainer, contextPath, collector, serverConfig.getConfigurator());

        if (collector.isEmpty()) {
            register(new TyrusEndpoint(ew));
        } else {
            throw collector.composeComprehensiveException();
        }
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

//    /**
//     * Un-registers all {@link WebSocketApplication} instances with the
//     * {@link WebSocketEngine}.
//     */
//    public void unregisterAll() {
//        applications.clear();
//    }

    /**
     * Returns <tt>true</tt> if passed Grizzly {@link org.glassfish.tyrus.spi.Writer} is associated with a {@link WebSocket}, or
     * <tt>false</tt> otherwise.
     *
     * @param writer Grizzly {@link org.glassfish.tyrus.spi.Writer}.
     * @return <tt>true</tt> if passed Grizzly {@link org.glassfish.tyrus.spi.Writer} is associated with a {@link WebSocket}, or
     *         <tt>false</tt> otherwise.
     */
    public boolean webSocketInProgress(Writer writer) {
        return webSocketHolderMap.get(writer) != null;
    }

    /**
     * Get the {@link WebSocket} associated with the Grizzly {@link org.glassfish.tyrus.spi.Writer}, or <tt>null</tt>, if there none is
     * associated.
     *
     * @param writer Grizzly {@link org.glassfish.tyrus.spi.Writer}.
     * @return the {@link WebSocket} associated with the Grizzly {@link org.glassfish.tyrus.spi.Writer}, or <tt>null</tt>, if there none is
     *         associated.
     */
    public WebSocket getWebSocket(Writer writer) {
        final WebSocketHolder holder = getWebSocketHolder(writer);
        return holder == null ? null : holder.webSocket;
    }

    public WebSocketHolder getWebSocketHolder(final Writer writer) {
        return webSocketHolderMap.get(writer);
    }

    public WebSocketHolder setWebSocketHolder(final Writer writer, ProtocolHandler handler, WebSocketRequest request, WebSocket socket, WebSocketApplication application) {
        final WebSocketHolder holder = new WebSocketHolder(handler, socket, (request == null ? null : handler.createClientHandShake(request)), application);

        webSocketHolderMap.put(writer, holder);
        return holder;
    }

    public void removeConnection(Writer writer) {
        webSocketHolderMap.remove(writer);
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

        WebSocketHolder(final ProtocolHandler handler, final WebSocket socket, final Handshake handshake,
                        final WebSocketApplication application) {
            this.handler = handler;
            this.webSocket = socket;
            this.handshake = handshake;
            this.application = application;
        }
    }
}
