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
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests life cycle of executor services managed by the client manager.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class ClientExecutorsManagementTest extends TestContainer {

    /**
     * Test basic executor services life cycle.
     */
    @Test
    public void testBasicLifecycle() {
        Server server = null;
        try {
            server = startServer(AnnotatedServerEndpoint.class);
            ClientManager clientManager1 = createClient();
            Session session1 = clientManager1.connectToServer(AnnotatedClientEndpoint.class, getURI(AnnotatedServerEndpoint.class));
            ExecutorService executorService1 = clientManager1.getExecutorService();
            ScheduledExecutorService scheduledExecutorService1 = clientManager1.getScheduledExecutorService();

            ClientManager clientManager2 = createClient();
            Session session2 = clientManager2.connectToServer(AnnotatedClientEndpoint.class, getURI(AnnotatedServerEndpoint.class));
            ExecutorService executorService2 = clientManager2.getExecutorService();
            ScheduledExecutorService scheduledExecutorService2 = clientManager2.getScheduledExecutorService();

            Session session3 = clientManager1.connectToServer(AnnotatedClientEndpoint.class, getURI(AnnotatedServerEndpoint.class));
            ExecutorService executorService3 = clientManager1.getExecutorService();
            ScheduledExecutorService scheduledExecutorService3 = clientManager1.getScheduledExecutorService();

            // executors from the same container should be the same
            assertTrue(executorService1 == executorService3);
            assertTrue(scheduledExecutorService1 == scheduledExecutorService3);

            assertTrue(executorService1 != executorService2);
            assertTrue(scheduledExecutorService1 != scheduledExecutorService2);

            assertFalse(executorService1.isShutdown());
            assertFalse(scheduledExecutorService1.isShutdown());

            assertFalse(executorService2.isShutdown());
            assertFalse(scheduledExecutorService2.isShutdown());

            session1.close();
            session2.close();

            assertTrue(executorService2.isShutdown());
            assertTrue(scheduledExecutorService2.isShutdown());

            // closing session1 should not close executorService1 and scheduledExecutorService1 as it is still used by session3
            assertFalse(executorService1.isShutdown());
            assertFalse(scheduledExecutorService1.isShutdown());

            session3.close();

            assertTrue(executorService1.isShutdown());
            assertTrue(scheduledExecutorService1.isShutdown());
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
        }
    }

    /**
     * Test that different situations that can cause connect to fail will not cause container resources not to be freed.
     * <p/>
     * (Client manager counts active connections. This test tests, that connection failures caused by different situations
     * are registered by the connection counter.)
     */
    @Test
    public void testConnectionFail() {
        Server server = null;
        try {
            server = startServer(AnnotatedServerEndpoint.class);
            ClientManager clientManager = createClient();

            Session session = clientManager.connectToServer(AnnotatedClientEndpoint.class, getURI(AnnotatedServerEndpoint.class));
            try {
                clientManager.connectToServer(FaultyEndpoint.class, getURI(AnnotatedServerEndpoint.class));
                fail();
            } catch (Exception e) {
                // exception is expected
            }

            try {
                clientManager.connectToServer(AnnotatedClientEndpoint.class, URI.create("ws://nonExistentServer.com"));
                fail();
            } catch (Exception e) {
                // exception is expected
            }

            try {
                clientManager.connectToServer(AnnotatedClientEndpoint.class, getURI("/nonExistentEndpoint"));
                fail();
            } catch (Exception e) {
                // exception is expected
            }

            CountDownLatch blockResponseLatch = new CountDownLatch(1);
            HttpServer lazyServer = getLazyServer(blockResponseLatch);
            clientManager.getProperties().put(ClientProperties.HANDSHAKE_TIMEOUT, 2000);
            try {
                clientManager.connectToServer(AnnotatedClientEndpoint.class, URI.create("ws://localhost:8025/lazyServer"));
                fail();
            } catch (Exception e) {
                // exception is expected
            } finally {
                blockResponseLatch.countDown();
                lazyServer.shutdown();
            }

            ExecutorService executorService = clientManager.getExecutorService();
            ScheduledExecutorService scheduledExecutorService = clientManager.getScheduledExecutorService();

            assertFalse(executorService.isShutdown());
            assertFalse(scheduledExecutorService.isShutdown());

            // closing the only successfully established connection should cause the executors to be released
            session.close();

            assertTrue(executorService.isShutdown());
            assertTrue(scheduledExecutorService.isShutdown());
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
        }
    }

    /**
     * Test that if executor services have been destroyed, new ones will be created if the client manager creates new connections.
     */
    @Test
    public void testConnectAfterClose() {
        Server server = null;
        try {
            server = startServer(AnnotatedServerEndpoint.class);
            ClientManager clientManager = createClient();

            Session session = clientManager.connectToServer(AnnotatedClientEndpoint.class, getURI(AnnotatedServerEndpoint.class));
            session.close();

            Session session2 = clientManager.connectToServer(AnnotatedClientEndpoint.class, getURI(AnnotatedServerEndpoint.class));

            ExecutorService executorService = clientManager.getExecutorService();
            ScheduledExecutorService scheduledExecutorService = clientManager.getScheduledExecutorService();

            assertFalse(executorService.isShutdown());
            assertFalse(scheduledExecutorService.isShutdown());

            session2.close();

            assertTrue(executorService.isShutdown());
            assertTrue(scheduledExecutorService.isShutdown());
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
        }
    }

    /**
     * Test that executor services get destroyed if reconnect is used.
     */
    @Test
    public void testReconnect() {
        Server server = null;
        try {
            server = startServer(ReconnectServerEndpoint.class);
            ClientManager clientManager = createClient();
            clientManager.getProperties().put(ClientProperties.RECONNECT_HANDLER, new ClientManager.ReconnectHandler() {
                private int counter = 0;

                @Override
                public boolean onDisconnect(CloseReason closeReason) {
                    counter++;

                    // reconnect once
                    if (counter < 2) {
                        return true;
                    }

                    return false;
                }

                @Override
                public long getDelay() {
                    return 0;
                }
            });

            final AtomicReference<Session> session = new AtomicReference<Session>();
            // connect once and reconnect once
            final CountDownLatch onOpenLatch = new CountDownLatch(2);
            clientManager.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session s, EndpointConfig config) {
                    session.set(s);
                    onOpenLatch.countDown();
                }
            }, getURI(ReconnectServerEndpoint.class));

            ExecutorService executorService = clientManager.getExecutorService();
            ScheduledExecutorService scheduledExecutorService = clientManager.getScheduledExecutorService();

            // force reconnect
            session.get().getBasicRemote().sendText("Close");
            assertTrue(onOpenLatch.await(5, TimeUnit.SECONDS));

            ExecutorService executorService2 = clientManager.getExecutorService();
            ScheduledExecutorService scheduledExecutorService2 = clientManager.getScheduledExecutorService();

            assertFalse(executorService2.isShutdown());
            assertFalse(scheduledExecutorService2.isShutdown());

            session.get().close();

            assertTrue(executorService.isShutdown());
            assertTrue(scheduledExecutorService.isShutdown());
            assertTrue(executorService2.isShutdown());
            assertTrue(scheduledExecutorService2.isShutdown());
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
        }
    }

    /**
     * Test that calling shut down on client manager does not clash with automatic executors management and does not
     * cause an error when there are still some sessions open.
     */
    @Test
    public void explicitShutDownTest() {
        Server server = null;
        try {
            server = startServer(AnnotatedServerEndpoint.class);
            ClientManager clientManager = createClient();
            Session session = clientManager.connectToServer(AnnotatedClientEndpoint.class, getURI(AnnotatedServerEndpoint.class));
            clientManager.shutdown();
            session.close();
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
        }
    }

    /**
     * Test that managed executors do not get closed if the last connection is closed.
     */
    @Test
    public void managedExecutorsTest() {
        if (System.getProperty("tyrus.test.host") == null) {
            return;
        }

        Server server = null;
        try {
            server = startServer(ManagedContainerEndpoint.class, AnnotatedServerEndpoint.class);
            ClientManager clientManager = createClient();
            CountDownLatch messageLatch = new CountDownLatch(1);
            Session session = clientManager.connectToServer(new AnnotatedClientEndpoint(messageLatch), getURI(ManagedContainerEndpoint.class));
            session.getBasicRemote().sendText(getURI(AnnotatedServerEndpoint.class).toString());

            assertTrue(messageLatch.await(5, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
        }
    }

    private HttpServer getLazyServer(final CountDownLatch blockResponseLatch) throws IOException {
        HttpServer server = HttpServer.createSimpleServer("/lazyServer", "localhost", 8025);
        server.getServerConfiguration().addHttpHandler(
                new HttpHandler() {
                    public void service(Request request, Response response) throws Exception {
                        blockResponseLatch.await(1, TimeUnit.MINUTES);
                    }
                }
        );

        server.start();
        return server;
    }

    @ServerEndpoint("/resourcesEchoEndpoint")
    public static class AnnotatedServerEndpoint {

    }

    @ClientEndpoint
    public static class AnnotatedClientEndpoint {

        private final CountDownLatch messageLatch;

        AnnotatedClientEndpoint(CountDownLatch messageLatch) {
            this.messageLatch = messageLatch;
        }

        @OnMessage
        public void onMessage(Session session, String message) {
            messageLatch.countDown();
        }
    }

    public static class FaultyEndpoint {
    }

    @ServerEndpoint("/resourcesEchoEndpoint")
    public static class ReconnectServerEndpoint {

        @OnMessage
        public void onMessage(String message, Session session) throws IOException {
            session.close();
        }
    }

    @ServerEndpoint("/resourcesManagedContainerEndpoint")
    public static class ManagedContainerEndpoint {

        @OnMessage
        public void onMessage(Session session, String message) throws IOException, DeploymentException {
            ClientManager clientManager = ClientManager.createClient();
            Session s = clientManager.connectToServer(AnnotatedClientEndpoint.class, URI.create(message));
            ExecutorService executorService = clientManager.getExecutorService();
            ScheduledExecutorService scheduledExecutorService = clientManager.getScheduledExecutorService();
            s.close();

            // check that the managed executors have not been closed
            if (!executorService.isShutdown() && !scheduledExecutorService.isShutdown()) {
                session.getBasicRemote().sendText("OK");
            }
        }
    }
}
