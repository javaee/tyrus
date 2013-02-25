/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2013 Oracle and/or its affiliates. All rights reserved.
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

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfiguration;
import javax.websocket.ClientEndpointConfigurationBuilder;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.MessageHandler;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;


/**
 * Tests sending and receiving ping and pongs
 *
 * @author Danny Coward (danny.coward at oracle.com)
 */
public class PingPongTest {
    @Ignore // TODO works on client test run, not on full build
    @Test
    public void testClient() {
        final ClientEndpointConfiguration cec = ClientEndpointConfigurationBuilder.create().build();
        Server server = new Server(PingPongEndpoint.class);

        try {
            server.start();
            CountDownLatch messageLatch = new CountDownLatch(1);

            PingPongClient htc = new PingPongClient(messageLatch);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(htc, cec, new URI("ws://localhost:8025/websockets/tests/pingpong"));

            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertTrue("The client got the pong back with the right message, and so did the server", PingPongEndpoint.gotCorrectMessage);
            Assert.assertTrue("The client got the pong back with the right message, and so did the server", htc.gotCorrectMessageBack);

            Assert.assertTrue("The client got the pong back with the right message, and so did the server", PingPongEndpoint.gotCorrectMessage && htc.gotCorrectMessageBack);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    /**
     * @author Danny Coward (danny.coward at oracle.com)
     */

    @ServerEndpoint(value = "/pingpong")
    public static class PingPongEndpoint {
        static boolean gotCorrectMessage = false;
        private static String SERVER_MESSAGE = "server ping data!";

        @OnOpen
        public void init(Session session) {
            try {
                session.addMessageHandler(new MessageHandler.Basic<ByteBuffer>() {
                    public void onMessage(ByteBuffer bb) {
                        System.out.println("PINGPONGSERVER received pong: " + new String(bb.array()));
                        gotCorrectMessage = SERVER_MESSAGE.equals(new String(bb.array()));

                    }
                });
                session.getBasicRemote().sendPing(ByteBuffer.wrap(SERVER_MESSAGE.getBytes()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * @author Danny Coward (danny.coward at oracle.com)
     */
    public static class PingPongClient extends Endpoint {
        boolean gotCorrectMessageBack = false;
        private final CountDownLatch messageLatch;
        private static String CLIENT_MESSAGE = "client ping data!";

        public PingPongClient(CountDownLatch messageLatch) {
            this.messageLatch = messageLatch;
        }

        //    @Override
        //    public EndpointConfiguration getEndpointConfiguration() {
        //        return null;
        //    }

        public void onOpen(Session session, EndpointConfiguration endpointConfiguration) {
            try {
                session.addMessageHandler(new MessageHandler.Basic<ByteBuffer>() {
                    public void onMessage(ByteBuffer bb) {
                        gotCorrectMessageBack = CLIENT_MESSAGE.equals(new String(bb.array()));
                        messageLatch.countDown();
                    }
                });
                session.getBasicRemote().sendPing(ByteBuffer.wrap(CLIENT_MESSAGE.getBytes()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


    }
}
