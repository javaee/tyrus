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

import java.util.concurrent.Future;

import org.glassfish.tyrus.websockets.ClosingDataFrame;
import org.glassfish.tyrus.websockets.DataFrame;
import org.glassfish.tyrus.websockets.WebSocket;
import org.glassfish.tyrus.websockets.WebSocketListener;

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
    public GrizzlyFuture<DataFrame> send(String message) {
        listener.onMessage(this, message);
        return null;
    }

    @Override
    public Future<DataFrame> send(byte[] data) {
        return null;
    }

    @Override
    public GrizzlyFuture<DataFrame> sendPing(byte[] bytes) {
        return null;
    }

    @Override
    public GrizzlyFuture<DataFrame> sendPong(byte[] bytes) {
        return null;
    }

    @Override
    public Future<DataFrame> stream(boolean last, String fragment) {
        return null;
    }


    @Override
    public GrizzlyFuture<DataFrame> stream(boolean b, byte[] bytes, int i, int i1) {
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
        return false;
    }

    @Override
    public void onConnect() {
    }

    @Override
    public void onMessage(String s) {
    }

    @Override
    public void onMessage(byte[] bytes) {
    }

    @Override
    public void onFragment(boolean b, String s) {
    }

    @Override
    public void onFragment(boolean b, byte[] bytes) {
    }

    @Override
    public void onClose(ClosingDataFrame dataFrame) {
    }

    @Override
    public void onPing(DataFrame dataFrame) {
    }

    @Override
    public void onPong(DataFrame dataFrame) {
    }

    @Override
    public boolean add(WebSocketListener webSocketListener) {
        return false;
    }

    @Override
    public void setWriteTimeout(long timeoutMs) {
    }
}
