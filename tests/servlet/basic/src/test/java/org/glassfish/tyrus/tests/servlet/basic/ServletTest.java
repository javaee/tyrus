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

package org.glassfish.tyrus.tests.servlet.basic;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;

import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Primarily meant to test servlet integration, might be someday used for simple stress testing.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class ServletTest {

    private final String CONTEXT_PATH = "/servlet-test";
    private final String DEFAULT_HOST = "localhost";
    private final int DEFAULT_PORT = 8025;

    private final Set<Class<?>> endpointClasses = new HashSet<Class<?>>() {{
        add(PlainEchoEndpoint.class);
        add(RequestUriEndpoint.class);
        add(OnOpenCloseEndpoint.class);
        add(MultiEchoEndpoint.class);
        add(TyrusBroadcastEndpoint.class);
        add(WebSocketBroadcastEndpoint.class);
    }};

    /**
     * Start embedded server unless "tyrus.test.host" system property is specified.
     *
     * @return new {@link Server} instance or {@code null} if "tyrus.test.host" system property is set.
     */
    private Server startServer() throws DeploymentException {
        final String host = System.getProperty("tyrus.test.host");
        if (host == null) {
            final Server server = new Server(DEFAULT_HOST, DEFAULT_PORT, CONTEXT_PATH, endpointClasses);
            server.start();
            return server;
        } else {
            return null;
        }
    }

    private String getHost() {
        final String host = System.getProperty("tyrus.test.host");
        if (host != null) {
            return host;
        }
        return DEFAULT_HOST;
    }

    private int getPort() {
        final String port = System.getProperty("tyrus.test.port");
        if (port != null) {
            try {
                return Integer.parseInt(port);
            } catch (NumberFormatException nfe) {
                // do nothing
            }
        }
        return DEFAULT_PORT;
    }

    private URI getURI(String endpointPath) {
        try {
            return new URI("ws", null, getHost(), getPort(), CONTEXT_PATH + endpointPath, null, null);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void stopServer(Server server) {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testPlainEchoShort() throws DeploymentException, InterruptedException, IOException {
        final Server server = startServer();

        final CountDownLatch messageLatch = new CountDownLatch(1);

        try {
            final ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                assertEquals(message, "Do or do not, there is no try.");
                                messageLatch.countDown();
                            }
                        });

                        session.getBasicRemote().sendText("Do or do not, there is no try.");
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }, ClientEndpointConfig.Builder.create().build(), getURI(PlainEchoEndpoint.class.getAnnotation(ServerEndpoint.class).value()));

            messageLatch.await(1, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());
        } finally {
            stopServer(server);
        }
    }

    @Test
    @Ignore("TODO")
    public void testPlainEchoShort100() throws DeploymentException, InterruptedException, IOException {
        final Server server = startServer();

        final CountDownLatch messageLatch = new CountDownLatch(100);

        try {
            final ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                assertEquals(message, "Do or do not, there is no try.");
                                messageLatch.countDown();
                            }
                        });

                        for (int i = 0; i < 100; i++) {
                            session.getBasicRemote().sendText("Do or do not, there is no try.");
                        }
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }, ClientEndpointConfig.Builder.create().build(), getURI(PlainEchoEndpoint.class.getAnnotation(ServerEndpoint.class).value()));


            messageLatch.await(20, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testPlainEchoShort10Sequence() throws DeploymentException, InterruptedException, IOException {
        final Server server = startServer();

        final CountDownLatch messageLatch = new CountDownLatch(10);

        try {
            for (int i = 0; i < 10; i++) {
                final ClientManager client = ClientManager.createClient();
                client.connectToServer(new Endpoint() {
                    @Override
                    public void onOpen(Session session, EndpointConfig EndpointConfig) {
                        try {
                            session.addMessageHandler(new MessageHandler.Whole<String>() {
                                @Override
                                public void onMessage(String message) {
                                    assertEquals(message, "Do or do not, there is no try.");
                                    messageLatch.countDown();
                                }
                            });

                            session.getBasicRemote().sendText("Do or do not, there is no try.");
                        } catch (IOException e) {
                            // do nothing
                        }
                    }
                }, ClientEndpointConfig.Builder.create().build(), getURI(PlainEchoEndpoint.class.getAnnotation(ServerEndpoint.class).value()));

                // TODO - remove when possible.
                Thread.sleep(100);
            }

            messageLatch.await(5, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testPlainEchoShort10SequenceReturnedSession() throws DeploymentException, InterruptedException, IOException {
        final Server server = startServer();

        final CountDownLatch messageLatch = new CountDownLatch(10);

        try {
            for (int i = 0; i < 10; i++) {
                final ClientManager client = ClientManager.createClient();
                Session session = client.connectToServer(new Endpoint() {
                    @Override
                    public void onOpen(Session session, EndpointConfig EndpointConfig) {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                assertEquals(message, "Do or do not, there is no try.");
                                messageLatch.countDown();
                            }
                        });
                    }
                }, ClientEndpointConfig.Builder.create().build(), getURI(PlainEchoEndpoint.class.getAnnotation(ServerEndpoint.class).value()));

                session.getBasicRemote().sendText("Do or do not, there is no try.");
                // TODO - remove when possible.
                Thread.sleep(100);
            }

            messageLatch.await(5, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());
        } finally {
            stopServer(server);
        }
    }

    /**
     * 10x10x10 bytes.
     */
    private static final String LONG_MESSAGE =
            "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" +
                    "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" +
                    "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" +
                    "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" +
                    "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" +
                    "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" +
                    "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" +
                    "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" +
                    "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" +
                    "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890";

    @Test
    public void testPlainEchoLong() throws DeploymentException, InterruptedException, IOException {
        final Server server = startServer();

        final CountDownLatch messageLatch = new CountDownLatch(1);

        try {
            final ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                assertEquals(message, LONG_MESSAGE);
                                messageLatch.countDown();
                            }
                        });

                        session.getBasicRemote().sendText(LONG_MESSAGE);
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }, ClientEndpointConfig.Builder.create().build(), getURI(PlainEchoEndpoint.class.getAnnotation(ServerEndpoint.class).value()));

            messageLatch.await(1, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testPlainEchoLong10() throws DeploymentException, InterruptedException, IOException {
        final Server server = startServer();

        final CountDownLatch messageLatch = new CountDownLatch(10);

        try {
            final ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                assertEquals(message, LONG_MESSAGE);
                                messageLatch.countDown();
                            }
                        });

                        for (int i = 0; i < 10; i++) {
                            session.getBasicRemote().sendText(LONG_MESSAGE);
                        }
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }, ClientEndpointConfig.Builder.create().build(), getURI(PlainEchoEndpoint.class.getAnnotation(ServerEndpoint.class).value()));

            messageLatch.await(10, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testPlainEchoLong10Sequence() throws DeploymentException, InterruptedException, IOException {
        final Server server = startServer();

        final CountDownLatch messageLatch = new CountDownLatch(10);

        try {
            for (int i = 0; i < 10; i++) {
                final ClientManager client = ClientManager.createClient();
                client.connectToServer(new Endpoint() {
                    @Override
                    public void onOpen(Session session, EndpointConfig EndpointConfig) {
                        try {
                            session.addMessageHandler(new MessageHandler.Whole<String>() {
                                @Override
                                public void onMessage(String message) {
                                    assertEquals(message, LONG_MESSAGE);
                                    messageLatch.countDown();
                                }
                            });

                            session.getBasicRemote().sendText(LONG_MESSAGE);
                        } catch (IOException e) {
                            // do nothing
                        }
                    }
                }, ClientEndpointConfig.Builder.create().build(), getURI(PlainEchoEndpoint.class.getAnnotation(ServerEndpoint.class).value()));

                // TODO - remove when possible.
                Thread.sleep(300);
            }

            messageLatch.await(10, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testGetRequestURI() throws DeploymentException, InterruptedException, IOException {
        final Server server = startServer();

        final CountDownLatch messageLatch = new CountDownLatch(1);

        try {
            final ClientManager client = ClientManager.createClient();

            URI uri = getURI(RequestUriEndpoint.class.getAnnotation(ServerEndpoint.class).value());
            uri = URI.create(uri.toString() + "?test=1;aaa");

            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                assertTrue(message.endsWith("?test=1;aaa"));
                                messageLatch.countDown();
                            }
                        });

                        session.getBasicRemote().sendText("test");
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }, ClientEndpointConfig.Builder.create().build(), uri);

            messageLatch.await(1, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testOnOpenClose() throws DeploymentException, InterruptedException, IOException {
        final Server server = startServer();

        final CountDownLatch latch = new CountDownLatch(2);

        try {
            final ClientManager client = ClientManager.createClient();

            URI uri = getURI(OnOpenCloseEndpoint.class.getAnnotation(ServerEndpoint.class).value());

            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    latch.countDown();
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    latch.countDown();
                }
            }, ClientEndpointConfig.Builder.create().build(), uri);

            latch.await(3, TimeUnit.SECONDS);
            assertEquals(0, latch.getCount());
        } finally {
            stopServer(server);
        }
    }

    // "performance" test; 500 kB message is echoed 10 times.
    @Test
    public void testMultiEcho() throws IOException, DeploymentException, InterruptedException {

        final int LENGTH = 587952;
        byte[] b = new byte[LENGTH];
        Arrays.fill(b, 0, LENGTH, (byte) 'a');

        final String text = new String(b);

        final Server server = startServer();

        final CountDownLatch messageLatch = new CountDownLatch(10);

        try {
            final ClientManager client = ClientManager.createClient();
            final Session session = client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            assertEquals(LENGTH, message.length());
                            messageLatch.countDown();
                        }
                    });
                }
            }, ClientEndpointConfig.Builder.create().build(), getURI(MultiEchoEndpoint.class.getAnnotation(ServerEndpoint.class).value()));

            session.getBasicRemote().sendText(text);

            messageLatch.await(10, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());
        } finally {
            stopServer(server);
        }
    }

    // "performance" test; 20 clients, endpoint broadcasts.
    @Test
    public void testTyrusBroadcast() throws IOException, DeploymentException, InterruptedException {

        final int LENGTH = 587952;
        byte[] b = new byte[LENGTH];
        Arrays.fill(b, 0, LENGTH, (byte) 'a');

        final String text = new String(b);

        final Server server = startServer();

        final CountDownLatch messageLatch = new CountDownLatch(800);
        final List<Session> sessions = new ArrayList<Session>(20);

        try {
            for (int i = 0; i < 20; i++) {
                final ClientManager client = ClientManager.createClient();
                final Session session = client.connectToServer(new Endpoint() {
                    @Override
                    public void onOpen(Session session, EndpointConfig EndpointConfig) {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                assertEquals(LENGTH, message.length());
                                System.out.println("### " + messageLatch.getCount());
                                messageLatch.countDown();
                            }
                        });
                    }
                }, ClientEndpointConfig.Builder.create().build(), getURI(TyrusBroadcastEndpoint.class.getAnnotation(ServerEndpoint.class).value()));
                System.out.println("Client " + i + " connected.");
                sessions.add(session);
            }

            final long l = System.currentTimeMillis();
            for (Session s : sessions) {
                s.getBasicRemote().sendText(text);
                s.getBasicRemote().sendText(text);
            }

            messageLatch.await(100, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());
            System.out.println("***** Tyrus broadcast ***** " + (System.currentTimeMillis() - l));
        } finally {
            stopServer(server);
        }
    }

    // "performance" test; 20 clients, endpoint broadcasts.
    @Test
    public void testWebSocketBroadcast() throws IOException, DeploymentException, InterruptedException {

        final int LENGTH = 587952;
        byte[] b = new byte[LENGTH];
        Arrays.fill(b, 0, LENGTH, (byte) 'a');

        final String text = new String(b);

        final Server server = startServer();

        final CountDownLatch messageLatch = new CountDownLatch(800);
        final List<Session> sessions = new ArrayList<Session>(20);

        try {
            for (int i = 0; i < 20; i++) {
                final ClientManager client = ClientManager.createClient();
                final Session session = client.connectToServer(new Endpoint() {
                    @Override
                    public void onOpen(Session session, EndpointConfig EndpointConfig) {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                assertEquals(LENGTH, message.length());
                                messageLatch.countDown();
                                System.out.println("### " + messageLatch.getCount());
                            }
                        });
                    }
                }, ClientEndpointConfig.Builder.create().build(), getURI(WebSocketBroadcastEndpoint.class.getAnnotation(ServerEndpoint.class).value()));
                System.out.println("Client " + i + " connected.");
                sessions.add(session);
            }

            final long l = System.currentTimeMillis();
            for (Session s : sessions) {
                s.getBasicRemote().sendText(text);
                s.getBasicRemote().sendText(text);
            }

            messageLatch.await(300, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());

            System.out.println("***** WebSocket broadcast ***** " + (System.currentTimeMillis() - l));

        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testWebSocketBroadcastLite() throws IOException, DeploymentException, InterruptedException {

        final int LENGTH = 587952;
        byte[] b = new byte[LENGTH];
        Arrays.fill(b, 0, LENGTH, (byte) 'a');

        final String text = new String(b);

        final Server server = startServer();

        final CountDownLatch messageLatch = new CountDownLatch(100);
        final List<Session> sessions = new ArrayList<Session>(10);

        try {
            for (int i = 0; i < 10; i++) {
                final ClientManager client = ClientManager.createClient();
                final Session session = client.connectToServer(new Endpoint() {
                    @Override
                    public void onOpen(Session session, EndpointConfig EndpointConfig) {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                assertEquals(LENGTH, message.length());
                                messageLatch.countDown();
                            }
                        });
                    }
                }, ClientEndpointConfig.Builder.create().build(), getURI(WebSocketBroadcastEndpoint.class.getAnnotation(ServerEndpoint.class).value()));
                System.out.println("Client " + i + " connected.");
                sessions.add(session);
            }

            final long l = System.currentTimeMillis();
            for (Session s : sessions) {
                s.getBasicRemote().sendText(text);
            }

            messageLatch.await(100, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());

            System.out.println("***** WebSocket broadcast ***** " + (System.currentTimeMillis() - l));

        } finally {
            stopServer(server);
        }
    }
}
