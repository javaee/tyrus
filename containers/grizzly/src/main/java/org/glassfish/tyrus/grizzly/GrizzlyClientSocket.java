/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 - 2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.grizzly;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.websocket.ClientEndpointConfiguration;
import javax.net.websocket.extensions.Extension;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.Processor;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.websockets.DataFrame;
import org.glassfish.grizzly.websockets.HandShake;
import org.glassfish.grizzly.websockets.HandshakeException;
import org.glassfish.grizzly.websockets.ProtocolHandler;
import org.glassfish.grizzly.websockets.WebSocket;
import org.glassfish.grizzly.websockets.WebSocketEngine;
import org.glassfish.grizzly.websockets.WebSocketFilter;
import org.glassfish.grizzly.websockets.WebSocketListener;
import org.glassfish.grizzly.websockets.draft06.ClosingFrame;
import org.glassfish.tyrus.spi.SPIEndpoint;
import org.glassfish.tyrus.spi.TyrusClientSocket;

/**
 * Implementation of the WebSocket interface from Grizzly.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
class GrizzlyClientSocket implements WebSocket, TyrusClientSocket {

    private URI uri;
    private ProtocolHandler protocolHandler;
    private Set<SPIEndpoint> endpoints = Collections.newSetFromMap(new ConcurrentHashMap<SPIEndpoint, Boolean>());
    private TCPNIOTransport transport;
    EnumSet<State> connected = EnumSet.range(State.CONNECTED, State.CLOSING);
    private final AtomicReference<State> state = new AtomicReference<State>(State.NEW);
    private GrizzlyRemoteEndpoint remoteEndpoint;
    private long timeoutMs;
    private ClientEndpointConfiguration clc;

    enum State {
        NEW, CONNECTED, CLOSING, CLOSED
    }

//    public GrizzlyClientSocket(URI uri, long timeoutMs, SPIEndpoint... endpoints) {
//        this(uri, timeoutMs);
//        for (SPIEndpoint endpoint : endpoints) {
//            this.addEndpoint(endpoint);
//        }
//    }

    GrizzlyClientSocket(URI uri, ClientEndpointConfiguration clc, long timeoutMs) {
        this.uri = uri;
        this.clc = clc;
        protocolHandler = WebSocketEngine.DEFAULT_VERSION.createHandler(true);
        remoteEndpoint = new GrizzlyRemoteEndpoint(this);
        this.timeoutMs = timeoutMs;
    }

    /**
     * Connects to the given {@link URI}.
     */
    public void connect() {
        try {
            transport = TCPNIOTransportBuilder.newInstance().build();
            transport.start();
            final TCPNIOConnectorHandler connectorHandler = new TCPNIOConnectorHandler(transport) {
                @Override
                protected void preConfigure(Connection conn) {
                    super.preConfigure(conn);
                    protocolHandler.setConnection(conn);
                    WebSocketEngine.WebSocketHolder holder = WebSocketEngine.getEngine().setWebSocketHolder(conn, protocolHandler,
                            GrizzlyClientSocket.this);
                    holder.handshake = protocolHandler.createHandShake(uri);
                    prepareHandshake(holder.handshake);
                }
            };
            connectorHandler.setProcessor(createFilterChain());
            connectorHandler.connect(new InetSocketAddress(uri.getHost(), uri.getPort()));
            connectorHandler.setSyncConnectTimeout(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            e.printStackTrace();
            throw new HandshakeException(e.getMessage());
        }
    }

    private void prepareHandshake(HandShake handshake) {
        ArrayList<String> extensions = new ArrayList<String>();
        for (Extension extension : clc.getExtensions()) {
            extensions.add(extension.getName());
        }
        handshake.setExtensions(extensions);
        handshake.setSubProtocol(clc.getPreferredSubprotocols());
    }

    /**
     * Add new SPI_Endpoint to the socket.
     *
     * @param endpoint to be added.
     */
    public void addEndpoint(SPIEndpoint endpoint) {
        endpoints.add(endpoint);
    }

    @Override
    public GrizzlyFuture<DataFrame> send(String s) {
        if (isConnected()) {
            return protocolHandler.send(s);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    @Override
    public GrizzlyFuture<DataFrame> send(byte[] bytes) {
        if (isConnected()) {
            return protocolHandler.send(bytes);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    @Override
    public GrizzlyFuture<DataFrame> sendPing(byte[] bytes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GrizzlyFuture<DataFrame> sendPong(byte[] bytes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GrizzlyFuture<DataFrame> stream(boolean b, String s) {
        if (isConnected()) {
            return protocolHandler.stream(b, s);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    @Override
    public GrizzlyFuture<DataFrame> stream(boolean b, byte[] bytes, int i, int i1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
        close(100000, "Closing");
    }

    @Override
    public void close(int i) {
        close(i, null);
    }

    @Override
    public void close(int i, String s) {
        if (state.compareAndSet(State.CONNECTED, State.CLOSING)) {
            protocolHandler.doClose();
            if (transport != null) {
                try {
                    transport.stop();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public boolean isConnected() {
        return connected.contains(state.get());
    }

    @Override
    public void onConnect() {
        state.set(State.CONNECTED);
        for (SPIEndpoint endpoint : endpoints) {
            endpoint.onConnect(remoteEndpoint);
        }
    }

    @Override
    public void onMessage(String message) {
        for (SPIEndpoint endpoint : endpoints) {
            endpoint.onMessage(remoteEndpoint, message);
        }
    }

    @Override
    public void onMessage(byte[] bytes) {
        for (SPIEndpoint endpoint : endpoints) {
            endpoint.onMessage(remoteEndpoint, ByteBuffer.wrap(bytes));
        }
    }

    @Override
    public void onFragment(boolean b, String s) {
        for (SPIEndpoint endpoint : endpoints) {
            endpoint.onPartialMessage(remoteEndpoint, s, b);
        }
    }

    @Override
    public void onFragment(boolean b, byte[] bytes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onClose(DataFrame dataFrame) {
        if (state.compareAndSet(State.CONNECTED, State.CLOSING)) {
            final ClosingFrame closing = (ClosingFrame) dataFrame;
            protocolHandler.close(closing.getCode(), closing.getTextPayload());
        } else {
            state.set(State.CLOSED);
            protocolHandler.doClose();
        }
        for (SPIEndpoint endpoint : endpoints) {
            endpoint.onClose(remoteEndpoint);
        }
    }

    @Override
    public void onPing(DataFrame dataFrame) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onPong(DataFrame dataFrame) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean add(WebSocketListener webSocketListener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(WebSocketListener webSocketListener) {
        throw new UnsupportedOperationException();
    }

    private static Processor createFilterChain() {
        FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
        clientFilterChainBuilder.add(new TransportFilter());
        clientFilterChainBuilder.add(new HttpClientFilter());
        clientFilterChainBuilder.add(new WebSocketFilter());
        return clientFilterChainBuilder.build();
    }

    public Set<SPIEndpoint> getEndpoints() {
        return new HashSet<SPIEndpoint>(endpoints);
    }
}
