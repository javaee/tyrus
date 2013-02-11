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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.websocket.CloseReason;
import javax.websocket.Decoder;
import javax.websocket.Encoder;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;
import javax.websocket.Session;
import javax.websocket.WebSocketOpen;
import javax.websocket.server.DefaultServerConfiguration;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfiguration;
import javax.websocket.server.WebSocketEndpoint;

import org.glassfish.tyrus.TyrusClientEndpointConfiguration;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class EndpointLifecycleTest {

    private static final String SENT_MESSAGE = "Hello World";

    private static final String PATH = "/EndpointLifecycleTest";

    Logger logger = Logger.getLogger(EndpointLifecycleTest.class.getName());

    final static int iterations = 3;
    static CountDownLatch messageLatch;

    @Ignore
    @Test
    public void testProgrammaticEndpoint() {

        messageLatch = new CountDownLatch(iterations);
        final AtomicInteger msgNumber = new AtomicInteger(0);
        Server server = new Server(ProgrammaticEndpointConfiguration.class);

        try {
            server.start();

            for (int i = 0; i < iterations; i++) {
                try {
                    final TyrusClientEndpointConfiguration.Builder builder = new TyrusClientEndpointConfiguration.Builder();
                    final TyrusClientEndpointConfiguration dcec = builder.build();

                    final String message = new String(SENT_MESSAGE + msgNumber.incrementAndGet());
                    // replace ClientManager with MockWebSocketClient to confirm the test passes if the backend
                    // does not have issues
                    final ClientManager client = ClientManager.createClient();
                    client.connectToServer(new TestEndpointAdapter() {

                        @Override
                        public EndpointConfiguration getEndpointConfiguration() {
                            return dcec;
                        }

                        @Override
                        public void onOpen(Session session) {
                            try {
                                session.addMessageHandler(new TestTextMessageHandler(this));
                                session.getRemote().sendString(message);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onMessage(String s) {

                        }
                    }, dcec, new URI("ws://localhost:8025/websockets/tests" + PATH));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            messageLatch.await(2000, TimeUnit.SECONDS);
            Assert.assertEquals(iterations, EchoEndpoint.getInstancesIds().size());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @Ignore
    @Test
    public void testAnnotatedEndpoint() {

        messageLatch = new CountDownLatch(iterations);
        final AtomicInteger msgNumber = new AtomicInteger(0);
        Server server = new Server(Annotated.class);

        try {
            server.start();

            for (int i = 0; i < iterations; i++) {
                try {
                    final TyrusClientEndpointConfiguration.Builder builder = new TyrusClientEndpointConfiguration.Builder();
                    final TyrusClientEndpointConfiguration dcec = builder.build();

                    final String message = new String(SENT_MESSAGE + msgNumber.incrementAndGet());
                    // replace ClientManager with MockWebSocketClient to confirm the test passes if the backend
                    // does not have issues
                    final ClientManager client = ClientManager.createClient();
                    client.connectToServer(new TestEndpointAdapter() {

                        @Override
                        public EndpointConfiguration getEndpointConfiguration() {
                            return dcec;
                        }

                        @Override
                        public void onOpen(Session session) {
                            try {
                                session.addMessageHandler(new TestTextMessageHandler(this));
                                session.getRemote().sendString(message);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onMessage(String s) {

                        }
                    }, dcec, new URI("ws://localhost:8025/websockets/tests" + PATH));
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

    public static class ProgrammaticEndpointConfiguration extends ServerEndpointConfigurationAdapter {

        @Override
        public Class<? extends Endpoint> getEndpointClass() {
            return EchoEndpoint.class;
        }

        @Override
        public boolean matchesURI(URI uri) {
            return true;
        }

        @Override
        public String getPath() {
            return PATH;
        }
    }

    @WebSocketEndpoint(value = "/EndpointLifecycleTest", configuration = DefaultServerConfiguration.class)
    public static class Annotated {

        private static Set<String> instancesIds = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

        @WebSocketOpen
        public void onOpen(Session s) {
            instancesIds.add(this.toString());
            messageLatch.countDown();
        }

        public static Set<String> getInstancesIds() {
            return instancesIds;
        }
    }

    public static class EchoEndpoint extends Endpoint {

        private static Set<String> instancesIds = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

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

    public static abstract class ServerEndpointConfigurationAdapter implements ServerEndpointConfiguration {
        @Override
        public String getNegotiatedSubprotocol(List<String> requestedSubprotocols) {
            return null;
        }

        @Override
        public List<Extension> getNegotiatedExtensions(List<Extension> requestedExtensions) {
            return Collections.emptyList();
        }

        @Override
        public boolean checkOrigin(String originHeaderValue) {
            return true;
        }

        @Override
        public void modifyHandshake(HandshakeRequest request, HandshakeResponse response) {

        }

        @Override
        public List<Encoder> getEncoders() {
            return Collections.emptyList();
        }

        @Override
        public List<Decoder> getDecoders() {
            return Collections.emptyList();
        }
    }
}
