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
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
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

import org.glassfish.tyrus.test.tools.TestContainer;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test for all supported message types.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class BinaryTest extends TestContainer {

    private CountDownLatch messageLatch;


    private static final byte[] BINARY_MESSAGE = new byte[]{1, 2, 3, 4};
    private static final String TEXT_MESSAGE = "Always pass on what you have learned.";


    private ByteBuffer receivedMessageBuffer;
    private byte[] receivedMessageArray;

    private final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

    /**
     * Bean to test correct processing of binary message.
     *
     * @author Stepan Kopriva (stepan.kopriva at oracle.com)
     */
    @ServerEndpoint(value = "/binary2")
    public static class BinaryByteBufferEndpoint {

        @OnMessage
        public ByteBuffer echo(ByteBuffer message) {
            return message;
        }
    }

    @Test
    public void testBinaryByteBufferBean() throws DeploymentException {
        Server server = startServer(BinaryByteBufferEndpoint.class);

        try {
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
            }, cec, getURI(BinaryByteBufferEndpoint.class));
            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertArrayEquals("The received message is the same as the sent one", BINARY_MESSAGE, receivedMessageBuffer.array());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testDirectByteBuffer() throws DeploymentException {
        Server server = startServer(BinaryByteBufferEndpoint.class);
        final Charset UTF8 = Charset.forName("UTF-8");

        try {
            messageLatch = new CountDownLatch(1);

            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        ByteBuffer buffer = ByteBuffer.allocateDirect(100);
                        buffer.put(TEXT_MESSAGE.getBytes(UTF8));
                        buffer.flip();
                        session.getBasicRemote().sendBinary(buffer);
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
            }, cec, getURI(BinaryByteBufferEndpoint.class));
            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertArrayEquals("The received message is the same as the sent one", TEXT_MESSAGE.getBytes(UTF8), receivedMessageBuffer.array());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint(value = "/binary1")
    public static class BinaryByteArrayEndpoint {

        @OnMessage
        public byte[] echo(byte[] message) {
            return message;
        }
    }

    @Test
    public void testBinaryByteArrayBean() throws DeploymentException {
        Server server = startServer(BinaryByteArrayEndpoint.class);

        try {
            messageLatch = new CountDownLatch(1);

            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        ByteBuffer buffer = ByteBuffer.wrap(BINARY_MESSAGE);
                        session.getBasicRemote().sendBinary(buffer);
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
            }, cec, getURI(BinaryByteArrayEndpoint.class));
            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertArrayEquals("The received message is the same as the sent one", BINARY_MESSAGE, receivedMessageArray);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    private ByteBuffer receivedMessageBinary;

    @ServerEndpoint(value = "/endpointbinary")
    public static class EndpointBinaryPartialReturningValue {

        @OnMessage
        public ByteBuffer doThatBinary(Session s, ByteBuffer message, boolean last) throws IOException {
            return message;
        }
    }

    @Test
    public void binaryPartialHandlerReturningValue() throws DeploymentException {
        Server server = startServer(EndpointBinaryPartialReturningValue.class);

        try {
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
            }, clientConfiguration, getURI(EndpointBinaryPartialReturningValue.class));

            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertEquals(0, messageLatch.getCount());
            Assert.assertEquals(ByteBuffer.wrap("TEST1".getBytes()), receivedMessageBinary);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint(value = "/endpoint21")
    public static class EndpointBinaryPartialReturningValueByteArray {

        @OnMessage
        public byte[] doThatBinary(Session s, byte[] message, boolean last) throws IOException {
            return message;
        }
    }

    @Test
    public void binaryPartialHandlerReturningValueByteArray() throws DeploymentException {
        Server server = startServer(EndpointBinaryPartialReturningValueByteArray.class);

        try {
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
            }, clientConfiguration, getURI(EndpointBinaryPartialReturningValueByteArray.class));

            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertEquals(0, messageLatch.getCount());
            Assert.assertEquals(ByteBuffer.wrap("TEST1".getBytes()), receivedMessageBinary);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }
}
