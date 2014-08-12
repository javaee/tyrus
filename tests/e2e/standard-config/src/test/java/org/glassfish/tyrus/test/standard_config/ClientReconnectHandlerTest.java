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

package org.glassfish.tyrus.test.standard_config;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class ClientReconnectHandlerTest extends TestContainer {

    @ServerEndpoint("/clientReconnectHandlerTest/disconnectingEndpoint")
    public static class DisconnectingEndpoint {
        @OnMessage
        public void onMessage(Session session, String message) throws IOException {
            session.close();
        }
    }

    @Test
    public void testReconnectDisconnect() throws DeploymentException {
        final Server server = startServer(DisconnectingEndpoint.class);
        try {
            final CountDownLatch messageLatch = new CountDownLatch(1);

            ClientManager client = createClient();

            ClientManager.ReconnectHandler reconnectHandler = new ClientManager.ReconnectHandler() {

                private final AtomicInteger counter = new AtomicInteger(0);

                @Override
                public boolean onDisconnect(CloseReason closeReason) {
                    final int i = counter.incrementAndGet();
                    if (i <= 3) {
                        System.out.println("### Reconnecting... (reconnect count: " + i + ")");
                        return true;
                    } else {
                        messageLatch.countDown();
                        return false;
                    }
                }

                @Override
                public long getDelay() {
                    return 0;
                }
            };

            client.getProperties().put(ClientProperties.RECONNECT_HANDLER, reconnectHandler);

            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    try {
                        session.getBasicRemote().sendText("Do or do not, there is no try.");
                    } catch (IOException e) {
                        // do nothing.
                    }
                }
            }, ClientEndpointConfig.Builder.create().build(), getURI(DisconnectingEndpoint.class));

            assertTrue(messageLatch.await(3, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testReconnectConnectFailure() throws DeploymentException {
        final Server server = startServer(DisconnectingEndpoint.class);
        try {
            final CountDownLatch messageLatch = new CountDownLatch(1);

            ClientManager client = createClient();

            ClientManager.ReconnectHandler reconnectHandler = new ClientManager.ReconnectHandler() {

                private final AtomicInteger counter = new AtomicInteger(0);

                @Override
                public boolean onConnectFailure(Exception exception) {
                    final int i = counter.incrementAndGet();
                    if (i <= 3) {
                        System.out.println("### Reconnecting... (reconnect count: " + i + ") " + exception.getMessage());
                        return true;
                    } else {
                        messageLatch.countDown();
                        return false;
                    }
                }

                @Override
                public long getDelay() {
                    return 0;
                }
            };

            client.getProperties().put(ClientProperties.RECONNECT_HANDLER, reconnectHandler);

            try {
                client.connectToServer(new Endpoint() {
                    @Override
                    public void onOpen(Session session, EndpointConfig config) {
                        try {
                            session.getBasicRemote().sendText("Do or do not, there is no try.");
                        } catch (IOException e) {
                            // do nothing.
                        }
                    }
                }, ClientEndpointConfig.Builder.create().build(), URI.create("ws://invalid.url"));
            } catch (Exception e) {
                //ignore.
            }

            assertTrue(messageLatch.await(3, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }
}
