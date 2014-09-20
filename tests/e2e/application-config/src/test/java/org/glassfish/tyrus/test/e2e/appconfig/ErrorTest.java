/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.test.e2e.appconfig;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.server.TyrusServerConfiguration;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;

/**
 * Tests the onError method of the WebSocket API.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class ErrorTest extends TestContainer {

    public ErrorTest() {
        this.setContextPath("/e2e-test-appconfig");
    }

    public static class ServerDeployApplicationConfig extends TyrusServerConfiguration {
        public ServerDeployApplicationConfig() {
            super(new HashSet<Class<?>>() {{
                add(OnOpenErrorTestEndpoint.class);
                add(OnCloseErrorTestEndpoint.class);
                add(ServiceEndpoint.class);
            }}, Collections.<ServerEndpointConfig>emptySet());
        }
    }

    /**
     * Exception thrown during execution @OnOpen annotated method.
     *
     * @author Danny Coward (danny.coward at oracle.com)
     */
    @ServerEndpoint("/openserver")
    public static class OnOpenErrorTestEndpoint {
        public static volatile Throwable throwable;
        public static volatile Session session;

        public static final CountDownLatch ON_ERROR_LATCH = new CountDownLatch(1);

        @OnOpen
        public void open() {
            throw new RuntimeException("testException");
        }

        @OnError
        public void handleError(Throwable throwable, Session session) {
            OnOpenErrorTestEndpoint.throwable = throwable;
            OnOpenErrorTestEndpoint.session = session;

            if (throwable.getClass().equals(RuntimeException.class) && throwable.getMessage().equals("testException")) {
                ON_ERROR_LATCH.countDown();
            }
        }
    }

    @Test
    public void testErrorOnOpen() throws DeploymentException {
        Server server = startServer(OnOpenErrorTestEndpoint.class, ServiceEndpoint.class);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager client = createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {

                        }
                    });
                }
            }, cec, getURI(OnOpenErrorTestEndpoint.class));

            testViaServiceEndpoint(client, ServiceEndpoint.class, POSITIVE, "OnOpenErrorTestEndpoint");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    /**
     * Exception thrown during execution @OnError annotated method.
     *
     * @author Danny Coward (danny.coward at oracle.com)
     */
    @ServerEndpoint("/close")
    public static class OnCloseErrorTestEndpoint {
        public static volatile Throwable throwable;
        public static volatile Session session;

        public static final CountDownLatch ON_ERROR_LATCH = new CountDownLatch(1);

        @OnClose
        public void close() {
            throw new RuntimeException("testException");
        }

        @OnError
        public void handleError(Throwable throwable, Session session) {
            OnCloseErrorTestEndpoint.throwable = throwable;
            OnCloseErrorTestEndpoint.session = session;

            if (throwable.getClass().equals(RuntimeException.class) && throwable.getMessage().equals("testException")) {
                ON_ERROR_LATCH.countDown();
            }
        }
    }

    @Test
    public void testErrorOnClose() throws DeploymentException {
        Server server = startServer(OnCloseErrorTestEndpoint.class, ServiceEndpoint.class);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager client = createClient();
            final Session session = client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {

                }
            }, cec, getURI(OnCloseErrorTestEndpoint.class));
            session.close();

            testViaServiceEndpoint(client, ServiceEndpoint.class, POSITIVE, "OnCloseErrorTestEndpoint");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testErrorOnCloseProgrammatic() throws DeploymentException {
        Server server = startServer(OnCloseExceptionEndpointServerApplicationConfig.class, ServerDeployApplicationConfig.class);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager client = createClient();
            final Session session = client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                }
            }, cec, getURI("/closeprogrammatic"));
            session.close();

            testViaServiceEndpoint(client, ServiceEndpoint.class, POSITIVE, "OnCloseExceptionEndpoint");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint(value = "/serviceerrortest")
    public static class ServiceEndpoint {

        @OnMessage
        public String onMessage(String message) throws InterruptedException {
            if (message.equals("OnCloseErrorTestEndpoint")) {

                final boolean await = OnCloseErrorTestEndpoint.ON_ERROR_LATCH.await(3, TimeUnit.SECONDS);

                if (await && OnCloseErrorTestEndpoint.throwable != null && OnCloseErrorTestEndpoint.session != null) {
                    return POSITIVE;
                }
            } else if (message.equals("OnCloseExceptionEndpoint")) {

                final boolean await = OnCloseExceptionEndpoint.ON_ERROR_LATCH.await(3, TimeUnit.SECONDS);

                if (await && OnCloseExceptionEndpoint.throwable != null && OnCloseExceptionEndpoint.session != null) {
                    return POSITIVE;
                }
            } else if (message.equals("OnOpenErrorTestEndpoint")) {

                final boolean await = OnOpenErrorTestEndpoint.ON_ERROR_LATCH.await(3, TimeUnit.SECONDS);

                if (await && OnOpenErrorTestEndpoint.throwable != null && OnOpenErrorTestEndpoint.session != null) {
                    return POSITIVE;
                }
            } else if (message.equals("OnOpenExceptionEndpoint")) {

                final boolean await = OnOpenExceptionEndpoint.ON_ERROR_LATCH.await(3, TimeUnit.SECONDS);

                if (await && OnOpenExceptionEndpoint.throwable != null && OnOpenExceptionEndpoint.session != null) {
                    return POSITIVE;
                }
            } else if (message.equals("OnMessageExceptionEndpoint")) {

                final boolean await = OnMessageExceptionEndpoint.ON_ERROR_LATCH.await(3, TimeUnit.SECONDS);

                if (await && OnMessageExceptionEndpoint.throwable != null && OnMessageExceptionEndpoint.session != null) {
                    return POSITIVE;
                }
            }

            return NEGATIVE;
        }
    }

    public static class OnCloseExceptionEndpointServerApplicationConfig extends TyrusServerConfiguration {
        public OnCloseExceptionEndpointServerApplicationConfig() {
            super(Collections.<Class<?>>emptySet(),
                    Collections.singleton(ServerEndpointConfig.Builder.create(OnCloseExceptionEndpoint.class, "/closeprogrammatic").build()));
        }
    }

    public static class OnCloseExceptionEndpoint extends Endpoint {

        public static volatile Throwable throwable;
        public static volatile Session session;

        public static final CountDownLatch ON_ERROR_LATCH = new CountDownLatch(1);

        @Override
        public void onOpen(Session session, EndpointConfig config) {
        }

        @Override
        public void onClose(Session session, CloseReason closeReason) {
            throw new RuntimeException("testException");
        }

        @Override
        public void onError(Session session, Throwable thr) {
            OnCloseExceptionEndpoint.throwable = thr;
            OnCloseExceptionEndpoint.session = session;

            if (thr.getClass().equals(RuntimeException.class) && thr.getMessage().equals("testException")) {
                ON_ERROR_LATCH.countDown();
            }
        }
    }

    public static class OnOpenExceptionEndpointServerApplicationConfig extends TyrusServerConfiguration {
        public OnOpenExceptionEndpointServerApplicationConfig() {
            super(Collections.<Class<?>>emptySet(),
                    Collections.singleton(ServerEndpointConfig.Builder.create(OnOpenExceptionEndpoint.class, "/openprogrammatic").build()));
        }
    }

    public static class OnOpenExceptionEndpoint extends Endpoint {

        public static volatile Throwable throwable;
        public static volatile Session session;

        public static final CountDownLatch ON_ERROR_LATCH = new CountDownLatch(1);

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            throw new RuntimeException("testException");
        }

        @Override
        public void onError(Session session, Throwable thr) {
            OnOpenExceptionEndpoint.throwable = thr;
            OnOpenExceptionEndpoint.session = session;

            if (thr.getClass().equals(RuntimeException.class) && thr.getMessage().equals("testException")) {
                ON_ERROR_LATCH.countDown();
            }
        }
    }

    @Test
    public void testErrorOnOpenProgrammatic() throws DeploymentException {
        Server server = startServer(OnOpenExceptionEndpointServerApplicationConfig.class, ServerDeployApplicationConfig.class);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager client = createClient();
            final Session session = client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {

                        }
                    });
                }
            }, cec, getURI("/openprogrammatic"));

            testViaServiceEndpoint(client, ServiceEndpoint.class, POSITIVE, "OnOpenExceptionEndpoint");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    public static class OnMessageExceptionEndpointServerApplicationConfig extends TyrusServerConfiguration {
        public OnMessageExceptionEndpointServerApplicationConfig() {
            super(Collections.<Class<?>>emptySet(),
                    Collections.singleton(ServerEndpointConfig.Builder.create(OnMessageExceptionEndpoint.class, "/openonmessageexception").build()));
        }
    }

    public static class OnMessageExceptionEndpoint extends Endpoint implements MessageHandler.Whole<String> {

        public static volatile Throwable throwable;
        public static volatile Session session;

        public static final CountDownLatch ON_ERROR_LATCH = new CountDownLatch(1);

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            session.addMessageHandler(this);
        }

        @Override
        public void onMessage(String message) {
            throw new RuntimeException("testException");
        }

        @Override
        public void onError(Session session, Throwable thr) {
            OnMessageExceptionEndpoint.throwable = thr;
            OnMessageExceptionEndpoint.session = session;

            if (thr.getClass().equals(RuntimeException.class) && thr.getMessage().equals("testException")) {
                ON_ERROR_LATCH.countDown();
            }
        }
    }

    @Test
    public void testErrorOnMessageProgrammatic() throws DeploymentException {
        Server server = startServer(OnMessageExceptionEndpointServerApplicationConfig.class, ServerDeployApplicationConfig.class);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            CountDownLatch messageLatch = new CountDownLatch(1);
            ClientManager client = createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {

                        }
                    });
                    try {
                        session.getBasicRemote().sendText("Throw an Exception!");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, cec, getURI("/openonmessageexception"));

            messageLatch.await(1, TimeUnit.SECONDS);

            testViaServiceEndpoint(client, ServiceEndpoint.class, POSITIVE, "OnMessageExceptionEndpoint");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }
}
