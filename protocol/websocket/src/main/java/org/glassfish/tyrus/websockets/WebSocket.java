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

package org.glassfish.tyrus.websockets;

import java.util.concurrent.Future;

import javax.websocket.CloseReason;

/**
 * General WebSocket unit interface.
 *
 * @author Alexey Stashok
 */
public interface WebSocket {

    /**
     * <p/>
     * Send a text frame to the remote end-point.
     * <p/>
     *
     * @return {@link Future} which could be used to control/check the sending completion state.
     */
    Future<DataFrame> send(String data);

    /**
     * <p>
     * Send a binary frame to the remote end-point.
     * </p>
     *
     * @return {@link Future} which could be used to control/check the sending completion state.
     */
    Future<DataFrame> send(byte[] data);

    /**
     * <p>
     * Send a frame to the remote endpoint.
     * </p>
     *
     * @return {@link Future} which could be used to control/check the sending completion state.
     */
    Future<DataFrame> sendRawFrame(byte[] data);

    /**
     * <p>
     * Sends a <code>ping</code> frame with the specified payload (if any).
     * </p>
     *
     * @param data optional payload.  Note that payload length is restricted
     *             to 125 bytes or less.
     * @return {@link Future} which could be used to control/check the sending completion state.
     * @since 2.1.9
     */
    Future<DataFrame> sendPing(byte[] data);

    /**
     * <p>
     * Sends a <code>ping</code> frame with the specified payload (if any).
     * </p>
     * <p/>
     * <p>It may seem odd to send a pong frame, however, RFC-6455 states:</p>
     * <p/>
     * <p>
     * "A Pong frame MAY be sent unsolicited.  This serves as a
     * unidirectional heartbeat.  A response to an unsolicited Pong frame is
     * not expected."
     * </p>
     *
     * @param data optional payload.  Note that payload length is restricted
     *             to 125 bytes or less.
     * @return {@link Future} which could be used to control/check the sending completion state.
     * @since 2.1.9
     */
    Future<DataFrame> sendPong(byte[] data);

    /**
     * <p>
     * Sends a fragment of a complete message.
     * </p>
     *
     * @param last     boolean indicating if this message fragment is the last.
     * @param fragment the textual fragment to send.
     * @return {@link Future} which could be used to control/check the sending completion state.
     */
    Future<DataFrame> stream(boolean last, String fragment);

    /**
     * <p>
     * Sends a fragment of a complete message.
     * </p>
     *
     * @param last     boolean indicating if this message fragment is the last.
     * @param fragment the binary fragment to send.
     * @param off      the offset within the fragment to send.
     * @param len      the number of bytes of the fragment to send.
     * @return {@link Future} which could be used to control/check the sending completion state.
     */
    Future<DataFrame> stream(boolean last, byte[] fragment, int off, int len);

    /**
     * <p>
     * Closes this {@link WebSocket}.
     * </p>
     */
    void close();

    /**
     * <p>
     * Closes this {@link WebSocket} using the specified status code and
     * reason.
     * </p>
     *
     * @param code   the closing status code.
     * @param reason the reason, if any.
     */
    void close(int code, String reason);

    /**
     * <p>
     * Convenience method to determine if this {@link WebSocket} is connected.
     * </p>
     *
     * @return <code>true</code> if the {@link WebSocket} is connected, otherwise
     *         <code>false</code>
     */
    boolean isConnected();

    /**
     * <p>
     * This callback will be invoked when the opening handshake between both
     * endpoints has been completed.
     * </p>
     */
    void onConnect();

    /**
     * <p>
     * This callback will be invoked when a text message has been received.
     * </p>
     *
     * @param text the text received from the remote end-point.
     */
    void onMessage(String text);

    /**
     * <p>
     * This callback will be invoked when a binary message has been received.
     * </p>
     *
     * @param data the binary data received from the remote end-point.
     */
    void onMessage(byte[] data);

    /**
     * <p>
     * This callback will be invoked when a fragmented textual message has
     * been received.
     * </p>
     *
     * @param last    flag indicating whether or not the payload received is the
     *                final fragment of a message.
     * @param payload the text received from the remote end-point.
     */
    void onFragment(boolean last, String payload);

    /**
     * <p>
     * This callback will be invoked when a fragmented binary message has
     * been received.
     * </p>
     *
     * @param last    flag indicating whether or not the payload received is the
     *                final fragment of a message.
     * @param payload the binary data received from the remote end-point.
     */
    void onFragment(boolean last, byte[] payload);

    /**
     * <p>
     * This callback will be invoked when the remote end-point sent a closing
     * frame.
     * </p>
     *
     * @param closeReason the close reason from the remote end-point.
     * @see DataFrame
     */
    void onClose(CloseReason closeReason);

    /**
     * <p>
     * This callback will be invoked when the remote end-point has sent a ping
     * frame.
     * </p>
     *
     * @param frame the ping frame from the remote end-point.
     * @see DataFrame
     */
    void onPing(DataFrame frame);

    /**
     * <p>
     * This callback will be invoked when the remote end-point has sent a pong
     * frame.
     * </p>
     *
     * @param frame the pong frame from the remote end-point.
     * @see DataFrame
     */
    void onPong(DataFrame frame);

    /**
     * Adds a {@link WebSocketListener} to be notified of events of interest.
     *
     * @param listener the {@link WebSocketListener} to add.
     * @return <code>true</code> if the listener was added, otherwise
     *         <code>false</code>
     * @see WebSocketListener
     */
    boolean add(WebSocketListener listener);

    /**
     * Sets the timeout for the writing operation.
     *
     * @param timeoutMs timeout in milliseconds.
     */
    public abstract void setWriteTimeout(long timeoutMs);
}
