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
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.ContainerProvider;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.server.Server;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * See https://java.net/jira/browse/TYRUS-205.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class SubProtocolOrderingTest {

    @ServerEndpoint(value = "/subProtocolTest", subprotocols = {"MBLWS.huawei.com", "wamp", "v11.stomp", "v10.stomp", "soap"})
    public static class Endpoint {
        @OnOpen
        public void onOpen(Session s) throws IOException {
            s.getBasicRemote().sendText(s.getNegotiatedSubprotocol());
        }
    }

    @Test
    public void test() {
        Server server = new Server(Endpoint.class);

        try {
            server.start();
            final CountDownLatch messageLatch = new CountDownLatch(1);

            final ClientEndpointConfig clientEndpointConfig = ClientEndpointConfig.Builder.create().
                    preferredSubprotocols(Arrays.asList("MBWS.huawei.com", "soap", "v10.stomp")).build();
            ContainerProvider.getWebSocketContainer().connectToServer(new javax.websocket.Endpoint() {
                @Override
                public void onOpen(final Session session, EndpointConfig config) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {

                            if (message.equals("soap") && session.getNegotiatedSubprotocol().equals("soap")) {
                                messageLatch.countDown();
                            }
                        }
                    });
                }
            }, clientEndpointConfig, new URI("ws://localhost:8025/websockets/tests/subProtocolTest"));

            messageLatch.await(1, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }
}
