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
import java.util.Arrays;
import java.util.List;
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
import org.glassfish.tyrus.client.RedirectException;
import org.glassfish.tyrus.client.auth.AuthenticationException;
import org.glassfish.tyrus.core.HandshakeException;
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Redirect tests.
 *
 * @author Ondrej Kosatka (ondrej.kosatka at oracle.com)
 */
public class RedirectTest extends TestContainer {

    private static final int REDIRECTION_PORT = 8026;
    private static final String REDIRECTION_URI = "ws://localhost:" + REDIRECTION_PORT;
    private static final String REDIRECTION_PATH = "/redirect";
    private static final List<HttpStatus> statuses = Arrays.asList(
            HttpStatus.MULTIPLE_CHOICES_300,
            HttpStatus.MOVED_PERMANENTLY_301,
            HttpStatus.FOUND_302,
            HttpStatus.SEE_OTHER_303,
            HttpStatus.TEMPORARY_REDIRECT_307,
            HttpStatus.PERMANENT_REDIRECT_308
    );

    private static final HttpStatus UNSUPPORTED_HTTP_STATUS = HttpStatus.NOT_MODIFIED_304;

    public RedirectTest() {
        setContextPath("/redirect-echo");
    }

    @Test
    public void testRedirect() throws InterruptedException, DeploymentException, AuthenticationException, IOException {
        Server server = null;
        try {
            server = startServer(RedirectedEchoEndpoint.class);

            for (HttpStatus httpstatus : statuses) {
                testRedirect(httpstatus);
            }
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    private void testRedirect(HttpStatus httpStatus) throws DeploymentException, InterruptedException, IOException, AuthenticationException {
        HttpServer httpServer = null;
        try {

            httpServer = startHttpRedirectionServer(REDIRECTION_PORT, httpStatus, "ws://localhost:8025/redirect-echo/echo");

            final ClientManager client = ClientManager.createClient();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            client.getProperties().put(ClientProperties.REDIRECT_ENABLED, true);

            final CountDownLatch messageLatch = new CountDownLatch(1);

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
            }, cec, URI.create(REDIRECTION_URI + REDIRECTION_PATH));

            messageLatch.await(1, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());
        } finally {
            if (httpServer != null) {
                httpServer.shutdownNow();
            }
        }
    }

    @Test
    public void testRelativePath() throws InterruptedException, DeploymentException, AuthenticationException, IOException {
        Server server = null;
        try {
            server = startServer(RedirectedEchoEndpoint.class);

            for (HttpStatus httpstatus : statuses) {
                testRelativePath(httpstatus);
            }
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    private void testRelativePath(HttpStatus httpStatus) throws InterruptedException, IOException, AuthenticationException, DeploymentException {
        HttpServer httpServer = null;
        try {
            httpServer = createHttpServer(REDIRECTION_PORT);
            httpServer.getServerConfiguration().addHttpHandler(new RedirectHandler(httpStatus, REDIRECTION_PATH + 1), REDIRECTION_PATH + 0);
            httpServer.getServerConfiguration().addHttpHandler(new RedirectHandler(httpStatus, REDIRECTION_URI + REDIRECTION_PATH + 2), REDIRECTION_PATH + 1);
            httpServer.getServerConfiguration().addHttpHandler(new RedirectHandler(httpStatus, "/la/la/la/.././../.." + REDIRECTION_PATH + 3), REDIRECTION_PATH + 2);
            httpServer.getServerConfiguration().addHttpHandler(new RedirectHandler(httpStatus, "http://127.0.0.1:8026" + REDIRECTION_PATH + 4), REDIRECTION_PATH + 3);
            httpServer.getServerConfiguration().addHttpHandler(new RedirectHandler(httpStatus, "ws://localhost:8025/redirect-echo/echo"), REDIRECTION_PATH + 4);
            httpServer.start();

            final ClientManager client = createClient();
            client.getProperties().put(ClientProperties.REDIRECT_ENABLED, true);
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            final CountDownLatch messageLatch = new CountDownLatch(1);

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
            }, cec, URI.create(REDIRECTION_URI + REDIRECTION_PATH + 0));

            messageLatch.await(1, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());
        } finally {
            if (httpServer != null) {
                httpServer.shutdownNow();
            }
        }
    }

    @Test
    public void testRedirectUnsupported3xx() throws InterruptedException, DeploymentException, AuthenticationException, IOException {
        Server server = null;
        try {
            server = startServer(RedirectedEchoEndpoint.class);

            testRedirectUnsupported3xx(UNSUPPORTED_HTTP_STATUS);
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    private void testRedirectUnsupported3xx(HttpStatus httpStatus) throws InterruptedException, IOException, AuthenticationException {
        HttpServer httpServer = null;
        try {
            httpServer = startHttpRedirectionServer(REDIRECTION_PORT, httpStatus, "ws://localhost:8025/redirect-echo/echo");

            final CountDownLatch messageLatch = new CountDownLatch(1);

            final ClientManager client = createClient();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            client.getProperties().put(ClientProperties.REDIRECT_ENABLED, true);

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
            }, cec, URI.create(REDIRECTION_URI + REDIRECTION_PATH));

            messageLatch.await(1, TimeUnit.SECONDS);
            assertTrue("Redirect for this 3xx code is not supported. HandshakeException must be thrown.", false);
        } catch (DeploymentException e) {
            assertTrue("Redirect for this 3xx code is not supported. HandshakeException must be thrown.", e.getCause() instanceof HandshakeException);
        } finally {
            if (httpServer != null) {
                httpServer.shutdownNow();
            }
        }
    }

    @Test
    public void testRedirectNotAllowed() throws InterruptedException, DeploymentException, AuthenticationException, IOException {
        Server server = null;
        try {
            server = startServer(RedirectedEchoEndpoint.class);

            for (HttpStatus httpstatus : statuses) {
                testRedirectNotAllowed(httpstatus);
            }
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    private void testRedirectNotAllowed(HttpStatus httpStatus) throws InterruptedException, IOException, AuthenticationException {
        HttpServer httpServer = null;
        try {
            httpServer = startHttpRedirectionServer(REDIRECTION_PORT, httpStatus, "ws://localhost:8025/redirect-echo/echo");

            final CountDownLatch messageLatch = new CountDownLatch(1);

            final ClientManager client = createClient();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            client.getProperties().put(ClientProperties.REDIRECT_ENABLED, false);

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
            }, cec, URI.create(REDIRECTION_URI + REDIRECTION_PATH));

            messageLatch.await(1, TimeUnit.SECONDS);
            assertTrue("Redirect is not allowed. RedirectException must be thrown.", false);
        } catch (DeploymentException e) {
            assertTrue("Redirect is not allowed. RedirectException must be thrown.", e.getCause() instanceof RedirectException);
        } finally {
            if (httpServer != null) {
                httpServer.shutdownNow();
            }
        }
    }

    @Test
    public void testRedirectNotAllowedByDefault() throws InterruptedException, DeploymentException, AuthenticationException, IOException {
        Server server = null;
        try {
            server = startServer(RedirectedEchoEndpoint.class);

            for (HttpStatus httpstatus : statuses) {
                testRedirectNotAllowedByDefault(httpstatus);
            }
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    private void testRedirectNotAllowedByDefault(HttpStatus httpStatus) throws InterruptedException, IOException, AuthenticationException {
        HttpServer httpServer = null;
        try {

            httpServer = startHttpRedirectionServer(REDIRECTION_PORT, httpStatus, "ws://localhost:8025/redirect-echo/echo");

            final CountDownLatch messageLatch = new CountDownLatch(1);

            final ClientManager client = createClient();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

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
            }, cec, URI.create(REDIRECTION_URI + REDIRECTION_PATH));

            messageLatch.await(1, TimeUnit.SECONDS);
            assertTrue("Redirect is not allowed. RedirectException must be thrown.", false);
        } catch (DeploymentException e) {
            assertTrue("Redirect is not allowed. RedirectException must be thrown.", e.getCause() instanceof RedirectException);
        } finally {
            if (httpServer != null) {
                httpServer.shutdownNow();
            }
        }
    }

    @Test
    public void testRedirectLoop() throws InterruptedException, DeploymentException, AuthenticationException, IOException {
        Server server = null;
        try {
            server = startServer(RedirectedEchoEndpoint.class);

            for (HttpStatus httpstatus : statuses) {
                testRedirectLoop(httpstatus);
            }
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    private void testRedirectLoop(HttpStatus httpStatus) throws InterruptedException, IOException, AuthenticationException {
        HttpServer httpServer = null;
        try {
            httpServer = startHttpRedirectionServer(REDIRECTION_PORT, httpStatus, REDIRECTION_URI + REDIRECTION_PATH);

            final CountDownLatch messageLatch = new CountDownLatch(1);

            final ClientManager client = createClient();
            client.getProperties().put(ClientProperties.REDIRECT_ENABLED, true);
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

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
            }, cec, URI.create(REDIRECTION_URI + REDIRECTION_PATH));

            assertTrue("Redirect loop must cause RedirectException", false);
        } catch (DeploymentException e) {
            assertTrue("Redirect loop must cause RedirectException", e.getCause() instanceof RedirectException);
        } finally {
            if (httpServer != null) {
                httpServer.shutdownNow();
            }
        }

    }

    @Test
    public void testMaxRedirectionExceed() throws InterruptedException, DeploymentException, AuthenticationException, IOException {
        Server server = null;
        try {
            server = startServer(RedirectedEchoEndpoint.class);

            for (HttpStatus httpstatus : statuses) {
                testMaxRedirectionExceed(httpstatus);
            }
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    private void testMaxRedirectionExceed(HttpStatus httpStatus) throws InterruptedException, IOException, AuthenticationException {
        HttpServer httpServer = null;
        try {
            httpServer = createHttpServer(REDIRECTION_PORT);
            httpServer.getServerConfiguration().addHttpHandler(new RedirectHandler(httpStatus, REDIRECTION_URI + REDIRECTION_PATH + 1), REDIRECTION_PATH + 0);
            httpServer.getServerConfiguration().addHttpHandler(new RedirectHandler(httpStatus, REDIRECTION_URI + REDIRECTION_PATH + 2), REDIRECTION_PATH + 1);
            httpServer.getServerConfiguration().addHttpHandler(new RedirectHandler(httpStatus, REDIRECTION_URI + REDIRECTION_PATH + 3), REDIRECTION_PATH + 2);
            httpServer.getServerConfiguration().addHttpHandler(new RedirectHandler(httpStatus, REDIRECTION_URI + REDIRECTION_PATH + 4), REDIRECTION_PATH + 3);
            httpServer.getServerConfiguration().addHttpHandler(new RedirectHandler(httpStatus, REDIRECTION_URI + REDIRECTION_PATH + 5), REDIRECTION_PATH + 4);
            httpServer.getServerConfiguration().addHttpHandler(new RedirectHandler(httpStatus, REDIRECTION_URI + REDIRECTION_PATH + 6), REDIRECTION_PATH + 5);
            httpServer.start();

            final ClientManager client = createClient();
            client.getProperties().put(ClientProperties.REDIRECT_ENABLED, true);
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                System.out.println("received message: " + message);
                                assertEquals(message, "Do or do not, there is no try.");
                            }
                        });

                        session.getBasicRemote().sendText("Do or do not, there is no try.");
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }, cec, URI.create(REDIRECTION_URI + REDIRECTION_PATH + 0));

            assertTrue("Too much redirection must cause RedirectException", false);
        } catch (DeploymentException e) {
            assertTrue("Too much redirection must cause RedirectException", e.getCause() instanceof RedirectException);
        } finally {
            if (httpServer != null) {
                httpServer.shutdownNow();
            }
        }
    }

    @Test
    public void testMaxRedirectionConfig() throws InterruptedException, DeploymentException, AuthenticationException, IOException {
        Server server = null;
        try {
            server = startServer(RedirectedEchoEndpoint.class);

            for (HttpStatus httpstatus : statuses) {
                testMaxRedirectionConfig(httpstatus);
            }
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    private void testMaxRedirectionConfig(HttpStatus httpStatus) throws InterruptedException, IOException, AuthenticationException {
        HttpServer httpServer = null;
        try {
            httpServer = createHttpServer(REDIRECTION_PORT);
            httpServer.getServerConfiguration().addHttpHandler(new RedirectHandler(httpStatus, REDIRECTION_URI + REDIRECTION_PATH + 1), REDIRECTION_PATH + 0);
            httpServer.getServerConfiguration().addHttpHandler(new RedirectHandler(httpStatus, REDIRECTION_URI + REDIRECTION_PATH + 2), REDIRECTION_PATH + 1);
            httpServer.getServerConfiguration().addHttpHandler(new RedirectHandler(httpStatus, REDIRECTION_URI + REDIRECTION_PATH + 3), REDIRECTION_PATH + 2);
            httpServer.start();

            final ClientManager client = createClient();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            client.getProperties().put(ClientProperties.REDIRECT_ENABLED, true);
            client.getProperties().put(ClientProperties.REDIRECT_THRESHOLD, 2);

            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                System.out.println("received message: " + message);
                                assertEquals(message, "Do or do not, there is no try.");
                            }
                        });

                        session.getBasicRemote().sendText("Do or do not, there is no try.");
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }, cec, URI.create(REDIRECTION_URI + REDIRECTION_PATH + 0));

            assertTrue("Too much redirection must cause RedirectException", false);
        } catch (DeploymentException e) {
            assertTrue("Too much redirection must cause RedirectException", e.getCause() instanceof RedirectException);
        } finally {
            if (httpServer != null) {
                httpServer.shutdownNow();
            }
        }
    }

    @Test
    public void testMaxRedirectionConfigNeg() throws InterruptedException, DeploymentException, AuthenticationException, IOException {
        Server server = null;
        try {
            server = startServer(RedirectedEchoEndpoint.class);

            for (HttpStatus httpstatus : statuses) {
                testMaxRedirectionConfigNeg(httpstatus);
            }
        } finally {
            if (server != null) {
                server.stop();
            }
        }
        for (HttpStatus httpstatus : statuses) {
            testMaxRedirectionConfigNeg(httpstatus);
        }
    }

    private void testMaxRedirectionConfigNeg(HttpStatus httpStatus) throws InterruptedException, IOException, AuthenticationException {

        HttpServer httpServer = null;
        try {

            httpServer = createHttpServer(REDIRECTION_PORT);
            httpServer.getServerConfiguration().addHttpHandler(new RedirectHandler(httpStatus, REDIRECTION_URI + REDIRECTION_PATH + 1), REDIRECTION_PATH + 0);
            httpServer.start();

            final ClientManager client = createClient();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            client.getProperties().put(ClientProperties.REDIRECT_ENABLED, true);
            client.getProperties().put(ClientProperties.REDIRECT_THRESHOLD, -2);

            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                System.out.println("received message: " + message);
                                assertEquals(message, "Do or do not, there is no try.");
                            }
                        });

                        session.getBasicRemote().sendText("Do or do not, there is no try.");
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }, cec, URI.create(REDIRECTION_URI + REDIRECTION_PATH + 0));

            assertTrue("Too much redirection must cause RedirectException", false);
        } catch (DeploymentException e) {
            assertTrue("Too much redirection must cause RedirectException", e.getCause() instanceof RedirectException);
        } finally {
            if (httpServer != null) {
                httpServer.shutdownNow();
            }
        }
    }

    @Test
    public void testRedirectMissingLocation() throws InterruptedException, DeploymentException, AuthenticationException, IOException {
        Server server = null;
        try {
            server = startServer(RedirectedEchoEndpoint.class);

            for (HttpStatus httpstatus : statuses) {
                testRedirectMissingLocation(httpstatus);
            }
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    private void testRedirectMissingLocation(HttpStatus httpStatus) throws InterruptedException, IOException, AuthenticationException {

        HttpServer httpServer = null;
        try {

            httpServer = createHttpServer(REDIRECTION_PORT);
            httpServer.getServerConfiguration().addHttpHandler(new BadRedirectHandler(httpStatus), REDIRECTION_PATH);
            httpServer.start();

            final ClientManager client = createClient();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            client.getProperties().put(ClientProperties.REDIRECT_ENABLED, true);

            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                System.out.println("received message: " + message);
                                assertEquals(message, "Do or do not, there is no try.");
                            }
                        });

                        session.getBasicRemote().sendText("Do or do not, there is no try.");
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }, cec, URI.create(REDIRECTION_URI + REDIRECTION_PATH));

            assertTrue("Missing location must cause RedirectException", false);
        } catch (DeploymentException e) {
            assertTrue("Missing location must cause RedirectException", e.getCause() instanceof RedirectException);
        } finally {
            if (httpServer != null) {
                httpServer.shutdownNow();
            }
        }
    }

    @Test
    public void testRedirectEmptyLocation() throws InterruptedException, DeploymentException, AuthenticationException, IOException {
        Server server = null;
        try {
            server = startServer(RedirectedEchoEndpoint.class);

            for (HttpStatus httpstatus : statuses) {
                testRedirectEmptyLocation(httpstatus);
            }
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    private void testRedirectEmptyLocation(HttpStatus httpStatus) throws InterruptedException, IOException, AuthenticationException {

        HttpServer httpServer = null;
        try {

            httpServer = createHttpServer(REDIRECTION_PORT);
            httpServer.getServerConfiguration().addHttpHandler(new RedirectHandler(httpStatus, ""), REDIRECTION_PATH);
            httpServer.start();

            final ClientManager client = createClient();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            client.getProperties().put(ClientProperties.REDIRECT_ENABLED, true);

            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                System.out.println("received message: " + message);
                                assertEquals(message, "Do or do not, there is no try.");
                            }
                        });

                        session.getBasicRemote().sendText("Do or do not, there is no try.");
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }, cec, URI.create(REDIRECTION_URI + REDIRECTION_PATH));

            assertTrue("Missing location must cause RedirectException", false);
        } catch (DeploymentException e) {
            assertTrue("Missing location must cause RedirectException", e.getCause() instanceof RedirectException);
        } finally {
            if (httpServer != null) {
                httpServer.shutdownNow();
            }
        }
    }

    private HttpServer startHttpRedirectionServer(int port, HttpStatus httpStatus, String location) throws IOException {
        HttpServer httpServer = createHttpServer(port);

        httpServer.getServerConfiguration().addHttpHandler(new RedirectHandler(httpStatus, location), REDIRECTION_PATH);

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

    private class RedirectHandler extends HttpHandler {

        private final HttpStatus statusCode;
        private String location;

        public RedirectHandler(HttpStatus statusCode, String newLocation) {
            this.statusCode = statusCode;
            this.location = newLocation;
        }

        @Override
        public void service(Request request, Response response) throws Exception {
            response.setStatus(statusCode);
            response.setHeader(UpgradeResponse.LOCATION, location);
        }
    }

    private class BadRedirectHandler extends HttpHandler {

        private final HttpStatus statusCode;

        public BadRedirectHandler(HttpStatus statusCode) {
            this.statusCode = statusCode;
        }

        @Override
        public void service(Request request, Response response) throws Exception {
            response.setStatus(statusCode);
        }
    }

    /**
     * Echo endpoint for string messages.
     *
     * @author Ondrej Kosatka (ondrej.kosatka at oracle.com)
     */
    @ServerEndpoint("/echo")
    public static class RedirectedEchoEndpoint {

        @OnMessage
        public String onMessage(String s) {
            return s;
        }
    }
}