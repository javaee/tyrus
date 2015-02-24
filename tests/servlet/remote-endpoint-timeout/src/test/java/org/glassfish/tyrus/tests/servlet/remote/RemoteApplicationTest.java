/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.tests.servlet.remote;

import java.io.IOException;
import java.nio.ByteBuffer;
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

import org.junit.Ignore;
import org.junit.Test;

import junit.framework.Assert;

/**
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
@Ignore
public class RemoteApplicationTest extends TestContainer {

    private static String receivedMessage1;
    private static String receivedMessage2;
    private static String receivedMessage3;
    private static String receivedMessage4;
    private static final String CONTEXT_PATH = "/remote-test";

    public RemoteApplicationTest() {
        setContextPath(CONTEXT_PATH);
    }

    @Test
    public void testTimeoutByHandler() throws DeploymentException, InterruptedException, IOException {
        final Server server = startServer(TimeoutEndpointResultByHandler.class, ServiceEndpoint.class);

        final CountDownLatch messageLatch = new CountDownLatch(1);

        try {
            final ClientManager client = createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
                            @Override
                            public void onMessage(ByteBuffer buffer) {
                                Assert.assertTrue(false);
                                messageLatch.countDown();
                            }
                        });

                        session.getBasicRemote().sendText("Message.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, ClientEndpointConfig.Builder.create().build(),
                                   getURI(TimeoutEndpointResultByHandler.class.getAnnotation(ServerEndpoint.class)
                                                                              .value()));

            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertEquals(1, messageLatch.getCount());

            final CountDownLatch serviceMessageLatch = new CountDownLatch(1);
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                System.out.println("Received message: " + message);
                                receivedMessage1 = message;
                                serviceMessageLatch.countDown();
                            }
                        });

                        session.getBasicRemote().sendText("handler");
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }, ClientEndpointConfig.Builder.create().build(),
                                   getURI(ServiceEndpoint.class.getAnnotation(ServerEndpoint.class).value()));

            serviceMessageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertEquals(0, serviceMessageLatch.getCount());
            Assert.assertTrue(receivedMessage1.equals("1"));
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testTimeoutByFuture() throws DeploymentException, InterruptedException, IOException {
        final Server server = startServer(TimeoutEndpointResultByFuture.class, ServiceEndpoint.class);

        final CountDownLatch messageLatch = new CountDownLatch(1);

        try {
            final ClientManager client = createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
                            @Override
                            public void onMessage(ByteBuffer buffer) {
                                Assert.assertTrue(false);
                                messageLatch.countDown();
                            }
                        });

                        session.getBasicRemote().sendText("Message.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, ClientEndpointConfig.Builder.create().build(),
                                   getURI(TimeoutEndpointResultByFuture.class.getAnnotation(ServerEndpoint.class)
                                                                             .value()));

            messageLatch.await(1, TimeUnit.SECONDS);
            Assert.assertEquals(1, messageLatch.getCount());

            final CountDownLatch serviceMessageLatch = new CountDownLatch(1);
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                System.out.println("Received message: " + message);
                                receivedMessage2 = message;
                                serviceMessageLatch.countDown();
                            }
                        });

                        session.getBasicRemote().sendText("future");
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }, ClientEndpointConfig.Builder.create().build(),
                                   getURI(ServiceEndpoint.class.getAnnotation(ServerEndpoint.class).value()));

            serviceMessageLatch.await(1, TimeUnit.SECONDS);
            Assert.assertEquals(0, serviceMessageLatch.getCount());
            Assert.assertTrue(receivedMessage2.equals("1"));
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testNoTimeoutByHandler() throws DeploymentException, InterruptedException, IOException {
        final Server server = startServer(NoTimeoutEndpointResultByHandler.class, ServiceEndpoint.class);

        final CountDownLatch messageLatch = new CountDownLatch(1);
        final String messageToSend = "M";

        try {
            final ClientManager client = createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String buffer) {
                                System.out.println("Received from action3: " + buffer);
                                Assert.assertTrue(messageToSend.equals(buffer));
                                messageLatch.countDown();
                            }
                        });

                        session.getBasicRemote().sendText(messageToSend);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, ClientEndpointConfig.Builder.create().build(),
                                   getURI(NoTimeoutEndpointResultByHandler.class.getAnnotation(ServerEndpoint.class)
                                                                                .value()));

            messageLatch.await(2, TimeUnit.SECONDS);
            Assert.assertEquals("Received message should be one.", 0, messageLatch.getCount());

            final CountDownLatch serviceMessageLatch = new CountDownLatch(1);
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                System.out.println("Received message: " + message);
                                receivedMessage3 = message;
                                serviceMessageLatch.countDown();
                            }
                        });

                        session.getBasicRemote().sendText("nohandler");
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }, ClientEndpointConfig.Builder.create().build(),
                                   getURI(ServiceEndpoint.class.getAnnotation(ServerEndpoint.class).value()));

            serviceMessageLatch.await(1, TimeUnit.SECONDS);
            Assert.assertEquals("One message should be received.", 0, serviceMessageLatch.getCount());
            Assert.assertTrue("Received service message should be \"1\".", receivedMessage3.equals("1"));
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testNoTimeoutByFuture() throws DeploymentException, InterruptedException, IOException {
        final Server server = startServer(NoTimeoutEndpointResultByFuture.class, ServiceEndpoint.class);

        final CountDownLatch messageLatch = new CountDownLatch(1);
        final String messageToSend = "M";

        try {
            final ClientManager client = createClient();
            Session clientSession = client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String buffer) {
                                System.out.println("Received from action3: " + buffer);
                                Assert.assertTrue(messageToSend.equals(buffer));
                                messageLatch.countDown();
                            }
                        });

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, ClientEndpointConfig.Builder.create().build(), getURI(NoTimeoutEndpointResultByFuture.class
                                                                             .getAnnotation(ServerEndpoint.class)
                                                                             .value()));
            clientSession.getBasicRemote().sendText(messageToSend);

            messageLatch.await(3, TimeUnit.SECONDS);
            Assert.assertEquals("Received message should be one", 0, messageLatch.getCount());

            final CountDownLatch serviceMessageLatch = new CountDownLatch(1);
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                System.out.println("Received message: " + message);
                                receivedMessage4 = message;
                                serviceMessageLatch.countDown();
                            }
                        });

                        session.getBasicRemote().sendText("nofuture");
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }, ClientEndpointConfig.Builder.create().build(),
                                   getURI(ServiceEndpoint.class.getAnnotation(ServerEndpoint.class).value()));

            serviceMessageLatch.await(1, TimeUnit.SECONDS);
            Assert.assertEquals("One message should be received.", 0, serviceMessageLatch.getCount());
            Assert.assertTrue("Received service message should be \"1\".", receivedMessage4.equals("1"));
        } finally {
            stopServer(server);
        }
    }
}
