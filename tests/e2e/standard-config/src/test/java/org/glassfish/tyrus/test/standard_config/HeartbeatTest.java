/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.test.standard_config;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.PongMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.core.TyrusSession;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class HeartbeatTest extends TestContainer {

    private static String PONG_RECEIVED = "pong received";

    @ServerEndpoint(value = "/replyingHeartbeatEndpoint")
    public static class ReplyingHeartbeatEndpoint {

        @OnMessage
        public void onPong(PongMessage pongMessage, Session session) {
            try {
                session.getBasicRemote().sendText(PONG_RECEIVED);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void testHeartbeatClient() throws DeploymentException {
        Server server = startServer(ReplyingHeartbeatEndpoint.class);
        try {
            final CountDownLatch messageLatch = new CountDownLatch(3);
            ClientManager client = createClient();
            Session session = client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    TyrusSession tyrusSession = (TyrusSession) session;
                    tyrusSession.setHeartbeatInterval(200);

                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            if (message.equals(PONG_RECEIVED)) {
                                messageLatch.countDown();
                            }
                        }
                    });
                }

            }, ClientEndpointConfig.Builder.create().build(), getURI(ReplyingHeartbeatEndpoint.class));
            assertTrue(messageLatch.await(2, TimeUnit.SECONDS));
            session.close();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint(value = "/heartbeatServerEndpoint")
    public static class HeartbeatServerEndpoint {

        @OnOpen
        public void onOpen(Session session) {
            TyrusSession tyrusSession = (TyrusSession) session;
            tyrusSession.setHeartbeatInterval(200);
        }

    }

    @Test
    public void testHeartbeatServer() throws DeploymentException {
        Server server = startServer(HeartbeatServerEndpoint.class);

        try {
            final CountDownLatch pongLatch = new CountDownLatch(3);
            ClientManager client = createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    session.addMessageHandler(new MessageHandler.Whole<PongMessage>() {
                        @Override
                        public void onMessage(PongMessage message) {
                            pongLatch.countDown();
                        }
                    });
                }
            }, ClientEndpointConfig.Builder.create().build(), getURI(HeartbeatServerEndpoint.class));
            assertTrue(pongLatch.await(3, TimeUnit.SECONDS));

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint(value = "/notReplyingHeartbeatEndpoint")
    public static class NotReplyingHeartbeatEndpoint {
    }

    @Test
    public void testSessionTimeout() throws DeploymentException {
        Server server = startServer(NotReplyingHeartbeatEndpoint.class);
        try {
            final CountDownLatch closeLatch = new CountDownLatch(1);
            ClientManager client = createClient();
            Session session = client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    TyrusSession tyrusSession = (TyrusSession) session;
                    tyrusSession.setHeartbeatInterval(100);
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    closeLatch.countDown();
                }

            }, ClientEndpointConfig.Builder.create().build(), getURI(NotReplyingHeartbeatEndpoint.class));
            session.setMaxIdleTimeout(500);
            assertFalse(closeLatch.await(1, TimeUnit.SECONDS));
            session.close();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testHeartbeatCancel() throws DeploymentException {
        Server server = startServer(ReplyingHeartbeatEndpoint.class);
        final AtomicBoolean firstReplyReceived = new AtomicBoolean(false);
        try {
            final CountDownLatch secondReplyLatch = new CountDownLatch(1);
            ClientManager client = createClient();
            Session session = client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(final Session session, EndpointConfig config) {
                    TyrusSession tyrusSession = (TyrusSession) session;
                    tyrusSession.setHeartbeatInterval(300);

                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            if (message.equals(PONG_RECEIVED) && !firstReplyReceived.get()) {
                                TyrusSession tyrusSession = (TyrusSession) session;
                                tyrusSession.setHeartbeatInterval(0);
                                firstReplyReceived.set(true);
                            } else {
                                secondReplyLatch.countDown();
                            }
                        }
                    });
                }

            }, ClientEndpointConfig.Builder.create().build(), getURI(ReplyingHeartbeatEndpoint.class));
            assertFalse(secondReplyLatch.await(1, TimeUnit.SECONDS));
            session.close();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

}
