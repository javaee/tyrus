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
import java.util.EnumSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import javax.websocket.CloseReason;
import javax.websocket.SendHandler;

import org.glassfish.tyrus.core.frame.BinaryFrame;
import org.glassfish.tyrus.core.frame.CloseFrame;
import org.glassfish.tyrus.core.frame.PingFrame;
import org.glassfish.tyrus.core.frame.PongFrame;
import org.glassfish.tyrus.core.frame.TextFrame;
import org.glassfish.tyrus.spi.UpgradeRequest;

/**
 * Tyrus implementation of {@link WebSocket}.
 * <p/>
 * Instance of this class represents one bi-directional websocket connection.
 */
public class TyrusWebSocket implements WebSocket {
    private final WebSocketListener listener;
    private final ProtocolHandler protocolHandler;

    private final CountDownLatch onConnectLatch = new CountDownLatch(1);

    enum State {
        NEW, CONNECTED, CLOSING, CLOSED
    }

    private final EnumSet<State> connected = EnumSet.range(State.CONNECTED, State.CLOSING);
    private final AtomicReference<State> state = new AtomicReference<State>(State.NEW);

    /**
     * Create new instance, set {@link ProtocolHandler} and register {@link WebSocketListener WebSocketListeners}.
     *
     * @param protocolHandler used for writing data (sending).
     * @param listener        notifies registered endpoints about incoming events.
     */
    public TyrusWebSocket(final ProtocolHandler protocolHandler,
                          final WebSocketListener listener) {
        this.protocolHandler = protocolHandler;
        this.listener = listener;
        protocolHandler.setWebSocket(this);
    }

    @Override
    public void setWriteTimeout(long timeoutMs) {
        protocolHandler.setWriteTimeout(timeoutMs);
    }

    @Override
    public boolean isConnected() {
        return connected.contains(state.get());
    }

    @Override
    public void onClose(CloseFrame frame) {
        final CloseReason closeReason = frame.getCloseReason();

        if (listener != null) {
            listener.onClose(this, closeReason);
        }
        if (state.compareAndSet(State.CONNECTED, State.CLOSING)) {
            protocolHandler.close(closeReason.getCloseCode().getCode(), closeReason.getReasonPhrase());
        } else {
            state.set(State.CLOSED);
            protocolHandler.doClose();
        }
    }

    @Override
    public void onConnect(UpgradeRequest upgradeRequest) {
        state.set(State.CONNECTED);

        if (listener != null) {
            listener.onConnect(this, upgradeRequest);
        }

        onConnectLatch.countDown();
    }

    @Override
    public void onFragment(boolean last, BinaryFrame frame) {
        awaitOnConnect();
        if (listener != null) {
            listener.onFragment(this, frame.getPayloadData(), last);
        }
    }

    @Override
    public void onFragment(boolean last, TextFrame frame) {
        awaitOnConnect();
        if (listener != null) {
            listener.onFragment(this, frame.getTextPayload(), last);
        }
    }

    @Override
    public void onMessage(BinaryFrame frame) {
        awaitOnConnect();
        if (listener != null) {
            listener.onMessage(this, frame.getPayloadData());
        }
    }

    @Override
    public void onMessage(TextFrame frame) {
        awaitOnConnect();
        if (listener != null) {
            listener.onMessage(this, frame.getTextPayload());
        }
    }

    @Override
    public void onPing(PingFrame frame) {
        awaitOnConnect();
        if (listener != null) {
            listener.onPing(this, frame.getPayloadData());
        }
    }

    @Override
    public void onPong(PongFrame frame) {
        awaitOnConnect();
        if (listener != null) {
            listener.onPong(this, frame.getPayloadData());
        }
    }

    @Override
    public void close() {
        close(CloseReason.CloseCodes.NORMAL_CLOSURE.getCode(), null);
    }

    @Override
    public void close(int code, String reason) {
        if (state.compareAndSet(State.CONNECTED, State.CLOSING)) {
            protocolHandler.close(code, reason);
        }
    }

    @Override
    public Future<Frame> send(byte[] data) {
        if (isConnected()) {
            return protocolHandler.send(data);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    @Override
    public void send(byte[] data, SendHandler handler) {
        if (isConnected()) {
            protocolHandler.send(data, handler);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    @Override
    public Future<Frame> send(String data) {
        if (isConnected()) {
            return protocolHandler.send(data);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    @Override
    public void send(String data, SendHandler handler) {
        if (isConnected()) {
            protocolHandler.send(data, handler);
        } else {
            throw new RuntimeException("Socket is not connected");
        }
    }


    @Override
    public Future<Frame> sendRawFrame(ByteBuffer data) {
        if (isConnected()) {
            return protocolHandler.sendRawFrame(data);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    @Override
    public Future<Frame> sendPing(byte[] data) {
        return send(new PingFrame(data));
    }

    @Override
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

    private Future<Frame> send(Frame frame) {
        if (isConnected()) {
            return protocolHandler.send(frame);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    @Override
    public Future<Frame> stream(boolean last, String fragment) {
        if (isConnected()) {
            return protocolHandler.stream(last, fragment);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    @Override
    public Future<Frame> stream(boolean last, byte[] bytes, int off, int len) {
        if (isConnected()) {
            return protocolHandler.stream(last, bytes, off, len);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    ProtocolHandler getProtocolHandler() {
        return protocolHandler;
    }
}
