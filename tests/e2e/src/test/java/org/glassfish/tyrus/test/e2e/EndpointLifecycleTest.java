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

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.websocket.ClientEndpointConfiguration;
import javax.websocket.ClientEndpointConfigurationBuilder;
import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfiguration;
import javax.websocket.server.ServerEndpointConfigurationBuilder;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.server.TyrusServerConfiguration;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class EndpointLifecycleTest {

    private static final String SENT_MESSAGE = "Hello World";

    private static final String PATH = "/EndpointLifecycleTest";

    final static int iterations = 3;
    static CountDownLatch messageLatch;

    @Test
    public void testProgrammaticEndpoint() {

        messageLatch = new CountDownLatch(iterations);
        final AtomicInteger msgNumber = new AtomicInteger(0);
        Server server = new Server(ProgrammaticEndpointApplicationConfiguration.class);

        try {
            server.start();

            for (int i = 0; i < iterations; i++) {
                try {
                    final ClientEndpointConfiguration cec = ClientEndpointConfigurationBuilder.create().build();

                    final String message = SENT_MESSAGE + msgNumber.incrementAndGet();
                    // replace ClientManager with MockClientEndpoint to confirm the test passes if the backend
                    // does not have issues
                    final ClientManager client = ClientManager.createClient();
                    client.connectToServer(new TestEndpointAdapter() {

                        @Override
                        public EndpointConfiguration getEndpointConfiguration() {
                            return cec;
                        }

                        @Override
                        public void onOpen(Session session) {
                            try {
                                session.addMessageHandler(new TestTextMessageHandler(this));
                                session.getBasicRemote().sendText(message);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onMessage(String s) {

                        }
                    }, cec, new URI("ws://localhost:8025/websockets/tests" + PATH));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            messageLatch.await(2, TimeUnit.SECONDS);
            Assert.assertEquals(iterations, EchoEndpoint.getInstancesIds().size());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @Test
    public void testAnnotatedEndpoint() {

        messageLatch = new CountDownLatch(iterations);
        final AtomicInteger msgNumber = new AtomicInteger(0);
        Server server = new Server(Annotated.class);

        try {
            server.start();

            for (int i = 0; i < iterations; i++) {
                try {
                    final ClientEndpointConfiguration cec = ClientEndpointConfigurationBuilder.create().build();

                    final String message = SENT_MESSAGE + msgNumber.incrementAndGet();
                    // replace ClientManager with MockClientEndpoint to confirm the test passes if the backend
                    // does not have issues
                    final ClientManager client = ClientManager.createClient();
                    client.connectToServer(new TestEndpointAdapter() {

                        @Override
                        public EndpointConfiguration getEndpointConfiguration() {
                            return cec;
                        }

                        @Override
                        public void onOpen(Session session) {
                            try {
                                session.addMessageHandler(new TestTextMessageHandler(this));
                                session.getBasicRemote().sendText(message);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onMessage(String s) {

                        }
                    }, cec, new URI("ws://localhost:8025/websockets/tests" + PATH));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            messageLatch.await(2, TimeUnit.SECONDS);
            Assert.assertEquals(iterations, Annotated.getInstancesIds().size());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    public static class ProgrammaticEndpointApplicationConfiguration extends TyrusServerConfiguration {

        public ProgrammaticEndpointApplicationConfiguration() {
            super(Collections.<Class<?>>emptySet(),
                    new HashSet<ServerEndpointConfiguration>() {{
                        add(ServerEndpointConfigurationBuilder.create(EchoEndpoint.class, PATH).build());
                    }});
        }
    }

    @ServerEndpoint(value = "/EndpointLifecycleTest")
    public static class Annotated {

        private static final Set<String> instancesIds = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

        @OnOpen
        public void onOpen(Session s) {
            instancesIds.add(this.toString());
            messageLatch.countDown();
        }

        public static Set<String> getInstancesIds() {
            return instancesIds;
        }
    }

    public static class EchoEndpoint extends Endpoint {

        private static final Set<String> instancesIds = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

        @Override
        public void onOpen(Session session, EndpointConfiguration config) {
            instancesIds.add(this.toString());
            messageLatch.countDown();
        }

        @Override
        public void onClose(Session session, CloseReason closeReason) {

        }

        public static Set<String> getInstancesIds() {
            return instancesIds;
        }
    }
}
