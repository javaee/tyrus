/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;

import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class SessionTimeoutTest {

    @ServerEndpoint(value = "/timeout")
    public static class SessionTimeoutEndpoint {
        private static final CountDownLatch latch = new CountDownLatch(1);
        private static final CountDownLatch onOpenLatch = new CountDownLatch(1);

        private static boolean onClosedCalled = false;
        private long timeoutSetTime;
        private static final long TIMEOUT = 300;

        @OnOpen
        public void onOpen(Session session) {
            session.setMaxIdleTimeout(TIMEOUT);
            timeoutSetTime = System.currentTimeMillis();
            onOpenLatch.countDown();
        }

        @OnMessage
        public void onMessage(String message, Session session){

        }

        @OnClose
        public void onClose(Session session) {
            assertTrue(System.currentTimeMillis() - timeoutSetTime - TIMEOUT < 20);
            onClosedCalled = true;
            latch.countDown();
        }
    }

    @Test
    public void testSessionTimeout() {
        Server server = new Server(SessionTimeoutEndpoint.class);

        try {
            server.start();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager.createClient().connectToServer(new TestEndpointAdapter() {
                @Override
                public void onMessage(String message) {
                }

                @Override
                public void onOpen(Session session) {
                }

                @Override
                public EndpointConfig getEndpointConfig() {
                    return cec;
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/timeout"));

            SessionTimeoutEndpoint.onOpenLatch.await(5, TimeUnit.SECONDS);
            assertEquals(0, SessionTimeoutEndpoint.onOpenLatch.getCount());

            SessionTimeoutEndpoint.latch.await(3, TimeUnit.SECONDS);
            assertTrue(SessionTimeoutEndpoint.onClosedCalled);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @ServerEndpoint(value = "/timeout")
    public static class SessionNoTimeoutEndpoint {
        private static final CountDownLatch latch = new CountDownLatch(1);
        private static final CountDownLatch onOpenLatch = new CountDownLatch(1);
        private static boolean onClosedCalled = false;
        private static final long TIMEOUT = 1000;

        @OnOpen
        public void onOpen(Session session) {
            session.setMaxIdleTimeout(TIMEOUT);
            onOpenLatch.countDown();
        }

        @OnMessage
        public void onMessage(String message, Session session){

        }

        @OnClose
        public void onClose(Session session, CloseReason closeReason) {
            onClosedCalled = true;
            latch.countDown();
        }
    }

    @Test
    public void testSessionNoTimeoutRaised() {
        Server server = new Server(SessionNoTimeoutEndpoint.class);

        try {
            server.start();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager.createClient().connectToServer(new TestEndpointAdapter() {
                @Override
                public void onMessage(String message) {

                }

                @Override
                public void onOpen(Session session) {
                    try {
                        session.getBasicRemote().sendText("Nothing");
                        Thread.sleep(250);
                        session.getBasicRemote().sendText("Nothing");
                        Thread.sleep(250);
                        session.getBasicRemote().sendText("Nothing");
                        Thread.sleep(250);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public EndpointConfig getEndpointConfig() {
                    return cec;
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/timeout"));

            SessionNoTimeoutEndpoint.onOpenLatch.await(5, TimeUnit.SECONDS);
            assertEquals(0, SessionNoTimeoutEndpoint.onOpenLatch.getCount());

            SessionNoTimeoutEndpoint.latch.await(500, TimeUnit.MILLISECONDS);
            assertEquals(1, SessionNoTimeoutEndpoint.latch.getCount());
            assertFalse(SessionNoTimeoutEndpoint.onClosedCalled);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @ServerEndpoint(value = "/timeout")
    public static class SessionTimeoutChangedEndpoint {
        private static CountDownLatch latch = new CountDownLatch(1);
        private long timeoutSetTime;
        private static final long TIMEOUT1 = 300;
        private static final long TIMEOUT2 = 700;
        private static boolean first = true;

        @OnOpen
        public void onOpen(Session session) {
            session.setMaxIdleTimeout(TIMEOUT1);
            timeoutSetTime = System.currentTimeMillis();
        }

        @OnMessage
        public String message(String message, Session session) {
            if (first) {
                session.setMaxIdleTimeout(TIMEOUT2);
                timeoutSetTime = System.currentTimeMillis();
                first = false;
            }
            return "message";
        }

        @OnClose
        public void onClose(Session session) {
            assertTrue(System.currentTimeMillis() - timeoutSetTime - TIMEOUT2 < 20);
            latch.countDown();
        }
    }

    @Test
    @Ignore("TODO: rewrite test; issues on linux/win8")
    public void testSessionTimeoutChanged() {
        Server server = new Server(SessionTimeoutChangedEndpoint.class);

        try {
            server.start();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager.createClient().connectToServer(new TestEndpointAdapter() {
                @Override
                public void onMessage(String message) {

                }

                @Override
                public void onOpen(Session session) {
                    try {
                        session.getBasicRemote().sendText("Nothing");
                        Thread.sleep(200);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public EndpointConfig getEndpointConfig() {
                    return cec;
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/timeout"));

            SessionNoTimeoutEndpoint.latch.await(3, TimeUnit.SECONDS);
            assertTrue(SessionNoTimeoutEndpoint.onClosedCalled);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @ServerEndpoint(value = "/timeout")
    public static class SessionClientTimeoutEndpoint {
        public static boolean clientOnCloseCalled = false;

        @OnOpen
        public void onOpen(Session session) {
        }

        @OnMessage
        public void onMessage(String message, Session session){

        }

        @OnClose
        public void onClose(Session session) {
        }
    }

    @Test
    public void testSessionClientTimeoutSession() {
        Server server = new Server(SessionClientTimeoutEndpoint.class);
        final CountDownLatch onCloseLatch = new CountDownLatch(1);
        SessionClientTimeoutEndpoint.clientOnCloseCalled = false;

        try {
            server.start();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            Session session = ClientManager.createClient().connectToServer(new TestEndpointAdapter() {
                @Override
                public void onMessage(String message) {
                }

                @Override
                public void onOpen(Session session) {
                }

                @Override
                public EndpointConfig getEndpointConfig() {
                    return cec;
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    SessionClientTimeoutEndpoint.clientOnCloseCalled = true;
                    onCloseLatch.countDown();
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/timeout"));
            session.setMaxIdleTimeout(200);

            onCloseLatch.await(2, TimeUnit.SECONDS);
            assertTrue(SessionClientTimeoutEndpoint.clientOnCloseCalled);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @Test
    public void testSessionClientTimeoutContainer() {
        Server server = new Server(SessionClientTimeoutEndpoint.class);
        final CountDownLatch onCloseLatch = new CountDownLatch(1);
        SessionClientTimeoutEndpoint.clientOnCloseCalled = false;

        try {
            server.start();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            final ClientManager client = ClientManager.createClient();
            client.setDefaultMaxSessionIdleTimeout(200);
            Session session = client.connectToServer(new TestEndpointAdapter() {
                @Override
                public void onMessage(String message) {
                }

                @Override
                public void onOpen(Session session) {
                }

                @Override
                public EndpointConfig getEndpointConfig() {
                    return cec;
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    SessionClientTimeoutEndpoint.clientOnCloseCalled = true;
                    onCloseLatch.countDown();
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/timeout"));

            onCloseLatch.await(2, TimeUnit.SECONDS);
            assertTrue(SessionClientTimeoutEndpoint.clientOnCloseCalled);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }


}