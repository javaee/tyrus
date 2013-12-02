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

import javax.websocket.SendHandler;

import org.glassfish.tyrus.core.frame.BinaryFrame;
import org.glassfish.tyrus.core.frame.CloseFrame;
import org.glassfish.tyrus.core.frame.PingFrame;
import org.glassfish.tyrus.core.frame.PongFrame;
import org.glassfish.tyrus.core.frame.TextFrame;
import org.glassfish.tyrus.spi.UpgradeRequest;

/**
 * General WebSocket unit interface.
 *
 * @author Alexey Stashok
 */
public interface WebSocket {

    /**
     * Send a text frame to the remote endpoint.
     *
     * @param data data to be sent.
     * @return {@link Future} which could be used to control/check the sending completion state.
     */
    Future<Frame> send(String data);

    /**
     * Send a text frame to the remote endpoint.
     *
     * @param data    data to be sent.
     * @param handler {@link SendHandler#onResult(javax.websocket.SendResult)} will be called when sending is complete.
     */
    void send(String data, SendHandler handler);

    /**
     * Send a binary frame to the remote endpoint.
     *
     * @param data data to be sent.
     * @return {@link Future} which could be used to control/check the sending completion state.
     */
    Future<Frame> send(byte[] data);

    /**
     * Send a binary frame to the remote endpoint.
     *
     * @param data    data to be sent.
     * @param handler {@link SendHandler#onResult(javax.websocket.SendResult)} will be called when sending is complete.
     */
    void send(byte[] data, SendHandler handler);

    /**
     * Send a frame to the remote endpoint.
     *
     * @param data complete data frame.
     * @return {@link Future} which could be used to control/check the sending completion state.
     */
    Future<Frame> sendRawFrame(ByteBuffer data);

    /**
     * Sends a <code>ping</code> frame with the specified payload (if any).
     *
     * @param data optional payload.  Note that payload length is restricted
     *             to 125 bytes or less.
     * @return {@link Future} which could be used to control/check the sending completion state.
     */
    Future<Frame> sendPing(byte[] data);

    /**
     * Sends a <code>ping</code> frame with the specified payload (if any).
     * <p/>
     * It may seem odd to send a pong frame, however, RFC-6455 states:
     * "A Pong frame MAY be sent unsolicited.  This serves as a
     * unidirectional heartbeat.  A response to an unsolicited Pong frame is
     * not expected."
     *
     * @param data optional payload.  Note that payload length is restricted
     *             to 125 bytes or less.
     * @return {@link Future} which could be used to control/check the sending completion state.
     */
    Future<Frame> sendPong(byte[] data);

    /**
     * Sends a fragment of a complete message.
     *
     * @param last     boolean indicating if this message fragment is the last.
     * @param fragment the textual fragment to send.
     * @return {@link Future} which could be used to control/check the sending completion state.
     */
    Future<Frame> stream(boolean last, String fragment);

    /**
     * Sends a fragment of a complete message.
     *
     * @param last     boolean indicating if this message fragment is the last.
     * @param fragment the binary fragment to send.
     * @param off      the offset within the fragment to send.
     * @param len      the number of bytes of the fragment to send.
     * @return {@link Future} which could be used to control/check the sending completion state.
     */
    Future<Frame> stream(boolean last, byte[] fragment, int off, int len);

    /**
     * Closes this {@link WebSocket}.
     */
    void close();

    /**
     * Closes this {@link WebSocket} using the specified status code and
     * reason.
     *
     * @param code   the closing status code.
     * @param reason the reason, if any.
     */
    void close(int code, String reason);

    /**
     * Convenience method to determine if this {@link WebSocket} is connected.
     *
     * @return <code>true</code> if the {@link WebSocket} is connected, otherwise
     * <code>false</code>
     */
    boolean isConnected();

    /**
     * This callback will be invoked when the opening handshake between both
     * endpoints has been completed.
     *
     * @param upgradeRequest request associated with this socket.
     */
    void onConnect(UpgradeRequest upgradeRequest);

    /**
     * This callback will be invoked when a text message has been received.
     *
     * @param frame the text received from the remote end-point.
     */
    void onMessage(TextFrame frame);

    /**
     * This callback will be invoked when a binary message has been received.
     *
     * @param frame the binary data received from the remote end-point.
     */
    void onMessage(BinaryFrame frame);

    /**
     * This callback will be invoked when a fragmented textual message has
     * been received.
     *
     * @param last  flag indicating whether or not the payload received is the
     *              final fragment of a message.
     * @param frame the text received from the remote end-point.
     */
    void onFragment(boolean last, TextFrame frame);

    /**
     * This callback will be invoked when a fragmented binary message has
     * been received.
     *
     * @param last  flag indicating whether or not the payload received is the
     *              final fragment of a message.
     * @param frame the binary data received from the remote end-point.
     */
    void onFragment(boolean last, BinaryFrame frame);

    /**
     * This callback will be invoked when the remote end-point sent a closing
     * frame.
     *
     * @param frame the close frame from the remote end-point.
     */
    void onClose(CloseFrame frame);

    /**
     * This callback will be invoked when the remote end-point has sent a ping
     * frame.
     *
     * @param frame the ping frame from the remote end-point.
     */
    void onPing(PingFrame frame);

    /**
     * This callback will be invoked when the remote end-point has sent a pong
     * frame.
     *
     * @param frame the pong frame from the remote end-point.
     */
    void onPong(PongFrame frame);

    /**
     * Sets the timeout for the writing operation.
     *
     * @param timeoutMs timeout in milliseconds.
     */
    public abstract void setWriteTimeout(long timeoutMs);
}
