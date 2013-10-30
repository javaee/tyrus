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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class ServerSessionConnectToServerTest extends TestContainer {

    private CountDownLatch messageLatch;

    @Test
    public void testConnectToServerWithinServerEndpoint() throws DeploymentException, IOException, InterruptedException {
        final Server server = startServer(ConnectToServerEndpoint.class, ConnectToServerEchoEndpoint.class);
        try {
            messageLatch = new CountDownLatch(1);

            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {


                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    try {
                        final String anotherEndpointURI = getURI(ConnectToServerEchoEndpoint.class).toString();

                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                System.out.println("### Client received: " + message);
                                assertEquals(anotherEndpointURI, message);

                                messageLatch.countDown();
                            }
                        });
                        session.getBasicRemote().sendText(anotherEndpointURI);
                        System.out.println("### Message from client sent.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, cec, getURI(ConnectToServerEndpoint.class));

            assertTrue(messageLatch.await(5, TimeUnit.SECONDS));
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint(value = "/connectToServerEndpoint")
    public static class ConnectToServerEndpoint {

        CountDownLatch messageLatch = new CountDownLatch(1);
        String receivedMessage;

        @OnMessage
        public String onMessage(String message, Session session) throws IOException, DeploymentException, InterruptedException {

            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();
            final WebSocketContainer serverWebSocketContainer = session.getContainer();

            serverWebSocketContainer.connectToServer(new Endpoint() {

                @Override
                public void onOpen(final Session session, EndpointConfig config) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                System.out.println("### Server endpoint received: " + message);

                                if (message.equals("Yo Dawg, I heard you like clients, so we put client into server so you can connectToServer while you connectToServer.")
                                        && (serverWebSocketContainer.equals(session.getContainer()))) {
                                    messageLatch.countDown();
                                }
                            }
                        });
                        session.getBasicRemote().sendText("Yo Dawg, I heard you like clients, so we put client into server so you can connectToServer while you connectToServer.");
                        System.out.println("### Message from client running inside server endpoint sent.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            }, cec, URI.create(message));

            if (messageLatch.await(3, TimeUnit.SECONDS)) {
                return message;
            } else {
                return null;
            }
        }
    }

    @ServerEndpoint(value = "/connectToServerEchoEndpoint")
    public static class ConnectToServerEchoEndpoint {

        @OnMessage
        public String onMessage(String message) {
            return message;
        }
    }
}
