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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpointConfig;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.core.ExtendedExtension;
import org.glassfish.tyrus.core.Frame;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class ExtendedExtensionTest extends TestContainer {

    public ExtendedExtensionTest() {
        this.setContextPath("/e2e-test-appconfig");
    }

    @Test
    public void extendedExtensionTest() throws DeploymentException {

        Server server = startServer(ExtendedExtensionApplicationConfig.class);
        final CountDownLatch messageLatch = new CountDownLatch(1);

        try {
            ArrayList<Extension> extensions = new ArrayList<Extension>();
            extensions.add(EXTENDED_EXTENSION);

            final ClientEndpointConfig clientConfiguration = ClientEndpointConfig.Builder.create().extensions(extensions)
                    .configurator(new LoggingClientEndpointConfigurator()).build();

            ClientManager client = ClientManager.createClient();
            final Session session = client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            System.out.println("client onMessage: " + message);
                            messageLatch.countDown();
                        }
                    });

                    try {
                        session.getBasicRemote().sendText("Always pass on what you have learned.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, clientConfiguration, getURI("/extendedExtensionEndpoint"));

            assertEquals(1, session.getNegotiatedExtensions().size());
            final Extension extension = session.getNegotiatedExtensions().get(0);
            assertEquals(EXTENDED_EXTENSION, extension);

            assertTrue(messageLatch.await(1, TimeUnit.SECONDS));

            // once for client side, once for server side
            assertEquals(2, EXTENDED_EXTENSION.incomingCounter.get());
            // dtto
            assertEquals(2, EXTENDED_EXTENSION.outgoingCounter.get());

            assertNotNull(EXTENDED_EXTENSION.onExtensionNegotiation);
            assertNotNull(EXTENDED_EXTENSION.onHandshakeResponse);

            assertEquals(1, EXTENDED_EXTENSION.onHandshakeResponse.size());
            assertEquals("param1", EXTENDED_EXTENSION.onHandshakeResponse.get(0).getName());
            assertEquals("value1", EXTENDED_EXTENSION.onHandshakeResponse.get(0).getValue());

            assertEquals(EXTENDED_EXTENSION.getParameters().size(), EXTENDED_EXTENSION.onExtensionNegotiation.size());
            assertEquals(EXTENDED_EXTENSION.getParameters().get(0).getName(), EXTENDED_EXTENSION.onExtensionNegotiation.get(0).getName());
            assertEquals(EXTENDED_EXTENSION.getParameters().get(0).getValue(), EXTENDED_EXTENSION.onExtensionNegotiation.get(0).getValue());

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    private final static MyExtendedExtension EXTENDED_EXTENSION = new MyExtendedExtension();

    public static class ExtendedExtensionEndpoint extends Endpoint {

        @Override
        public void onOpen(final Session session, EndpointConfig config) {
            print("onOpen " + session);
            session.addMessageHandler(new MessageHandler.Whole<String>() {
                @Override
                public void onMessage(String message) {
                    try {
                        print("onMessage");
                        session.getBasicRemote().sendText(message);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        @Override
        public void onClose(Session session, CloseReason closeReason) {
            print("onClose " + session);
        }

        @Override
        public void onError(Session session, Throwable thr) {
            print("onError " + session);
            thr.printStackTrace();
        }

        private void print(String s) {
            System.out.println(this.getClass().getName() + " " + s);
        }
    }

    public static class ExtendedExtensionApplicationConfig implements ServerApplicationConfig {

        @Override
        public Set<ServerEndpointConfig> getEndpointConfigs(Set<Class<? extends Endpoint>> endpointClasses) {
            Set<ServerEndpointConfig> endpointConfigs = new HashSet<ServerEndpointConfig>();
            endpointConfigs.add(
                    ServerEndpointConfig.Builder.create(ExtendedExtensionEndpoint.class, "/extendedExtensionEndpoint")
                            .extensions(Arrays.<Extension>asList(EXTENDED_EXTENSION)).build()
            );
            return endpointConfigs;
        }

        @Override
        public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> scanned) {
            return Collections.<Class<?>>emptySet();
        }
    }

    public static class MyExtendedExtension implements ExtendedExtension {

        private final static String NAME = "MyExtendedExtension";
        public final AtomicInteger incomingCounter = new AtomicInteger(0);
        public final AtomicInteger outgoingCounter = new AtomicInteger(0);

        public volatile List<Parameter> onExtensionNegotiation = null;
        public volatile List<Parameter> onHandshakeResponse = null;

        @Override
        public Frame processIncoming(ExtendedExtension.ExtensionContext context, Frame frame) {
            print("processIncoming :: " + incomingCounter.incrementAndGet() + " :: " + frame);
            return frame;
        }

        @Override
        public Frame processOutgoing(ExtendedExtension.ExtensionContext context, Frame frame) {
            print("processOutgoing :: " + outgoingCounter.incrementAndGet() + " :: " + frame);
            return frame;
        }

        @Override
        public List<Extension.Parameter> onExtensionNegotiation(ExtendedExtension.ExtensionContext context, List<Extension.Parameter> requestedParameters) {
            print("onExtensionNegotiation :: " + context + " :: " + requestedParameters);
            onExtensionNegotiation = requestedParameters;

            List<Extension.Parameter> paramList = new ArrayList<Extension.Parameter>();
            paramList.add(new Parameter() {
                @Override
                public String getName() {
                    return "param1";
                }

                @Override
                public String getValue() {
                    return "value1";
                }

                @Override
                public String toString() {
                    return "[param1=value1]";
                }
            });

            print(paramList.toString());
            print("");

            return paramList;
        }

        @Override
        public void onHandshakeResponse(ExtensionContext context, List<Parameter> responseParameters) {
            onHandshakeResponse = responseParameters;
            print("onHandshakeResponse :: " + context + " :: " + responseParameters);
        }

        @Override
        public String getName() {
            print("getName");
            return NAME;
        }

        @Override
        public List<Parameter> getParameters() {
            print("getParameters");
            List<Parameter> paramList = new ArrayList<Parameter>();
            paramList.add(new Parameter() {
                @Override
                public String getName() {
                    return "basicParam1";
                }

                @Override
                public String getValue() {
                    return "basicValue1";
                }
            });

            return paramList;
        }

        @Override
        public void destroy(ExtensionContext context) {
            print("destroy :: " + context);
        }

        private void print(String s) {
            System.out.println("##### " + NAME + " " + s);
        }
    }

    public static class LoggingClientEndpointConfigurator extends ClientEndpointConfig.Configurator {
        @Override
        public void beforeRequest(Map<String, List<String>> headers) {
            System.out.println("##### beforeRequest");
            System.out.println(headers);
            System.out.println();
        }

        @Override
        public void afterResponse(HandshakeResponse hr) {
            System.out.println("##### afterResponse");
            System.out.println(hr.getHeaders());
            System.out.println();
        }
    }
}
