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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class BufferSizeTest extends TestContainer {

    private volatile CountDownLatch messageLatch;
    private volatile String receivedTextMessage;
    private volatile byte[] receivedBinaryMessage;

    @ServerEndpoint(value = "/endpointbuffersize")
    public static class StringEndpoint {

        public static volatile CloseReason closeReason = null;

        @OnOpen
        public void onOpen(Session session) {
            session.setMaxTextMessageBufferSize(5);
        }

        @OnMessage
        public String doThat(String message) {
            return message;
        }

        @OnClose
        public void onClose(CloseReason c) {
            closeReason = c;
        }
    }

    @Test
    public void testText() throws DeploymentException {
        Server server = startServer(StringEndpoint.class);

        try {
            messageLatch = new CountDownLatch(1);

            final ClientEndpointConfig clientConfiguration = ClientEndpointConfig.Builder.create().build();
            ClientManager client = ClientManager.createClient();

            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                receivedTextMessage = message;
                                messageLatch.countDown();
                            }
                        });

                        session.getBasicRemote().sendText("TES", false);
                        session.getBasicRemote().sendText("T1", true);
                    } catch (IOException e) {
                        // do nothing.
                    }
                }
            }, clientConfiguration, getURI(StringEndpoint.class));

            messageLatch.await(5, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());
            assertEquals("TEST1", receivedTextMessage);

            messageLatch = new CountDownLatch(1);

            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.getBasicRemote().sendText("LON", false);
                        session.getBasicRemote().sendText("G--", true);
                    } catch (IOException e) {
                        // do nothing.
                    }
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    if (closeReason.getCloseCode().equals(CloseReason.CloseCodes.TOO_BIG)) {
                        messageLatch.countDown();
                    } else {
                        System.err.println("Wrong close code: " + closeReason);
                    }
                }
            }, clientConfiguration, getURI(StringEndpoint.class));

            assertTrue(messageLatch.await(5, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint(value = "/endpoint22")
    public static class ByteArrayEndpoint {

        public static volatile CloseReason closeReason = null;

        @OnOpen
        public void onOpen(Session session) {
            session.setMaxBinaryMessageBufferSize(5);
        }

        @OnMessage
        public byte[] doThat(byte[] message) {
            return message;
        }

        @OnClose
        public void onClose(CloseReason c) {
            closeReason = c;
        }
    }

    @Test
    public void testBinary() throws DeploymentException {
        Server server = startServer(ByteArrayEndpoint.class);

        try {
            messageLatch = new CountDownLatch(1);

            final ClientEndpointConfig clientConfiguration = ClientEndpointConfig.Builder.create().build();
            ClientManager client = ClientManager.createClient();

            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<byte[]>() {
                            @Override
                            public void onMessage(byte[] message) {
                                receivedBinaryMessage = message;
                                messageLatch.countDown();
                            }
                        });

                        session.getBasicRemote().sendBinary(ByteBuffer.wrap("TES".getBytes()), false);
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap("T1".getBytes()), true);
                    } catch (IOException e) {
                        // do nothing.
                    }
                }
            }, clientConfiguration, getURI(ByteArrayEndpoint.class));

            messageLatch.await(5, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());
            assertArrayEquals("TEST1".getBytes(), receivedBinaryMessage);

            messageLatch = new CountDownLatch(1);

            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap("LON".getBytes()), false);
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap("G--".getBytes()), false);
                    } catch (IOException e) {
                        // do nothing.
                    }
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    if (closeReason.getCloseCode().equals(CloseReason.CloseCodes.TOO_BIG)) {
                        messageLatch.countDown();
                    }
                }
            }, clientConfiguration, getURI(ByteArrayEndpoint.class));

            messageLatch.await(5, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint(value = "/endpoint3")
    public static class ReaderEndpoint {

        public static volatile CloseReason closeReason = null;
        private StringBuffer bufferedMessage;

        @OnOpen
        public void onOpen(Session session) {
            session.setMaxTextMessageBufferSize(5);
        }

        @OnMessage
        public String doThat(Reader message) throws IOException {
            bufferedMessage = new StringBuffer();
            int i;

            while ((i = message.read()) != -1) {
                bufferedMessage.append((char) i);
            }

            return bufferedMessage.toString();
        }

        @OnClose
        public void onClose(CloseReason c) {
            closeReason = c;
        }

    }

    @Test
    public void testReader() throws DeploymentException {
        Server server = startServer(ReaderEndpoint.class);

        try {
            messageLatch = new CountDownLatch(1);

            final ClientEndpointConfig clientConfiguration = ClientEndpointConfig.Builder.create().build();
            ClientManager client = ClientManager.createClient();

            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                receivedTextMessage = message;
                                messageLatch.countDown();
                            }
                        });

                        session.getBasicRemote().sendText("TES", false);
                        session.getBasicRemote().sendText("T1", true);
                    } catch (IOException e) {
                        // do nothing.
                    }
                }
            }, clientConfiguration, getURI(ReaderEndpoint.class));

            messageLatch.await(5, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());
            assertEquals("TEST1", receivedTextMessage);

            messageLatch = new CountDownLatch(1);

            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.getBasicRemote().sendText("LON", false);
                        session.getBasicRemote().sendText("G--", true);
                    } catch (IOException e) {
                        // do nothing.
                    }
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    if (closeReason.getCloseCode().equals(CloseReason.CloseCodes.TOO_BIG)) {
                        messageLatch.countDown();
                    }
                }
            }, clientConfiguration, getURI(ReaderEndpoint.class));

            messageLatch.await(5, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }


    @ServerEndpoint(value = "/endpoint4")
    public static class InputStreamEndpoint {

        public static volatile CloseReason closeReason = null;
        private ByteArrayOutputStream byteArrayOutputStream;


        @OnOpen
        public void onOpen(Session session) {
            session.setMaxBinaryMessageBufferSize(5);
        }

        @OnMessage
        public byte[] doThat(InputStream is) throws IOException {
            byteArrayOutputStream = new ByteArrayOutputStream();
            int i;

            while ((i = is.read()) != -1) {
                byteArrayOutputStream.write(i);
            }

            return byteArrayOutputStream.toByteArray();
        }

        @OnClose
        public void onClose(CloseReason c) {
            closeReason = c;
        }
    }

    @Test
    public void testInputStream() throws DeploymentException {
        Server server = startServer(InputStreamEndpoint.class);

        try {
            messageLatch = new CountDownLatch(1);

            final ClientEndpointConfig clientConfiguration = ClientEndpointConfig.Builder.create().build();
            ClientManager client = ClientManager.createClient();

            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<byte[]>() {
                            @Override
                            public void onMessage(byte[] message) {
                                receivedBinaryMessage = message;
                                messageLatch.countDown();
                            }
                        });

                        session.getBasicRemote().sendBinary(ByteBuffer.wrap("TES".getBytes()), false);
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap("T1".getBytes()), true);
                    } catch (IOException e) {
                        // do nothing.
                    }
                }
            }, clientConfiguration, getURI(InputStreamEndpoint.class));

            messageLatch.await(5, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());
            assertArrayEquals("TEST1".getBytes(), receivedBinaryMessage);

            messageLatch = new CountDownLatch(1);

            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap("LON".getBytes()), false);
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap("G--".getBytes()), false);
                    } catch (IOException e) {
                        // do nothing.
                    }
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    if (closeReason.getCloseCode().equals(CloseReason.CloseCodes.TOO_BIG)) {
                        messageLatch.countDown();
                    }
                }
            }, clientConfiguration, getURI(InputStreamEndpoint.class));

            messageLatch.await(5, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }
}
