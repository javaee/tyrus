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

package org.glassfish.tyrus.websockets;

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

import javax.websocket.DeploymentException;

import org.glassfish.tyrus.spi.SPIHandshakeRequest;
import org.glassfish.tyrus.spi.SPIWebSocketEngine;
import org.glassfish.tyrus.spi.SPIWriter;
import org.glassfish.tyrus.websockets.uri.Match;

/**
 * WebSockets engine implementation (singleton), which handles {@link WebSocketApplication}s registration, responsible
 * for client and server handshake validation.
 *
 * @author Alexey Stashok
 * @see WebSocket
 * @see WebSocketApplication
 */
public class WebSocketEngine implements SPIWebSocketEngine {

    public static final String SEC_WS_ACCEPT = "Sec-WebSocket-Accept";
    public static final String SEC_WS_KEY_HEADER = "Sec-WebSocket-Key";
    public static final String SEC_WS_ORIGIN_HEADER = "Sec-WebSocket-Origin";
    public static final String ORIGIN_HEADER = "Origin";
    public static final String SEC_WS_PROTOCOL_HEADER = "Sec-WebSocket-Protocol";
    public static final String SEC_WS_EXTENSIONS_HEADER = "Sec-WebSocket-Extensions";
    public static final String SEC_WS_VERSION = "Sec-WebSocket-Version";
    public static final String WEBSOCKET = "websocket";
    public static final String RESPONSE_CODE_MESSAGE = "Switching Protocols";
    public static final int RESPONSE_CODE_VALUE = 101;
    public static final String UPGRADE = "Upgrade";
    public static final String CONNECTION = "Connection";
    public static final String HOST = "Host";
    public static final Version DEFAULT_VERSION = Version.DRAFT17;
    public static final String SERVER_KEY_HASH = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    public static final int MASK_SIZE = 4;

//    private static final WebSocketEngine engine = new WebSocketEngine();
    private static final Logger LOGGER = Logger.getLogger(WebSocketEngine.WEBSOCKET);

    private final Set<WebSocketApplication> applications = Collections.newSetFromMap(new ConcurrentHashMap<WebSocketApplication, Boolean>());
    private final Map<SPIWriter, WebSocketHolder> webSocketHolderMap = new ConcurrentHashMap<SPIWriter, WebSocketHolder>();

    public WebSocketEngine() {
    }

//    public static synchronized WebSocketEngine getEngine() {
//        return engine;
//    }

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

    private static ProtocolHandler loadHandler(SPIHandshakeRequest request) {
        for (Version version : Version.values()) {
            if (version.validate(request)) {
                return version.createHandler(false);
            }
        }
        return null;
    }

    private static void handleUnsupportedVersion(final SPIWriter writer,
                                                 final SPIHandshakeRequest request) {
        WebSocketResponse response = new WebSocketResponse();
        response.setStatus(426);
        response.getHeaders().put(WebSocketEngine.SEC_WS_VERSION, Version.getSupportedWireProtocolVersions());
        writer.write(response);
    }

    WebSocketApplication getApplication(SPIHandshakeRequest request) {
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

    /**
     * Evaluate whether connection/request is suitable for upgrade and perform it.
     *
     * @param writer  connection.
     * @param request request.
     * @return {@code true} if upgrade is performed, {@code false} otherwise.
     */
    public boolean upgrade(SPIWriter writer, SPIHandshakeRequest request) {
        return upgrade(writer, request, null);
    }

    /**
     * Evaluate whether connection/request is suitable for upgrade and perform it.
     *
     * @param writer                  connection.
     * @param request                 request.
     * @param webSocketHolderListener called when upgrade is going to be performed. Additinally, leaves
     *                                {@link org.glassfish.tyrus.websockets.WebSocket#onConnect()} call
     *                                responsibility to {@link WebSocketHolderListener} instance.
     * @return {@code true} if upgrade is performed, {@code false} otherwise.
     * @throws HandshakeException if an error occurred during the upgrade.
     */
    public boolean upgrade(SPIWriter writer, SPIHandshakeRequest request, WebSocketHolderListener webSocketHolderListener) throws HandshakeException {
        final WebSocketApplication app = getApplication(request);

        WebSocket socket = null;
        try {
            if (app != null) {
                final ProtocolHandler protocolHandler = loadHandler(request);
                if (protocolHandler == null) {
                    handleUnsupportedVersion(writer, request);
                    return false;
                }
                protocolHandler.setWriter(writer);
                socket = app.createSocket(protocolHandler, app);
                WebSocketHolder holder =
                        setWebSocketHolder(writer, protocolHandler, null, socket, app);
                protocolHandler.handshake(writer, app, request);
                writer.addCloseListener(new SPIWriter.CloseListener() {
                    @Override
                    public void onClose(final SPIWriter writer/*, final CloseType type*/) {

                        final WebSocket webSocket = getWebSocket(writer);
                        if (webSocket != null) {
                            webSocket.close();
                            webSocket.onClose(new ClosingDataFrame(WebSocket.END_POINT_GOING_DOWN,
                                    "Close detected on connection"));
                        }
                    }
                });

                if (webSocketHolderListener != null) {
                    webSocketHolderListener.onWebSocketHolder(holder);
                } else {
                    socket.onConnect();
                }
                return true;
            }
        } catch (HandshakeException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            if (socket != null) {
                socket.close();
            }
        }
        return false;
    }

    /**
     * Registers the specified {@link WebSocketApplication} with the
     * <code>WebSocketEngine</code>.
     *
     * @param app the {@link WebSocketApplication} to register.
     * @throws DeploymentException when added applications responds to same path as some already registered application.
     */
    public void register(WebSocketApplication app) throws DeploymentException {
        checkPath(app);
        applications.add(app);
    }

    private void checkPath(WebSocketApplication app) throws DeploymentException {
        for (WebSocketApplication webSocketApplication : applications) {
            if (Match.isEquivalent(app.getPath(), webSocketApplication.getPath())) {
                throw new DeploymentException(String.format(
                        "Found Equivalent paths. Added path: '%s' is equivalent with '%s'.", app.getPath(),
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
     * Returns <tt>true</tt> if passed Grizzly {@link org.glassfish.tyrus.spi.SPIWriter} is associated with a {@link WebSocket}, or
     * <tt>false</tt> otherwise.
     *
     * @param writer Grizzly {@link org.glassfish.tyrus.spi.SPIWriter}.
     * @return <tt>true</tt> if passed Grizzly {@link org.glassfish.tyrus.spi.SPIWriter} is associated with a {@link WebSocket}, or
     *         <tt>false</tt> otherwise.
     */
    public boolean webSocketInProgress(SPIWriter writer) {
        return webSocketHolderMap.get(writer) != null;
    }

    /**
     * Get the {@link WebSocket} associated with the Grizzly {@link org.glassfish.tyrus.spi.SPIWriter}, or <tt>null</tt>, if there none is
     * associated.
     *
     * @param writer Grizzly {@link org.glassfish.tyrus.spi.SPIWriter}.
     * @return the {@link WebSocket} associated with the Grizzly {@link org.glassfish.tyrus.spi.SPIWriter}, or <tt>null</tt>, if there none is
     *         associated.
     */
    public WebSocket getWebSocket(SPIWriter writer) {
        final WebSocketHolder holder = getWebSocketHolder(writer);
        return holder == null ? null : holder.webSocket;
    }

    public WebSocketHolder getWebSocketHolder(final SPIWriter writer) {
        return webSocketHolderMap.get(writer);
    }

    public WebSocketHolder setWebSocketHolder(final SPIWriter writer, ProtocolHandler handler, WebSocketRequest request, WebSocket socket, WebSocketApplication application) {
        final WebSocketHolder holder = new WebSocketHolder(handler, socket, (request == null ? null : handler.createClientHandShake(request)), application);

        webSocketHolderMap.put(writer, holder);
        return holder;
    }

    public void removeConnection(SPIWriter writer) {
        webSocketHolderMap.remove(writer);
    }

    /**
     * {@link WebSocketHolder} listener.
     */
    public static abstract class WebSocketHolderListener {
        /**
         * Called when request is ready to upgrade. The responsibility for making {@link org.glassfish.tyrus.websockets.WebSocket#onConnect()}
         * call is on listener when it is used.
         *
         * @param webSocketHolder holder instance.
         * @throws HandshakeException if an error occurred during the upgrade process.
         * @see WebSocketHolder#webSocket
         */
        public abstract void onWebSocketHolder(WebSocketHolder webSocketHolder) throws HandshakeException;
    }

    /**
     * WebSocketHolder object, which gets associated with the Grizzly {@link org.glassfish.tyrus.spi.SPIWriter}.
     */
    public final static class WebSocketHolder {
        public final WebSocket webSocket;
        public final ProtocolHandler handler;
        public final HandShake handshake;
        public final WebSocketApplication application;
        public volatile ByteBuffer buffer;

        WebSocketHolder(final ProtocolHandler handler, final WebSocket socket, final HandShake handshake,
                        final WebSocketApplication application) {
            this.handler = handler;
            this.webSocket = socket;
            this.handshake = handshake;
            this.application = application;
        }
    }
}
