/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2013 Oracle and/or its affiliates. All rights reserved.
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
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests the basic client behavior, sending and receiving message
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Martin Matula (martin.matula at oracle.com)
 */
public class BlockingStreamingTextTest {

    @Ignore
    @Test
    public void testBlockingStreamingTextServer() {
        final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

        Server server = new Server(BlockingStreamingTextEndpoint.class);

        try {
            server.start();
            CountDownLatch messageLatch = new CountDownLatch(1);

            BlockingStreamingTextClient bstc = new BlockingStreamingTextClient(messageLatch);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(bstc, cec, new URI("ws://localhost:8025/websockets/tests/blockingstreaming"));

            messageLatch.await(5, TimeUnit.SECONDS);
            System.out.println("SENT: " + bstc.sentMessage);
            System.out.println("RECEIVED: " + bstc.receivedMessage);
            Assert.assertTrue("Client got back what it sent, all pieces in the right order.", bstc.sentMessage.equals(bstc.receivedMessage));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    /**
     * @author Danny Coward (danny.coward at oracle.com)
     * @author Martin Matula (martin.matula at oracle.com)
     */
    @ServerEndpoint(value = "/blockingstreaming")
    public static class BlockingStreamingTextEndpoint extends Endpoint {
        class MyCharacterStreamHandler implements MessageHandler.Partial<Reader> {
            Session session;

            MyCharacterStreamHandler(Session session) {
                this.session = session;
            }

            @Override
            public void onMessage(Reader r, boolean isLast) {
                System.out.println("BLOCKINGSTREAMSERVER: on message reader called");
                StringBuilder sb = new StringBuilder();
                try {
                    int i;
                    while ((i = r.read()) != -1) {
                        sb.append((char) i);
                    }
                    r.close();

                    String receivedMessage = sb.toString();
                    System.out.println("BLOCKINGSTREAMSERVER received: " + receivedMessage);

                    Writer w = session.getBasicRemote().getSendWriter();
                    w.write(receivedMessage.substring(0, 4));
                    w.write(receivedMessage.substring(4, receivedMessage.length()));
                    w.close();
                    System.out.println("BLOCKINGSTREAMSERVER sent back: " + receivedMessage);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        @OnOpen
        public void onOpen(Session session, EndpointConfig EndpointConfig) {
            System.out.println("BLOCKINGSERVER opened !");
            session.addMessageHandler(new MyCharacterStreamHandler(session));
        }

    }

    /**
     * @author Danny Coward (danny.coward at oracle.com)
     */
    public static class BlockingStreamingTextClient extends Endpoint {
        String receivedMessage;
        private final CountDownLatch messageLatch;
        String sentMessage;

        public BlockingStreamingTextClient(CountDownLatch messageLatch) {
            this.messageLatch = messageLatch;
        }

        public void onOpen(Session session, EndpointConfig EndpointConfig) {
            System.out.println("BLOCKINGCLIENT opened !");

            send(session);

            session.addMessageHandler(new MessageHandler.Partial<Reader>() {
                final StringBuilder sb = new StringBuilder();

                public void onMessage(Reader r, boolean isLast) {
                    System.out.println("BLOCKINGCLIENT onMessage called ");
                    try {
                        int i;
                        while ((i = r.read()) != -1) {
                            sb.append((char) i);
                        }
                        receivedMessage = sb.toString();
                        System.out.println("BLOCKINGCLIENT received: " + receivedMessage);
                        messageLatch.countDown();
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }


                }
            });
        }

        public void send(Session session) {
            try {
                StringBuilder sb = new StringBuilder();
                String part;
                for (int i = 0; i < 10; i++) {
                    part = "blk" + i;
                    session.getBasicRemote().sendText(part, false);
                    sb.append(part);
                }
                part = "END";
                session.getBasicRemote().sendText(part, true);
                sb.append(part);
                sentMessage = sb.toString();
                System.out.println("BLOCKINGCLIENT: Sent" + sentMessage);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
