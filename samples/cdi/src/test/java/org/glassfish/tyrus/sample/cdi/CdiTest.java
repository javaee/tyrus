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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfigurationBuilder;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import org.glassfish.tyrus.client.ClientManager;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * This test works properly with EE container only.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class CdiTest {

    private static final String CONTEXT_PATH = "/sample-cdi";
    private static final String SENT_MESSAGE = "Do or do not, there is no try.";

    private String getHost() {
        return System.getProperty("tyrus.test.host");
    }

    private int getPort() {
        final String port = System.getProperty("tyrus.test.port");
        return Integer.parseInt(port);
    }

    private URI getURI(String path) {
        try {
            return new URI("ws", null, getHost(), getPort(), CONTEXT_PATH + path, null, null);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }


    @Test
    public void testEcho() throws DeploymentException, InterruptedException {
        final String host = System.getProperty("tyrus.test.host");
        if (host == null) {
            return;
        }

        final CountDownLatch messageLatch = new CountDownLatch(1);

        final ClientManager client = ClientManager.createClient();
        client.connectToServer(new Endpoint() {
            @Override
            public void onOpen(Session session, EndpointConfiguration endpointConfiguration) {
                try {
                    session.addMessageHandler(new MessageHandler.Basic<String>() {
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
        }, ClientEndpointConfigurationBuilder.create().build(), getURI("/simple"));

        messageLatch.await(1, TimeUnit.SECONDS);
        if (messageLatch.getCount() != 0) {
            fail();
        }
    }


    @Test
    public void testStatefulOneClientTwoMessages() throws DeploymentException, InterruptedException {
        final String host = System.getProperty("tyrus.test.host");
        if (host == null) {
            return;
        }

        final CountDownLatch messageLatch = new CountDownLatch(2);

        final ClientManager client = ClientManager.createClient();
        client.connectToServer(new Endpoint() {
            boolean first = true;

            @Override
            public void onOpen(final Session session, EndpointConfiguration endpointConfiguration) {
                try {
                    session.addMessageHandler(new MessageHandler.Basic<String>() {
                        @Override
                        public void onMessage(String message) {
                            if (first) {
                                assertEquals("First message was wrong", String.format("%s%s1", SENT_MESSAGE, InjectToBeanStateful.TEXT), message);
                                messageLatch.countDown();
                                try {
                                    session.getBasicRemote().sendText(SENT_MESSAGE);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                first = false;
                            } else {
                                assertEquals("Second message was wrong", String.format("%s%s2", SENT_MESSAGE, InjectToBeanStateful.TEXT), message);
                                messageLatch.countDown();
                            }
                        }
                    });

                    session.getBasicRemote().sendText("Do or do not, there is no try.");
                } catch (IOException e) {
                    //do nothing
                }
            }
        }, ClientEndpointConfigurationBuilder.create().build(), getURI("/stateful"));

        messageLatch.await(1, TimeUnit.SECONDS);
        if (messageLatch.getCount() != 0) {
            fail("Messages not received");
        }
    }

    @Test
    public void testStatefulTwoMessagesFromTwoClients() throws InterruptedException, DeploymentException {
        testFromTwoClients("/stateful", String.format("%s%s1", SENT_MESSAGE, InjectToBeanStateful.TEXT), String.format("%s%s1", SENT_MESSAGE, InjectToBeanStateful.TEXT));
    }

    @Test
    public void testSingletonTwoMessagesFromTwoClients() throws InterruptedException, DeploymentException {
        testFromTwoClients("/singleton", String.format("%s%s1", SENT_MESSAGE, InjectToBeanStateful.TEXT), String.format("%s%s2", SENT_MESSAGE, InjectToBeanStateful.TEXT));
    }

    public void testFromTwoClients(String path, final String expected1, final String expected2) throws DeploymentException, InterruptedException {
        ClientManager client = ClientManager.createClient();

        testOneClient(client, path, expected1);
        testOneClient(client, path, expected2);
    }

    public void testOneClient(ClientManager client, String path, final String expected) throws InterruptedException, DeploymentException {
        final String host = System.getProperty("tyrus.test.host");
        if (host == null) {
            return;
        }

        final CountDownLatch messageLatch = new CountDownLatch(1);
        client.connectToServer(new Endpoint() {

            @Override
            public void onOpen(final Session session, EndpointConfiguration endpointConfiguration) {
                try {
                    session.addMessageHandler(new MessageHandler.Basic<String>() {
                        @Override
                        public void onMessage(String message) {
                            assertEquals("Unexpected message.", message, expected);
                            messageLatch.countDown();
                        }
                    });

                    session.getBasicRemote().sendText("Do or do not, there is no try.");
                } catch (IOException e) {
                    //do nothing
                }
            }
        }, ClientEndpointConfigurationBuilder.create().build(), getURI(path));

        messageLatch.await(2, TimeUnit.SECONDS);
        if (messageLatch.getCount() != 0) {
            fail("Wrong number of received messages");
        }
    }
}
