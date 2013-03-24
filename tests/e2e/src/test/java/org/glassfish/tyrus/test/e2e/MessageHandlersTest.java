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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
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

import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class MessageHandlersTest {

    @ServerEndpoint("/whole")
    public static class WholeString {
        @OnMessage
        public String onMessage(String message) {
            return message;
        }
    }

    @ServerEndpoint("/partial")
    public static class PartialString {

        private StringBuffer sb = new StringBuffer();

        @OnMessage
        public void onMessage(Session session, String message, boolean isLast) throws IOException {
            sb.append(message);

            if (isLast) {
                final String completeMessage = sb.toString();
                sb = new StringBuffer();
                session.getBasicRemote().sendText(completeMessage);
            }
        }
    }

    @Test
    public void clientWholeServerWhole() {
        Server server = new Server(WholeString.class);

        try {
            server.start();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            messageLatch = new CountDownLatch(1);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            if (message.equals("In my experience, there's no such thing as luck.")) {
                                messageLatch.countDown();
                            }
                        }
                    });

                    try {
                        session.getBasicRemote().sendText("In my experience, there's no such thing as luck.");
                    } catch (IOException e) {
                        // don't care
                    }
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/whole"));

            messageLatch.await(5, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());


        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @Test
    public void clientWholeServerPartial() {
        Server server = new Server(PartialString.class);

        try {
            server.start();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            messageLatch = new CountDownLatch(1);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            if (message.equals("In my experience, there's no such thing as luck.")) {
                                messageLatch.countDown();
                            }
                        }
                    });

                    try {
                        session.getBasicRemote().sendText("In my experience, there's no such thing as luck.");
                    } catch (IOException e) {
                        // don't care
                    }
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/partial"));

            messageLatch.await(5, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());


        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @Test
    public void clientPartialServerWhole() {
        Server server = new Server(WholeString.class);

        try {
            server.start();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            messageLatch = new CountDownLatch(1);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            if (message.equals("In my experience, there's no such thing as luck.")) {
                                messageLatch.countDown();
                            }
                        }
                    });

                    try {
                        session.getBasicRemote().sendText("In my experience", false);
                        session.getBasicRemote().sendText(", there's no such ", false);
                        session.getBasicRemote().sendText("thing as luck.", true);
                    } catch (IOException e) {
                        // don't care
                    }
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/whole"));

            messageLatch.await(1, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());


        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @Test
    public void clientPartialServerPartial() {
        Server server = new Server(PartialString.class);

        try {
            server.start();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            messageLatch = new CountDownLatch(1);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            if (message.equals("In my experience, there's no such thing as luck.")) {
                                messageLatch.countDown();
                            }
                        }
                    });

                    try {
                        session.getBasicRemote().sendText("In my experience", false);
                        session.getBasicRemote().sendText(", there's no such ", false);
                        session.getBasicRemote().sendText("thing as luck.", true);
                    } catch (IOException e) {
                        // don't care
                    }
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/partial"));

            messageLatch.await(1, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());


        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @ServerEndpoint("/whole")
    public static class WholeByteArray {
        @OnMessage
        public byte[] onMessage(byte[] message) {
            return message;
        }
    }

    @ServerEndpoint("/partial")
    public static class PartialByteArray {

        private List<byte[]> buffer = new ArrayList<byte[]>();

        @OnMessage
        public void onMessage(Session session, byte[] message, boolean isLast) {
            buffer.add(message);
            if (isLast) {
                try {
                    ByteBuffer b = null;

                    for (byte[] bytes : buffer) {
                        if (b == null) {
                            b = ByteBuffer.wrap(bytes);
                        } else {
                            b = joinBuffers(b, ByteBuffer.wrap(bytes));
                        }
                    }

                    session.getBasicRemote().sendBinary(b);
                } catch (IOException e) {
                    //
                }
                buffer.clear();
            }
        }
    }

    @ServerEndpoint("/whole")
    public static class WholeByteBuffer {
        @OnMessage
        public byte[] onMessage(byte[] message) {
            return message;
        }
    }

    @ServerEndpoint("/partial")
    public static class PartialByteBuffer {

        private List<byte[]> buffer = new ArrayList<byte[]>();

        @OnMessage
        public void onMessage(Session session, ByteBuffer message, boolean isLast) {
            buffer.add(message.array());
            if (isLast) {
                try {
                    ByteBuffer b = null;

                    for (byte[] bytes : buffer) {
                        if (b == null) {
                            b = ByteBuffer.wrap(bytes);
                        } else {
                            b = joinBuffers(b, ByteBuffer.wrap(bytes));
                        }
                    }

                    session.getBasicRemote().sendBinary(b);
                } catch (IOException e) {
                    //
                }
                buffer.clear();
            }
        }
    }

    private static ByteBuffer joinBuffers(ByteBuffer bb1, ByteBuffer bb2) {

        final int remaining1 = bb1.remaining();
        final int remaining2 = bb2.remaining();
        byte[] array = new byte[remaining1 + remaining2];
        bb1.get(array, 0, remaining1);
        System.arraycopy(bb2.array(), 0, array, remaining1, remaining2);


        ByteBuffer buf = ByteBuffer.wrap(array);
        buf.limit(remaining1 + remaining2);

        return buf;
    }

    private CountDownLatch messageLatch;

    @Test
    public void clientWholeServerWholeByteArray() {
        Server server = new Server(WholeByteArray.class);

        try {
            server.start();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            messageLatch = new CountDownLatch(1);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    session.addMessageHandler(new MessageHandler.Whole<byte[]>() {
                        @Override
                        public void onMessage(byte[] message) {
                            if (new String(message).equals("In my experience, there's no such thing as luck.")) {
                                messageLatch.countDown();
                            }
                        }
                    });

                    try {
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap("In my experience, there's no such thing as luck.".getBytes()));
                    } catch (IOException e) {
                        // don't care
                    }
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/whole"));

            messageLatch.await(1, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());


        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @Test
    public void clientWholeServerPartialByteArray() {
        Server server = new Server(PartialByteArray.class);

        try {
            server.start();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            messageLatch = new CountDownLatch(1);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    session.addMessageHandler(new MessageHandler.Whole<byte[]>() {
                        @Override
                        public void onMessage(byte[] message) {
                            if (new String(message).equals("In my experience, there's no such thing as luck.")) {
                                messageLatch.countDown();
                            }
                        }
                    });

                    try {
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap("In my experience, there's no such thing as luck.".getBytes()));
                    } catch (IOException e) {
                        // don't care
                    }
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/partial"));

            messageLatch.await(1, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());


        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @Test
    public void clientPartialServerWholeByteArray() {
        Server server = new Server(WholeByteArray.class);

        try {
            server.start();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            messageLatch = new CountDownLatch(1);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    session.addMessageHandler(new MessageHandler.Whole<byte[]>() {
                        @Override
                        public void onMessage(byte[] message) {
                            if (new String(message).equals("In my experience, there's no such thing as luck.")) {
                                messageLatch.countDown();
                            }
                        }
                    });

                    try {
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap("In my experience".getBytes()), false);
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap(", there's no such ".getBytes()), false);
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap("thing as luck.".getBytes()), true);
                    } catch (IOException e) {
                        // don't care
                    }
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/whole"));

            messageLatch.await(1, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());


        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @Test
    public void clientPartialServerPartialByteArray() {
        Server server = new Server(PartialByteArray.class);

        try {
            server.start();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            messageLatch = new CountDownLatch(1);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    session.addMessageHandler(new MessageHandler.Whole<byte[]>() {
                        @Override
                        public void onMessage(byte[] message) {
                            if (new String(message).equals("In my experience, there's no such thing as luck.")) {
                                messageLatch.countDown();
                            }
                        }
                    });

                    try {
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap("In my experience".getBytes()), false);
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap(", there's no such ".getBytes()), false);
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap("thing as luck.".getBytes()), true);
                    } catch (IOException e) {
                        // don't care
                    }
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/partial"));

            messageLatch.await(1, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());


        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @Test
    public void clientWholeServerWholeByteBuffer() {
        Server server = new Server(WholeByteBuffer.class);

        try {
            server.start();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            messageLatch = new CountDownLatch(1);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
                        @Override
                        public void onMessage(ByteBuffer message) {
                            if (new String(message.array()).equals("In my experience, there's no such thing as luck.")) {
                                messageLatch.countDown();
                            }
                        }
                    });

                    try {
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap("In my experience, there's no such thing as luck.".getBytes()));
                    } catch (IOException e) {
                        // don't care
                    }
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/whole"));

            messageLatch.await(1, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());


        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @Test
    public void clientWholeServerPartialByteBuffer() {
        Server server = new Server(PartialByteBuffer.class);

        try {
            server.start();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            messageLatch = new CountDownLatch(1);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
                        @Override
                        public void onMessage(ByteBuffer message) {
                            if (new String(message.array()).equals("In my experience, there's no such thing as luck.")) {
                                messageLatch.countDown();
                            }
                        }
                    });

                    try {
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap("In my experience, there's no such thing as luck.".getBytes()));
                    } catch (IOException e) {
                        // don't care
                    }
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/partial"));

            messageLatch.await(1, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());


        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @Test
    public void clientPartialServerWholeByteBuffer() {
        Server server = new Server(WholeByteBuffer.class);

        try {
            server.start();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            messageLatch = new CountDownLatch(1);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
                        @Override
                        public void onMessage(ByteBuffer message) {
                            if (new String(message.array()).equals("In my experience, there's no such thing as luck.")) {
                                messageLatch.countDown();
                            }
                        }
                    });

                    try {
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap("In my experience".getBytes()), false);
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap(", there's no such ".getBytes()), false);
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap("thing as luck.".getBytes()), true);
                    } catch (IOException e) {
                        // don't care
                    }
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/whole"));

            messageLatch.await(1, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());


        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @Test
    public void clientPartialServerPartialByteBuffer() {
        Server server = new Server(PartialByteBuffer.class);

        try {
            server.start();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            messageLatch = new CountDownLatch(1);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
                        @Override
                        public void onMessage(ByteBuffer message) {
                            if (new String(message.array()).equals("In my experience, there's no such thing as luck.")) {
                                messageLatch.countDown();
                            }
                        }
                    });

                    try {
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap("In my experience".getBytes()), false);
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap(", there's no such ".getBytes()), false);
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap("thing as luck.".getBytes()), true);
                    } catch (IOException e) {
                        // don't care
                    }
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/partial"));

            messageLatch.await(1, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());


        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }


    @ServerEndpoint("/clientPartialText")
    public static class ClientPartialText {
        @OnMessage
        public void onMessage(Session session, String message) throws IOException {
            session.getBasicRemote().sendText("In my experience", false);
            session.getBasicRemote().sendText(", there's no such ", false);
            session.getBasicRemote().sendText("thing as luck.", true);
        }
    }

    @ServerEndpoint("/clientPartialBinary")
    public static class ClientPartialBinary {
        @OnMessage
        public void onMessage(Session session, String message) throws IOException {
            session.getBasicRemote().sendBinary(ByteBuffer.wrap("In my experience".getBytes()), false);
            session.getBasicRemote().sendBinary(ByteBuffer.wrap(", there's no such ".getBytes()), false);
            session.getBasicRemote().sendBinary(ByteBuffer.wrap("thing as luck.".getBytes()), true);
        }
    }

    @Test
    public void clientReceivePartialTextAsWhole() {
        Server server = new Server(ClientPartialText.class);

        try {
            server.start();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            messageLatch = new CountDownLatch(1);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            if (message.equals("In my experience, there's no such thing as luck.")) {
                                messageLatch.countDown();
                            }
                        }
                    });

                    try {
                        session.getBasicRemote().sendText("In my experience, there's no such thing as luck.");
                    } catch (IOException e) {
                        // don't care
                    }
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/clientPartialText"));

            messageLatch.await(1, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());


        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @Test
    public void clientReceivePartialBinaryAsWhole() {
        Server server = new Server(ClientPartialBinary.class);

        try {
            server.start();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            messageLatch = new CountDownLatch(1);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
                        @Override
                        public void onMessage(ByteBuffer message) {
                            if (message.equals(ByteBuffer.wrap("In my experience, there's no such thing as luck.".getBytes()))) {
                                messageLatch.countDown();
                            }
                        }
                    });

                    try {
                        session.getBasicRemote().sendText("In my experience, there's no such thing as luck.");
                    } catch (IOException e) {
                        // don't care
                    }
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/clientPartialBinary"));

            messageLatch.await(1, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());


        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @ServerEndpoint("/reader")
    public static class WholeReader {

        public static CountDownLatch receivedMessageLatch = new CountDownLatch(1);


        @OnMessage
        public void onMessage(Session session, Reader reader) throws IOException {
            receivedMessageLatch.countDown();

            StringBuilder sb = new StringBuilder();
            int i;

            while ((i = reader.read()) != -1) {
                sb.append((char) i);
            }

            reader.close();
            session.getBasicRemote().sendText(sb.toString());
        }
    }

    @Test
    public void clientPartialServerWholeReader() {
        Server server = new Server(WholeReader.class);

        try {
            server.start();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            messageLatch = new CountDownLatch(2);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {
                boolean first = true;

                @Override
                public void onOpen(final Session session, EndpointConfig EndpointConfig) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            System.out.println("Client received message: " + message);

                            if (message.equals("In my experience, there's no such thing as luck.")) {
                                messageLatch.countDown();
                            }
                            if (first) {
                                try {
                                    WholeReader.receivedMessageLatch = new CountDownLatch(1);
                                    session.getBasicRemote().sendText("In my experience", false);
                                    WholeReader.receivedMessageLatch.await(1, TimeUnit.SECONDS);
                                    WholeReader.receivedMessageLatch = new CountDownLatch(1);
                                    session.getBasicRemote().sendText(", there's no such ", false);
                                    WholeReader.receivedMessageLatch.await(1, TimeUnit.SECONDS);
                                    WholeReader.receivedMessageLatch = new CountDownLatch(1);
                                    session.getBasicRemote().sendText("thing as luck.", true);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                                first = false;
                            }
                        }
                    });

                    try {
                        session.getBasicRemote().sendText("In my experience", false);
                        WholeReader.receivedMessageLatch.await(1, TimeUnit.SECONDS);
                        WholeReader.receivedMessageLatch = new CountDownLatch(1);
                        session.getBasicRemote().sendText(", there's no such ", false);
                        WholeReader.receivedMessageLatch.await(1, TimeUnit.SECONDS);
                        WholeReader.receivedMessageLatch = new CountDownLatch(1);
                        session.getBasicRemote().sendText("thing as luck.", true);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                public void onError(Session session, Throwable thr) {
                    thr.printStackTrace();
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/reader"));

            messageLatch.await(3, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());


        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @ServerEndpoint("/inputstream")
    public static class WholeInputStream {

        public static CountDownLatch receivedMessageLatch = new CountDownLatch(1);

        @OnMessage
        public void onMessage(Session session, InputStream is) throws IOException {
            receivedMessageLatch.countDown();

            ArrayList<Byte> bytes = new ArrayList<Byte>();
            int i;

            while ((i = is.read()) != -1) {
                bytes.add((byte) i);
            }

            byte[] result = new byte[bytes.size()];
            for (int j = 0; j < bytes.size(); j++) {
                result[j] = bytes.get(j);
            }

            is.close();
            session.getBasicRemote().sendBinary(ByteBuffer.wrap(result));
        }
    }

    @Test
    public void clientPartialServerWholeInputStream() {
        Server server = new Server(WholeInputStream.class);

        try {
            server.start();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            messageLatch = new CountDownLatch(2);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {
                boolean first = true;
                byte[] buf1 = {1, 2, 3};
                byte[] buf2 = {4, 5, 6};
                byte[] buf3 = {7, 8, 9};
                byte[] result = {1, 2, 3, 4, 5, 6, 7, 8, 9};

                @Override
                public void onOpen(final Session session, EndpointConfig EndpointConfig) {
                    session.addMessageHandler(new MessageHandler.Whole<byte[]>() {
                        @Override
                        public void onMessage(byte[] message) {
                            System.out.println("Client received message: " + message.toString());
                            assertArrayEquals(result, message);
                            messageLatch.countDown();

                            if (first) {
                                try {
                                    WholeInputStream.receivedMessageLatch = new CountDownLatch(1);
                                    session.getBasicRemote().sendBinary(ByteBuffer.wrap(buf1), false);
                                    WholeInputStream.receivedMessageLatch.await(1, TimeUnit.SECONDS);
                                    WholeInputStream.receivedMessageLatch = new CountDownLatch(1);
                                    session.getBasicRemote().sendBinary(ByteBuffer.wrap(buf2), false);
                                    WholeInputStream.receivedMessageLatch.await(1, TimeUnit.SECONDS);
                                    WholeInputStream.receivedMessageLatch = new CountDownLatch(1);
                                    session.getBasicRemote().sendBinary(ByteBuffer.wrap(buf3), true);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                                first = false;
                            }
                        }
                    });

                    try {
                        WholeInputStream.receivedMessageLatch = new CountDownLatch(1);
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap(buf1), false);
                        WholeInputStream.receivedMessageLatch.await(1, TimeUnit.SECONDS);
                        WholeInputStream.receivedMessageLatch = new CountDownLatch(1);
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap(buf2), false);
                        WholeInputStream.receivedMessageLatch.await(1, TimeUnit.SECONDS);
                        WholeInputStream.receivedMessageLatch = new CountDownLatch(1);
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap(buf3), true);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                public void onError(Session session, Throwable thr) {
                    thr.printStackTrace();
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/inputstream"));

            messageLatch.await(3, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());


        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @Test
    public void clientSendStreamServerWholeInputStream() {
        Server server = new Server(WholeInputStream.class);

        try {
            server.start();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            messageLatch = new CountDownLatch(1);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {
                byte[] buf1 = {1, 2, 3};
                byte[] buf2 = {4, 5, 6};
                byte[] buf3 = {7, 8, 9};
                byte[] result = {1, 2, 3, 4, 5, 6, 7, 8, 9};

                @Override
                public void onOpen(final Session session, EndpointConfig EndpointConfig) {

                    session.addMessageHandler(new MessageHandler.Whole<byte[]>() {
                        @Override
                        public void onMessage(byte[] message) {
                            for (int i = 0; i < result.length; i++) {
                                assertEquals(result[i], message[i]);
                            }

                            messageLatch.countDown();
                        }
                    });

                    try {
                        final OutputStream sendStream = session.getBasicRemote().getSendStream();

                        sendStream.write(buf1);
                        sendStream.write(buf2);
                        sendStream.write(buf3);

                        sendStream.flush();
                        sendStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                public void onError(Session session, Throwable thr) {
                    thr.printStackTrace();
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/inputstream"));

            messageLatch.await(3, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }
}
