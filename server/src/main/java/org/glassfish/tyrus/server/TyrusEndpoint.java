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
package org.glassfish.tyrus.server;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.Extension;
import javax.websocket.Session;

import org.glassfish.tyrus.spi.SPIEndpoint;
import org.glassfish.tyrus.spi.SPIRegisteredEndpoint;
import org.glassfish.tyrus.websockets.DataFrame;
import org.glassfish.tyrus.websockets.DefaultWebSocket;
import org.glassfish.tyrus.websockets.ProtocolHandler;
import org.glassfish.tyrus.websockets.WebSocket;
import org.glassfish.tyrus.websockets.WebSocketApplication;
import org.glassfish.tyrus.websockets.WebSocketEngine;
import org.glassfish.tyrus.websockets.WebSocketListener;
import org.glassfish.tyrus.websockets.WebSocketRequest;

/**
 * Implementation of {@link SPIRegisteredEndpoint}.
 *
 * Please note that for one connection to WebSocketApplication it is guaranteed that the methods:
 * isApplicationRequest, createSocket, getSupportedProtocols, getSupportedExtensions are called in this order.
 * Handshakes
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class TyrusEndpoint extends WebSocketApplication implements SPIRegisteredEndpoint {

    private final SPIEndpoint endpoint;

    /**
     * Used to store negotiated extensions between the call of isApplicationRequest method and getSupportedExtensions.
     */
    private List<Extension> temporaryNegotiatedExtensions;

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
    public boolean isApplicationRequest(WebSocketRequest o) {
        // TODO - proper header parsing

        String protocols = o.getHeaders().get(WebSocketEngine.SEC_WS_PROTOCOL_HEADER);
        temporaryNegotiatedProtocol = endpoint.getNegotiatedProtocol(protocols == null ? Collections.<String>emptyList() : Arrays.asList(protocols.split(",")));

        String extensions = o.getHeaders().get(WebSocketEngine.SEC_WS_EXTENSIONS_HEADER);
        // TODO
        // temporaryNegotiatedExtensions = endpoint.getNegotiatedExtensions(extensions == null ? Collections.<Extension>emptyList() : Arrays.asList(extensions.split(",")));
        temporaryNegotiatedExtensions = endpoint.getNegotiatedExtensions(Collections.<Extension>emptyList());

        return endpoint.checkHandshake(new TyrusHandshakeRequest(o));
    }

    @Override
    public WebSocket createSocket(final ProtocolHandler handler, final WebSocketRequest requestPacket, final WebSocketListener... listeners) {
        return new DefaultWebSocket(handler, requestPacket, listeners);
    }

    @Override
    public void onConnect(WebSocket socket) {
        TyrusRemoteEndpoint gs = TyrusRemoteEndpoint.get(socket);
        this.endpoint.onConnect(gs, temporaryNegotiatedProtocol, temporaryNegotiatedExtensions);
    }

    @Override
    public void onFragment(WebSocket socket, String fragment, boolean last) {
        TyrusRemoteEndpoint gs = TyrusRemoteEndpoint.get(socket);
        try {
            this.endpoint.onPartialMessage(gs, fragment, last);
        } catch (Throwable t) {
            Logger.getLogger(TyrusEndpoint.class.getName()).severe("Error !!!" + t);
            t.printStackTrace();
        }
    }

    @Override
    public void onFragment(WebSocket socket, byte[] fragment, boolean last) {
        TyrusRemoteEndpoint gs = TyrusRemoteEndpoint.get(socket);
        try {
            this.endpoint.onPartialMessage(gs, ByteBuffer.wrap(fragment), last);
        } catch (Throwable t) {
            Logger.getLogger(TyrusEndpoint.class.getName()).severe("Error !!!" + t);
            t.printStackTrace();
        }
    }

    @Override
    public void onMessage(WebSocket socket, String messageString) {
        TyrusRemoteEndpoint gs = TyrusRemoteEndpoint.get(socket);
        this.endpoint.onMessage(gs, messageString);
    }


    @Override
    public void onMessage(WebSocket socket, byte[] bytes) {
        TyrusRemoteEndpoint gs = TyrusRemoteEndpoint.get(socket);
        this.endpoint.onMessage(gs, ByteBuffer.wrap(bytes));
    }

    @Override
    public void onClose(WebSocket socket, DataFrame frame) {
        TyrusRemoteEndpoint gs = TyrusRemoteEndpoint.get(socket);
        this.endpoint.onClose(gs);
        TyrusRemoteEndpoint.remove(socket);

    }

    @Override
    public void onPong(WebSocket socket, byte[] bytes) {
        TyrusRemoteEndpoint gs = TyrusRemoteEndpoint.get(socket);
        this.endpoint.onPong(gs, ByteBuffer.wrap(bytes));
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
    public List<org.glassfish.tyrus.websockets.Extension> getSupportedExtensions() {
        List<org.glassfish.tyrus.websockets.Extension> grizzlyExtensions = new ArrayList<org.glassfish.tyrus.websockets.Extension>();

        for (Extension ext : temporaryNegotiatedExtensions) {
            grizzlyExtensions.add(new org.glassfish.tyrus.websockets.Extension(ext.getName()));
        }

        return grizzlyExtensions;
    }

    @Override
    public boolean onError(WebSocket webSocket, Throwable t) {
        Logger.getLogger(TyrusEndpoint.class.getName()).log(Level.WARNING, "onError!", t);
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
}
