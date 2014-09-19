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
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import javax.websocket.CloseReason;
import javax.websocket.Extension;
import javax.websocket.SendHandler;

import org.glassfish.tyrus.core.frame.BinaryFrame;
import org.glassfish.tyrus.core.frame.CloseFrame;
import org.glassfish.tyrus.core.frame.Frame;
import org.glassfish.tyrus.core.frame.PingFrame;
import org.glassfish.tyrus.core.frame.PongFrame;
import org.glassfish.tyrus.core.frame.TextFrame;
import org.glassfish.tyrus.core.frame.TyrusFrame;
import org.glassfish.tyrus.core.l10n.LocalizationMessages;
import org.glassfish.tyrus.core.monitoring.MessageEventListener;
import org.glassfish.tyrus.spi.UpgradeRequest;

/**
 * Tyrus representation of web socket connection.
 * <p/>
 * Instance of this class represents one bi-directional websocket connection.
 */
public class TyrusWebSocket {

    private final TyrusEndpointWrapper endpointWrapper;
    private final ProtocolHandler protocolHandler;
    private final CountDownLatch onConnectLatch = new CountDownLatch(1);
    private final EnumSet<State> connected = EnumSet.range(State.CONNECTED, State.CLOSING);
    //TODO try refactoring to make immutable.
    private final AtomicReference<State> state = new AtomicReference<State>(State.NEW);
    private volatile MessageEventListener messageEventListener = MessageEventListener.NO_OP;

    /**
     * Create new instance, set {@link ProtocolHandler} and register {@link TyrusEndpointWrapper}.
     *
     * @param protocolHandler used for writing data (sending).
     * @param endpointWrapper notifies registered endpoints about incoming events.
     */
    public TyrusWebSocket(final ProtocolHandler protocolHandler,
                          final TyrusEndpointWrapper endpointWrapper) {
        this.protocolHandler = protocolHandler;
        this.endpointWrapper = endpointWrapper;
        protocolHandler.setWebSocket(this);
    }

    /**
     * Sets the timeout for the writing operation.
     *
     * @param timeoutMs timeout in milliseconds.
     */
    public void setWriteTimeout(long timeoutMs) {
        // do nothing.
    }

    /**
     * Convenience method to determine if this {@link TyrusWebSocket} instance is connected.
     *
     * @return {@code true} if the {@link TyrusWebSocket} is connected, {@code false} otherwise.
     */
    public boolean isConnected() {
        return connected.contains(state.get());
    }

    /**
     * This callback will be invoked when the remote end-point sent a closing
     * frame.
     *
     * @param frame the close frame from the remote end-point.
     */
    public synchronized void onClose(CloseFrame frame) {
        final CloseReason closeReason = frame.getCloseReason();

        if (endpointWrapper != null) {
            endpointWrapper.onClose(this, closeReason);
        }
        if (state.compareAndSet(State.CONNECTED, State.CLOSING)) {
            protocolHandler.close(closeReason.getCloseCode().getCode(), closeReason.getReasonPhrase());
        } else {
            state.set(State.CLOSED);
            protocolHandler.doClose();
        }
    }

    /**
     * This callback will be invoked when the opening handshake between both
     * endpoints has been completed.
     *
     * @param upgradeRequest request associated with this socket.
     */
    public void onConnect(UpgradeRequest upgradeRequest, String subProtocol, List<Extension> extensions, String connectionId, DebugContext debugContext) {
        state.set(State.CONNECTED);

        if (endpointWrapper != null) {
            endpointWrapper.onConnect(this, upgradeRequest, subProtocol, extensions, connectionId, debugContext);
        }

        onConnectLatch.countDown();
    }

    /**
     * This callback will be invoked when a fragmented binary message has
     * been received.
     *
     * @param frame the binary data received from the remote end-point.
     * @param last  flag indicating whether or not the payload received is the
     *              final fragment of a message.
     */
    public void onFragment(BinaryFrame frame, boolean last) {
        awaitOnConnect();
        if (endpointWrapper != null) {
            endpointWrapper.onPartialMessage(this, ByteBuffer.wrap(frame.getPayloadData()), last);
            messageEventListener.onFrameReceived(frame.getFrameType(), frame.getPayloadLength());
        }
    }

    /**
     * This callback will be invoked when a fragmented textual message has
     * been received.
     *
     * @param frame the text received from the remote end-point.
     * @param last  flag indicating whether or not the payload received is the
     *              final fragment of a message.
     */
    public void onFragment(TextFrame frame, boolean last) {
        awaitOnConnect();
        if (endpointWrapper != null) {
            endpointWrapper.onPartialMessage(this, frame.getTextPayload(), last);
            messageEventListener.onFrameReceived(frame.getFrameType(), frame.getPayloadLength());
        }
    }

    /**
     * This callback will be invoked when a binary message has been received.
     *
     * @param frame the binary data received from the remote end-point.
     */
    public void onMessage(BinaryFrame frame) {
        awaitOnConnect();
        if (endpointWrapper != null) {
            endpointWrapper.onMessage(this, ByteBuffer.wrap(frame.getPayloadData()));
            messageEventListener.onFrameReceived(frame.getFrameType(), frame.getPayloadLength());
        }
    }

    /**
     * This callback will be invoked when a text message has been received.
     *
     * @param frame the text received from the remote end-point.
     */
    public void onMessage(TextFrame frame) {
        awaitOnConnect();
        if (endpointWrapper != null) {
            endpointWrapper.onMessage(this, frame.getTextPayload());
            messageEventListener.onFrameReceived(frame.getFrameType(), frame.getPayloadLength());
        }
    }

    /**
     * This callback will be invoked when the remote end-point has sent a ping
     * frame.
     *
     * @param frame the ping frame from the remote end-point.
     */
    public void onPing(PingFrame frame) {
        awaitOnConnect();
        if (endpointWrapper != null) {
            endpointWrapper.onPing(this, ByteBuffer.wrap(frame.getPayloadData()));
            messageEventListener.onFrameReceived(frame.getFrameType(), frame.getPayloadLength());
        }
    }

    /**
     * This callback will be invoked when the remote end-point has sent a pong
     * frame.
     *
     * @param frame the pong frame from the remote end-point.
     */
    public void onPong(PongFrame frame) {
        awaitOnConnect();
        if (endpointWrapper != null) {
            endpointWrapper.onPong(this, ByteBuffer.wrap(frame.getPayloadData()));
            messageEventListener.onFrameReceived(frame.getFrameType(), frame.getPayloadLength());
        }
    }

    /**
     * Closes this {@link TyrusWebSocket}.
     */
    public void close() {
        close(CloseReason.CloseCodes.NORMAL_CLOSURE.getCode(), null);
    }

    /**
     * Closes this {@link TyrusWebSocket} using the specified status code and
     * reason.
     *
     * @param code   the closing status code.
     * @param reason the reason, if any.
     */
    public void close(int code, String reason) {
        if (state.compareAndSet(State.CONNECTED, State.CLOSING)) {
            protocolHandler.close(code, reason);
        }
    }

    /**
     * Closes this {@link TyrusWebSocket} using the {@link javax.websocket.CloseReason}.
     *
     * @param closeReason the close reason.
     */
    public void close(CloseReason closeReason) {
        close(closeReason.getCloseCode().getCode(), closeReason.getReasonPhrase());
    }

    /**
     * Send a binary frame to the remote endpoint.
     *
     * @param data data to be sent.
     * @return {@link Future} which could be used to control/check the sending completion state.
     */
    public Future<Frame> sendBinary(byte[] data) {
        checkConnectedState();
        return protocolHandler.send(data);
    }

    /**
     * Send a binary frame to the remote endpoint.
     *
     * @param data    data to be sent.
     * @param handler {@link SendHandler#onResult(javax.websocket.SendResult)} will be called when sending is complete.
     */
    public void sendBinary(byte[] data, SendHandler handler) {
        checkConnectedState();
        protocolHandler.send(data, handler);
    }

    /**
     * Send a text frame to the remote endpoint.
     *
     * @param data data to be sent.
     * @return {@link Future} which could be used to control/check the sending completion state.
     */
    public Future<Frame> sendText(String data) {
        checkConnectedState();
        return protocolHandler.send(data);
    }

    /**
     * Send a text frame to the remote endpoint.
     *
     * @param data    data to be sent.
     * @param handler {@link SendHandler#onResult(javax.websocket.SendResult)} will be called when sending is complete.
     */
    public void sendText(String data, SendHandler handler) {
        checkConnectedState();
        protocolHandler.send(data, handler);
    }

    /**
     * Send a frame to the remote endpoint.
     *
     * @param data complete data frame.
     * @return {@link Future} which could be used to control/check the sending completion state.
     */
    public Future<Frame> sendRawFrame(ByteBuffer data) {
        checkConnectedState();
        return protocolHandler.sendRawFrame(data);
    }

    /**
     * Sends a <code>ping</code> frame with the specified payload (if any).
     *
     * @param data optional payload.  Note that payload length is restricted
     *             to 125 bytes or less.
     * @return {@link Future} which could be used to control/check the sending completion state.
     */
    public Future<Frame> sendPing(byte[] data) {
        return send(new PingFrame(data));
    }

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
    public Future<Frame> sendPong(byte[] data) {
        return send(new PongFrame(data));
    }

    // return boolean, check return value
    private void awaitOnConnect() {
        try {
            onConnectLatch.await();
        } catch (InterruptedException e) {
            // do nothing.
        }
    }

    private Future<Frame> send(TyrusFrame frame) {
        checkConnectedState();
        return protocolHandler.send(frame);
    }

    /**
     * Sends a fragment of a complete message.
     *
     * @param fragment the textual fragment to send.
     * @param last     boolean indicating if this message fragment is the last.
     * @return {@link Future} which could be used to control/check the sending completion state.
     */
    public Future<Frame> sendText(String fragment, boolean last) {
        checkConnectedState();
        return protocolHandler.stream(last, fragment);
    }

    /**
     * Sends a fragment of a complete message.
     *
     * @param bytes the binary fragment to send.
     * @param last  boolean indicating if this message fragment is the last.
     * @return {@link Future} which could be used to control/check the sending completion state.
     */
    public Future<Frame> sendBinary(byte[] bytes, boolean last) {
        return sendBinary(bytes, 0, bytes.length, last);
    }

    /**
     * Sends a fragment of a complete message.
     *
     * @param bytes the binary fragment to send.
     * @param off   the offset within the fragment to send.
     * @param len   the number of bytes of the fragment to send.
     * @param last  boolean indicating if this message fragment is the last.
     * @return {@link Future} which could be used to control/check the sending completion state.
     */
    public Future<Frame> sendBinary(byte[] bytes, int off, int len, boolean last) {
        checkConnectedState();
        return protocolHandler.stream(last, bytes, off, len);
    }

    ProtocolHandler getProtocolHandler() {
        return protocolHandler;
    }

    /**
     * Set message event listener.
     *
     * @param messageEventListener message event listener.
     */
    void setMessageEventListener(MessageEventListener messageEventListener) {
        this.messageEventListener = messageEventListener;
        protocolHandler.setMessageEventListener(messageEventListener);
    }

    /**
     * Get message event listener.
     *
     * @return message event listener.
     */
    MessageEventListener getMessageEventListener() {
        return messageEventListener;
    }

    private void checkConnectedState() {
        if (!isConnected()) {
            throw new RuntimeException(LocalizationMessages.SOCKET_NOT_CONNECTED());
        }
    }

    private enum State {
        NEW, CONNECTED, CLOSING, CLOSED
    }
}
