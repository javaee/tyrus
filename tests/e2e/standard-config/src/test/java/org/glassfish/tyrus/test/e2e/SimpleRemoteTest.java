/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import org.glassfish.tyrus.testing.TestUtilities;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.e2e.bean.SimpleRemoteTestEndpoint;

import org.junit.Assert;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the basic behaviour of remote
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Martin Matula (martin.matula at oracle.com)
 */
public class SimpleRemoteTest extends TestUtilities{
    private String receivedMessage;
    private static final String SENT_MESSAGE = "Hello World";
    private static  final Logger LOGGER = Logger.getLogger(SimpleRemoteTest.class.getName());

    @Test
    public void testSimpleRemote() throws DeploymentException {
        final CountDownLatch messageLatch = new CountDownLatch(1);
        Server server = startServer(SimpleRemoteTestEndpoint.class);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            final ClientManager client = ClientManager.createClient();
            client.connectToServer(new TestEndpointAdapter() {
                @Override
                public EndpointConfig getEndpointConfig() {
                    return cec;
                }

                @Override
                public void onOpen(Session session) {
                    try {
                        session.addMessageHandler(new TestTextMessageHandler(this));
                        session.getBasicRemote().sendText(SENT_MESSAGE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onMessage(String message) {
                    receivedMessage = message;
                    messageLatch.countDown();
                }
            }, cec, getURI(SimpleRemoteTestEndpoint.class));

            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertTrue("The received message is the same as the sent one", receivedMessage.equals(SENT_MESSAGE));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testSimpleRemoteMT() throws DeploymentException {
        final int clients = 10;
        final CountDownLatch messageLatch = new CountDownLatch(2 * clients);
        final AtomicInteger msgNumber = new AtomicInteger(0);
        Server server = startServer(SimpleRemoteTestEndpoint.class);

        try {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

                        final CountDownLatch perClientLatch = new CountDownLatch(2);
                        final String[] message = new String[]{SENT_MESSAGE + msgNumber.incrementAndGet(),
                                SENT_MESSAGE + msgNumber.incrementAndGet()};
                        // replace ClientManager with MockClientEndpoint to confirm the test passes if the backend
                        // does not have issues
                        final ClientManager client = ClientManager.createClient();
                        client.connectToServer(new Endpoint()  {

                            @Override
                            public void onOpen(Session session, EndpointConfig EndpointConfig) {
                                try {
                                    session.addMessageHandler(new MessageHandler.Whole<String>(){
                                        @Override
                                        public void onMessage(String s) {
                                            perClientLatch.countDown();
                                            String testString = message[(int) perClientLatch.getCount()];
                                            assertEquals(testString, s);
                                            messageLatch.countDown();
                                        }
                                    });
                                    session.getBasicRemote().sendText(message[1]);
                                    Thread.sleep(100);
                                    session.getBasicRemote().sendText(message[0]);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onError(Session session, Throwable thr) {
                                LOGGER.log(Level.SEVERE, "onError: ");
                                thr.printStackTrace();
                            }
                        }, cec, getURI(SimpleRemoteTestEndpoint.class));
                        perClientLatch.await(5, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            for (int i = 0; i < clients; i++) {
                new Thread(runnable).start();
            }
            messageLatch.await(5, TimeUnit.SECONDS);
            assertTrue("The following number of messages was not delivered correctly: " + messageLatch.getCount()
                    + ". See exception traces above for the complete list of issues.",
                    0 == messageLatch.getCount());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }
}
