/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.Session;
import javax.websocket.WebSocketClient;
import javax.websocket.WebSocketMessage;
import javax.websocket.WebSocketOpen;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;
import org.junit.Assert;
import org.junit.Test;


/**
 * Tests the client with the annotated version of the
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class AnnotatedClientTest {

    private static String receivedMessage;

    private static String receivedTestMessage;

    private static CountDownLatch messageLatch;

    @Test
    public void testAnnotatedInstance() {
        Server server = new Server(TestBean.class);
        server.start();
        messageLatch = new CountDownLatch(1);

        try {
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new ClientTestBean(true), new URI("ws://localhost:8025/websockets/tests/echo"));
            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertEquals("hello", receivedMessage);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @Test
    public void testAnnotatedInstanceWithDecoding() {
        Server server = new Server(TestBean.class);
        server.start();
        messageLatch = new CountDownLatch(1);

        try {
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new ClientTestBean(false), new URI("ws://localhost:8025/websockets/tests/echo"));
            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertEquals("testHello", receivedTestMessage);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @Test
    public void testAnnotatedClass() {
        Server server = new Server(TestBean.class);
        server.start();
        messageLatch = new CountDownLatch(1);
        receivedMessage = null;

        try {
            ClientManager client = ClientManager.createClient();
            client.connectToServer(SimpleClientTestBean.class, new URI("ws://localhost:8025/websockets/tests/echo"));
            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertEquals("hello", receivedMessage);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    /**
     * Testing the basic annotations.
     *
     * @author Stepan Kopriva (stepan.kopriva at oracle.com)
     */
    @WebSocketClient(decoders = {TestDecoder.class})
    public class ClientTestBean {

        private static final String SENT_MESSAGE = "hello";
        private static final String SENT_TEST_MESSAGE = "testHello";
        private boolean messageType;

        public ClientTestBean(boolean messageType) {
            this.messageType = messageType;
        }

        @WebSocketOpen
        public void onOpen(Session p) {
            try {
                if (messageType) {
                    p.getRemote().sendString(SENT_MESSAGE);
                } else {
                    p.getRemote().sendString(TestMessage.PREFIX + SENT_TEST_MESSAGE);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @WebSocketMessage
        public void onMessage(String message) {
            receivedMessage = message;
            messageLatch.countDown();
        }

        @WebSocketMessage
        public void onTestMesage(TestMessage tm) {
            receivedTestMessage = tm.getData();
            System.out.println("Received: " + receivedTestMessage);
            messageLatch.countDown();
        }
    }

    @WebSocketClient
    public static class SimpleClientTestBean {
        private static final String SENT_MESSAGE = "hello";

        public SimpleClientTestBean() {
        }

        @WebSocketOpen
        public void onOpen(Session p) {
            try {
                p.getRemote().sendString(SENT_MESSAGE);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @WebSocketMessage
        public void onMessage(String message) {
            receivedMessage = message;
            messageLatch.countDown();
        }
    }
}
