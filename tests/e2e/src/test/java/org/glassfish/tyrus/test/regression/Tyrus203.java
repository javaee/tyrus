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
package org.glassfish.tyrus.test.regression;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * https://java.net/jira/browse/TYRUS-203
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class Tyrus203 {

    @ServerEndpoint("/echo/{color}")
    public static class EchoServerEndpoint {
        @OnMessage
        public String echo(String message, @PathParam("color") String color) {
            return color + ":" + message;
        }
    }

    @Test
    public void test() {
        Server server = new Server(EchoServerEndpoint.class);

        try {
            server.start();
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            final CountDownLatch client1messageLatch = new CountDownLatch(1);
            final CountDownLatch client2messageLatch = new CountDownLatch(1);
            ClientManager client = ClientManager.createClient();
            Session session1 = client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(final Session session, EndpointConfig configuration) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            System.out.println(session.getId() + " Client1 @OnMessage -> " + message);
                            if(message.equals("first:test")) {
                                client1messageLatch.countDown();
                            }
                        }
                    });
                    // do nothing
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/echo/first"));

            Session session2 = client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(final Session session, EndpointConfig configuration) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            System.out.println(session.getId() + " Client2 @OnMessage -> " + message);
                            if(message.equals("second:test")) {
                                client2messageLatch.countDown();
                            }

                        }
                    });
                    // do nothing
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/echo/second"));

            session1.getBasicRemote().sendText("test");
            session2.getBasicRemote().sendText("test");

            client1messageLatch.await(1, TimeUnit.SECONDS);
            client2messageLatch.await(1, TimeUnit.SECONDS);

            assertEquals(0, client1messageLatch.getCount());
            assertEquals(0, client2messageLatch.getCount());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }
}
