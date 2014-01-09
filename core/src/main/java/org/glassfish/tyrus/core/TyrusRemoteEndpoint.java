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
import java.util.concurrent.Future;

import javax.websocket.CloseReason;
import javax.websocket.SendHandler;

/**
 * Subset of {@link javax.websocket.RemoteEndpoint} interface which should be implemented
 * by container implementations.
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class TyrusRemoteEndpoint {

    private final TyrusWebSocket socket;

    /**
     * Create remote endpoint.
     *
     * @param socket to be used for sending messages.
     */
    public TyrusRemoteEndpoint(TyrusWebSocket socket) {
        this.socket = socket;
    }

    /**
     * Send text message.
     *
     * @param text the message to be sent.
     * @return {@link Future} related to send command.
     */
    public Future<Frame> sendText(String text) {
        return socket.send(text);
    }

    /**
     * Send text message.
     *
     * @param text    the message to be sent.
     * @param handler notification handler. {@link SendHandler#onResult(javax.websocket.SendResult)} is called when send
     *                operation is completed.
     */
    public void sendText(String text, SendHandler handler) {
        socket.send(text, handler);
    }

    /**
     * Send binary message.
     *
     * @param data the message to be sent.
     * @return {@link Future} related to send command.
     */
    public Future<Frame> sendBinary(ByteBuffer data) {
        return socket.send(Utils.getRemainingArray(data));
    }

    /**
     * Send binary message.
     *
     * @param data    the message to be sent.
     * @param handler notification handler. {@link SendHandler#onResult(javax.websocket.SendResult)} is called when send
     *                operation is completed.
     */
    public void sendBinary(ByteBuffer data, SendHandler handler) {
        socket.send(Utils.getRemainingArray(data), handler);
    }

    /**
     * Send text message in pieces, blocking until all of the message has been transmitted. The runtime
     * reads the message in order. Non-final pieces are sent with isLast set to false. The final piece
     * must be sent with isLast set to true.
     *
     * @param fragment the piece of the message being sent.
     * @param isLast   Whether the fragment being sent is the last piece of the message.
     */
    public Future<Frame> sendText(String fragment, boolean isLast) {
        return socket.stream(isLast, fragment);
    }

    /**
     * Send a binary message in pieces, blocking until all of the message has been transmitted. The runtime
     * reads the message in order. Non-final pieces are sent with isLast set to false. The final piece
     * must be sent with isLast set to true.
     *
     * @param data   the piece of the message being sent.
     * @param isLast Whether the fragment being sent is the last piece of the message.
     */
    public Future<Frame> sendBinary(ByteBuffer data, boolean isLast) {
        byte[] bytes = Utils.getRemainingArray(data);
        return socket.stream(isLast, bytes, 0, bytes.length);
    }

    /**
     * Send a Ping message containing the given application data to the remote endpoint. The corresponding Pong message may be picked
     * up using the MessageHandler.Pong handler.
     *
     * @param data the data to be carried in the ping request.
     */
    public Future<Frame> sendPing(ByteBuffer data) {
        return socket.sendPing(Utils.getRemainingArray(data));
    }

    /**
     * Allows the developer to send an unsolicited Pong message containing the given application
     * data in order to serve as a unidirectional
     * heartbeat for the session.
     *
     * @param data the application data to be carried in the pong response.
     */
    public Future<Frame> sendPong(ByteBuffer data) {
        return socket.sendPong(Utils.getRemainingArray(data));
    }

    /**
     * Send a Close message.
     *
     * @param closeReason close reason.
     */
    public void close(CloseReason closeReason) {
        socket.close(closeReason.getCloseCode().getCode(), closeReason.getReasonPhrase());
    }

    /**
     * Sets the timeout for the writing operation.
     *
     * @param timeoutMs timeout in milliseconds.
     */
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

    TyrusWebSocket getSocket() {
        return socket;
    }
}
