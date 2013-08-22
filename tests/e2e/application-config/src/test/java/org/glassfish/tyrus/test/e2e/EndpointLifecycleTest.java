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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.server.TyrusServerConfiguration;
import org.glassfish.tyrus.testing.TestUtilities;

import org.junit.Test;

/**
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class EndpointLifecycleTest extends TestUtilities {

    private static final String SENT_MESSAGE = "Hello World";

    private static final String PATH = "/EndpointLifecycleTest1";

    final static int iterations = 3;

    public static class ServerDeployApplicationConfig extends TyrusServerConfiguration {
        public ServerDeployApplicationConfig() {
            super(new HashSet<Class<?>>() {{
                add(Annotated.class);
                add(ServiceEndpoint.class);
            }}, Collections.<ServerEndpointConfig> emptySet());
        }
    }

    @ServerEndpoint(value = "/servicelifecycletest")
    public static class ServiceEndpoint {

        @OnMessage
        public String onMessage(String message) {
            if (message.equals("Annotated")){
                if(Annotated.getInstancesIds().size() == iterations) {
                    return POSITIVE;
                }
            } else if(message.equals("Programmatic")) {
                if(Programmatic.getInstancesIds().size() == iterations) {
                    return POSITIVE;
                }
            }

            return NEGATIVE;
        }
    }

    @Test
    public void testProgrammaticEndpoint() throws DeploymentException {

        final AtomicInteger msgNumber = new AtomicInteger(0);
        Server server = startServer(ProgrammaticEndpointApplicationConfiguration.class, ServerDeployApplicationConfig.class);

        final ClientManager client = ClientManager.createClient();
        try {
            for (int i = 0; i < iterations; i++) {
                try {
                    final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

                    final String message = SENT_MESSAGE + msgNumber.incrementAndGet();
                    // replace ClientManager with MockClientEndpoint to confirm the test passes if the backend
                    // does not have issues

                    client.connectToServer(new Endpoint() {
                        @Override
                        public void onOpen(Session session, EndpointConfig config) {
                            try {
                                session.addMessageHandler(new MessageHandler.Whole<String>() {
                                    @Override
                                    public void onMessage(String message) {

                                    }
                                });
                                session.getBasicRemote().sendText(message);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }, cec, getURI(PATH));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            testViaServiceEndpoint(client, ServiceEndpoint.class, POSITIVE, "Programmatic");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testAnnotatedEndpoint() throws DeploymentException {

        final AtomicInteger msgNumber = new AtomicInteger(0);
        Server server = startServer(Annotated.class, ServiceEndpoint.class);

        final ClientManager client = ClientManager.createClient();
        try {
            for (int i = 0; i < iterations; i++) {
                try {
                    final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

                    final String message = SENT_MESSAGE + msgNumber.incrementAndGet();
                    // replace ClientManager with MockClientEndpoint to confirm the test passes if the backend
                    // does not have issues

                    client.connectToServer(new Endpoint() {

                        @Override
                        public void onOpen(Session session, EndpointConfig config) {
                            try {
                                session.addMessageHandler(new MessageHandler.Whole<String>() {
                                    @Override
                                    public void onMessage(String message) {

                                    }
                                });
                                session.getBasicRemote().sendText(message);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }, cec, getURI("/EndpointLifecycleTest2"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            testViaServiceEndpoint(client, ServiceEndpoint.class, POSITIVE, "Annotated");

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    public static class ProgrammaticEndpointApplicationConfiguration extends TyrusServerConfiguration {

        public ProgrammaticEndpointApplicationConfiguration() {
            super(Collections.<Class<?>>emptySet(),
                    new HashSet<ServerEndpointConfig>() {{
                        add(ServerEndpointConfig.Builder.create(Programmatic.class, PATH).build());
                    }});
        }
    }

    @ServerEndpoint(value = "/EndpointLifecycleTest2")
    public static class Annotated {

        private static final Set<String> instancesIds = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

        @OnOpen
        public void onOpen(Session s) {
            instancesIds.add(this.toString());
        }

        @OnMessage
        public void onMessage(String message, Session session) {

        }

        public static Set<String> getInstancesIds() {
            return instancesIds;
        }
    }

    public static class Programmatic extends Endpoint {

        private static final Set<String> instancesIds = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            instancesIds.add(this.toString());
        }

        @Override
        public void onClose(Session session, CloseReason closeReason) {

        }

        public static Set<String> getInstancesIds() {
            return instancesIds;
        }
    }
}
