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

package org.glassfish.tyrus.test.e2e.non_deployable;

import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.client.RetryAfterException;
import org.glassfish.tyrus.client.auth.AuthenticationException;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.spi.UpgradeResponse;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * RetryAfter tests.
 *
 * @author Ondrej Kosatka (ondrej.kosatka at oracle.com)
 */
public class RetryAfterTest extends TestContainer {

    private static final int REDIRECTION_PORT = 8026;
    private static final HttpStatus SERVICE_UNAVAILABLE = HttpStatus.SERVICE_UNAVAILABLE_503;
    private static final String CONTEXT_ROOT = "/retry-after-echo";

    public RetryAfterTest() {
        setContextPath(CONTEXT_ROOT);
    }

    @Test
    public void testServiceUnavailableWithDate() throws InterruptedException, DeploymentException, AuthenticationException, IOException {
        Server server = null;
        try {
            server = startServer(RetryAfterEchoEndpoint.class);

            testRetryAfter(new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz").format(new Date(System.currentTimeMillis() + 1000)));

        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    @Test
    public void testServiceUnavailableWithSeconds() throws InterruptedException, DeploymentException, AuthenticationException, IOException {
        Server server = null;
        try {
            server = startServer(RetryAfterEchoEndpoint.class);

            testRetryAfter("1");

        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    private void testRetryAfter(String retryAfter) throws DeploymentException, InterruptedException, IOException, AuthenticationException {
        HttpServer httpServer = null;
        try {

            httpServer = startHttpServer(REDIRECTION_PORT, new MultipleRetryAfterHandler(1, retryAfter, "ws://localhost:8025/retry-after-echo/echo"));

            final ClientManager client = ClientManager.createClient();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            final CountDownLatch messageLatch = new CountDownLatch(1);

            client.getProperties().put(ClientProperties.REDIRECT_ENABLED, true);
            client.getProperties().put(ClientProperties.RETRY_AFTER_SERVICE_UNAVAILABLE, true);

            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                System.out.println("received message: " + message);
                                assertEquals(message, "Do or do not, there is no try.");
                                messageLatch.countDown();
                            }
                        });

                        session.getBasicRemote().sendText("Do or do not, there is no try.");
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }, cec, URI.create("ws://localhost:8026/retry-after-echo/echo"));

            assertTrue("Message has not been received", messageLatch.await(1, TimeUnit.SECONDS));
        } finally {
            if (httpServer != null) {
                httpServer.shutdownNow();
            }
        }
    }

    @Test
    public void testServiceUnavailableDisabled() throws InterruptedException, DeploymentException, AuthenticationException, IOException {
        Server server = null;
        try {
            server = startServer(RetryAfterEchoEndpoint.class);

            testRetryAfterConnectFail("1", false, 1);

        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    @Test
    public void testServiceUnavailableNotSet() throws InterruptedException, DeploymentException, AuthenticationException, IOException {
        Server server = null;
        try {
            server = startServer(RetryAfterEchoEndpoint.class);

            testRetryAfterConnectFail("1", null, 1);

        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    @Test
    public void testServiceUnavailableInvalidValue() throws InterruptedException, DeploymentException, AuthenticationException, IOException {
        Server server = null;
        try {
            server = startServer(RetryAfterEchoEndpoint.class);

            testRetryAfterWrongHeader("invalid", true, 1);

        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    @Test
    public void testServiceUnavailableEmptyValue() throws InterruptedException, DeploymentException, AuthenticationException, IOException {
        Server server = null;
        try {
            server = startServer(RetryAfterEchoEndpoint.class);

            testRetryAfterWrongHeader("", true, 1);

        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    @Test
    public void testServiceUnavailableNullValue() throws InterruptedException, DeploymentException, AuthenticationException, IOException {
        Server server = null;
        try {
            server = startServer(RetryAfterEchoEndpoint.class);

            testRetryAfterWrongHeader(null, true, 1);

        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    @Test
    public void testServiceUnavailableTooLongValue() throws InterruptedException, DeploymentException, AuthenticationException, IOException {
        Server server = null;
        try {
            server = startServer(RetryAfterEchoEndpoint.class);

            testRetryAfterWrongHeader("12345678901234567890", true, 1);

        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    @Test
    public void testServiceUnavailableThreshold() throws InterruptedException, DeploymentException, AuthenticationException, IOException {
        Server server = null;
        try {
            server = startServer(RetryAfterEchoEndpoint.class);

            testRetryAfterConnectFail("0", true, 6);

        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    private void testRetryAfterWrongHeader(String retryAfter, Boolean retryAfterEnabled, int numberOfRetries) throws DeploymentException, InterruptedException, IOException, AuthenticationException {
        final CountDownLatch latch = new CountDownLatch(1);

        HttpServer httpServer = null;
        try {

            httpServer = startHttpServer(REDIRECTION_PORT, new MultipleRetryAfterHandler(numberOfRetries, retryAfter, "ws://localhost:8025/retry-after-echo/echo"));

            final ClientManager client = ClientManager.createClient();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            client.getProperties().put(ClientProperties.REDIRECT_ENABLED, true);
            if (retryAfterEnabled != null) {
                client.getProperties().put(ClientProperties.RETRY_AFTER_SERVICE_UNAVAILABLE, retryAfterEnabled);
            }

            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                System.out.println("received message: " + message);
                                fail("Connection should not been even established");
                            }
                        });

                        session.getBasicRemote().sendText("Do or do not, there is no try.");
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }, cec, URI.create("ws://localhost:8026/retry-after-echo/echo"));

            fail("Connection to the endpoint should fail");
        } catch (Exception e) {
            if (e.getCause() != null && e.getCause() instanceof RetryAfterException && ((RetryAfterException) e.getCause()).getDelay() == null) {
                latch.countDown();
            } else {
                fail("Connect should throw RetryAfterException with null delay");
            }
        } finally {
            if (httpServer != null) {
                httpServer.shutdownNow();
            }
        }

        assertTrue("Exception should be caught", latch.await(3, TimeUnit.SECONDS));
    }

    private void testRetryAfterConnectFail(String retryAfter, Boolean retryAfterEnabled, int numberOfRetries) throws DeploymentException, InterruptedException, IOException, AuthenticationException {
        final CountDownLatch latch = new CountDownLatch(1);

        HttpServer httpServer = null;
        try {

            httpServer = startHttpServer(REDIRECTION_PORT, new MultipleRetryAfterHandler(numberOfRetries, retryAfter, "ws://localhost:8025/retry-after-echo/echo"));

            final ClientManager client = ClientManager.createClient();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            client.getProperties().put(ClientProperties.REDIRECT_ENABLED, true);
            if (retryAfterEnabled != null) {
                client.getProperties().put(ClientProperties.RETRY_AFTER_SERVICE_UNAVAILABLE, retryAfterEnabled);
            }

            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                System.out.println("received message: " + message);
                                fail("Connection should not been even established");
                            }
                        });

                        session.getBasicRemote().sendText("Do or do not, there is no try.");
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }, cec, URI.create("ws://localhost:8026/retry-after-echo/echo"));

            fail("Connection to the endpoint should fail");
        } catch (Exception e) {
            if (e.getCause() != null && e.getCause() instanceof RetryAfterException) {
                latch.countDown();
            } else {
                fail("Connect should throw RetryAfterException");
            }
        } finally {
            if (httpServer != null) {
                httpServer.shutdownNow();
            }
        }

        assertTrue("Exception should be caught", latch.await(3, TimeUnit.SECONDS));
    }

    @Test
    public void testServiceUnavailableTwice() throws InterruptedException, DeploymentException, AuthenticationException, IOException {
        Server server = null;
        try {
            server = startServer(RetryAfterEchoEndpoint.class);

            testRetryAfterTwice("1");

        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    private void testRetryAfterTwice(String retryAfter) throws DeploymentException, InterruptedException, IOException, AuthenticationException {
        HttpServer httpServer = null;
        try {

            httpServer = startHttpServer(REDIRECTION_PORT, new MultipleRetryAfterHandler(2, retryAfter, "ws://localhost:8025/retry-after-echo/echo"));

            final ClientManager client = ClientManager.createClient();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            final CountDownLatch messageLatch = new CountDownLatch(1);

            client.getProperties().put(ClientProperties.REDIRECT_ENABLED, true);
            client.getProperties().put(ClientProperties.RETRY_AFTER_SERVICE_UNAVAILABLE, true);

            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                System.out.println("received message: " + message);
                                assertEquals(message, "Do or do not, there is no try.");
                                messageLatch.countDown();
                            }
                        });

                        session.getBasicRemote().sendText("Do or do not, there is no try.");
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }, cec, URI.create("ws://localhost:8026/retry-after-echo/echo"));

            assertTrue("Message has not been received", messageLatch.await(1, TimeUnit.SECONDS));
        } finally {
            if (httpServer != null) {
                httpServer.shutdownNow();
            }
        }
    }

    @Test
    public void testServiceUnavailableUserDefinedHandler() throws InterruptedException, DeploymentException, AuthenticationException, IOException {
        Server server = null;
        try {
            server = startServer(RetryAfterEchoEndpoint.class);

            testRetryAfterUserDefinedHandler("0");

        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    private void testRetryAfterUserDefinedHandler(String retryAfter) throws DeploymentException, InterruptedException, IOException, AuthenticationException {
        HttpServer httpServer = null;
        try {

            httpServer = startHttpServer(REDIRECTION_PORT, new MultipleRetryAfterHandler(6, retryAfter, "ws://localhost:8025/retry-after-echo/echo"));

            final ClientManager client = ClientManager.createClient();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            final CountDownLatch messageLatch = new CountDownLatch(1);
            final CountDownLatch reconnectHandlerLatch = new CountDownLatch(1);

            client.getProperties().put(ClientProperties.REDIRECT_ENABLED, true);
            client.getProperties().put(ClientProperties.RETRY_AFTER_SERVICE_UNAVAILABLE, true);
            client.getProperties().put(ClientProperties.RECONNECT_HANDLER, new ClientManager.ReconnectHandler() {
                @Override
                public boolean onConnectFailure(Exception e) {
                    if (e != null && e.getCause() instanceof RetryAfterException && ((RetryAfterException) e.getCause()).getDelay() != null) {
                        System.out.println("RetryAfterException received.");
                        reconnectHandlerLatch.countDown();
                        return true;
                    } else {
                        return super.onConnectFailure(e);
                    }
                }
            });

            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                System.out.println("received message: " + message);
                                assertEquals(message, "Do or do not, there is no try.");
                                messageLatch.countDown();
                            }
                        });

                        session.getBasicRemote().sendText("Do or do not, there is no try.");
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }, cec, URI.create("ws://localhost:8026/retry-after-echo/echo"));

            assertTrue("Message has not been received", messageLatch.await(1, TimeUnit.SECONDS));
            assertTrue("User-defined ReconnectHandler was not called", reconnectHandlerLatch.await(1, TimeUnit.SECONDS));
        } finally {
            if (httpServer != null) {
                httpServer.shutdownNow();
            }
        }
    }

    private HttpServer startHttpServer(int port, HttpHandler httpHandler) throws IOException {
        HttpServer httpServer = createHttpServer(port);

        httpServer.getServerConfiguration().addHttpHandler(httpHandler, CONTEXT_ROOT + "/echo");

        httpServer.start();

        return httpServer;
    }

    private HttpServer createHttpServer(int port) throws IOException {
        HttpServer httpServer = new HttpServer();

        final NetworkListener listener =
                new NetworkListener("grizzly",
                        "0.0.0.0",
                        port);
        httpServer.addListener(listener);

        return httpServer;
    }

    private class MultipleRetryAfterHandler extends HttpHandler {

        private final CountDownLatch unavailable;
        private final String retryAfter;
        private final String location;

        public MultipleRetryAfterHandler(int counter, String retryAfter, String location) {
            this.unavailable = new CountDownLatch(counter);
            this.retryAfter = retryAfter;
            this.location = location;
        }

        @Override
        public void service(Request request, Response response) throws Exception {
            if (unavailable.getCount() > 0) {
                response.setStatus(SERVICE_UNAVAILABLE);
                response.setHeader(UpgradeResponse.RETRY_AFTER, retryAfter);
                unavailable.countDown();
            } else {
                response.setStatus(HttpStatus.MOVED_PERMANENTLY_301);
                response.setHeader(UpgradeResponse.LOCATION, location);
            }
        }
    }


    /**
     * Echo endpoint for string messages.
     *
     * @author Ondrej Kosatka (ondrej.kosatka at oracle.com)
     */
    @ServerEndpoint("/echo")
    public static class RetryAfterEchoEndpoint {

        @OnMessage
        public String onMessage(String s) {
            return s;
        }
    }
}