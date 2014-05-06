/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2014 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.core.coder.CoderAdapter;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.standard_config.message.StringContainer;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests encoding of custom objects.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class EncodedObjectTest extends TestContainer {

    private CountDownLatch messageLatch;

    private String receivedMessage;

    private static final String SENT_MESSAGE = "hello";

    @Test
    public void testEncodingReturnViaSession() throws DeploymentException {
        final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();
        Server server = startServer(TestEncodeEndpoint.class);

        try {
            messageLatch = new CountDownLatch(1);

            ClientManager client = createClient();
            client.connectToServer(new TestEndpointAdapter() {
                @Override
                public void onMessage(String message) {
                    receivedMessage = message;
                    messageLatch.countDown();
                    System.out.println("Received message = " + message);
                }

                @Override
                public EndpointConfig getEndpointConfig() {
                    return null;
                }

                @Override
                public void onOpen(Session session) {
                    try {
                        session.addMessageHandler(new TestTextMessageHandler(this));
                        session.getBasicRemote().sendText(SENT_MESSAGE);
                        System.out.println("Sent message: " + SENT_MESSAGE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, cec, getURI(TestEncodeEndpoint.class));

            messageLatch.await(5, TimeUnit.SECONDS);
            assertEquals(SENT_MESSAGE, receivedMessage);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint(value = "/echo3", encoders = {StringContainerEncoder.class})
    public static class TestEncodeEndpoint {
        @OnOpen
        public void onOpen() {
            System.out.println("Client connected to the server!");
        }

        @OnMessage
        public void helloWorld(String message, Session session) {
            try {
                System.out.println("##### Encode Test Bean: Received message: " + message);

                session.getBasicRemote().sendObject(new StringContainer(message));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void testExtendedEncodingReturnViaSession() throws DeploymentException {
        final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();
        Server server = startServer(TestExtendedEncodeEndpoint.class);

        try {
            messageLatch = new CountDownLatch(1);

            ClientManager client = createClient();
            client.connectToServer(new TestEndpointAdapter() {
                @Override
                public void onMessage(String message) {
                    receivedMessage = message;
                    messageLatch.countDown();
                    System.out.println("Received message = " + message);
                }

                @Override
                public EndpointConfig getEndpointConfig() {
                    return null;
                }

                @Override
                public void onOpen(Session session) {
                    try {
                        session.addMessageHandler(new TestTextMessageHandler(this));
                        session.getBasicRemote().sendText(SENT_MESSAGE);
                        System.out.println("Sent message: " + SENT_MESSAGE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, cec, getURI(TestExtendedEncodeEndpoint.class));

            messageLatch.await(5, TimeUnit.SECONDS);
            assertEquals(SENT_MESSAGE, receivedMessage);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint(value = "/echo3ext", encoders = {StringContainerEncoder.class})
    public static class TestExtendedEncodeEndpoint {
        @OnOpen
        public void onOpen() {
            System.out.println("Client connected to the server!");
        }

        @OnMessage
        public void helloWorld(String message, Session session) {
            try {
                System.out.println("##### Encode Test Bean: Received message: " + message);

                session.getBasicRemote().sendObject(new StringContainer(message) {

                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void testEncodingReturnFromMethod() throws DeploymentException {
        final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();
        Server server = startServer(TestEncodeBeanMethodReturn.class);

        try {
            messageLatch = new CountDownLatch(1);

            ClientManager client = createClient();
            client.connectToServer(new TestEndpointAdapter() {
                @Override
                public void onMessage(String message) {
                    receivedMessage = message;
                    messageLatch.countDown();
                    System.out.println("Received message = " + message);
                }

                @Override
                public EndpointConfig getEndpointConfig() {
                    return null;
                }

                @Override
                public void onOpen(Session session) {
                    try {
                        session.addMessageHandler(new TestTextMessageHandler(this));
                        session.getBasicRemote().sendText(SENT_MESSAGE);
                        System.out.println("Sent message: " + SENT_MESSAGE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, cec, getURI(TestEncodeBeanMethodReturn.class));

            messageLatch.await(5, TimeUnit.SECONDS);
            assertEquals(SENT_MESSAGE, receivedMessage);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint(value = "/echo2", encoders = {StringContainerEncoder.class})
    public static class TestEncodeBeanMethodReturn {

        @OnMessage
        public StringContainer helloWorld(String message) {
            return new StringContainer(message);
        }
    }

    /**
     * @author Stepan Kopriva (stepan.kopriva at oracle.com)
     */
    public static class StringContainerEncoder extends CoderAdapter implements Encoder.Text<StringContainer> {

        @Override
        public String encode(StringContainer object) throws EncodeException {
            return object.getString();
        }
    }

    @Test
    public void testCustomPrimitiveEncoder() throws DeploymentException {
        final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();
        Server server = startServer(CustomPrimitiveEncoderEndpoint.class);

        try {
            messageLatch = new CountDownLatch(1);

            ClientManager client = createClient();
            client.connectToServer(new TestEndpointAdapter() {
                @Override
                public void onMessage(String message) {
                    receivedMessage = message;
                    messageLatch.countDown();
                    System.out.println("Received message = " + message);
                }

                @Override
                public EndpointConfig getEndpointConfig() {
                    return null;
                }

                @Override
                public void onOpen(Session session) {
                    try {
                        session.addMessageHandler(new TestTextMessageHandler(this));
                        session.getBasicRemote().sendText(SENT_MESSAGE);
                        System.out.println("Sent message: " + SENT_MESSAGE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, cec, getURI(CustomPrimitiveEncoderEndpoint.class));

            assertTrue(messageLatch.await(5, TimeUnit.SECONDS));
            assertEquals("encoded5", receivedMessage);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint(value = "/echo-primitive-encoder", encoders = {CustomIntEncoder.class})
    public static class CustomPrimitiveEncoderEndpoint {

        @OnMessage
        public Integer onMessage(String message) {
            return message.length();
        }
    }

    public static class CustomIntEncoder extends CoderAdapter implements Encoder.Text<Integer> {

        @Override
        public String encode(Integer object) throws EncodeException {
            return ("encoded" + object);
        }
    }

    @Test
    public void testStringEncoderSendText() throws DeploymentException {
        final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().encoders(Collections.<Class<? extends Encoder>>singletonList(StringEncoder.class)).build();
        Server server = startServer(StringEncoderSendTextEndpoint.class);

        try {
            messageLatch = new CountDownLatch(1);

            ClientManager client = createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            // decoders should NOT be used, because we are using sendText, not sendObject
                            if (message.equals("test")) {
                                messageLatch.countDown();
                            }
                        }
                    });

                    try {
                        session.getBasicRemote().sendText("test");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, cec, getURI(StringEncoderSendTextEndpoint.class));

            assertTrue(messageLatch.await(3, TimeUnit.SECONDS));

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testStringEncoder() throws DeploymentException {
        final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().encoders(Collections.<Class<? extends Encoder>>singletonList(StringEncoder.class)).build();
        Server server = startServer(StringEncoderSendObjectEndpoint.class);

        try {
            messageLatch = new CountDownLatch(1);

            ClientManager client = createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            // "testtest" is sent from client to server, server replies "testtesttesttest".
                            if (message.equals("testtesttesttest")) {
                                messageLatch.countDown();
                            }
                        }
                    });

                    try {
                        session.getBasicRemote().sendObject("test");
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (EncodeException e) {
                        e.printStackTrace();
                    }
                }
            }, cec, getURI(StringEncoderSendObjectEndpoint.class));

            assertTrue(messageLatch.await(3, TimeUnit.SECONDS));

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint(value = "/encodedObjectTest-stringEncoder-sendText", encoders = {EncodedObjectTest.StringEncoder.class})
    public static class StringEncoderSendTextEndpoint {
        @OnMessage
        public void onMessage(Session session, String message) throws IOException {
            session.getBasicRemote().sendText(message);
        }
    }

    @ServerEndpoint(value = "/encodedObjectTest-stringEncoder-sendObject", encoders = {EncodedObjectTest.StringEncoder.class})
    public static class StringEncoderSendObjectEndpoint {
        @OnMessage
        public void onMessage(Session session, String message) throws IOException, EncodeException {
            session.getBasicRemote().sendObject(message);
        }
    }

    public static class StringEncoder extends CoderAdapter implements Encoder.Text<String> {
        @Override
        public String encode(String object) throws EncodeException {
            return object + object;
        }
    }
}

