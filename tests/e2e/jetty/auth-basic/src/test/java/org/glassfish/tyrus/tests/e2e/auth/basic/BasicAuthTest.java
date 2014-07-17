/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.tests.e2e.auth.basic;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.client.auth.AuthConfig;
import org.glassfish.tyrus.client.auth.AuthenticationException;
import org.glassfish.tyrus.client.auth.Credentials;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Authorization tests.
 *
 * @author Ondrej Kosatka (ondrej.kosatka at oracle.com)
 */
public class BasicAuthTest extends TestContainer {

    public static final String SCHEME = "ws";
    private static final String CONTEXT_PATH = "/e2e-basic-auth-test";

    public BasicAuthTest() {
        setDefaultPort(8025);
        setContextPath(CONTEXT_PATH);
    }


    @Test
    public void testAuthorizationSuccessAuthConfig() throws DeploymentException, InterruptedException, IOException, AuthenticationException {
        final AuthConfig authConfig = AuthConfig.Builder.create().build();

        final CountDownLatch messageLatch = new CountDownLatch(1);

        final ClientManager client = createClient();

        final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

        client.getProperties().put(ClientProperties.CREDENTIALS, new Credentials("ws_user", "password"));
        client.getProperties().put(ClientProperties.AUTH_CONFIG, authConfig);

        client.connectToServer(new Endpoint() {
            @Override
            public void onOpen(Session session, EndpointConfig EndpointConfig) {
                try {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            System.out.println("received message: " + message);
                            assertEquals(message, "Do or do not, there is no try.");
                            messageLatch.countDown();
                        }
                    });

                    session.getBasicRemote().sendText("Do or do not, there is no try.");
                } catch (IOException e) {
                    // do nothing
                }
            }
        }, cec, getURI(BasicAuthEchoEndpoint.class.getAnnotation(ServerEndpoint.class).value(), SCHEME));

        messageLatch.await(1, TimeUnit.SECONDS);
        assertEquals(0, messageLatch.getCount());
    }


    @Test
    public void testAuthorizationSuccessCredentials() throws DeploymentException, InterruptedException, IOException, AuthenticationException {

        final CountDownLatch messageLatch = new CountDownLatch(1);

        final ClientManager client = createClient();

        final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

        client.getProperties().put(ClientProperties.CREDENTIALS, new Credentials("ws_user", "password"));

        client.connectToServer(new Endpoint() {
            @Override
            public void onOpen(Session session, EndpointConfig EndpointConfig) {
                try {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            System.out.println("received message: " + message);
                            assertEquals(message, "Do or do not, there is no try.");
                            messageLatch.countDown();
                        }
                    });

                    session.getBasicRemote().sendText("Do or do not, there is no try.");
                } catch (IOException e) {
                    // do nothing
                }
            }
        }, cec, getURI(BasicAuthEchoEndpoint.class.getAnnotation(ServerEndpoint.class).value(), SCHEME));

        messageLatch.await(1, TimeUnit.SECONDS);
        assertEquals(0, messageLatch.getCount());
    }

    @Test
    public void testAuthorizationFailed() throws InterruptedException, IOException {

        final ClientManager client = createClient();

        final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

        client.getProperties().put(ClientProperties.CREDENTIALS, new Credentials("non-existing-user", "password"));

        try {
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    assertTrue("Handshake should fail", true);
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                System.out.println("received message: " + message);
                                assertTrue("No massage can be received", true);
                            }
                        });

                        session.getBasicRemote().sendText("Do or do not, there is no try.");
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }, cec, getURI(BasicAuthEchoEndpoint.class.getAnnotation(ServerEndpoint.class).value(), SCHEME));
        } catch (DeploymentException e) {
            assertTrue("Client should throw an AuthorizationException", e.getCause() instanceof AuthenticationException);
        }
    }

}