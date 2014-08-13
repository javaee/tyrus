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

package org.glassfish.tyrus.tests.servlet.debug;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Formatter;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.HandshakeResponse;
import javax.websocket.MessageHandler;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.client.auth.Credentials;
import org.glassfish.tyrus.core.Base64Utils;
import org.glassfish.tyrus.core.TyrusWebSocketEngine;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.spi.UpgradeRequest;
import org.glassfish.tyrus.spi.UpgradeResponse;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Does not automatically test anything, it only servers as a sample use of the debug mode or for manual testing of the debug mode.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class DebugSampleTest extends TestContainer {

    String loggingConfigPath = new File(DebugSampleTest.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getPath();

    public DebugSampleTest() {
        setContextPath("/samples-debug");

        System.setProperty("java.util.logging.config.file", loggingConfigPath + "/logging.properties");
    }

    @Test
    public void testMatch() throws DeploymentException, InterruptedException, IOException {

        final Server server = startServer(Endpoint1.class, Endpoint2.class, Endpoint3.class, Endpoint4.class, Endpoint5.class, Endpoint6.class);
        final CountDownLatch onOpenLatch = new CountDownLatch(1);

        try {
            final ClientManager client = createClient();

            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    onOpenLatch.countDown();
                }

            }, ClientEndpointConfig.Builder.create().build(), getURI("/endpoint/a/b"));

            assertTrue(onOpenLatch.await(5, TimeUnit.SECONDS));
            // wait for all messages to be logged
            Thread.sleep(1000);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void test404() throws DeploymentException, InterruptedException, IOException {

        final Server server = startServer(Endpoint1.class, Endpoint2.class, Endpoint3.class, Endpoint4.class, Endpoint5.class, Endpoint6.class);
        final CountDownLatch onOpenLatch = new CountDownLatch(1);

        try {
            final ClientManager client = createClient();

            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    onOpenLatch.countDown();
                }

            }, ClientEndpointConfig.Builder.create().build(), getURI("/endpoint/b"));

            fail();

        } catch (Exception e) {
            // do nothing
        } finally {
            stopServer(server);
            // wait for all messages to be logged
            Thread.sleep(1000);
        }
    }

    @Test
    public void testDeploy() throws DeploymentException, InterruptedException, IOException {

        final Server server = startServer(Endpoint1.class, Endpoint2.class, Endpoint3.class, Endpoint4.class, Endpoint5.class, Endpoint6.class);
        final CountDownLatch onOpenLatch = new CountDownLatch(1);

        try {
            final ClientManager client = createClient();

            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    onOpenLatch.countDown();
                }

            }, ClientEndpointConfig.Builder.create().build(), getURI("/endpoint/a/b"));

            assertTrue(onOpenLatch.await(3, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
            // wait for all messages to be logged
            Thread.sleep(1000);
        }
    }

    @Test
    public void testHttpLoggingConfiguration() throws DeploymentException, InterruptedException, IOException {
        System.setProperty("java.util.logging.config.file", loggingConfigPath + "/emptyLogging.properties");
        LogManager.getLogManager().readConfiguration();


        final Server server = startServer(Endpoint1.class, Endpoint2.class, Endpoint3.class, Endpoint4.class, Endpoint5.class, Endpoint6.class);
        final CountDownLatch onOpenLatch = new CountDownLatch(1);

        try {
            final ClientManager client = createClient();
            client.getProperties().put(ClientProperties.LOG_UPGRADE_MESSAGES, true);

            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    onOpenLatch.countDown();
                }

            }, ClientEndpointConfig.Builder.create().build(), getURI("/endpoint/a/b"));

            assertTrue(onOpenLatch.await(3, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
            // wait for all messages to be logged
            Thread.sleep(1000);
            System.setProperty("java.util.logging.config.file", loggingConfigPath + "/logging.properties");
            LogManager.getLogManager().readConfiguration();
        }
    }

    @Test
    public void testClientAuthenticationLogging() throws DeploymentException, InterruptedException, IOException {
        final CountDownLatch onOpenLatch = new CountDownLatch(1);
        HttpServer server = getAuthenticationServer();
        server.start();
        try {
            final ClientManager client = createClient();
            client.getProperties().put(ClientProperties.CREDENTIALS, new Credentials("Petr", "My secret password"));

            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    onOpenLatch.countDown();
                }

            }, ClientEndpointConfig.Builder.create().build(), URI.create("ws://localhost:8025/testAuthentication"));

            assertTrue(onOpenLatch.await(3, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            server.shutdown();
            // wait for all messages to be logged
            Thread.sleep(1000);
        }
    }

    @Test
    public void testMessages() throws DeploymentException, InterruptedException, IOException {
        final Server server = startServer(EchoEndpoint.class);
        final CountDownLatch stringMessageLatch = new CountDownLatch(1);
        final CountDownLatch binaryMessageLatch = new CountDownLatch(1);

        try {
            final ClientManager client = createClient();

            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {

                        @Override
                        public void onMessage(String m) {
                            stringMessageLatch.countDown();
                        }
                    });

                    session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {

                        @Override
                        public void onMessage(ByteBuffer m) {
                            binaryMessageLatch.countDown();
                        }
                    });

                    try {
                        session.getBasicRemote().sendText("Hello");
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap("Hello".getBytes()));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            }, ClientEndpointConfig.Builder.create().build(), getURI(EchoEndpoint.class));

            assertTrue(stringMessageLatch.await(3, TimeUnit.SECONDS));
            assertTrue(binaryMessageLatch.await(3, TimeUnit.SECONDS));

        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
            // wait for all messages to be logged
            Thread.sleep(1000);
        }
    }

    @Test
    public void test404Trace() throws DeploymentException, InterruptedException, IOException {

        getServerProperties().put(TyrusWebSocketEngine.TRACING_TYPE, "ON_DEMAND");
        final Server server = startServer(Endpoint1.class, Endpoint2.class, Endpoint3.class, Endpoint4.class, Endpoint5.class, Endpoint6.class);

        try {
            final ClientManager client = createClient();

            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig endpointConfig) {
                }
            }, getTraceConfigurator(new CountDownLatch(1)), getURI("/endpoint/b"));

            fail();
        } catch (Exception e) {
            //  do nothing - the exception is expected
        } finally {
            stopServer(server);
            // wait for all messages to be logged
            Thread.sleep(1000);
        }
    }

    @Test
    public void testMatchTrace() throws DeploymentException, InterruptedException, IOException {

        getServerProperties().put(TyrusWebSocketEngine.TRACING_TYPE, "on_demand");
        final Server server = startServer(Endpoint1.class, Endpoint2.class, Endpoint3.class, Endpoint4.class, Endpoint5.class, Endpoint6.class);

        CountDownLatch traceHeaderLatch = new CountDownLatch(1);
        try {
            final ClientManager client = createClient();
            client.getProperties().put(ClientProperties.LOG_UPGRADE_MESSAGES, true);
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig endpointConfig) {

                }
            }, getTraceConfigurator(traceHeaderLatch), getURI(Endpoint4.class));

            assertTrue(traceHeaderLatch.await(1, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
            // wait for all messages to be logged
            Thread.sleep(1000);
        }
    }

    @Test
    public void testMatchTraceAll() throws DeploymentException, InterruptedException, IOException {

        getServerProperties().put(TyrusWebSocketEngine.TRACING_TYPE, "ALL");
        final Server server = startServer(Endpoint1.class, Endpoint2.class, Endpoint3.class, Endpoint4.class, Endpoint5.class, Endpoint6.class);

        try {
            final ClientManager client = createClient();
            CountDownLatch traceHeaderLatch = new CountDownLatch(1);

            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig endpointConfig) {

                }
            }, getTraceConfigurator(traceHeaderLatch), getURI(Endpoint4.class));

            assertTrue(traceHeaderLatch.await(5, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
            // wait for all messages to be logged
            Thread.sleep(1000);
        }
    }

    @Test
    public void testErrorTrace() throws DeploymentException, InterruptedException, IOException {

        getServerProperties().put(TyrusWebSocketEngine.TRACING_TYPE, "all");
        final Server server = startServer(Endpoint1.class, Endpoint2.class, Endpoint3.class, Endpoint4.class, Endpoint5.class, Endpoint6.class);

        try {
            final ClientManager client = createClient();
            client.getProperties().put(ClientProperties.LOG_UPGRADE_MESSAGES, true);
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig endpointConfig) {

                }
            }, getWebsocketVersion12Configurator(), getURI(Endpoint4.class));

            fail();
        } catch (Exception e) {
            //  do nothing - the exception is expected
        } finally {
            stopServer(server);
            // wait for all messages to be logged
            Thread.sleep(1000);
        }
    }

    private HttpServer getAuthenticationServer() throws IOException {
        HttpServer server = HttpServer.createSimpleServer("/testAuthentication", getHost(), getPort());
        server.getServerConfiguration().addHttpHandler(
                new HttpHandler() {

                    boolean authenticated = false;

                    public void service(Request request, Response response) throws Exception {
                        if (authenticated) {
                            response.setStatus(101);

                            response.addHeader(UpgradeRequest.CONNECTION, UpgradeRequest.UPGRADE);
                            response.addHeader(UpgradeRequest.UPGRADE, UpgradeRequest.WEBSOCKET);

                            String secKey = request.getHeader(HandshakeRequest.SEC_WEBSOCKET_KEY);
                            String key = secKey + UpgradeRequest.SERVER_KEY_HASH;

                            MessageDigest instance;
                            try {
                                instance = MessageDigest.getInstance("SHA-1");
                                instance.update(key.getBytes("UTF-8"));
                                final byte[] digest = instance.digest();
                                String responseKey = Base64Utils.encodeToString(digest, false);

                                response.addHeader(UpgradeResponse.SEC_WEBSOCKET_ACCEPT, responseKey);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        } else {
                            authenticated = true;
                            response.setStatus(401);
                            response.addHeader(UpgradeResponse.WWW_AUTHENTICATE, "Basic realm=\"my realm\"");
                            response.addHeader(UpgradeRequest.UPGRADE, UpgradeRequest.WEBSOCKET);
                        }
                    }
                }
        );
        return server;
    }

    public static class DebugTestFormatter extends Formatter {

        @Override
        public String format(LogRecord record) {
            return record.getMessage() + "\n";
        }
    }

    @ServerEndpoint("/endpoint/{a}/b")
    public static class Endpoint1 {
    }

    @ServerEndpoint("/endpoint/{a}/{b}")
    public static class Endpoint2 {
    }

    @ServerEndpoint("/endpoint/a/{b}")
    public static class Endpoint3 {
    }

    @ServerEndpoint("/endpoint/a/b")
    public static class Endpoint4 {
    }

    @ServerEndpoint("/endpoint/a")
    public static class Endpoint5 {
    }

    @ServerEndpoint("/endpoint/a/a")
    public static class Endpoint6 {
    }

    @ServerEndpoint("/endpoint/echo")
    public static class EchoEndpoint {

        @OnMessage
        public void onMessage(String message, Session session) throws IOException {
            session.getBasicRemote().sendText(message + " (from the server)");
        }

        @OnMessage
        public void onMessage(ByteBuffer message, Session session) {
            session.getAsyncRemote().sendBinary(message);
        }
    }

    private ClientEndpointConfig getTraceConfigurator(final CountDownLatch traceHeaderLatch) {
        return ClientEndpointConfig.Builder.create().configurator(new ClientEndpointConfig.Configurator() {
            @Override
            public void beforeRequest(Map<String, List<String>> headers) {
                headers.put(UpgradeRequest.ENABLE_TRACING_HEADER, Arrays.asList("Whatever"));
                headers.put(UpgradeRequest.TRACING_THRESHOLD, Arrays.asList("SUMMARY"));
            }

            @Override
            public void afterResponse(HandshakeResponse hr) {
                for (Map.Entry<String, List<String>> header : hr.getHeaders().entrySet()) {
                    if (header.getKey().contains(UpgradeResponse.TRACING_HEADER_PREFIX.toLowerCase())) {
                        traceHeaderLatch.countDown();
                    }
                }
            }
        }).build();
    }

    private ClientEndpointConfig getWebsocketVersion12Configurator() {
        return ClientEndpointConfig.Builder.create().configurator(new ClientEndpointConfig.Configurator() {
            @Override
            public void beforeRequest(Map<String, List<String>> headers) {
                headers.put(HandshakeRequest.SEC_WEBSOCKET_VERSION, Arrays.asList("12"));
            }
        }).build();
    }
}
