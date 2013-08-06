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

package org.glassfish.tyrus.testing;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpoint;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;

import org.junit.Assert;

/**
 * Utilities for creating automated tests easily.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class TestUtilities {

    private String contextPath = "/servlet-test";
    private String defaultHost = "localhost";
    private int defaultPort = 8025;

    protected static final String POSITIVE = "POSITIVE";
    protected static final String NEGATIVE = "NEGATIVE";

    /**
     * Start embedded server unless "tyrus.test.host" system property is specified.
     *
     * @return new {@link Server} instance or {@code null} if "tyrus.test.host" system property is set.
     */
    protected Server startServer(Class<?>... endpointClasses) throws DeploymentException {
        final String host = System.getProperty("tyrus.test.host");
        if (host == null) {
            final Server server = new Server(defaultHost, defaultPort, contextPath, endpointClasses);
            server.start();
            return server;
        } else {
            return null;
        }
    }

    protected void stopServer(Server server) {
        if (server != null) {
            server.stop();
        }
    }

    private String getHost() {
        final String host = System.getProperty("tyrus.test.host");
        if (host != null) {
            return host;
        }
        return defaultHost;
    }

    private int getPort() {
        final String port = System.getProperty("tyrus.test.port");
        if (port != null) {
            try {
                return Integer.parseInt(port);
            } catch (NumberFormatException nfe) {
                // do nothing
            }
        }
        return defaultPort;
    }

    protected URI getURI(Class<?> serverClass) {
        try {
            String endpointPath = serverClass.getAnnotation(ServerEndpoint.class).value();
            return new URI("ws", null, getHost(), getPort(), contextPath + endpointPath, null, null);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }

    protected URI getURI(String endpointPath) {
        try {
            return new URI("ws", null, getHost(), getPort(), contextPath + endpointPath, null, null);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Sets the context path.
     *
     * @param contextPath the path to be set.
     */
    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    /**
     * Sets the default host.
     *
     * @param defaultHost the host to be set.
     */
    public void setDefaultHost(String defaultHost) {
        this.defaultHost = defaultHost;
    }

    /**
     * Sets the default port.
     *
     * @param defaultPort default port number.
     */
    public void setDefaultPort(int defaultPort) {
        this.defaultPort = defaultPort;
    }

    public void testEndpointString(Class<?> serverEndpoint, final TextSimplificator simplificator, Class<?>... endpointClasses) throws DeploymentException, InterruptedException, IOException {
        final Server server = startServer(endpointClasses);

        final CountDownLatch messageLatch = new CountDownLatch(1);

        try {
            final ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(final Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                simplificator.onMessage(message, session);
                                messageLatch.countDown();
                            }
                        });

                        session.getBasicRemote().sendText(simplificator.getMessageToSend());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, ClientEndpointConfig.Builder.create().build(), getURI(serverEndpoint));

            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertEquals(0, messageLatch.getCount());
            simplificator.assertion();
        } finally {
            stopServer(server);
        }
    }

    public void testEndpointBinary(Class<?> serverEndpoint, final BinarySimplificator simplificator, Class<?>... endpointClasses) throws DeploymentException, InterruptedException, IOException {
        final Server server = startServer(endpointClasses);

        final CountDownLatch messageLatch = new CountDownLatch(1);

        try {
            final ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(final Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
                            @Override
                            public void onMessage(ByteBuffer message) {
                                simplificator.onMessage(message, session);
                                messageLatch.countDown();
                            }
                        });

                        session.getBasicRemote().sendBinary(simplificator.getMessageToSend());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, ClientEndpointConfig.Builder.create().build(), getURI(serverEndpoint));

            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertEquals(0, messageLatch.getCount());
            simplificator.assertion();
        } finally {
            stopServer(server);
        }
    }

//    public void testEndpoint(Class<?> serverEndpoint, Class<?> clientEndpoint, final Simplificator simplificator, Class<?>... endpointClasses) throws DeploymentException, InterruptedException, IOException {
//        final Server server = startServer(endpointClasses);
//
//        final CountDownLatch messageLatch = new CountDownLatch(1);
//
//        try {
//            final ClientManager client = ClientManager.createClient();
//            client.connectToServer(clientEndpoint, ClientEndpointConfig.Builder.create().build(), getURI(serverEndpoint.getAnnotation(ServerEndpoint.class).value()));
//            messageLatch.await(1, TimeUnit.SECONDS);
//            Assert.assertEquals(0, messageLatch.getCount());
//            simplificator.assertion();
//        } finally {
//            stopServer(server);
//        }
//    }


    public static abstract class Simplificator {

        public abstract void assertion();
    }

    public static abstract class TextSimplificator extends Simplificator{
        private final String messageToSend;

        public TextSimplificator(String messageToSend) {
            this.messageToSend = messageToSend;
        }

        String getMessageToSend() {
            return messageToSend;
        }

        public abstract void onMessage(String message, Session session);
    }


    public static abstract class BinarySimplificator extends Simplificator{
        private final ByteBuffer messageToSend;

        public BinarySimplificator(ByteBuffer messageToSend) {
            this.messageToSend = messageToSend;
        }

        ByteBuffer getMessageToSend() {
            return messageToSend;
        }

        public abstract void onMessage(ByteBuffer message, Session session);
    }

    @ClientEndpoint
    public static class MyServiceClientEndpoint {

        public static CountDownLatch latch;
        public static String receivedMessage;

        @OnMessage
        public void onMessage(String message) {
            receivedMessage = message;
            latch.countDown();
        }
    }
}