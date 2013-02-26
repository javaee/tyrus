/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.test.e2e;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfiguration;
import javax.websocket.ClientEndpointConfigurationBuilder;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.MessageHandler;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfiguration;
import javax.websocket.server.ServerEndpointConfigurationBuilder;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.server.TyrusServerConfiguration;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests the onError method of the WebSocket API
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class ErrorTest {

    private CountDownLatch messageLatch;

    /**
     * Exception thrown during execution @OnOpen annotated method.
     *
     * @author Danny Coward (danny.coward at oracle.com)
     */
    @ServerEndpoint("/open")
    public static class OnOpenErrorTestEndpoint {
        public static Throwable throwable;
        public static Session session;

        @OnOpen
        public void open() {
            throw new RuntimeException("testException");
        }

        @OnMessage
        public String message(String message, Session session) {
            // won't be called.
            return "message";
        }

        @OnError
        public void handleError(Throwable throwable, Session session) {
            OnOpenErrorTestEndpoint.throwable = throwable;
            OnOpenErrorTestEndpoint.session = session;
        }
    }

    @Test
    public void testErrorOnOpen() {
        Server server = new Server(OnOpenErrorTestEndpoint.class);

        try {
            server.start();
            final ClientEndpointConfiguration cec = ClientEndpointConfigurationBuilder.create().build();

            messageLatch = new CountDownLatch(1);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new TestEndpointAdapter() {
                @Override
                public EndpointConfiguration getEndpointConfiguration() {
                    return cec;
                }

                @Override
                public void onOpen(Session session) {
                    session.addMessageHandler(new TestTextMessageHandler(this));
                }

                @Override
                public void onMessage(String message) {
                    // do nothing
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/open"));

            // TODO: is this really needed? Cannot we somehow force underlying protocol impl to connect immediately
            // TODO: after connectToServer call?
            messageLatch.await(1, TimeUnit.SECONDS);

            assertTrue(OnOpenErrorTestEndpoint.session != null);
            assertTrue(OnOpenErrorTestEndpoint.throwable != null);
            assertEquals("testException", OnOpenErrorTestEndpoint.throwable.getCause().getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    /**
     * Exception thrown during execution @OnError annotated method.
     *
     * @author Danny Coward (danny.coward at oracle.com)
     */
    @ServerEndpoint("/close")
    public static class OnCloseErrorTestEndpoint {
        public static Throwable throwable;
        public static Session session;

        @OnClose
        public void close() {
            throw new RuntimeException("testException");
        }

        @OnMessage
        public String message(String message, Session session) {
            // won't be called.
            return "message";
        }

        @OnError
        public void handleError(Throwable throwable, Session session) {
            OnCloseErrorTestEndpoint.throwable = throwable;
            OnCloseErrorTestEndpoint.session = session;
        }
    }

    @Test
    public void testErrorOnClose() {
        Server server = new Server(OnCloseErrorTestEndpoint.class);

        try {
            server.start();
            final ClientEndpointConfiguration cec = ClientEndpointConfigurationBuilder.create().build();

            messageLatch = new CountDownLatch(1);
            ClientManager client = ClientManager.createClient();
            final Session session = client.connectToServer(new TestEndpointAdapter() {
                @Override
                public EndpointConfiguration getEndpointConfiguration() {
                    return cec;
                }

                @Override
                public void onOpen(Session session) {
                    session.addMessageHandler(new TestTextMessageHandler(this));
                }

                @Override
                public void onMessage(String message) {
                    // do nothing
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/close"));

            // TODO: is this really needed? Cannot we somehow force underlying protocol impl to connect immediately
            // TODO: after connectToServer call?
            messageLatch.await(1, TimeUnit.SECONDS);
            session.close();

            // TODO: is this really needed? Cannot we somehow force underlying protocol impl to connect immediately
            // TODO: after close call?
            messageLatch.await(1, TimeUnit.SECONDS);

            assertTrue(OnCloseErrorTestEndpoint.session != null);
            assertTrue(OnCloseErrorTestEndpoint.throwable != null);
            assertEquals("testException", OnCloseErrorTestEndpoint.throwable.getCause().getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    public static class OnOpenExceptionEndpointServerApplicationConfiguration extends TyrusServerConfiguration {
        public OnOpenExceptionEndpointServerApplicationConfiguration() {
            super(Collections.<Class<?>>emptySet(), new HashSet<ServerEndpointConfiguration>() {{
                add(ServerEndpointConfigurationBuilder.create(OnOpenExceptionEndpoint.class, "/open").build());
            }});
        }
    }

    public static class OnOpenExceptionEndpoint extends Endpoint {

        public static Throwable throwable;
        public static Session session;

        @Override
        public void onOpen(Session session, EndpointConfiguration config) {
            throw new RuntimeException("testException");
        }

        @Override
        public void onError(Session session, Throwable thr) {
            OnOpenExceptionEndpoint.throwable = thr;
            OnOpenExceptionEndpoint.session = session;
        }
    }

    @Test
    public void testErrorOnOpenProgrammatic() {
        Server server = new Server(OnOpenExceptionEndpointServerApplicationConfiguration.class);

        try {
            server.start();
            final ClientEndpointConfiguration cec = ClientEndpointConfigurationBuilder.create().build();

            messageLatch = new CountDownLatch(1);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new TestEndpointAdapter() {
                @Override
                public EndpointConfiguration getEndpointConfiguration() {
                    return cec;
                }

                @Override
                public void onOpen(Session session) {
                    session.addMessageHandler(new TestTextMessageHandler(this));
                }

                @Override
                public void onMessage(String message) {
                    // do nothing
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/open"));

            // TODO: is this really needed? Cannot we somehow force underlying protocol impl to connect immediately
            // TODO: after connectToServer call?
            messageLatch.await(1, TimeUnit.SECONDS);

            assertTrue(OnOpenExceptionEndpoint.session != null);
            assertTrue(OnOpenExceptionEndpoint.throwable != null);
            assertEquals("testException", OnOpenExceptionEndpoint.throwable.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    public static class OnMessageExceptionEndpointServerApplicationConfiguration extends TyrusServerConfiguration {
        public OnMessageExceptionEndpointServerApplicationConfiguration() {
            super(Collections.<Class<?>>emptySet(), new HashSet<ServerEndpointConfiguration>() {{
                add(ServerEndpointConfigurationBuilder.create(OnMessageExceptionEndpoint.class, "/open").build());
            }});
        }
    }

    public static class OnMessageExceptionEndpoint extends Endpoint implements MessageHandler.Basic<String> {

        public static Throwable throwable;
        public static Session session;

        @Override
        public void onOpen(Session session, EndpointConfiguration config) {
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
        }
    }

    @Test
    public void testErrorOnMessageProgrammatic() {
        Server server = new Server(OnMessageExceptionEndpointServerApplicationConfiguration.class);

        try {
            server.start();
            final ClientEndpointConfiguration cec = ClientEndpointConfigurationBuilder.create().build();

            messageLatch = new CountDownLatch(1);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new TestEndpointAdapter() {
                @Override
                public EndpointConfiguration getEndpointConfiguration() {
                    return cec;
                }

                @Override
                public void onOpen(Session session) {
                    try {
                        session.getBasicRemote().sendText("Do or do not, there is no try.");
                    } catch (IOException e) {
                        fail();
                    }
                }

                @Override
                public void onMessage(String message) {
                    // nothing
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/open"));

            // TODO: is this really needed? Cannot we somehow force underlying protocol impl to connect immediately
            // TODO: after connectToServer call?
            messageLatch.await(1, TimeUnit.SECONDS);

            assertTrue(OnMessageExceptionEndpoint.session != null);
            assertTrue(OnMessageExceptionEndpoint.throwable != null);
            assertEquals("testException", OnMessageExceptionEndpoint.throwable.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }
}
