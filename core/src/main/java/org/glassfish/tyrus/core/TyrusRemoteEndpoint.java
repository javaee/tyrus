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
import java.util.concurrent.Future;

import javax.websocket.CloseReason;
import javax.websocket.SendHandler;

/**
 * {@link RemoteEndpoint} implementation.
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class TyrusRemoteEndpoint extends RemoteEndpoint {

    private final WebSocket socket;

    /**
     * Create remote endpoint.
     *
     * @param socket to be used for sending messages.
     */
    public TyrusRemoteEndpoint(WebSocket socket) {
        this.socket = socket;
    }

    @Override
    public Future<Frame> sendText(String text) {
        return socket.send(text);
    }

    @Override
    public void sendText(String text, SendHandler handler) {
        socket.send(text, handler);
    }

    @Override
    public Future<Frame> sendBinary(ByteBuffer byteBuffer) {
        return socket.send(Utils.getRemainingArray(byteBuffer));
    }

    @Override
    public void sendBinary(ByteBuffer data, SendHandler handler) {
        socket.send(Utils.getRemainingArray(data), handler);
    }

    @Override
    public Future<Frame> sendText(String fragment, boolean isLast) {
        return socket.stream(isLast, fragment);
    }

    @Override
    public Future<Frame> sendBinary(ByteBuffer byteBuffer, boolean b) {
        byte[] bytes = Utils.getRemainingArray(byteBuffer);
        return socket.stream(b, bytes, 0, bytes.length);
    }

    @Override
    public Future<Frame> sendPing(ByteBuffer byteBuffer) {
        return socket.sendPing(Utils.getRemainingArray(byteBuffer));
    }

    @Override
    public Future<Frame> sendPong(ByteBuffer byteBuffer) {
        return socket.sendPong(Utils.getRemainingArray(byteBuffer));
    }

    @Override
    public void close(CloseReason closeReason) {
        socket.close(closeReason.getCloseCode().getCode(), closeReason.getReasonPhrase());
    }

    @Override
    public void setWriteTimeout(long timeoutMs) {
        socket.setWriteTimeout(timeoutMs);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TyrusRemoteEndpoint)) return false;

        TyrusRemoteEndpoint that = (TyrusRemoteEndpoint) o;

        return socket.equals(that.socket);
    }

    @Override
    public int hashCode() {
        return socket.hashCode();
    }

    /**
     * Write raw data to underlying connection.
     * <p/>
     * Use this only when you know what you are doing.
     *
     * @param dataFrame bytes to be send.
     * @return future can be used to get information about sent message.
     */
    public Future<Frame> sendRawFrame(ByteBuffer dataFrame) {
        return socket.sendRawFrame(dataFrame);
    }

    WebSocket getSocket() {
        return socket;
    }
}
