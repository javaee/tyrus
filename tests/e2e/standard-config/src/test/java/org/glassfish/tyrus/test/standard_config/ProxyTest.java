/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.test.standard_config;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpoint;
import javax.websocket.DeploymentException;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.GrizzlyModProxy;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;

import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class ProxyTest extends TestContainer {

    private static final String PROXY_IP = "localhost";
    private static final int PROXY_PORT = 8090;
    private static final String PROXY_URL = "http://" + PROXY_IP + ":" + PROXY_PORT;

    /**
     * A basic positive test.
     * <p/>
     * A client manages to connect from behind a proxy and send and receive a message.
     */
    @Test
    public void testBasic() throws DeploymentException, IOException, InterruptedException {
        Server server = startServer(AnnotatedServerEndpoint.class);

        GrizzlyModProxy proxy = new GrizzlyModProxy(PROXY_IP, PROXY_PORT);
        proxy.start();

        try {
            ClientManager client = createClient();
            Map<String, Object> properties = client.getProperties();
            properties.put(ClientProperties.PROXY_URI, PROXY_URL);

            CountDownLatch latch = new CountDownLatch(1);
            client.connectToServer(new AnnotatedClientEndpoint(latch), getURI(AnnotatedServerEndpoint.class));

            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } finally {
            proxy.stop();
            stopServer(server);
        }
    }

    /**
     * Test a situation when the client receives a response to CONNECT with a status code other than 200.
     * This can happen for instance if the server is down or does not exist.
     */
    @Test
    public void testNonExistentServer() throws DeploymentException, InterruptedException, IOException {
        GrizzlyModProxy proxy = new GrizzlyModProxy(PROXY_IP, PROXY_PORT);
        proxy.start();

        try {
            ClientManager client = createClient();
            Map<String, Object> properties = client.getProperties();
            properties.put(ClientProperties.PROXY_URI, PROXY_URL);

            try {
                client.connectToServer(new AnnotatedClientEndpoint(new CountDownLatch(1)),
                        URI.create("ws://nonExistentServer.com"));
                fail();
            } catch (DeploymentException e) {
                // At least check it is an IOException and that there is a [P|p]roxy problem
                assertTrue(e.getCause() instanceof IOException);
                assertTrue(e.getCause().getMessage().contains("roxy"));
            }

        } finally {
            proxy.stop();
        }
    }

    /**
     * Tests a situation when a client sends CONNECT to a proxy, but does not receive any reply.
     */
    @Ignore // JDK connector is stuck forever
    @Test
    public void testConnectStuck() throws IOException {
        GrizzlyModProxy proxy = new GrizzlyModProxy(PROXY_IP, PROXY_PORT) {
            @Override
            protected NextAction handleConnect(final FilterChainContext ctx, final HttpContent content) {

                // simulate a situation when we receive CONNECT, but don't reply for some reason
                return ctx.getStopAction();
            }
        };

        proxy.start();

        try {
            ClientManager client = createClient();
            Map<String, Object> properties = client.getProperties();
            properties.put(ClientProperties.PROXY_URI, PROXY_URL);
            properties.put(ClientProperties.HANDSHAKE_TIMEOUT, 200);

            try {
                client.connectToServer(new AnnotatedClientEndpoint(new CountDownLatch(1)), getURI(AnnotatedServerEndpoint.class));
                fail();
            } catch (DeploymentException e) {
                assertTrue(e.getMessage().contains("Handshake response not received"));
            }

        } finally {
            proxy.stop();
        }
    }

    @ServerEndpoint("/destinationEndpoint")
    public static class AnnotatedServerEndpoint {

        @OnMessage
        public String onMessage(String message) {
            return message;
        }

    }

    @ClientEndpoint
    public static class AnnotatedClientEndpoint {

        private final CountDownLatch latch;

        public AnnotatedClientEndpoint(final CountDownLatch latch) {
            this.latch = latch;
        }

        @OnOpen
        public void onOpen(Session session) throws IOException {
            session.getBasicRemote().sendText("Hello");
        }

        @OnMessage
        public void onMessage(String message) {
            latch.countDown();
        }
    }
}
