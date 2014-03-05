/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * Tests the BufferedInputStream and bug fix TYRUS-274
 * <p/>
 * Client opens DataOutputStream to write int and server uses DataInputStream
 * to read int and verify the message
 *
 * @author Raghuram Krishnamchari (raghuramcbz at gmail.com) jira/github user: raghucbz
 */
public class BufferedInputStreamTest extends TestContainer {
    public static int MESSAGE = 1234;

    @Test
    public void testClient() throws DeploymentException {
        final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();
        Server server = startServer(BufferedInputStreamEndpoint.class);

        try {
            final CountDownLatch messageLatch = new CountDownLatch(1);
            BufferedInputStreamClient bisc = new BufferedInputStreamClient(messageLatch);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(bisc, cec, getURI(BufferedInputStreamEndpoint.class));

            assertTrue(messageLatch.await(3, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    /**
     * BufferedInputStream Server Endpoint.
     *
     * @author Raghuram Krishnamchari (raghuramcbz at gmail.com) jira/github user: raghucbz
     */
    @ServerEndpoint(value = "/bufferedinputstreamserver")
    public static class BufferedInputStreamEndpoint {

        @OnOpen
        public void init(final Session session) {
            System.out.println("BufferedInputStreamServer opened");
            session.addMessageHandler(new MessageHandler.Whole<InputStream>() {

                @Override
                public void onMessage(InputStream inputStream) {
                    System.out.println("BufferedInputStreamServer got message: " + inputStream);
                    try {
                        DataInputStream dataInputStream = new DataInputStream(inputStream);
                        int messageReceived = dataInputStream.readInt();

                        // assertTrue("Server did not get the right message: " + messageReceived, messageReceived == BufferedInputStreamTest.MESSAGE);
                        if (messageReceived == BufferedInputStreamTest.MESSAGE) {
                            System.out.println("Server successfully got message: " + messageReceived);
                            session.getBasicRemote().sendText("ok");
                        }
                    } catch (Exception e) {
                        System.out.println("BufferedInputStreamServer exception: " + e);
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    /**
     * BufferedInputStream Client Endpoint.
     *
     * @author Raghuram Krishnamchari (raghuramcbz at gmail.com) jira/github user: raghucbz
     */
    public class BufferedInputStreamClient extends Endpoint {

        private final CountDownLatch messageLatch;

        public BufferedInputStreamClient(CountDownLatch messageLatch) {
            this.messageLatch = messageLatch;
        }

        @Override
        public void onOpen(Session session, EndpointConfig EndpointConfig) {
            session.addMessageHandler(new MessageHandler.Whole<String>() {
                @Override
                public void onMessage(String message) {
                    if (message.equals("ok")) {
                        messageLatch.countDown();
                    }
                }
            });

            System.out.println("BufferedInputStreamClient opened !!");
            try {
                OutputStream outputStream = session.getBasicRemote().getSendStream();
                DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
                dataOutputStream.writeInt(MESSAGE);
                dataOutputStream.close();
                System.out.println("## BufferedInputStreamClient - binary message sent");

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onError(Session session, Throwable thr) {
            thr.printStackTrace();
        }
    }
}