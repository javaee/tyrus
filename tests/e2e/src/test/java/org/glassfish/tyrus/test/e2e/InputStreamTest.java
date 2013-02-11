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
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfiguration;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.Extension;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.WebSocketMessage;
import javax.websocket.server.DefaultServerConfiguration;
import javax.websocket.server.WebSocketEndpoint;

import org.glassfish.tyrus.TyrusClientEndpointConfiguration;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class InputStreamTest {

    @WebSocketEndpoint(value = "/inputStream", configuration = DefaultServerConfiguration.class)
    public static class InputStreamEndpoint {

        @WebSocketMessage
        public String readInputStream(InputStream is) {
            byte[] buffer = new byte[64];
            try {
                int i = is.read(buffer);
                return new String(buffer, 0, i);
            } catch (IOException e) {
                return null;
            }
        }
    }

    public static class InputStreamEndpointConfiguration extends DefaultServerConfiguration {

        public InputStreamEndpointConfiguration() {
            super(InputStreamEndpoint.class, "/inputStream");
        }

        @Override
        public String getNegotiatedSubprotocol(List<String> requestedSubprotocols) {
            return null;
        }

        @Override
        public List<Extension> getNegotiatedExtensions(List<Extension> requestedExtensions) {
            return requestedExtensions;
        }

        @Override
        public boolean checkOrigin(String originHeaderValue) {
            return true;
        }

        public static class InputStreamEndpoint extends Endpoint {
            @Override
            public void onOpen(final Session session, EndpointConfiguration config) {
                session.addMessageHandler(new MessageHandler.Basic<InputStream>() {
                    @Override
                    public void onMessage(InputStream is) {
                        byte[] buffer = new byte[64];
                        try {
                            int i = is.read(buffer);
                            session.getRemote().sendString(new String(buffer, 0, i));
                        } catch (IOException e) {
                            //
                        }
                    }
                });
            }
        }
    }

    @Test
    public void testInputStreamAnnotated() {
        _testInputStream(InputStreamEndpoint.class);
    }

    @Test
    public void testInputStreamProgrammatic() {
        _testInputStream(InputStreamEndpointConfiguration.class);
    }

    public void _testInputStream(Class<?> endpoint) {
        final ClientEndpointConfiguration cec = new TyrusClientEndpointConfiguration.Builder().build();
        Server server = new Server(endpoint);
        final CountDownLatch messageLatch;

        try {
            server.start();
            messageLatch = new CountDownLatch(1);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfiguration configuration) {
                    try {
                        session.addMessageHandler(new MessageHandler.Basic<String>() {
                            @Override
                            public void onMessage(String message) {
                                if(message.equals("Do or do not, there is no try.")) {
                                    messageLatch.countDown();
                                }
                            }
                        });
                        session.getRemote().sendBytes(ByteBuffer.wrap("Do or do not, there is no try.".getBytes()));
                    } catch (IOException e) {
                        fail();
                    }
                }
            }, cec, new URI("ws://localhost:8025/websockets/tests/inputStream"));

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
