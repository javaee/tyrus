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
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.server.TyrusServerConfiguration;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class InputStreamTest extends TestContainer {

    public InputStreamTest() {
        this.setContextPath("/servlet-test-appconfig");
    }

    public static class ServerDeployApplicationConfig extends TyrusServerConfiguration {
        public ServerDeployApplicationConfig() {
            super(new HashSet<Class<?>>() {{
                add(InputStreamEndpoint.class);
            }}, Collections.<ServerEndpointConfig> emptySet());
        }
    }

    @ServerEndpoint(value = "/inputStream")
    public static class InputStreamEndpoint {

        @OnMessage
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

    public static class InputStreamApplicationConfiguration extends TyrusServerConfiguration {

        public InputStreamApplicationConfiguration() {
            super(Collections.<Class<?>>emptySet(), new HashSet<ServerEndpointConfig>() {{
                add(ServerEndpointConfig.Builder.create(InputStreamProgrammaticEndpoint.class, "/inputStreamProgrammatic").build());
            }});
        }
    }

    @Test
    public void testInputStreamAnnotated() throws DeploymentException {
        _testInputStream(InputStreamEndpoint.class, InputStreamEndpoint.class.getAnnotation(ServerEndpoint.class).value());
    }

    @Test
    public void testInputStreamProgrammatic() throws DeploymentException {
        _testInputStream(InputStreamApplicationConfiguration.class, "/inputStreamProgrammatic");
    }

    public void _testInputStream(Class<?> endpoint, String path) throws DeploymentException {
        final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();
        Server server = startServer(endpoint);
        final CountDownLatch messageLatch;

        try {
            messageLatch = new CountDownLatch(1);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig configuration) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                if (message.equals("Do or do not, there is no try.")) {
                                    messageLatch.countDown();
                                }
                            }
                        });
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap("Do or do not, there is no try.".getBytes()));
                    } catch (IOException e) {
                        fail();
                    }
                }
            }, cec, getURI(path));

            messageLatch.await(5, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    public static class InputStreamProgrammaticEndpoint extends Endpoint {
        @Override
        public void onOpen(final Session session, EndpointConfig config) {
            session.addMessageHandler(new MessageHandler.Whole<InputStream>() {
                @Override
                public void onMessage(InputStream is) {
                    byte[] buffer = new byte[64];
                    try {
                        int i = is.read(buffer);
                        session.getBasicRemote().sendText(new String(buffer, 0, i));
                    } catch (IOException e) {
                        //
                    }
                }
            });
        }
    }
}
