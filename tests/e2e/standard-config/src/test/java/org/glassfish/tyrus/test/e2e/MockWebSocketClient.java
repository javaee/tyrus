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

package org.glassfish.tyrus.test.e2e;

import java.nio.ByteBuffer;
import java.util.concurrent.Future;

import javax.websocket.SendHandler;
import javax.websocket.SendResult;

import org.glassfish.tyrus.core.Frame;
import org.glassfish.tyrus.core.WebSocket;
import org.glassfish.tyrus.core.WebSocketListener;
import org.glassfish.tyrus.core.frame.BinaryFrame;
import org.glassfish.tyrus.core.frame.CloseFrame;
import org.glassfish.tyrus.core.frame.PingFrame;
import org.glassfish.tyrus.core.frame.PongFrame;
import org.glassfish.tyrus.core.frame.TextFrame;
import org.glassfish.tyrus.spi.UpgradeRequest;

import org.glassfish.grizzly.GrizzlyFuture;

/**
 * Mock client that can be used to confirm that the test is written correctly. When writing a test and not sure if the
 * failures happen due to issues in the test or issues in the web socket runtime, you can simply replace usages of
 * ClientManager in the test by this class and see if the test passes.
 *
 * @author Martin Matula (martin.matula at oracle.com)
 */
class MockWebSocketClient implements WebSocket {
    private final WebSocketListener listener;

    public MockWebSocketClient(String url, WebSocketListener listener) {
        this.listener = listener;
    }

    @Override
    public GrizzlyFuture<Frame> send(String message) {
        listener.onMessage(this, message);
        return null;
    }

    @Override
    public void send(String data, SendHandler handler) {
        listener.onMessage(this, data);
        handler.onResult(new SendResult());
    }


    @Override
    public Future<Frame> send(byte[] data) {
        return null;
    }

    @Override
    public void send(byte[] data, SendHandler handler) {
    }

    @Override
    public Future<Frame> sendRawFrame(ByteBuffer data) {
        return null;
    }

    @Override
    public GrizzlyFuture<Frame> sendPing(byte[] bytes) {
        return null;
    }

    @Override
    public GrizzlyFuture<Frame> sendPong(byte[] bytes) {
        return null;
    }

    @Override
    public Future<Frame> stream(boolean last, String fragment) {
        return null;
    }


    @Override
    public GrizzlyFuture<Frame> stream(boolean b, byte[] bytes, int i, int i1) {
        return null;
    }

    @Override
    public void close() {
    }

    @Override
    public void close(int i, String s) {
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public void onConnect(UpgradeRequest upgradeRequest) {
    }

    @Override
    public void onMessage(TextFrame frame) {
    }

    @Override
    public void onMessage(BinaryFrame frame) {
    }

    @Override
    public void onFragment(boolean last, TextFrame frame) {
    }

    @Override
    public void onFragment(boolean last, BinaryFrame frame) {
    }

    @Override
    public void onClose(CloseFrame frame) {
    }

    @Override
    public void onPing(PingFrame frame) {
    }

    @Override
    public void onPong(PongFrame frame) {
    }

    @Override
    public void setWriteTimeout(long timeoutMs) {
    }
}
