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
package org.glassfish.tyrus.grizzly;

import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.websockets.DataFrame;
import org.glassfish.grizzly.websockets.ProtocolHandler;
import org.glassfish.grizzly.websockets.WebSocket;
import org.glassfish.grizzly.websockets.WebSocketApplication;
import org.glassfish.grizzly.websockets.WebSocketEngine;
import org.glassfish.grizzly.websockets.WebSocketListener;
import org.glassfish.tyrus.spi.SPIEndpoint;
import org.glassfish.tyrus.spi.SPIRegisteredEndpoint;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of {@link SPIRegisteredEndpoint} for Grizzly.
 * Please note that for one connection to WebSocketApplication it is guaranteed that the methods:
 * isApplicationRequest, createSocket, getSupportedProtocols, getSupportedExtensions are called in this order.
 * Handshakes
 *
 * @author dannycoward
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
class GrizzlyEndpoint extends WebSocketApplication implements SPIRegisteredEndpoint {
    private SPIEndpoint endpoint;

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
    public boolean isApplicationRequest(HttpRequestPacket o) {
        List<String> protocols = createList(o.getHeader(WebSocketEngine.SEC_WS_PROTOCOL_HEADER));
        temporaryNegotiatedProtocol = endpoint.getNegotiatedProtocol(protocols);

        List<String> extensions = createList(o.getHeader(WebSocketEngine.SEC_WS_EXTENSIONS_HEADER));
        temporaryNegotiatedExtensions = endpoint.getNegotiatedExtensions(extensions);

        return endpoint.checkHandshake(new GrizzlyHandshakeRequest(o));
    }

    @Override
    public WebSocket createSocket(final ProtocolHandler handler, final HttpRequestPacket requestPacket, final WebSocketListener... listeners) {
        return new GrizzlySocket(handler, requestPacket, listeners);
    }

    @Override
    public void onConnect(WebSocket socket) {
        GrizzlyRemoteEndpoint gs = GrizzlyRemoteEndpoint.get(socket);
        this.endpoint.onConnect(gs);
    }


    @Override
    public void onFragment(WebSocket socket, String fragment, boolean last) {
        GrizzlyRemoteEndpoint gs = GrizzlyRemoteEndpoint.get(socket);
        try {
            this.endpoint.onPartialMessage(gs, fragment, last);
        } catch (Throwable t) {
            System.out.println("ERROR !!" + t);
            t.printStackTrace();
        }
    }

    @Override
    public void onFragment(WebSocket socket, byte[] fragment, boolean last) {
        GrizzlyRemoteEndpoint gs = GrizzlyRemoteEndpoint.get(socket);
        try {
            this.endpoint.onPartialMessage(gs, ByteBuffer.wrap(fragment), last);
        } catch (Throwable t) {
            System.out.println("ERROR !!" + t);
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
    public List<String> getSupportedExtensions() {
        return temporaryNegotiatedExtensions;
    }

    @Override
    public List<String> getSupportedProtocols(List<String> subProtocol) {
        List<String> result;

        if(temporaryNegotiatedProtocol == null){
            result = Collections.emptyList();
        }else{
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
            List<String> result = Collections.emptyList();
            return result;
        }
        String delimiter = ",";
        String[] tokens = input.split(delimiter);

        return Arrays.asList(tokens);
    }
}
