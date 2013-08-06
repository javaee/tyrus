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

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.testing.TestUtilities;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;

import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class SessionTimeoutTest extends TestUtilities {

    @ServerEndpoint(value = "/timeout3")
    public static class SessionTimeoutEndpoint {
        private static boolean onClosedCalled = false;
        private long timeoutSetTime;
        private static final long TIMEOUT = 300;

        @OnOpen
        public void onOpen(Session session) {
            session.setMaxIdleTimeout(TIMEOUT);
            timeoutSetTime = System.currentTimeMillis();
        }

        @OnMessage
        public void onMessage(String message, Session session){

        }

        @OnClose
        public void onClose(Session session) {
            if(System.currentTimeMillis() - timeoutSetTime - TIMEOUT < 20){
                onClosedCalled = true;
            }
        }
    }

    @Test
    public void testSessionTimeout() throws DeploymentException {
        Server server = startServer(SessionTimeoutEndpoint.class, ServiceEndpoint.class);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            final CountDownLatch latch = new CountDownLatch(1);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {

                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    latch.countDown();
                }
            }, cec, getURI(SessionTimeoutEndpoint.class));

            latch.await(2, TimeUnit.SECONDS);

            final Session serviceSession = client.connectToServer(MyServiceClientEndpoint.class, getURI(ServiceEndpoint.class));
            MyServiceClientEndpoint.latch = new CountDownLatch(1);
            MyServiceClientEndpoint.receivedMessage = null;
            serviceSession.getBasicRemote().sendText("SessionTimeoutEndpoint");
            MyServiceClientEndpoint.latch.await(2, TimeUnit.SECONDS);
            assertEquals(0, MyServiceClientEndpoint.latch.getCount());
            assertEquals(POSITIVE, MyServiceClientEndpoint.receivedMessage);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint(value = "/servicesessiontimeout")
    public static class ServiceEndpoint {

        @OnMessage
        public String onMessage(String message) {
            if (message.equals("SessionTimeoutEndpoint")){
               if(SessionTimeoutEndpoint.onClosedCalled){
                   return POSITIVE;
               }
            } else if(message.equals("SessionNoTimeoutEndpoint")){
                if(SessionNoTimeoutEndpoint.onClosedCalled = false){
                    return POSITIVE;
                }
            }

            return NEGATIVE;
        }
    }

    @ServerEndpoint(value = "/timeout2")
    public static class SessionNoTimeoutEndpoint {
        private static boolean onClosedCalled = false;
        private static final long TIMEOUT = 400;
        private AtomicInteger counter = new AtomicInteger(0);

        @OnOpen
        public void onOpen(Session session) {
            session.setMaxIdleTimeout(TIMEOUT);
        }

        @OnMessage
        public void onMessage(String message, Session session){
            System.out.println("Message received: "+message);
            if(counter.incrementAndGet() == 3){
                try {
                    if(!onClosedCalled){
                        session.getBasicRemote().sendText(POSITIVE);
                    } else{
                        session.getBasicRemote().sendText(NEGATIVE);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        @OnClose
        public void onClose(Session session, CloseReason closeReason) {
            System.out.println("###################### onClose called.");
            onClosedCalled = true;
        }
    }

    @Test
    public void testSessionNoTimeoutRaised() throws DeploymentException {
        Server server = startServer(SessionNoTimeoutEndpoint.class, ServiceEndpoint.class);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            final CountDownLatch latch = new CountDownLatch(1);

            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            System.out.println("Client message received");
                            assertEquals(POSITIVE, message);
                            latch.countDown();
                        }
                    });

                    try {
                        session.getBasicRemote().sendText("Nothing");
                        Thread.sleep(250);
                        session.getBasicRemote().sendText("Nothing");
                        Thread.sleep(250);
                        session.getBasicRemote().sendText("Nothing");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, cec, getURI(SessionNoTimeoutEndpoint.class));
            latch.await(2, TimeUnit.SECONDS);
            assertEquals(0, latch.getCount());

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint(value = "/timeout4")
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
    public void testSessionTimeoutChanged() throws DeploymentException {
        Server server = startServer(SessionTimeoutChangedEndpoint.class);

        try {
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
            }, cec, getURI(SessionTimeoutChangedEndpoint.class));

//            SessionNoTimeoutEndpoint.latch.await(3, TimeUnit.SECONDS);
            assertTrue(SessionNoTimeoutEndpoint.onClosedCalled);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint(value = "/timeout1")
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
    public void testSessionClientTimeoutSession() throws DeploymentException {
        Server server = startServer(SessionClientTimeoutEndpoint.class);
        final CountDownLatch onCloseLatch = new CountDownLatch(1);
        SessionClientTimeoutEndpoint.clientOnCloseCalled = false;

        try {
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
            }, cec, getURI(SessionClientTimeoutEndpoint.class));
            session.setMaxIdleTimeout(200);

            onCloseLatch.await(2, TimeUnit.SECONDS);
            assertTrue(SessionClientTimeoutEndpoint.clientOnCloseCalled);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testSessionClientTimeoutContainer() throws DeploymentException {
        Server server = startServer(SessionClientTimeoutEndpoint.class);
        final CountDownLatch onCloseLatch = new CountDownLatch(1);
        SessionClientTimeoutEndpoint.clientOnCloseCalled = false;

        try {
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
            }, cec, getURI(SessionClientTimeoutEndpoint.class));

            onCloseLatch.await(2, TimeUnit.SECONDS);
            assertTrue(SessionClientTimeoutEndpoint.clientOnCloseCalled);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }


}