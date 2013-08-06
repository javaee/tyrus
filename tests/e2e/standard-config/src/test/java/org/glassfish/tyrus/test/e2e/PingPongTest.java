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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnMessage;
import javax.websocket.PongMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.testing.TestUtilities;
import org.glassfish.tyrus.server.Server;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;


/**
 * Tests sending and receiving ping and pongs
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class PingPongTest extends TestUtilities {

    private static final String PONG_RECEIVED = "PONG RECEIVED";

    @ServerEndpoint(value = "/pingpong")
    public static class PingPongEndpoint {

        @OnMessage
        public void onPong(PongMessage pongMessage, Session session) {
            System.out.println("### PingPongEndpoint - received pong \"" + new String(pongMessage.getApplicationData().array()) + "\"");
            if (pongMessage.getApplicationData().equals(ByteBuffer.wrap("ping message server".getBytes()))) {
                try {
                    session.getBasicRemote().sendText(PONG_RECEIVED);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        @OnMessage
        public void onMessage(Session session, String message) throws IOException {
            System.out.println("### PingPongEndpoint - sending ping \"ping message server\"");
            session.getBasicRemote().sendPing(ByteBuffer.wrap("ping message server".getBytes()));
        }
    }

    @Test
    public void testPongClient() throws DeploymentException {
        Server server = startServer(PingPongEndpoint.class);

        try {
            final CountDownLatch messageLatch = new CountDownLatch(1);

            ContainerProvider.getWebSocketContainer().connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<PongMessage>() {
                            @Override
                            public void onMessage(PongMessage message) {
                                System.out.println("### Client - received pong \"" + new String(message.getApplicationData().array()) + "\"");
                                if (message.getApplicationData().equals(ByteBuffer.wrap("ping message client".getBytes()))) {
                                    messageLatch.countDown();
                                }
                            }
                        });

                        System.out.println("### Client - sending ping \"ping message client\"");
                        session.getBasicRemote().sendPing(ByteBuffer.wrap("ping message client".getBytes()));

                    } catch (IOException e) {
                        // do nothing.
                    }
                }
            }, ClientEndpointConfig.Builder.create().build(), getURI(PingPongEndpoint.class));

            messageLatch.await(1, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testPongServer() throws DeploymentException {
        Server server = startServer(PingPongEndpoint.class);

        try {
            final CountDownLatch messageLatch = new CountDownLatch(1);

            ContainerProvider.getWebSocketContainer().connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                if(message.equals(PONG_RECEIVED)){
                                    messageLatch.countDown();
                                }
                            }
                        });
                        session.getBasicRemote().sendText("ping-initiator");
                    } catch (IOException e) {
                        // do nothing.
                    }
                }
            }, ClientEndpointConfig.Builder.create().build(), getURI(PingPongEndpoint.class));

            messageLatch.await(2, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testLimits() throws DeploymentException {
        Server server = startServer(PingPongEndpoint.class);

        try {

            final Session session = ContainerProvider.getWebSocketContainer().connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    // do nothing.
                }
            }, ClientEndpointConfig.Builder.create().build(), getURI(PingPongEndpoint.class));

            session.getBasicRemote().sendPing(ByteBuffer.wrap("12345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345".getBytes()));
            try {
                session.getBasicRemote().sendPing(ByteBuffer.wrap("123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456".getBytes()));
                fail();
            } catch (IllegalArgumentException e) {
            }
            session.getBasicRemote().sendPong(ByteBuffer.wrap("12345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345".getBytes()));
            try {
                session.getBasicRemote().sendPong(ByteBuffer.wrap("123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456".getBytes()));
                fail();
            } catch (IllegalArgumentException e) {

            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }
}
