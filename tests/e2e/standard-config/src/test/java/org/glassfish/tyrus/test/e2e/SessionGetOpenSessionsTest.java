/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.MessageHandler;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.testing.TestUtilities;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Tests the ServerContainer.getOpenSessions method.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class SessionGetOpenSessionsTest extends TestUtilities{

    @ServerEndpoint(value = "/customremote/hello1")
    public static class SessionTestEndpoint {

        @OnOpen
        public void onOpen(Session s) {
            System.out.println("s ### opened! " + s);
        }

        @OnMessage
        public String onMessage(String message, Session session){
            if(message.equals("count")){
                return String.valueOf(session.getOpenSessions().size());
            }

            return null;
        }

        @OnError
        public void onError(Throwable t) {
            t.printStackTrace();
        }
    }

    @Test
    public void testGetOpenSessions() throws DeploymentException {
        final CountDownLatch messageLatch = new CountDownLatch(1);

        Server server = startServer(SessionTestEndpoint.class);

        final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

        try {
            Thread.sleep(1000);
            final ClientManager client = ClientManager.createClient();
            for (int i = 0; i < 2; i++) {
                client.connectToServer(new TestEndpointAdapter() {
                    @Override
                    public void onOpen(Session session) {
                        System.out.println("c ### opened! " + session);
                        try {
                            session.getBasicRemote().sendText("a");
                        } catch (IOException e) {
                            // nothing
                        }
                    }

                    @Override
                    public void onMessage(String s) {
                    }
                }, cec, getURI(SessionTestEndpoint.class));
            }

            for (int i = 0; i < 2; i++) {
                client.connectToServer(new TestEndpointAdapter() {
                    @Override
                    public void onOpen(Session session) {
                        System.out.println("c ### opened! " + session);
                        try {
                            session.getBasicRemote().sendText("a");
                        } catch (IOException e) {
                            // nothing
                        }
                    }

                    @Override
                    public void onMessage(String s) {
                    }
                }, cec, getURI(SessionTestEndpoint.class));
            }

            client.connectToServer(new TestEndpointAdapter() {
                @Override
                public void onOpen(Session session) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            assertEquals("5", message);
                            messageLatch.countDown();
                        }
                    });

                    System.out.println("c ### opened! " + session);
                    try {
                        session.getBasicRemote().sendText("count");
                    } catch (IOException e) {
                        // nothing
                    }
                }

                @Override
                public void onMessage(String s) {
                }
            }, cec, getURI(SessionTestEndpoint.class));

            messageLatch.await(1, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }
}
