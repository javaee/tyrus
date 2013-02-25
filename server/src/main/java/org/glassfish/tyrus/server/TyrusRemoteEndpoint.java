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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

import javax.websocket.CloseReason;

import org.glassfish.tyrus.spi.SPIRemoteEndpoint;
import org.glassfish.tyrus.websockets.WebSocket;

/**
 * {@link SPIRemoteEndpoint} implementation.
 *
 * @author Danny Coward (danny.coward at oracle.com)
 */
public class TyrusRemoteEndpoint extends SPIRemoteEndpoint {
    private final WebSocket socket;
    private final static ConcurrentHashMap<WebSocket, TyrusRemoteEndpoint> sockets = new ConcurrentHashMap<WebSocket, TyrusRemoteEndpoint>();

    /**
     * Create remote endpoint. Used directly on client side to represent server endpoint.
     *
     * @param socket to be used for sending messages.
     */
    public TyrusRemoteEndpoint(WebSocket socket) {
        this.socket = socket;
    }

    /**
     * Get {@link TyrusRemoteEndpoint} instance. Used on server side for managing multiple connected clients.
     *
     * @param socket {@link WebSocket} instance used for lookup.
     * @return Corresponding {@link TyrusRemoteEndpoint}.
     */
    public static TyrusRemoteEndpoint get(WebSocket socket) {
        TyrusRemoteEndpoint s = sockets.get(socket);
        if (s == null) {
            s = new TyrusRemoteEndpoint(socket);
            sockets.put(socket, s);
        }
        return s;
    }

    /**
     * Remove socket.
     *
     * @param socket socket instance to be removed.
     */
    public static void remove(WebSocket socket) {
        sockets.remove(socket);
    }

    @Override
    public void sendText(String text) throws IOException {
        this.socket.send(text);
    }

    @Override
    public void sendBinary(ByteBuffer byteBuffer) throws IOException {
        this.socket.send(byteBuffer.array());
    }

    @Override
    public void sendText(String fragment, boolean isLast) throws IOException {
        this.socket.stream(isLast, fragment);
    }

    @Override
    public void sendBinary(ByteBuffer byteBuffer, boolean b) throws IOException {
        byte[] bytes = byteBuffer.array();
        this.socket.stream(b, bytes, 0, bytes.length);
    }

    @Override
    public void sendPing(ByteBuffer byteBuffer) {
        this.socket.sendPing(byteBuffer.array());
    }

    @Override
    public void sendPong(ByteBuffer byteBuffer) {
        this.socket.sendPong(byteBuffer.array());
    }

    @Override
    public void close(CloseReason closeReason) {
        this.socket.close(closeReason.getCloseCode().getCode(), closeReason.getReasonPhrase());
    }
}
