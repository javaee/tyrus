/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.test.servlet.session;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;
import org.glassfish.tyrus.tests.servlet.session.CloseClientEndpoint;
import org.glassfish.tyrus.tests.servlet.session.CloseServerEndpoint;
import org.glassfish.tyrus.tests.servlet.session.ServiceEndpoint;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

import junit.framework.Assert;

/**
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class SessionCloseApplicationTest extends TestContainer {
    private static final String CONTEXT_PATH = "/session-test";
    private static String messageReceived1 = "not received.";
    private static String messageReceived2 = "not received.";
    private static boolean inCloseSendMessageExceptionThrown1 = false;
    private static boolean inCloseSendMessageExceptionThrown2 = false;

    public SessionCloseApplicationTest() {
        setContextPath(CONTEXT_PATH);
    }

    @Test
    public void testCloseSessionServer() throws DeploymentException {

        final CountDownLatch clientLatch = new CountDownLatch(1);

        boolean exceptionAddMessageHandlerThrown = false;
        boolean exceptionRemoveMessageHandlerThrown = false;
        boolean exceptionGetAsyncRemoteThrown = false;
        boolean exceptionGetBasicRemoteThrown = false;

        final Server server = startServer(CloseServerEndpoint.class, ServiceEndpoint.class);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager client = createClient();
            Session clientSession = client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig endpointConfig) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {

                        @Override
                        public void onMessage(String s) {
                        }
                    });
                    try {
                        session.getBasicRemote().sendText("message");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onClose(javax.websocket.Session session, javax.websocket.CloseReason closeReason) {
                    try {
                        session.getBasicRemote().sendText("Hello.");
                    } catch (Exception e) {
                        inCloseSendMessageExceptionThrown1 = true;
                    }

                    // clientLatch.countDown();
                }

                @Override
                public void onError(javax.websocket.Session session, Throwable thr) {
                    thr.printStackTrace();
                }
            }, cec, getURI(CloseServerEndpoint.class.getAnnotation(ServerEndpoint.class).value()));

            // assertTrue(clientLatch.await(5, TimeUnit.SECONDS));
            clientLatch.await(5, TimeUnit.SECONDS);

            try {
                clientSession.addMessageHandler(null);
            } catch (IllegalStateException e) {
                exceptionAddMessageHandlerThrown = true;
            }

            try {
                clientSession.removeMessageHandler(null);
            } catch (IllegalStateException e) {
                exceptionRemoveMessageHandlerThrown = true;
            }

            try {
                clientSession.getBasicRemote();
            } catch (IllegalStateException e) {
                exceptionGetBasicRemoteThrown = true;
            }

            try {
                clientSession.getAsyncRemote();
            } catch (IllegalStateException e) {
                exceptionGetAsyncRemoteThrown = true;
            }

            Assert.assertEquals(true, exceptionAddMessageHandlerThrown);
            Assert.assertEquals(true, exceptionGetAsyncRemoteThrown);
            Assert.assertEquals(true, exceptionGetBasicRemoteThrown);
            Assert.assertEquals(true, exceptionRemoveMessageHandlerThrown);
            Assert.assertEquals(true, inCloseSendMessageExceptionThrown1);

            final CountDownLatch messageLatch = new CountDownLatch(1);

            ClientManager client2 = createClient();
            client2.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig endpointConfig) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {

                        @Override
                        public void onMessage(String s) {
                            messageReceived1 = s;
                            messageLatch.countDown();
                        }
                    });
                    try {
                        session.getBasicRemote().sendText("server");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }

                @Override
                public void onError(javax.websocket.Session session, Throwable thr) {
                    thr.printStackTrace();
                }

            }, cec, getURI(ServiceEndpoint.class.getAnnotation(ServerEndpoint.class).value()));
            messageLatch.await(3, TimeUnit.SECONDS);
            assertEquals("Received message was not 1.", messageReceived1, "1");

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testCloseSessionClient() throws DeploymentException {

        final CountDownLatch clientLatch = new CountDownLatch(1);

        boolean exceptionAddMessageHandlerThrown = false;
        boolean exceptionRemoveMessageHandlerThrown = false;
        boolean exceptionGetAsyncRemoteThrown = false;
        boolean exceptionGetBasicRemoteThrown = false;

        final Server server = startServer(CloseClientEndpoint.class, ServiceEndpoint.class);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager client = createClient();
            Session clientSession = client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(final Session session, EndpointConfig endpointConfig) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {

                        @Override
                        public void onMessage(String s) {
                            try {
                                session.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    });

                    try {
                        session.getBasicRemote().sendText("message");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onClose(javax.websocket.Session session, javax.websocket.CloseReason closeReason) {
                    try {
                        session.getBasicRemote().sendText("Hello");
                    } catch (Exception e) {
                        inCloseSendMessageExceptionThrown2 = true;
                    }
                }

                @Override
                public void onError(javax.websocket.Session session, Throwable thr) {
                    thr.printStackTrace();
                }
            }, cec, getURI(CloseClientEndpoint.class.getAnnotation(ServerEndpoint.class).value()));

            clientLatch.await(5, TimeUnit.SECONDS);

            try {
                clientSession.addMessageHandler(null);
            } catch (IllegalStateException e) {
                exceptionAddMessageHandlerThrown = true;
            }

            try {
                clientSession.removeMessageHandler(null);
            } catch (IllegalStateException e) {
                exceptionRemoveMessageHandlerThrown = true;
            }

            try {
                clientSession.getBasicRemote();
            } catch (IllegalStateException e) {
                exceptionGetBasicRemoteThrown = true;
            }

            try {
                clientSession.getAsyncRemote();
            } catch (IllegalStateException e) {
                exceptionGetAsyncRemoteThrown = true;
            }

            Assert.assertEquals(true, exceptionAddMessageHandlerThrown);
            Assert.assertEquals(true, exceptionGetAsyncRemoteThrown);
            Assert.assertEquals(true, exceptionGetBasicRemoteThrown);
            Assert.assertEquals(true, exceptionRemoveMessageHandlerThrown);
            Assert.assertEquals(true, inCloseSendMessageExceptionThrown2);

            final CountDownLatch messageLatch = new CountDownLatch(1);

            ClientManager client2 = createClient();
            client2.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig endpointConfig) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {

                        @Override
                        public void onMessage(String s) {
                            messageReceived2 = s;
                            messageLatch.countDown();
                        }
                    });
                    try {
                        session.getBasicRemote().sendText("client");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }

                @Override
                public void onError(javax.websocket.Session session, Throwable thr) {
                    thr.printStackTrace();
                }

            }, cec, getURI(ServiceEndpoint.class.getAnnotation(ServerEndpoint.class).value()));
            messageLatch.await(3, TimeUnit.SECONDS);
            assertEquals("Received message was not 1.", messageReceived2, "1");

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testSessionTimeoutClient() throws DeploymentException {

        final Server server = startServer(CloseClientEndpoint.class, ServiceEndpoint.class);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager client = createClient();
            Session clientSession = client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(final Session session, EndpointConfig endpointConfig) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {

                        @Override
                        public void onMessage(String s) {
                        }
                    });
                }

                @Override
                public void onError(javax.websocket.Session session, Throwable thr) {
                    thr.printStackTrace();
                }
            }, cec, getURI(CloseClientEndpoint.class.getAnnotation(ServerEndpoint.class).value()));

            clientSession.setMaxIdleTimeout(100);
            Thread.sleep(200);
            Assert.assertFalse("Session is not closed", clientSession.isOpen());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }
}
