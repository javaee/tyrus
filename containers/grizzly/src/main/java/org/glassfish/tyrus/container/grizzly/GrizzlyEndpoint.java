/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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


import org.glassfish.tyrus.spi.SPIEndpoint;
import org.glassfish.tyrus.spi.SPIRegisteredEndpoint;
import org.glassfish.tyrus.websockets.DataFrame;
import org.glassfish.tyrus.websockets.Extension;
import org.glassfish.tyrus.websockets.ProtocolHandler;
import org.glassfish.tyrus.websockets.WebSocket;
import org.glassfish.tyrus.websockets.WebSocketApplication;
import org.glassfish.tyrus.websockets.WebSocketEngine;
import org.glassfish.tyrus.websockets.WebSocketListener;
import org.glassfish.tyrus.websockets.WebSocketRequest;

import javax.websocket.Session;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of {@link SPIRegisteredEndpoint} for Grizzly.
 * Please note that for one connection to WebSocketApplication it is guaranteed that the methods:
 * isApplicationRequest, createSocket, getSupportedProtocols, getSupportedExtensions are called in this order.
 * Handshakes
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
class GrizzlyEndpoint extends WebSocketApplication implements SPIRegisteredEndpoint {
    private final SPIEndpoint endpoint;

    /**
     * Used to store negotiated extensions between the call of isApplicationRequest method and getSupportedExtensions.
     */
    private List<String> temporaryNegotiatedExtensions;

    /**
     * Used to store negotiated protocol between the call of isApplicationRequest method and getSupportedProtocols.
     */
    private String temporaryNegotiatedProtocol;

    GrizzlyEndpoint(SPIEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public boolean isApplicationRequest(WebSocketRequest o) {
        List<String> protocols = createList(o.getHeaders().get(WebSocketEngine.SEC_WS_PROTOCOL_HEADER));
        temporaryNegotiatedProtocol = endpoint.getNegotiatedProtocol(protocols);

        List<String> extensions = createList(o.getHeaders().get(WebSocketEngine.SEC_WS_EXTENSIONS_HEADER));
        temporaryNegotiatedExtensions = endpoint.getNegotiatedExtensions(extensions);

        return endpoint.checkHandshake(new GrizzlyHandshakeRequest(o));
    }

    @Override
    public WebSocket createSocket(final ProtocolHandler handler, final WebSocketRequest requestPacket, final WebSocketListener... listeners) {
        return new GrizzlySocket(handler, requestPacket, listeners);
    }

    @Override
    public void onConnect(WebSocket socket) {
        GrizzlyRemoteEndpoint gs = GrizzlyRemoteEndpoint.get(socket);
        this.endpoint.onConnect(gs, temporaryNegotiatedProtocol, temporaryNegotiatedExtensions);
    }


    @Override
    public void onFragment(WebSocket socket, String fragment, boolean last) {
        GrizzlyRemoteEndpoint gs = GrizzlyRemoteEndpoint.get(socket);
        try {
            this.endpoint.onPartialMessage(gs, fragment, last);
        } catch (Throwable t) {
            Logger.getLogger(GrizzlyEndpoint.class.getName()).severe("Error !!!" + t);
            t.printStackTrace();
        }
    }

    @Override
    public void onFragment(WebSocket socket, byte[] fragment, boolean last) {
        GrizzlyRemoteEndpoint gs = GrizzlyRemoteEndpoint.get(socket);
        try {
            this.endpoint.onPartialMessage(gs, ByteBuffer.wrap(fragment), last);
        } catch (Throwable t) {
            Logger.getLogger(GrizzlyEndpoint.class.getName()).severe("Error !!!" + t);
            t.printStackTrace();
        }
    }


    @Override
    public void onMessage(WebSocket socket, String messageString) {
        GrizzlyRemoteEndpoint gs = GrizzlyRemoteEndpoint.get(socket);
        this.endpoint.onMessage(gs, messageString);
    }


    @Override
    public void onMessage(WebSocket socket, byte[] bytes) {
        GrizzlyRemoteEndpoint gs = GrizzlyRemoteEndpoint.get(socket);
        this.endpoint.onMessage(gs, ByteBuffer.wrap(bytes));
    }

    @Override
    public void onClose(WebSocket socket, DataFrame frame) {
        GrizzlyRemoteEndpoint gs = GrizzlyRemoteEndpoint.get(socket);
        this.endpoint.onClose(gs);
        GrizzlyRemoteEndpoint.remove(socket);

    }

    @Override
    public void onPong(WebSocket socket, byte[] bytes) {
        GrizzlyRemoteEndpoint gs = GrizzlyRemoteEndpoint.get(socket);
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
    public List<Extension> getSupportedExtensions() {
        List<Extension> grizzlyExtensions = new ArrayList<Extension>();

        for (String ext : temporaryNegotiatedExtensions) {
            grizzlyExtensions.add(new Extension(ext));
        }

        return grizzlyExtensions;
    }

    @Override
    public boolean onError(WebSocket webSocket, Throwable t) {
        Logger.getLogger(getClass().getName()).log(Level.WARNING, "onError!", t);
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

    /**
     * Creates a {@link List} from {@link String} in which the data values are separated by commas.
     *
     * @param input data values separated by commas.
     * @return data in {@link List}.
     */
    @SuppressWarnings("unchecked")
    private List<String> createList(String input) {
        if (input == null) {
            return Collections.emptyList();
        }
        String delimiter = ",";
        String[] tokens = input.split(delimiter);

        return Arrays.asList(tokens);
    }
}
