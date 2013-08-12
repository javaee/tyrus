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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;
import javax.websocket.Session;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;

import org.glassfish.tyrus.spi.SPIEndpoint;
import org.glassfish.tyrus.spi.SPIRegisteredEndpoint;
import org.glassfish.tyrus.websockets.ClosingDataFrame;
import org.glassfish.tyrus.websockets.ProtocolHandler;
import org.glassfish.tyrus.websockets.WebSocket;
import org.glassfish.tyrus.websockets.WebSocketApplication;
import org.glassfish.tyrus.websockets.WebSocketEngine;
import org.glassfish.tyrus.websockets.WebSocketListener;
import org.glassfish.tyrus.websockets.WebSocketRequest;
import org.glassfish.tyrus.websockets.WebSocketResponse;

/**
 * Implementation of {@link SPIRegisteredEndpoint}.
 * <p/>
 * Please note that for one connection to WebSocketApplication it is guaranteed that the methods:
 * isApplicationRequest, createSocket, getSupportedProtocols, getSupportedExtensions are called in this order.
 * Handshakes
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class TyrusEndpoint extends WebSocketApplication implements SPIRegisteredEndpoint {

    private final SPIEndpoint endpoint;

    /**
     * Used to store negotiated extensions between the call of isApplicationRequest method and getSupportedExtensions.
     */
    private List<Extension> temporaryNegotiatedExtensions = Collections.emptyList();

    /**
     * Used to store negotiated protocol between the call of isApplicationRequest method and getSupportedProtocols.
     */
    private String temporaryNegotiatedProtocol;

    /**
     * Create {@link TyrusEndpoint} which represents given {@link SPIEndpoint}.
     *
     * @param endpoint endpoint to be wrapped.
     */
    public TyrusEndpoint(SPIEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public boolean isApplicationRequest(WebSocketRequest webSocketRequest) {
        final List<String> protocols = webSocketRequest.getHeaders().get(WebSocketEngine.SEC_WS_PROTOCOL_HEADER);
        temporaryNegotiatedProtocol = endpoint.getNegotiatedProtocol(protocols);

        final List<Extension> extensions = TyrusExtension.fromString(webSocketRequest.getHeaders().get(WebSocketEngine.SEC_WS_EXTENSIONS_HEADER));
        temporaryNegotiatedExtensions = endpoint.getNegotiatedExtensions(extensions);

        return endpoint.checkHandshake(webSocketRequest instanceof RequestContext ? (RequestContext) webSocketRequest : null);
    }

    @Override
    public String getPath() {
        return endpoint.getEndpointPath();
    }

    @Override
    public WebSocket createSocket(final ProtocolHandler handler, final WebSocketListener... listeners) {
        handler.setContainer(endpoint.getWebSocketContainer());
        return new TyrusWebSocket(handler, listeners);
    }

    @Override
    public void onConnect(WebSocket socket) {
        this.endpoint.onConnect(new TyrusRemoteEndpoint(socket), temporaryNegotiatedProtocol, temporaryNegotiatedExtensions);
    }

    @Override
    public void onFragment(WebSocket socket, String fragment, boolean last) {
        try {
            this.endpoint.onPartialMessage(new TyrusRemoteEndpoint(socket), fragment, last);
        } catch (Throwable t) {
            Logger.getLogger(TyrusEndpoint.class.getName()).severe("Error !!!" + t);
            t.printStackTrace();
        }
    }

    @Override
    public void onFragment(WebSocket socket, byte[] fragment, boolean last) {
        try {
            this.endpoint.onPartialMessage(new TyrusRemoteEndpoint(socket), ByteBuffer.wrap(fragment), last);
        } catch (Throwable t) {
            Logger.getLogger(TyrusEndpoint.class.getName()).severe("Error !!!" + t);
            t.printStackTrace();
        }
    }

    @Override
    public void onMessage(WebSocket socket, String messageString) {
        this.endpoint.onMessage(new TyrusRemoteEndpoint(socket), messageString);
    }

    @Override
    public void onMessage(WebSocket socket, byte[] bytes) {
        this.endpoint.onMessage(new TyrusRemoteEndpoint(socket), ByteBuffer.wrap(bytes));
    }

    @Override
    public void onClose(WebSocket socket, ClosingDataFrame frame) {
        CloseReason closeReason = null;

        if (frame != null) {
            closeReason = new CloseReason(CloseReason.CloseCodes.getCloseCode(frame.getCode()), frame.getReason() == null ? "" : frame.getReason());
        }

        this.endpoint.onClose(new TyrusRemoteEndpoint(socket), closeReason);
    }

    @Override
    public void onPing(WebSocket socket, byte[] bytes) {
        this.endpoint.onPing(new TyrusRemoteEndpoint(socket), ByteBuffer.wrap(bytes));
    }

    @Override
    public void onPong(WebSocket socket, byte[] bytes) {
        this.endpoint.onPong(new TyrusRemoteEndpoint(socket), ByteBuffer.wrap(bytes));
    }

    @Override
    public void remove() {
        this.endpoint.remove();
    }

    @Override
    public Set<Session> getOpenSessions() {
        return endpoint.getOpenSessions();
    }

    @Override
    public void onExtensionNegotiation(List<org.glassfish.tyrus.websockets.Extension> extensions) {
    }

    @Override
    public List<org.glassfish.tyrus.websockets.Extension> getSupportedExtensions() {
        List<org.glassfish.tyrus.websockets.Extension> grizzlyExtensions = new ArrayList<org.glassfish.tyrus.websockets.Extension>();

        for (Extension ext : temporaryNegotiatedExtensions) {
            final org.glassfish.tyrus.websockets.Extension extension = new org.glassfish.tyrus.websockets.Extension(ext.getName());
            for (Extension.Parameter p : ext.getParameters()) {
                extension.getParameters().add(new org.glassfish.tyrus.websockets.Extension.Parameter(p.getName(), p.getValue()));
            }
            grizzlyExtensions.add(extension);
        }

        return grizzlyExtensions;
    }

    @Override
    public boolean onError(WebSocket webSocket, Throwable t) {
        Logger.getLogger(TyrusEndpoint.class.getName()).log(Level.WARNING, "Unexpected error, closing connection.", t);
        return true;
    }

    @Override
    public List<String> getSupportedProtocols(List<String> subProtocol) {
        List<String> result;

        if (temporaryNegotiatedProtocol == null) {
            result = Collections.emptyList();
        } else {
            result = new ArrayList<String>();
            result.add(temporaryNegotiatedProtocol);
        }

        return result;
    }

    @Override
    public void onHandShakeResponse(WebSocketRequest request, WebSocketResponse response) {
        final EndpointConfig configuration = this.endpoint.getEndpointConfig();

        if (configuration instanceof ServerEndpointConfig) {
            final HandshakeResponse handshakeResponse = createHandshakeResponse(response);

            // http://java.net/jira/browse/TYRUS-62
            final ServerEndpointConfig serverEndpointConfig = (ServerEndpointConfig) configuration;
            serverEndpointConfig.getConfigurator().modifyHandshake(serverEndpointConfig, createHandshakeRequest(request),
                    handshakeResponse);

            for (Map.Entry<String, List<String>> headerEntry : handshakeResponse.getHeaders().entrySet()) {
                StringBuilder finalHeaderValue = new StringBuilder();

                for (String headerValue : headerEntry.getValue()) {
                    if (finalHeaderValue.length() != 0) {
                        finalHeaderValue.append(", ");
                    }

                    finalHeaderValue.append(headerValue);
                }

                response.getHeaders().put(headerEntry.getKey(), finalHeaderValue.toString());
            }
        }
    }

    private HandshakeRequest createHandshakeRequest(final WebSocketRequest webSocketRequest) {
        if (webSocketRequest instanceof RequestContext) {
            final RequestContext requestContext = (RequestContext) webSocketRequest;
            // TYRUS-208; spec requests headers to be read only when passed to ServerEndpointConfig.Configurator#modifyHandshake.
            // TYRUS-211; spec requests parameterMap to be read only when passed to ServerEndpointConfig.Configurator#modifyHandshake.
            requestContext.lock();
            return requestContext;
        }

        return null;
    }

    private HandshakeResponse createHandshakeResponse(final WebSocketResponse webSocketResponse) {

        final Map<String, List<String>> headers = new TreeMap<String, List<String>>(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return o1.toLowerCase().compareTo(o2.toLowerCase());
            }
        });

        for (Map.Entry<String, String> entry : webSocketResponse.getHeaders().entrySet()) {
            headers.put(entry.getKey(), Arrays.asList(entry.getValue()));
        }

        return new HandshakeResponse() {
            @Override
            public Map<String, List<String>> getHeaders() {
                return headers;
            }
        };
    }
}
