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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpoint;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.EndpointConfig;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.glassfish.tyrus.testing.TestUtilities;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.e2e.bean.TestEndpoint;
import org.glassfish.tyrus.test.e2e.decoder.TestDecoder;
import org.glassfish.tyrus.test.e2e.message.TestMessage;

import org.junit.Assert;
import org.junit.Test;


/**
 * Tests the client with the annotated version of the
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class AnnotatedClientTest extends TestUtilities {

    private static String receivedMessage;

    private static String receivedTestMessage;

    private static CountDownLatch messageLatch;

    @Test
    public void testAnnotatedInstance() throws DeploymentException {
        Server server = startServer(TestEndpoint.class);
        final ClientEndpointConfig configuration = ClientEndpointConfig.Builder.create().build();

        messageLatch = new CountDownLatch(1);

        try {
            ClientManager client = ClientManager.createClient();


            client.connectToServer(new TestEndpointAdapter() {
                @Override
                public EndpointConfig getEndpointConfig() {
                    return configuration;
                }

                @Override
                public void onOpen(Session session) {
                    try {
                        session.addMessageHandler(new TestTextMessageHandler(this));
                        session.getBasicRemote().sendText("hello");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onMessage(String message) {
                    receivedMessage = message;
                    messageLatch.countDown();
                }
            }, configuration, getURI(TestEndpoint.class));
            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertEquals("hello", receivedMessage);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testAnnotatedInstanceWithDecoding() throws DeploymentException {
        Server server = startServer(TestEndpoint.class);
        messageLatch = new CountDownLatch(1);

        try {
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new ClientTestEndpoint(), getURI(TestEndpoint.class));
            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertEquals("testHello", receivedTestMessage);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testAnnotatedClass() throws DeploymentException {
        Server server = startServer(TestEndpoint.class);
        messageLatch = new CountDownLatch(1);
        receivedMessage = null;

        try {
            ClientManager client = ClientManager.createClient();
            client.connectToServer(SimpleClientTestEndpoint.class, getURI(TestEndpoint.class));
            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertEquals("hello", receivedMessage);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testNonAnnotatedClass() throws Exception {
        WebSocketContainer wc = ContainerProvider.getWebSocketContainer();
        try {
            wc.connectToServer(String.class, getURI(TestEndpoint.class));
        } catch(DeploymentException de) {
            // Expected exception, ignore
        }
    }

    /**
     * Testing the basic annotations.
     *
     * @author Stepan Kopriva (stepan.kopriva at oracle.com)
     */
    @ClientEndpoint(decoders = {TestDecoder.class})
    public class ClientTestEndpoint {

        private static final String SENT_TEST_MESSAGE = "testHello";

        @OnOpen
        public void onOpen(Session p) {
            try {
                p.getBasicRemote().sendText(TestMessage.PREFIX + SENT_TEST_MESSAGE);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @OnMessage
        public void onTestMesage(TestMessage tm) {
            receivedTestMessage = tm.getData();
            messageLatch.countDown();
        }
    }

    @ClientEndpoint
    public static class SimpleClientTestEndpoint {
        private static final String SENT_MESSAGE = "hello";

        public SimpleClientTestEndpoint() {
        }

        @OnOpen
        public void onOpen(Session p) {
            try {
                p.getBasicRemote().sendText(SENT_MESSAGE);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @OnMessage
        public void onMessage(String message) {
            receivedMessage = message;
            messageLatch.countDown();
        }
    }
}
