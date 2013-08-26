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
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class GetEndpointInstanceTest extends TestContainer {
    private String receivedMessage;

    private static final String SENT_MESSAGE = "Always pass on what you have learned.";

    @ServerEndpoint(value = "/echoAnnotated", configurator = MyServerConfigurator.class)
    public static class MyEndpointAnnotated {

        @OnMessage
        public String onMessage(String message) {

            assertEquals(MyServerConfigurator.testEndpoint1, this);

            return message;
        }
    }

    public static class MyEndpointProgrammatic extends Endpoint implements MessageHandler.Whole<String> {

        Session session;

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            this.session = session;
            session.addMessageHandler(this);
        }

        @Override
        public void onMessage(String message) {

            assertEquals(MyServerConfigurator.testEndpoint2, this);

            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException e) {
                // do nothing.
            }
        }
    }

    public static class MyServerConfigurator extends ServerEndpointConfig.Configurator {

        public static final MyEndpointAnnotated testEndpoint1 = new MyEndpointAnnotated();
        public static final MyEndpointProgrammatic testEndpoint2 = new MyEndpointProgrammatic();

        @Override
        public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
            if (endpointClass.equals(MyEndpointAnnotated.class)) {
                return (T) testEndpoint1;
            } else if (endpointClass.equals(MyEndpointProgrammatic.class)) {
                return (T) testEndpoint2;
            }

            throw new InstantiationException();
        }
    }

    public static class MyApplication implements ServerApplicationConfig {
        @Override
        public Set<ServerEndpointConfig> getEndpointConfigs(Set<Class<? extends Endpoint>> endpointClasses) {
            return new HashSet<ServerEndpointConfig>(Arrays.asList(ServerEndpointConfig.Builder.create(MyEndpointProgrammatic.class, "/echoProgrammatic").configurator(new MyServerConfigurator()).build()));
        }

        @Override
        public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> scanned) {
            return new HashSet<Class<?>>(Arrays.asList(MyEndpointAnnotated.class));
        }
    }

    @Test
    public void testAnnotated() throws DeploymentException {
        Server server = startServer(MyEndpointAnnotated.class);

        final CountDownLatch messageLatch = new CountDownLatch(1);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(final Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                receivedMessage = message;
                                messageLatch.countDown();
                            }
                        });

                        session.getBasicRemote().sendText(SENT_MESSAGE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, cec, getURI(MyEndpointAnnotated.class));

            messageLatch.await(5, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());
            assertEquals(SENT_MESSAGE, receivedMessage);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testProgrammatic() {
        Server server = new Server(MyApplication.class);

        final CountDownLatch messageLatch = new CountDownLatch(1);

        try {
            server.start();

            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(final Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                receivedMessage = message;
                                messageLatch.countDown();
                            }
                        });

                        session.getBasicRemote().sendText(SENT_MESSAGE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/echoProgrammatic"));

            messageLatch.await(5, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());
            assertEquals(SENT_MESSAGE, receivedMessage);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }
}
