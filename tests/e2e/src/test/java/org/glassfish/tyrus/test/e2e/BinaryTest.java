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
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test for all supported message types.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class BinaryTest {

    private CountDownLatch messageLatch;


    private static final byte[] BINARY_MESSAGE = new byte[]{1, 2, 3, 4};


    private ByteBuffer receivedMessageBuffer;
    private byte[] receivedMessageArray;

    private final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

    /**
     * Bean to test correct processing of binary message.
     *
     * @author Stepan Kopriva (stepan.kopriva at oracle.com)
     */
    @ServerEndpoint(value = "/binary")
    public static class BinaryByteBufferEndpoint {

        @OnMessage
        public ByteBuffer echo(ByteBuffer message) {
            return message;
        }
    }

    @Test
    public void testBinaryByteBufferBean() {
        Server server = new Server(BinaryByteBufferEndpoint.class);

        try {
            server.start();
            messageLatch = new CountDownLatch(1);

            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap(BINARY_MESSAGE));
                        session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
                            @Override
                            public void onMessage(ByteBuffer data) {
                                receivedMessageBuffer = data;
                                messageLatch.countDown();
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/binary"));
            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertArrayEquals("The received message is the same as the sent one", receivedMessageBuffer.array(), BINARY_MESSAGE);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @ServerEndpoint(value = "/binary")
    public static class BinaryByteArrayEndpoint {

        @OnMessage
        public byte[] echo(byte[] message) {
            return message;
        }
    }

    @Test
    public void testBinaryByteArrayBean() {
        Server server = new Server(BinaryByteArrayEndpoint.class);

        try {
            server.start();
            messageLatch = new CountDownLatch(1);

            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap(BINARY_MESSAGE));
                        session.addMessageHandler(new MessageHandler.Whole<byte[]>() {
                            @Override
                            public void onMessage(byte[] data) {
                                receivedMessageArray = data;
                                messageLatch.countDown();
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/binary"));
            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertArrayEquals("The received message is the same as the sent one", receivedMessageArray, BINARY_MESSAGE);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    private ByteBuffer receivedMessageBinary;

    @ServerEndpoint(value = "/endpoint1")
    public static class EndpointBinaryPartialReturningValue {

        @OnMessage
        public ByteBuffer doThatBinary(Session s, ByteBuffer message, boolean last) throws IOException {
            return message;
        }
    }

    @Test
    public void binaryPartialHandlerReturningValue() {
        Server server = new Server(EndpointBinaryPartialReturningValue.class);

        try {
            server.start();

            messageLatch = new CountDownLatch(1);

            final ClientEndpointConfig clientConfiguration = ClientEndpointConfig.Builder.create().build();
            ClientManager client = ClientManager.createClient();

            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
                            @Override
                            public void onMessage(ByteBuffer message) {
                                receivedMessageBinary = message;
                                messageLatch.countDown();
                            }
                        });

                        session.getBasicRemote().sendBinary(ByteBuffer.wrap("TEST1".getBytes()), false);
                    } catch (IOException e) {
                        // do nothing.
                    }
                }
            }, clientConfiguration, new URI("ws://localhost:8025/websockets/tests/endpoint1"));

            messageLatch.await(1, TimeUnit.SECONDS);
            Assert.assertEquals(0, messageLatch.getCount());
            Assert.assertEquals(ByteBuffer.wrap("TEST1".getBytes()), receivedMessageBinary);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @ServerEndpoint(value = "/endpoint1")
    public static class EndpointBinaryPartialReturningValueByteArray {

        @OnMessage
        public byte[] doThatBinary(Session s, byte[] message, boolean last) throws IOException {
            return message;
        }
    }

    @Test
    public void binaryPartialHandlerReturningValueByteArray() {
        Server server = new Server(EndpointBinaryPartialReturningValueByteArray.class);

        try {
            server.start();

            messageLatch = new CountDownLatch(1);

            final ClientEndpointConfig clientConfiguration = ClientEndpointConfig.Builder.create().build();
            ClientManager client = ClientManager.createClient();

            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
                            @Override
                            public void onMessage(ByteBuffer message) {
                                receivedMessageBinary = message;
                                messageLatch.countDown();
                            }
                        });

                        session.getBasicRemote().sendBinary(ByteBuffer.wrap("TEST1".getBytes()), false);
                    } catch (IOException e) {
                        // do nothing.
                    }
                }
            }, clientConfiguration, new URI("ws://localhost:8025/websockets/tests/endpoint1"));

            messageLatch.await(1, TimeUnit.SECONDS);
            Assert.assertEquals(0, messageLatch.getCount());
            Assert.assertEquals(ByteBuffer.wrap("TEST1".getBytes()), receivedMessageBinary);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }
}
