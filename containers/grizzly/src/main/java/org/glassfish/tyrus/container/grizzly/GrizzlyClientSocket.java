/*
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
*
* Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.container.grizzly;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.Session;

import org.glassfish.tyrus.core.RequestContext;
import org.glassfish.tyrus.core.TyrusExtension;
import org.glassfish.tyrus.core.TyrusRemoteEndpoint;
import org.glassfish.tyrus.spi.SPIEndpoint;
import org.glassfish.tyrus.spi.SPIHandshakeListener;
import org.glassfish.tyrus.spi.TyrusClientSocket;
import org.glassfish.tyrus.websockets.DataFrame;
import org.glassfish.tyrus.websockets.Extension;
import org.glassfish.tyrus.websockets.HandShake;
import org.glassfish.tyrus.websockets.HandshakeException;
import org.glassfish.tyrus.websockets.ProtocolHandler;
import org.glassfish.tyrus.websockets.WebSocket;
import org.glassfish.tyrus.websockets.WebSocketEngine;
import org.glassfish.tyrus.websockets.WebSocketListener;
import org.glassfish.tyrus.websockets.draft06.ClosingFrame;
import org.glassfish.tyrus.websockets.frametypes.PingFrameType;
import org.glassfish.tyrus.websockets.frametypes.PongFrameType;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Processor;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.ssl.SSLFilter;

/**
 * Implementation of the WebSocket interface.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class GrizzlyClientSocket implements WebSocket, TyrusClientSocket {

    /**
     * Can be used as client-side user property to set proxy.
     *
     * Value is expected to be {@link String} and represent proxy URI. Protocol part is currently ignored
     * but must be present ({@link URI#URI(String)} is used for parsing).
     *
     * <pre>
     *     client.getProperties().put(GrizzlyClientSocket.PROXY_URI, "http://www-proxy.us.oracle.com:80");
     *     client.connectToServer(...);
     * </pre>
     *
     * @see javax.websocket.ClientEndpointConfig#getUserProperties()
     */
    public static final String PROXY_URI = "org.glassfish.tyrus.client.proxy";

    public static final Logger LOGGER = Logger.getLogger(GrizzlyClientSocket.class.getName());

    private final URI uri;
    private final ProtocolHandler protocolHandler;
    private final SPIEndpoint endpoint;
    private TCPNIOTransport transport;
    private final EnumSet<State> connected = EnumSet.range(State.CONNECTED, State.CLOSING);
    private final AtomicReference<State> state = new AtomicReference<State>(State.NEW);
    private final TyrusRemoteEndpoint remoteEndpoint;
    private final long timeoutMs;
    private final ClientEndpointConfig configuration;
    private final SPIHandshakeListener listener;
    private final SSLEngineConfigurator clientSSLEngineConfigurator;
    private final String proxyUri;
    private Session session = null;

    private final CountDownLatch onConnectLatch = new CountDownLatch(1);

    private final List<javax.websocket.Extension> responseExtensions = new ArrayList<javax.websocket.Extension>();

    enum State {
        NEW, CONNECTED, CLOSING, CLOSED
    }

    /**
     * Create new instance.
     *
     * @param uri                         endpoint address.
     * @param configuration               client endpoint configuration.
     * @param timeoutMs                   TODO
     * @param listener                    listener called when response is received.
     * @param clientSSLEngineConfigurator ssl engine configurator
     */
    public GrizzlyClientSocket(SPIEndpoint endpoint, URI uri, ClientEndpointConfig configuration, long timeoutMs,
                               SPIHandshakeListener listener,
                               SSLEngineConfigurator clientSSLEngineConfigurator,
                               String proxyUri) {
        this.endpoint = endpoint;
        this.uri = uri;
        this.configuration = configuration;
        protocolHandler = WebSocketEngine.DEFAULT_VERSION.createHandler(true);
        protocolHandler.setContainer(endpoint.getWebSocketContainer());
        remoteEndpoint = new TyrusRemoteEndpoint(this);
        this.timeoutMs = timeoutMs;
        this.listener = listener;
        this.clientSSLEngineConfigurator = clientSSLEngineConfigurator;
        this.proxyUri = proxyUri;
        if (session == null) {
            session = endpoint.createSessionForRemoteEndpoint(remoteEndpoint, null, null);
        }
    }

    /**
     * Connects to the given {@link URI}.
     */
    public void connect() {
        try {
            // TYRUS-188: lots of threads were created for every single client instance.
            final TCPNIOTransportBuilder transportBuilder = TCPNIOTransportBuilder.newInstance();
            transportBuilder.getWorkerThreadPoolConfig().setMaxPoolSize(1).setCorePoolSize(1);
            transportBuilder.getSelectorThreadPoolConfig().setMaxPoolSize(1).setCorePoolSize(1);

            transport = transportBuilder.build();

            transport.start();

            final TCPNIOConnectorHandler connectorHandler = new TCPNIOConnectorHandler(transport) {
                @Override
                protected void preConfigure(Connection conn) {
                    super.preConfigure(conn);

                    final org.glassfish.tyrus.websockets.Connection connection = getConnection(conn);

                    protocolHandler.setConnection(connection);
                    WebSocketEngine.WebSocketHolder holder = WebSocketEngine.getEngine()
                            .setWebSocketHolder(connection, protocolHandler, RequestContext.Builder.create().requestURI(uri).build(), GrizzlyClientSocket.this, null);

                    prepareHandshake(holder.handshake);
                }
            };

            URI proxy = null;
            try {
                if(proxyUri != null) {
                    proxy = new URI(proxyUri);
                }
            } catch (URISyntaxException e) {
                LOGGER.log(Level.WARNING, String.format("Invalid proxy '%s'.", proxyUri), e);
            }

            connectorHandler.setProcessor(createFilterChain(null, clientSSLEngineConfigurator, proxy != null));
            int port = uri.getPort();
            if (port == -1) {
                String scheme = uri.getScheme();
                assert scheme != null && (scheme.equals("ws") || scheme.equals("wss"));
                if (scheme.equals("ws")) {
                    port = 80;
                } else if (scheme.equals("wss")) {
                    port = 443;
                }
            }

            if(proxy != null) {
                connectorHandler.connect(new InetSocketAddress(proxy.getHost(), proxy.getPort()));
            } else {
                connectorHandler.connect(new InetSocketAddress(uri.getHost(), port));
            }
            connectorHandler.setSyncConnectTimeout(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            e.printStackTrace();
            throw new HandshakeException(e.getMessage());
        }
    }

    private void prepareHandshake(HandShake handshake) {
        List<Extension> grizzlyExtensions = new ArrayList<Extension>();

        for (javax.websocket.Extension e : configuration.getExtensions()) {
            final Extension grizzlyExtension = new Extension(e.getName());
            for (javax.websocket.Extension.Parameter p : e.getParameters()) {
                grizzlyExtension.getParameters().add(new Extension.Parameter(p.getName(), p.getValue()));
            }

            grizzlyExtensions.add(grizzlyExtension);
        }

        handshake.setExtensions(grizzlyExtensions);
        handshake.setSubProtocols(configuration.getPreferredSubprotocols());

        handshake.setResponseListener(new HandShake.HandShakeResponseListener() {
            @Override
            public void onResponseHeaders(final Map<String, String> originalHeaders) {

                String value = originalHeaders.get(WebSocketEngine.SEC_WS_EXTENSIONS_HEADER);
                if (value != null) {
                    responseExtensions.addAll(TyrusExtension.fromString(value));
                }

                listener.onResponseHeaders(originalHeaders);
            }

            @Override
            public void onError(HandshakeException exception) {
                listener.onError(exception);
            }
        });

        handshake.prepareRequest();
        configuration.getConfigurator().beforeRequest(handshake.getRequest().getHeaders());
    }

    @Override
    public Future<DataFrame> send(String s) {
        if (isConnected()) {
            return protocolHandler.send(s);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    @Override
    public Future<DataFrame> send(byte[] bytes) {
        if (isConnected()) {
            return protocolHandler.send(bytes);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    @Override
    public Future<DataFrame> sendPing(byte[] bytes) {
        DataFrame df = new DataFrame(new PingFrameType(), bytes);
        return this.protocolHandler.send(df, false);
    }

    @Override
    public Future<DataFrame> sendPong(byte[] bytes) {
        DataFrame df = new DataFrame(new PongFrameType(), bytes);
        return this.protocolHandler.send(df, false);
    }

    @Override
    public Future<DataFrame> stream(boolean b, String s) {
        if (isConnected()) {
            return protocolHandler.stream(b, s);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    @Override
    public Future<DataFrame> stream(boolean b, byte[] bytes, int i, int i1) {

        if (isConnected()) {
            return protocolHandler.stream(b, bytes, i, i1);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }

    }

    @Override
    public void close() {
        close(CloseReason.CloseCodes.NORMAL_CLOSURE.getCode(), "Closing");
    }

    @Override
    public Session getSession() {
        return session;
    }

    @Override
    public void close(int i) {
        close(i, null);
    }

    @Override
    public void close(int i, String s) {
        if (state.compareAndSet(State.CONNECTED, State.CLOSING)) {
            protocolHandler.close(i, s);
            closeTransport();
        }

        this.onClose(new ClosingFrame(i, s));
    }

    @Override
    public boolean isConnected() {
        return connected.contains(state.get());
    }

    @Override
    public void onConnect() {
        state.set(State.CONNECTED);
        endpoint.onConnect(remoteEndpoint, null, responseExtensions);
        onConnectLatch.countDown();
    }

    @Override
    public void onMessage(String message) {
        awaitOnConnect();
        endpoint.onMessage(remoteEndpoint, message);
    }

    @Override
    public void onMessage(byte[] bytes) {
        awaitOnConnect();
        endpoint.onMessage(remoteEndpoint, ByteBuffer.wrap(bytes));
    }

    @Override
    public void onFragment(boolean b, String s) {
        awaitOnConnect();
        endpoint.onPartialMessage(remoteEndpoint, s, b);
    }

    @Override
    public void onFragment(boolean bool, byte[] bytes) {
        awaitOnConnect();
        endpoint.onPartialMessage(remoteEndpoint, ByteBuffer.wrap(bytes), bool);
    }

    @Override
    public void onClose(ClosingFrame dataFrame) {
        awaitOnConnect();

        if (state.get() == State.CLOSED) {
            return;
        }

        if (!state.compareAndSet(State.CLOSING, State.CLOSED)) {
            state.set(State.CLOSED);
            protocolHandler.doClose();
            closeTransport();
        }

        CloseReason closeReason = null;

        if (dataFrame != null) {
            closeReason = new CloseReason(CloseReason.CloseCodes.getCloseCode(dataFrame.getCode()), dataFrame.getReason());
        }
        endpoint.onClose(remoteEndpoint, closeReason);
    }

    @Override
    public void onPing(DataFrame dataFrame) {
        awaitOnConnect();
        endpoint.onPing(remoteEndpoint, ByteBuffer.wrap(dataFrame.getBytes()));
    }

    @Override
    public void onPong(DataFrame dataFrame) {
        awaitOnConnect();
        endpoint.onPong(remoteEndpoint, ByteBuffer.wrap(dataFrame.getBytes()));
    }

    @Override
    public boolean add(WebSocketListener webSocketListener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(WebSocketListener webSocketListener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setWriteTimeout(long timeoutMs) {
        protocolHandler.setWriteTimeout(timeoutMs);
    }

    // return boolean, check return value
    private void awaitOnConnect() {
        try {
            onConnectLatch.await();
        } catch (InterruptedException e) {
            // do nothing.
        }
    }

    private static Processor createFilterChain(SSLEngineConfigurator serverSSLEngineConfigurator,
                                               SSLEngineConfigurator clientSSLEngineConfigurator,
                                               boolean proxy) {
        FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
        clientFilterChainBuilder.add(new TransportFilter());
        if (serverSSLEngineConfigurator != null || clientSSLEngineConfigurator != null) {
            clientFilterChainBuilder.add(new SSLFilter(serverSSLEngineConfigurator, clientSSLEngineConfigurator));
        }
        clientFilterChainBuilder.add(new HttpClientFilter());
        clientFilterChainBuilder.add(new WebSocketFilter(WebSocketFilter.DEFAULT_WS_IDLE_TIMEOUT_IN_SECONDS, proxy));
        return clientFilterChainBuilder.build();
    }

    private static org.glassfish.tyrus.websockets.Connection getConnection(final Connection connection) {
        return new ConnectionImpl(connection);
    }

    private void closeTransport() {
        if (transport != null) {
            try {
                transport.stop();
            } catch (IOException e) {
                Logger.getLogger(GrizzlyClientSocket.class.getName()).log(Level.FINE, "Transport closing problem.");
            }
        }
    }
}
