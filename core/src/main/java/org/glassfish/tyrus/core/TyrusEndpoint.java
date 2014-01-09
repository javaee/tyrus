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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.Extension;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;

import org.glassfish.tyrus.spi.UpgradeRequest;
import org.glassfish.tyrus.spi.UpgradeResponse;

/**
 * Tyrus endpoint representation.
 * <p/>
 * Please note that for one connection to TyrusEndpoint it is guaranteed that the methods:
 * isApplicationRequest, createSocket, getSupportedProtocols, getSupportedExtensions are called in this order.
 * Handshakes
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class TyrusEndpoint {

    private static final Logger LOGGER = Logger.getLogger(TyrusEndpoint.class.getName());

    private final TyrusEndpointWrapper endpoint;

    /**
     * Used to store negotiated extensions between the call of isApplicationRequest method and getSupportedExtensions.
     */
    private List<Extension> temporaryNegotiatedExtensions = Collections.emptyList();

    /**
     * Used to store negotiated protocol between the call of isApplicationRequest method and getSupportedProtocols.
     */
    private String temporaryNegotiatedProtocol;

    /**
     * Create {@link TyrusEndpoint} which represents given {@link TyrusEndpointWrapper}.
     *
     * @param endpoint endpoint to be wrapped.
     */
    public TyrusEndpoint(TyrusEndpointWrapper endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * Checks application specific criteria to determine if this application can
     * process the request as a WebSocket connection.
     *
     * @param request the incoming HTTP request.
     * @return <code>true</code> if this application can service this request
     */
    protected boolean isApplicationRequest(UpgradeRequest request) {
        final List<String> protocols = request.getHeaders().get(UpgradeRequest.SEC_WEBSOCKET_PROTOCOL);
        temporaryNegotiatedProtocol = endpoint.getNegotiatedProtocol(protocols);

        final List<Extension> extensions = TyrusExtension.fromString(request.getHeaders().get(UpgradeRequest.SEC_WEBSOCKET_EXTENSIONS));
        temporaryNegotiatedExtensions = endpoint.getNegotiatedExtensions(extensions);

        return endpoint.checkHandshake(request);
    }

    /**
     * Checks protocol specific information can and should be upgraded.
     * <p/>
     * The default implementation will check for the presence of the
     * <code>Upgrade</code> header with a value of <code>WebSocket</code>.
     * If present, {@link #isApplicationRequest(org.glassfish.tyrus.spi.UpgradeRequest)}
     * will be invoked to determine if the request is a valid websocket request.
     *
     * @param request TODO
     * @return <code>true</code> if the request should be upgraded to a
     * WebSocket connection
     */
    public final boolean upgrade(UpgradeRequest request) {
        final String upgradeHeader = request.getHeader(UpgradeRequest.UPGRADE);
        return request.getHeaders().get(UpgradeRequest.UPGRADE) != null &&
                // RFC 6455, paragraph 4.2.1.3
                UpgradeRequest.WEBSOCKET.equalsIgnoreCase(upgradeHeader) && isApplicationRequest(request);
    }

    /**
     * Return path for which is current {@link TyrusEndpoint} registered.
     *
     * @return path. {@code null} will be returned when called on client.
     */
    public String getPath() {
        return endpoint.getEndpointPath();
    }

    /**
     * Factory method to create new {@link TyrusWebSocket} instances.  Developers may
     * wish to override this to return customized {@link TyrusWebSocket} implementations.
     *
     * @param handler the {@link ProtocolHandler} to use with the newly created
     *                {@link TyrusWebSocket}.
     * @return TODO
     */
    public TyrusWebSocket createSocket(final ProtocolHandler handler) {
        return new TyrusWebSocket(handler, this);
    }


    /**
     * <p>
     * Invoked when the opening handshake has been completed for a specific
     * {@link WebSocket} instance.
     * </p>
     *
     * @param socket         the newly connected {@link WebSocket}
     * @param upgradeRequest request associated with accepted connection.
     */
    /**
     * When a new {@link TyrusWebSocket} connection is made to this application, the
     * {@link TyrusWebSocket} will be associated with this application.
     *
     * @param socket         the new {@link TyrusWebSocket} connection.
     * @param upgradeRequest request associated with connection.
     */
    public void onConnect(TyrusWebSocket socket, UpgradeRequest upgradeRequest) {
        this.endpoint.onConnect(new TyrusRemoteEndpoint(socket), temporaryNegotiatedProtocol, temporaryNegotiatedExtensions, upgradeRequest);
    }

    /**
     * <p>
     * Invoked when {@link TyrusWebSocket#onFragment(boolean, org.glassfish.tyrus.core.frame.TextFrame)} has been called
     * on a particular {@link TyrusWebSocket} instance.
     * </p>
     *
     * @param socket   the {@link TyrusWebSocket} received the message fragment.
     * @param fragment the message fragment.
     * @param last     flag indicating if this was the last fragment.
     */
    public void onFragment(TyrusWebSocket socket, String fragment, boolean last) {
        try {
            this.endpoint.onPartialMessage(new TyrusRemoteEndpoint(socket), fragment, last);
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, t.getMessage(), t);
        }
    }

    /**
     * <p>
     * Invoked when {@link TyrusWebSocket#onFragment(boolean, org.glassfish.tyrus.core.frame.BinaryFrame)} has been called
     * on a particular {@link TyrusWebSocket} instance.
     * </p>
     *
     * @param socket   the {@link TyrusWebSocket} received the message fragment.
     * @param fragment the message fragment.
     * @param last     flag indicating if this was the last fragment.
     */
    public void onFragment(TyrusWebSocket socket, byte[] fragment, boolean last) {
        try {
            this.endpoint.onPartialMessage(new TyrusRemoteEndpoint(socket), ByteBuffer.wrap(fragment), last);
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, t.getMessage(), t);
        }
    }

    /**
     * <p>
     * Invoked when {@link TyrusWebSocket#onMessage(org.glassfish.tyrus.core.frame.TextFrame)} has been called on a
     * particular {@link TyrusWebSocket} instance.
     * </p>
     *
     * @param socket the {@link TyrusWebSocket} that received a message.
     * @param text   the message received.
     */
    public void onMessage(TyrusWebSocket socket, String text) {
        this.endpoint.onMessage(new TyrusRemoteEndpoint(socket), text);
    }

    /**
     * <p>
     * Invoked when {@link TyrusWebSocket#onMessage(org.glassfish.tyrus.core.frame.BinaryFrame)} has been called on a
     * particular {@link TyrusWebSocket} instance.
     * </p>
     *
     * @param socket the {@link TyrusWebSocket} that received a message.
     * @param bytes  the message received.
     */
    public void onMessage(TyrusWebSocket socket, byte[] bytes) {
        this.endpoint.onMessage(new TyrusRemoteEndpoint(socket), ByteBuffer.wrap(bytes));
    }

    /**
     * When a {@link WebSocket#onClose(org.glassfish.tyrus.core.frame.CloseFrame)} is invoked, the {@link WebSocket}
     * will be unassociated with this application and closed.
     *
     * @param socket      the {@link WebSocket} being closed.
     * @param closeReason the {@link CloseReason}.
     */
    /**
     * <p/>
     * Invoked when {@link TyrusWebSocket#onClose(org.glassfish.tyrus.core.frame.CloseFrame)} has been called on a
     * particular {@link TyrusWebSocket} instance.
     * <p/>
     *
     * @param socket      the {@link TyrusWebSocket} being closed.
     * @param closeReason the {@link CloseReason} sent by the remote end-point.
     */
    public void onClose(TyrusWebSocket socket, CloseReason closeReason) {
        this.endpoint.onClose(new TyrusRemoteEndpoint(socket), closeReason);
    }

    /**
     * <p>
     * Invoked when {@link TyrusWebSocket#onPing(org.glassfish.tyrus.core.frame.PingFrame)} has been called on a
     * particular {@link TyrusWebSocket} instance.
     * </p>
     *
     * @param socket the {@link TyrusWebSocket} that received the ping.
     * @param bytes  the payload of the ping frame, if any.
     */
    public void onPing(TyrusWebSocket socket, byte[] bytes) {
        this.endpoint.onPing(new TyrusRemoteEndpoint(socket), ByteBuffer.wrap(bytes));
    }

    /**
     * <p>
     * Invoked when {@link TyrusWebSocket#onPong(org.glassfish.tyrus.core.frame.PongFrame)} has been called on a
     * particular {@link TyrusWebSocket} instance.
     * </p>
     *
     * @param socket the {@link TyrusWebSocket} that received the pong.
     * @param bytes  the payload of the pong frame, if any.
     */
    public void onPong(TyrusWebSocket socket, byte[] bytes) {
        this.endpoint.onPong(new TyrusRemoteEndpoint(socket), ByteBuffer.wrap(bytes));
    }

    /**
     * Return the websocket extensions supported by this {@link TyrusEndpoint}
     * The {@link Extension}s added to this {@link List} should not include
     * any {@link Extension.Parameter}s as they will be ignored.  This is used
     * exclusively for matching the requested extensions.
     *
     * @return the websocket extensions supported by this {@link TyrusEndpoint}.
     */
    public List<Extension> getSupportedExtensions() {
        return new ArrayList<Extension>(temporaryNegotiatedExtensions);
    }

    /**
     * This method will be invoked if an unexpected exception is caught by
     * the WebSocket runtime.
     *
     * @param webSocket the websocket being processed at the time the
     *                  exception occurred.
     * @param t         the unexpected exception.
     * @return {@code true} if the WebSocket should be closed otherwise
     * {@code false}.
     */
    public boolean onError(TyrusWebSocket webSocket, Throwable t) {
        Logger.getLogger(TyrusEndpoint.class.getName()).log(Level.WARNING, "Unexpected error, closing connection.", t);
        return true;
    }

    /**
     * @param subProtocol TODO
     * @return TODO
     */
    public List<String> getSupportedProtocols(List<String> subProtocol) {
        List<String> result;

        if (temporaryNegotiatedProtocol == null || temporaryNegotiatedProtocol.isEmpty()) {
            result = Collections.emptyList();
        } else {
            result = new ArrayList<String>();
            result.add(temporaryNegotiatedProtocol);
        }

        return result;
    }

    /**
     * Invoked when server side handshake is ready to send response.
     * <p/>
     * Changes in response parameter will be reflected in data sent back to client.
     *
     * @param request  original request which caused this handshake.
     * @param response response to be send.
     */
    public void onHandShakeResponse(UpgradeRequest request, UpgradeResponse response) {
        final EndpointConfig configuration = this.endpoint.getEndpointConfig();

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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TyrusEndpoint that = (TyrusEndpoint) o;

        if (endpoint == null) {
            return super.equals(o);
        } else {
            return endpoint.equals(that.endpoint);
        }
    }

    @Override
    public int hashCode() {
        if (endpoint == null) {
            return super.hashCode();
        } else {
            return endpoint.hashCode();
        }
    }
}
