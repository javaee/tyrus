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
package org.glassfish.tyrus.tests.servlet.async;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;

import org.junit.Assert;
import org.junit.Test;

/**
 * Basic test for RemoteEndpoint.Async.sendBinary().
 *
 * @author Jitendra Kotamraju
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class AsyncBinaryTest {

    private static final int MESSAGE_NO = 100;
    private final String CONTEXT_PATH = "/servlet-test-async";
    private final String DEFAULT_HOST = "localhost";
    private final int DEFAULT_PORT = 8025;

    private final Set<Class<?>> endpointClasses = new HashSet<Class<?>>() {{
        add(BinaryFutureEndpoint.class);
        add(BinaryHandlerEndpoint.class);
        add(ServiceEndpoint.class);
    }};

    /**
     * Start embedded server unless "tyrus.test.host" system property is specified.
     *
     * @return new {@link Server} instance or {@code null} if "tyrus.test.host" system property is set.
     */
    private Server startServer() throws DeploymentException {
        final String host = System.getProperty("tyrus.test.host");
        if (host == null) {
            final Server server = new Server(DEFAULT_HOST, DEFAULT_PORT, CONTEXT_PATH, null, endpointClasses);
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
    public void testBinaryFuture() throws DeploymentException, InterruptedException, IOException {
        final Server server = startServer();

        CountDownLatch sentLatch = new CountDownLatch(MESSAGE_NO);
        CountDownLatch receivedLatch = new CountDownLatch(MESSAGE_NO);

        try {
            final ClientManager client = ClientManager.createClient();
            client.connectToServer(
                    new AsyncFutureClient(sentLatch, receivedLatch),
                    ClientEndpointConfig.Builder.create().build(),
                    getURI(BinaryFutureEndpoint.class.getAnnotation(ServerEndpoint.class).value()));

            sentLatch.await(5, TimeUnit.SECONDS);
            receivedLatch.await(5, TimeUnit.SECONDS);

            // Check the number of received messages
            Assert.assertEquals("Didn't send all the messages. ", 0, sentLatch.getCount());
            Assert.assertEquals("Didn't receive all the messages. ", 0, receivedLatch.getCount());

            final CountDownLatch serviceLatch = new CountDownLatch(1);
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    session.addMessageHandler(new MessageHandler.Whole<Integer>() {
                        @Override
                        public void onMessage(Integer message) {
                            Assert.assertEquals("Server callback wasn't called at all cases.", 0, message.intValue());
                            serviceLatch.countDown();
                        }
                    });
                    try {
                        session.getBasicRemote().sendText(ServiceEndpoint.BINARY_FUTURE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, ClientEndpointConfig.Builder.create().build(), getURI(ServiceEndpoint.class.getAnnotation(ServerEndpoint.class).value()));

            serviceLatch.await(5, TimeUnit.SECONDS);
            Assert.assertEquals("Didn't receive all the messages. ", 0, receivedLatch.getCount());
        } finally {
            stopServer(server);
        }
    }


    // Client endpoint that sends messages asynchronously
    public static class AsyncFutureClient extends Endpoint {
        private final CountDownLatch sentLatch;
        private final CountDownLatch receivedLatch;
        private final long noMessages;

        public AsyncFutureClient(CountDownLatch sentLatch, CountDownLatch receivedLatch) {
            noMessages = sentLatch.getCount();
            this.sentLatch = sentLatch;
            this.receivedLatch = receivedLatch;
        }

        @Override
        public void onOpen(Session session, EndpointConfig EndpointConfig) {
            try {
                session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
                    @Override
                    public void onMessage(ByteBuffer buf) {
                        receivedLatch.countDown();
                    }
                });

                for (int i = 0; i < noMessages; i++) {
                    Future future = session.getAsyncRemote().sendBinary(ByteBuffer.wrap(new byte[]{(byte) i}));
                    future.get();
                    sentLatch.countDown();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void testBinaryHandler() throws DeploymentException, InterruptedException, IOException {
        final Server server = startServer();

        CountDownLatch sentLatch = new CountDownLatch(MESSAGE_NO);
        CountDownLatch receivedLatch = new CountDownLatch(MESSAGE_NO);

        try {
            final ClientManager client = ClientManager.createClient();
            client.connectToServer(
                    new AsyncHandlerClient(sentLatch, receivedLatch),
                    ClientEndpointConfig.Builder.create().build(),
                    getURI(BinaryHandlerEndpoint.class.getAnnotation(ServerEndpoint.class).value()));

            sentLatch.await(5, TimeUnit.SECONDS);
            receivedLatch.await(5, TimeUnit.SECONDS);

            // Check the number of received messages
            Assert.assertEquals("Didn't send all the messages. ", 0, sentLatch.getCount());
            Assert.assertEquals("Didn't receive all the messages. ", 0, receivedLatch.getCount());

            final CountDownLatch serviceLatch = new CountDownLatch(1);
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    session.addMessageHandler(new MessageHandler.Whole<Integer>() {
                        @Override
                        public void onMessage(Integer message) {
                            Assert.assertEquals("Server callback wasn't called at all cases.", 0, message.intValue());
                            serviceLatch.countDown();
                        }
                    });
                    try {
                        session.getBasicRemote().sendText(ServiceEndpoint.BINARY_HANDLER);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, ClientEndpointConfig.Builder.create().build(),
                    getURI(ServiceEndpoint.class.getAnnotation(ServerEndpoint.class).value()));

            serviceLatch.await(5, TimeUnit.SECONDS);
            Assert.assertEquals("Didn't receive all the messages. ", 0, receivedLatch.getCount());
        } finally {
            stopServer(server);
        }
    }

    // Client endpoint that sends messages asynchronously
    public static class AsyncHandlerClient extends Endpoint {
        private final CountDownLatch sentLatch;
        private final CountDownLatch receivedLatch;
        private final long noMessages;

        public AsyncHandlerClient(CountDownLatch sentLatch, CountDownLatch receivedLatch) {
            noMessages = sentLatch.getCount();
            this.sentLatch = sentLatch;
            this.receivedLatch = receivedLatch;
        }

        @Override
        public void onOpen(Session session, EndpointConfig EndpointConfig) {
            try {
                session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
                    @Override
                    public void onMessage(ByteBuffer buf) {
                        receivedLatch.countDown();
                    }
                });

                for (int i = 0; i < noMessages; i++) {
                    session.getAsyncRemote().sendBinary(ByteBuffer.wrap(new byte[]{(byte) i}), new SendHandler() {
                        @Override
                        public void onResult(SendResult result) {
                            if (result.isOK()) {
                                sentLatch.countDown();
                            }
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}