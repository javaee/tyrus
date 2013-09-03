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

package org.glassfish.tyrus.sample.cdi;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * This test works properly with EE container only.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class CdiTest extends TestContainer {

    private static final String SENT_MESSAGE = "Do or do not, there is no try.";
    private static volatile int result;

    public CdiTest() {
        setContextPath("/sample-cdi");
    }

    @Test
    public void testSimple() throws DeploymentException, InterruptedException, IOException {
        final String host = System.getProperty("tyrus.test.host");
        if (host == null) {
            return;
        }

        final CountDownLatch messageLatch = new CountDownLatch(1);

        final ClientManager client = ClientManager.createClient();
        client.connectToServer(new Endpoint() {
            @Override
            public void onOpen(Session session, EndpointConfig EndpointConfig) {
                try {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            assertEquals(message, String.format("%s (from your server)", SENT_MESSAGE));
                            messageLatch.countDown();
                        }
                    });

                    session.getBasicRemote().sendText(SENT_MESSAGE);
                } catch (IOException e) {
                    //do nothing
                }
            }
        }, ClientEndpointConfig.Builder.create().build(), getURI("/simple"));

        messageLatch.await(2, TimeUnit.SECONDS);
        assertEquals("Number of received messages is not 0.", 0, messageLatch.getCount());
    }

    @Test
    public void testStatefulOneClientTwoMessages() throws DeploymentException, InterruptedException, IOException {
        final String host = System.getProperty("tyrus.test.host");
        if (host == null) {
            return;
        }

        final CountDownLatch messageLatch = new CountDownLatch(2);

        final ClientManager client = ClientManager.createClient();
        client.connectToServer(new Endpoint() {
            boolean first = true;
            int firstMessage;
            int secondMessage;

            @Override
            public void onOpen(final Session session, EndpointConfig EndpointConfig) {
                try {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {

                        @Override
                        public void onMessage(String message) {
                            try{
                                if (first) {
                                    firstMessage = Integer.parseInt(message.split(":")[1]);
                                    messageLatch.countDown();
                                    try {
                                        session.getBasicRemote().sendText(SENT_MESSAGE);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                    first = false;
                                } else {
                                    secondMessage = Integer.parseInt(message.split(":")[1]);
                                    assertEquals("secondMessage - firstMessage != 1",1,secondMessage - firstMessage);
                                    messageLatch.countDown();
                                }
                            }catch(Exception e){
                                e.printStackTrace();
                            }
                        }
                    });
                    session.getBasicRemote().sendText("Do or do not, there is no try.");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, ClientEndpointConfig.Builder.create().build(), getURI("/injectingstateful"));

        messageLatch.await(2, TimeUnit.SECONDS);
        assertEquals("Number of received messages is not 0.", 0, messageLatch.getCount());

    }

    @Test
    public void testInjectedStatefulTwoMessagesFromTwoClients() throws InterruptedException, DeploymentException, IOException {
        testFromTwoClients("/injectingstateful", 0);
    }

    @Test
    public void testInjectedSingletonTwoMessagesFromTwoClients() throws InterruptedException, DeploymentException, IOException {
        testFromTwoClients("/injectingsingleton", 1);
    }

    @Test
    public void testStatefulTwoMessagesFromTwoClients() throws InterruptedException, DeploymentException, IOException {
        testFromTwoClients("/stateful", 0);
    }

    @Test
    public void testSingletonTwoMessagesFromTwoClients() throws InterruptedException, DeploymentException, IOException {
        testFromTwoClients("/singleton", 1);
    }

    public void testFromTwoClients(String path, int diff) throws DeploymentException, InterruptedException, IOException {
        final String host = System.getProperty("tyrus.test.host");
        if (host == null) {
            return;
        }
        ClientManager client = ClientManager.createClient();

        int value1 = testOneClient(client, path);
        int value2 = testOneClient(client, path);

        assertEquals("The difference is not as expected", diff, value2 - value1);
    }

    public int testOneClient(ClientManager client, String path) throws InterruptedException, DeploymentException, IOException {

        final CountDownLatch messageLatch = new CountDownLatch(1);
        client.connectToServer(new Endpoint() {

            @Override
            public void onOpen(final Session session, EndpointConfig EndpointConfig) {
                try {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            result = Integer.parseInt(message.split(":")[1]);
                            messageLatch.countDown();
                        }
                    });

                    session.getBasicRemote().sendText("Do or do not, there is no try.");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            public void onError(Session session, Throwable thr) {
                thr.printStackTrace();
            }
        }, ClientEndpointConfig.Builder.create().build(), getURI(path));

        messageLatch.await(2, TimeUnit.SECONDS);
        assertEquals("Number of received messages is not 0.", 0, messageLatch.getCount());

        return result;
    }

    @Test
    public void testApplicationScoped() throws DeploymentException, InterruptedException, IOException {
        final String host = System.getProperty("tyrus.test.host");
        if (host == null) {
            return;
        }

        final CountDownLatch messageLatch = new CountDownLatch(1);

        final ClientManager client = ClientManager.createClient();
        client.connectToServer(new Endpoint() {
            @Override
            public void onOpen(Session session, EndpointConfig EndpointConfig) {
                try {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            System.out.println("IAS received: " + message);
                            assertEquals(message, String.format("%s (from your server)", SENT_MESSAGE));
                            messageLatch.countDown();
                        }
                    });

                    session.getBasicRemote().sendText(SENT_MESSAGE);
                } catch (IOException e) {
                    //do nothing
                }
            }
        }, ClientEndpointConfig.Builder.create().build(), getURI("/injectingappscoped"));

        messageLatch.await(2, TimeUnit.SECONDS);
        assertEquals("Number of received messages is not 0.", 0, messageLatch.getCount());
    }
}
