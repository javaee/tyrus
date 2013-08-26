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
package org.glassfish.tyrus.test.e2e;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpoint;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.HandshakeResponse;
import javax.websocket.MessageHandler;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import org.glassfish.tyrus.test.tools.TestContainer;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class ModifyRequestResponseHeadersTest extends TestContainer {

    private String receivedMessage;

    private static final String SENT_MESSAGE = "Always pass on what you have learned.";
    private static final String HEADER_NAME = "myHeader";
    private static final String[] HEADER_VALUE = {"\"Always two there are, a master and an apprentice.\"", "b", "c"};

    @ServerEndpoint(value = "/echo6", configurator = MyServerConfigurator.class)
    public static class TestEndpoint {

        @OnMessage
        public String onMessage(String message) {
            return message;
        }
    }

    public static class MyServerConfigurator extends ServerEndpointConfig.Configurator {

        @Override
        public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
            final List<String> list = request.getHeaders().get(HEADER_NAME);

            try {
                // TYRUS-208: HandshakeRequest.getHeaders() should return read-only map.
                request.getHeaders().put("test", Arrays.asList("TYRUS-208"));
                return;
            } catch (UnsupportedOperationException e) {
                // expected.
            }

            try {
                // TYRUS-211: HandshakeRequest.getHeaders() should return read-only map.
                request.getParameterMap().put("test", Arrays.asList("TYRUS-211"));
                return;
            } catch (UnsupportedOperationException e) {
                // expected.
            }

            response.getHeaders().put(HEADER_NAME, list);
            response.getHeaders().put("Origin", request.getHeaders().get("Origin"));
        }
    }

    public static class MyClientConfigurator extends ClientEndpointConfig.Configurator {
        static volatile boolean called = false;

        @Override
        public void beforeRequest(Map<String, List<String>> headers) {
            called = true;
            headers.put(HEADER_NAME, Arrays.asList(HEADER_VALUE));
            headers.put("Origin", Arrays.asList("myOrigin"));
        }

        @Override
        public void afterResponse(HandshakeResponse handshakeResponse) {
            final Map<String, List<String>> headers = handshakeResponse.getHeaders();

            assertEquals(HEADER_VALUE[0], headers.get(HEADER_NAME).get(0));
            assertEquals(HEADER_VALUE[1], headers.get(HEADER_NAME).get(1));
            assertEquals(HEADER_VALUE[2], headers.get(HEADER_NAME).get(2));
            assertEquals("myOrigin", headers.get("origin").get(0));
        }
    }

    @Test
    public void testHeadersProgrammatic() throws DeploymentException {
        Server server = startServer(TestEndpoint.class);
        MyClientConfigurator.called = false;

        final CountDownLatch messageLatch = new CountDownLatch(1);

        try {
            final MyClientConfigurator clientConfigurator = new MyClientConfigurator();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().configurator(clientConfigurator).build();

            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(final Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                receivedMessage = message;
                                messageLatch.countDown();
                            }
                        });

                        session.getBasicRemote().sendText(SENT_MESSAGE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, cec, getURI(TestEndpoint.class));

            messageLatch.await(5, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());
            assertTrue(MyClientConfigurator.called);
            assertEquals(SENT_MESSAGE, receivedMessage);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @ClientEndpoint(configurator = MyClientConfigurator.class)
    public static class MyClientEndpoint {
        public static final CountDownLatch messageLatch = new CountDownLatch(1);
        public static volatile String receivedMessage;

        @OnOpen
        public void onOpen(Session session) throws IOException {
            session.getBasicRemote().sendText(SENT_MESSAGE);
        }

        @OnMessage
        public void onMessage(String message) {
            receivedMessage = message;
            messageLatch.countDown();
        }
    }

    @Test
    public void testHeadersAnnotated() throws DeploymentException {
        Server server = startServer(TestEndpoint.class);
        MyClientConfigurator.called = false;

        try {
            ClientManager client = ClientManager.createClient();
            client.connectToServer(MyClientEndpoint.class, getURI(TestEndpoint.class));

            MyClientEndpoint.messageLatch.await(5, TimeUnit.SECONDS);
            assertTrue(MyClientConfigurator.called);
            assertEquals(0, MyClientEndpoint.messageLatch.getCount());
            assertEquals(SENT_MESSAGE, MyClientEndpoint.receivedMessage);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }
}