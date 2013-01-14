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

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfiguration;
import javax.websocket.EndpointConfiguration;
import javax.websocket.Session;
import javax.websocket.WebSocketClose;
import javax.websocket.WebSocketError;
import javax.websocket.WebSocketMessage;
import javax.websocket.WebSocketOpen;
import javax.websocket.server.DefaultServerConfiguration;
import javax.websocket.server.WebSocketEndpoint;

import org.glassfish.tyrus.TyrusClientEndpointConfiguration;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the onError method of the WebSocket API
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class ErrorTest {

    private CountDownLatch messageLatch;
    private String receivedMessage;
    private static final String SENT_MESSAGE = "Hello World";

    /**
     * Exception thrown during execution @WebSocketOpen annotated method.
     *
     * @author Danny Coward (danny.coward at oracle.com)
     */
    @WebSocketEndpoint(
            value = "/open",
            configuration = DefaultServerConfiguration.class
    )
    public static class OnOpenErrorTestBean {
        public static Throwable throwable;
        public static Session session;

        @WebSocketOpen
        public void open() {
            throw new RuntimeException("testException");
        }

        @WebSocketMessage
        public String message(String message, Session session) {
            // won't be called.
            return "message";
        }

        @WebSocketError
        public void handleError(Throwable throwable, Session session) {
            OnOpenErrorTestBean.throwable = throwable;
            OnOpenErrorTestBean.session = session;
        }
    }

    @Test
    public void testErrorOnOpen() {
        final ClientEndpointConfiguration cec = new TyrusClientEndpointConfiguration.Builder().build();
        Server server = new Server(OnOpenErrorTestBean.class);

        try {
            server.start();
            final TyrusClientEndpointConfiguration.Builder builder = new TyrusClientEndpointConfiguration.Builder();
            final TyrusClientEndpointConfiguration dcec = builder.build();

            messageLatch = new CountDownLatch(1);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new TestEndpointAdapter() {
                @Override
                public EndpointConfiguration getEndpointConfiguration() {
                    return dcec;
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

            assertTrue(OnOpenErrorTestBean.session != null);
            assertTrue(OnOpenErrorTestBean.throwable != null);
            assertEquals("testException", OnOpenErrorTestBean.throwable.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    /**
     * Exception thrown during execution @WebSocketError annotated method.
     *
     * @author Danny Coward (danny.coward at oracle.com)
     */
    @WebSocketEndpoint(
            value = "/close",
            configuration = DefaultServerConfiguration.class
    )
    public static class OnCloseErrorTestBean {
        public static Throwable throwable;
        public static Session session;

        @WebSocketClose
        public void close() {
            throw new RuntimeException("testException");
        }

        @WebSocketMessage
        public String message(String message, Session session) {
            // won't be called.
            return "message";
        }

        @WebSocketError
        public void handleError(Throwable throwable, Session session) {
            OnCloseErrorTestBean.throwable = throwable;
            OnCloseErrorTestBean.session = session;
        }
    }

    @Test
    public void testErrorOnClose() {
        final ClientEndpointConfiguration cec = new TyrusClientEndpointConfiguration.Builder().build();
        Server server = new Server(OnCloseErrorTestBean.class);

        try {
            server.start();
            final TyrusClientEndpointConfiguration.Builder builder = new TyrusClientEndpointConfiguration.Builder();
            final TyrusClientEndpointConfiguration dcec = builder.build();

            messageLatch = new CountDownLatch(1);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new TestEndpointAdapter() {
                @Override
                public EndpointConfiguration getEndpointConfiguration() {
                    return dcec;
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
            client.close();

            // TODO: is this really needed? Cannot we somehow force underlying protocol impl to connect immediately
            // TODO: after close call?
            messageLatch.await(1, TimeUnit.SECONDS);

            assertTrue(OnCloseErrorTestBean.session != null);
            assertTrue(OnCloseErrorTestBean.throwable != null);
            assertEquals("testException", OnCloseErrorTestBean.throwable.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }
}
