/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
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

import javax.websocket.ClientEndpointConfig;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.glassfish.tyrus.testing.TestUtilities;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.e2e.bean.EchoEndpoint;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the basic echo.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class HelloTest extends TestUtilities{

    private CountDownLatch messageLatch;

    private volatile String receivedMessage;

    private static final String SENT_MESSAGE = "Hello World";

    @Test
    public void testHello() throws DeploymentException {
//        Server server = new Server(EchoEndpoint.class);

        final Server server = startServer(EchoEndpoint.class);
        try {
            messageLatch = new CountDownLatch(1);

            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager client = ClientManager.createClient();
            client.connectToServer(new TestEndpointAdapter() {

                private Session session;

                @Override
                public EndpointConfig getEndpointConfig() {
                    return cec;
                }

                @Override
                public void onOpen(Session session) {

                    this.session = session;

                    try {
                        session.addMessageHandler(new TestTextMessageHandler(this));
                        session.getBasicRemote().sendText(SENT_MESSAGE);
                        System.out.println("Hello message sent.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onMessage(String message) {
                    receivedMessage = message;

                    // TYRUS-141
                    if (session.getNegotiatedSubprotocol() != null) {
                        messageLatch.countDown();
                    }
                }
            }, cec, getURI(EchoEndpoint.class));
            messageLatch.await(1000, TimeUnit.SECONDS);
            Assert.assertEquals(0L, messageLatch.getCount());
            Assert.assertEquals(SENT_MESSAGE, receivedMessage);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

//    @Test
//    public void testHello404() throws DeploymentException {
//        Server server = startServer(EchoEndpoint.class);
//
//        try {
//            messageLatch = new CountDownLatch(1);
//
//            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();
//
//            ClientManager client = ClientManager.createClient();
//            client.connectToServer(new TestEndpointAdapter() {
//                @Override
//                public EndpointConfig getEndpointConfig() {
//                    return cec;
//                }
//
//                @Override
//                public void onOpen(Session session) {
//                    try {
//                        session.addMessageHandler(new TestTextMessageHandler(this));
//                        session.getBasicRemote().sendText(SENT_MESSAGE);
//                        System.out.println("Hello message sent.");
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//
//                @Override
//                public void onMessage(String message) {
//                    receivedMessage = message;
//                    messageLatch.countDown();
//                }
//            }, cec, new URI("ws://localhost:8025/websockets/tests/echo404"));
//            fail();
//        } catch (Exception e) {
//            assertNotNull(e);
//            assertTrue(e instanceof DeploymentException);
//            assertTrue(e.getCause() instanceof HandshakeException);
//        } finally {
//            stopServer(server);
//        }
//    }

    public static CountDownLatch messageLatchEndpoint;
    public static volatile String receivedMessageEndpoint;

    // TYRUS-63: connectToServer with Endpoint class
    // http://java.net/jira/browse/TYRUS-63
    @Test
    public void testHelloEndpointClass() throws DeploymentException {
        final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();
        Server server = startServer(EchoEndpoint.class);

        try {
            messageLatchEndpoint = new CountDownLatch(1);

            WebSocketContainer client = ContainerProvider.getWebSocketContainer();
            client.connectToServer(MyEndpoint.class, cec, getURI(EchoEndpoint.class));
            messageLatchEndpoint.await(5, TimeUnit.SECONDS);
            Assert.assertEquals(0L, messageLatchEndpoint.getCount());
            Assert.assertEquals(SENT_MESSAGE, receivedMessageEndpoint);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    public final static class MyEndpoint extends Endpoint implements MessageHandler.Whole<String> {
        @Override
        public void onOpen(Session session, EndpointConfig config) {
            try {
                session.addMessageHandler(this);
                session.getBasicRemote().sendText(SENT_MESSAGE);
                System.out.println("Hello message sent.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onMessage(String message) {
            receivedMessageEndpoint = message;
            messageLatchEndpoint.countDown();
        }
    }
}
