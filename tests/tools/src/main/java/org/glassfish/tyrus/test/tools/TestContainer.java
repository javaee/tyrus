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

package org.glassfish.tyrus.test.tools;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpoint;
import javax.websocket.DeploymentException;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;

import static junit.framework.Assert.assertEquals;

/**
 * Utilities for creating automated tests easily.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class TestContainer {

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

    /**
     * Stop the server.
     *
     * @param server to be stopped.
     */
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

    /**
     * Get the {@link URI} for the {@link ServerEndpoint} annotated class.
     *
     * @param serverClass the annotated class the {@link URI} is computed for.
     * @return {@link URI} which is used to connect to the given endpoint.
     */
    protected URI getURI(Class<?> serverClass) {
        try {
            String endpointPath = serverClass.getAnnotation(ServerEndpoint.class).value();
            return new URI("ws", null, getHost(), getPort(), contextPath + endpointPath, null, null);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Get the {@link URI} for the given {@link String} path.
     *
     * @param endpointPath the path the {@link URI} is computed for.
     * @return {@link URI} which is used to connect to the given path.
     */
    protected URI getURI(String endpointPath) {
        try {
            return new URI("ws", null, getHost(), getPort(), contextPath + endpointPath, null, null);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Send message to the service endpoint and compare the received result with the specified one.
     *
     * @param client          client used to send the message.
     * @param serviceEndpoint endpoint to which the message will be sent.
     * @param expectedResult  expected reply.
     * @param message         message to be sent.
     * @throws DeploymentException
     * @throws IOException
     * @throws InterruptedException
     */
    protected void testViaServiceEndpoint(ClientManager client, Class<?> serviceEndpoint, String expectedResult, String message) throws DeploymentException, IOException, InterruptedException {
        final Session serviceSession = client.connectToServer(MyServiceClientEndpoint.class, getURI(serviceEndpoint));
        MyServiceClientEndpoint.latch = new CountDownLatch(1);
        MyServiceClientEndpoint.receivedMessage = null;
        serviceSession.getBasicRemote().sendText(message);
        MyServiceClientEndpoint.latch.await(1, TimeUnit.SECONDS);
        assertEquals(0, MyServiceClientEndpoint.latch.getCount());
        assertEquals(expectedResult, MyServiceClientEndpoint.receivedMessage);
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