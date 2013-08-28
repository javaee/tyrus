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

import java.util.EnumSet;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import javax.websocket.CloseReason;

import org.glassfish.tyrus.websockets.DataFrame;
import org.glassfish.tyrus.websockets.ProtocolHandler;
import org.glassfish.tyrus.websockets.WebSocket;
import org.glassfish.tyrus.websockets.WebSocketListener;
import org.glassfish.tyrus.websockets.frame.PingFrame;
import org.glassfish.tyrus.websockets.frame.PongFrame;

/**
 * Tyrus implementation of {@link org.glassfish.tyrus.websockets.WebSocket}.
 * <p/>
 * Instance of this class represents one bi-directional websocket connection.
 */
public class TyrusWebSocket implements WebSocket {
    private final Queue<WebSocketListener> listeners = new ConcurrentLinkedQueue<WebSocketListener>();
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
     * @param listeners       notifies registered endpoints about incoming events.
     */
    public TyrusWebSocket(final ProtocolHandler protocolHandler,
                          final WebSocketListener... listeners) {
        this.protocolHandler = protocolHandler;
        for (WebSocketListener listener : listeners) {
            add(listener);
        }
        protocolHandler.setWebSocket(this);
    }

    @Override
    public final boolean add(WebSocketListener listener) {
        return listeners.add(listener);
    }

    @Override
    public void setWriteTimeout(long timeoutMs) {
        protocolHandler.setWriteTimeout(timeoutMs);
    }

    @Override
    public boolean isConnected() {
        return connected.contains(state.get());
    }

    public void setClosed() {
        state.set(State.CLOSED);
    }

    @Override
    public void onClose(final CloseReason closeReason) {
        WebSocketListener listener;
        while ((listener = listeners.poll()) != null) {
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
    public void onConnect() {
        state.set(State.CONNECTED);

        for (WebSocketListener listener : listeners) {
            listener.onConnect(this);
        }

        onConnectLatch.countDown();
    }

    @Override
    public void onFragment(boolean last, byte[] fragment) {
        awaitOnConnect();
        for (WebSocketListener listener : listeners) {
            listener.onFragment(this, fragment, last);
        }
    }

    @Override
    public void onFragment(boolean last, String fragment) {
        awaitOnConnect();
        for (WebSocketListener listener : listeners) {
            listener.onFragment(this, fragment, last);
        }
    }

    @Override
    public void onMessage(byte[] data) {
        awaitOnConnect();
        for (WebSocketListener listener : listeners) {
            listener.onMessage(this, data);
        }
    }

    @Override
    public void onMessage(String text) {
        awaitOnConnect();
        for (WebSocketListener listener : listeners) {
            listener.onMessage(this, text);
        }
    }

    @Override
    public void onPing(DataFrame frame) {
        awaitOnConnect();
        for (WebSocketListener listener : listeners) {
            listener.onPing(this, frame.getBytes());
        }
    }

    @Override
    public void onPong(DataFrame frame) {
        awaitOnConnect();
        for (WebSocketListener listener : listeners) {
            listener.onPong(this, frame.getBytes());
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
    public Future<DataFrame> send(byte[] data) {
        if (isConnected()) {
            return protocolHandler.send(data);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    @Override
    public Future<DataFrame> send(String data) {
        if (isConnected()) {
            return protocolHandler.send(data);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    @Override
    public Future<DataFrame> sendRawFrame(byte[] data) {
        if (isConnected()) {
            return protocolHandler.sendRawFrame(data);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    @Override
    public Future<DataFrame> sendPing(byte[] data) {
        return send(new DataFrame(new PingFrame(), data));
    }

    @Override
    public Future<DataFrame> sendPong(byte[] data) {
        return send(new DataFrame(new PongFrame(), data));
    }

    // return boolean, check return value
    private void awaitOnConnect() {
        try {
            onConnectLatch.await();
        } catch (InterruptedException e) {
            // do nothing.
        }
    }

    private Future<DataFrame> send(DataFrame frame) {
        if (isConnected()) {
            return protocolHandler.send(frame);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    @Override
    public Future<DataFrame> stream(boolean last, String fragment) {
        if (isConnected()) {
            return protocolHandler.stream(last, fragment);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    @Override
    public Future<DataFrame> stream(boolean last, byte[] bytes, int off, int len) {
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
