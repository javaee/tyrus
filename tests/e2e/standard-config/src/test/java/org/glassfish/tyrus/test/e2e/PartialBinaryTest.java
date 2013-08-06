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

import org.glassfish.tyrus.testing.TestUtilities;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class PartialBinaryTest extends TestUtilities {

    private CountDownLatch messageLatch;
    private static final byte[] BINARY_MESSAGE_1 = new byte[]{1, 2};
    private static final byte[] BINARY_MESSAGE_2 = new byte[]{3, 4};
    private String receivedMessage;

    private final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

    @ServerEndpoint(value = "/partialbinary")
    public static class WSByteArrayPartialAndSessionServer {

        StringBuffer sb = new StringBuffer();
        int messageCounter = 0;

        @OnMessage
        public void bytesToString(byte[] array, Session s, boolean finito) throws IOException {
            messageCounter++;
            sb.append(new String(array));
            if (messageCounter == 2) {
                s.getBasicRemote().sendText(sb.toString());
                sb = new StringBuffer();
            }
        }
    }

    @Test
    public void testBinaryByteArrayBean() throws DeploymentException {
        Server server = startServer(WSByteArrayPartialAndSessionServer.class);

        try {
            messageLatch = new CountDownLatch(1);

            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap(BINARY_MESSAGE_1), false);
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap(BINARY_MESSAGE_2), false);
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String data) {
                                receivedMessage = data;
                                messageLatch.countDown();
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, cec, getURI(WSByteArrayPartialAndSessionServer.class));
            messageLatch.await(2, TimeUnit.SECONDS);
            Assert.assertEquals("The received message is the same as the sent one", new String(BINARY_MESSAGE_1) + new String(BINARY_MESSAGE_2), receivedMessage);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint(value = "/partialbinary2")
    public static class WSByteBufferPartialAndSessionServer {

        StringBuffer sb = new StringBuffer();
        int messageCounter = 0;

        @OnMessage
        public void bytesToString(ByteBuffer buffer, Session s, boolean finito) throws IOException {
            messageCounter++;
            sb.append(new String(buffer.array()));
            if (messageCounter == 2) {
                s.getBasicRemote().sendText(sb.toString());
                sb = new StringBuffer();
            }
        }
    }

    @Test
    public void testBinaryByteBufferBean() throws DeploymentException {
        Server server = startServer(WSByteBufferPartialAndSessionServer.class);

        try {
            messageLatch = new CountDownLatch(1);

            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap(BINARY_MESSAGE_1), false);
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap(BINARY_MESSAGE_2), false);
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String data) {
                                receivedMessage = data;
                                messageLatch.countDown();
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, cec, getURI(WSByteBufferPartialAndSessionServer.class));
            messageLatch.await(2, TimeUnit.SECONDS);
            Assert.assertEquals("The received message is the same as the sent one", new String(BINARY_MESSAGE_1) + new String(BINARY_MESSAGE_2), receivedMessage);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }
}