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
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.ContainerProvider;
import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import org.glassfish.tyrus.core.CoderAdapter;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.server.TyrusServerConfiguration;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * See TYRUS-261.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class TextAndBinaryProgrammaticDecoder extends TestContainer {

    public TextAndBinaryProgrammaticDecoder() {
        this.setContextPath("/e2e-test-appconfig");
    }

    @Test
    public void test2Decoders1Handler() throws DeploymentException, IOException, InterruptedException {
        final Server server = startServer(ServerDeployApplicationConfig.class);

        try {
            final CountDownLatch textLatch = new CountDownLatch(1);
            final CountDownLatch binaryLatch = new CountDownLatch(1);

            final Session session = ContainerProvider.getWebSocketContainer().connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            if (message.equals("text")) {
                                textLatch.countDown();
                            } else if (message.equals("binary")) {
                                binaryLatch.countDown();
                            }
                        }
                    });
                }
            }, ClientEndpointConfig.Builder.create().build(), getURI("/textAndBinaryDecoderEndpoint"));

            session.getBasicRemote().sendText("test");
            session.getBasicRemote().sendBinary(ByteBuffer.wrap("test".getBytes("UTF-8")));

            assertTrue(textLatch.await(3, TimeUnit.SECONDS));
            assertTrue(binaryLatch.await(3, TimeUnit.SECONDS));
        } finally {
            server.stop();
        }
    }

    @Test
    public void testAnnotatedEndpointRegisteredProgramatically() throws DeploymentException, IOException, InterruptedException {
        final Server server = startServer(ServerDeployApplicationConfig.class);

        try {
            final CountDownLatch textLatch = new CountDownLatch(1);

            final Session session = ContainerProvider.getWebSocketContainer().connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            if (message.equals("text")) {
                                textLatch.countDown();
                            }
                        }
                    });
                }
            }, ClientEndpointConfig.Builder.create().build(), getURI("/annotatedEndpointRegisteredProgramatically"));

            session.getBasicRemote().sendText("text");

            assertTrue(textLatch.await(3, TimeUnit.SECONDS));
        } finally {
            server.stop();
        }
    }

    public static class ServerDeployApplicationConfig extends TyrusServerConfiguration {
        public ServerDeployApplicationConfig() {
            super(Collections.<Class<?>>emptySet(), new HashSet<ServerEndpointConfig>(
            ) {{
                add(ServerEndpointConfig.Builder.create(TextAndBinaryDecoderEndpoint.class, "/textAndBinaryDecoderEndpoint")
                        .decoders(Arrays.<Class<? extends Decoder>>asList(TextContainerDecoder.class, BinaryContainerDecoder.class))
                        .build());
                add(ServerEndpointConfig.Builder.create(AnnotatedEndpoint.class, "/annotatedEndpointRegisteredProgramatically").build());

            }});
        }
    }

    @ServerEndpoint(value = "/annotatedEndpointRegisteredProgramatically")
    public static class AnnotatedEndpoint {
        @OnMessage
        public String onMessage(String message) {
            return message;
        }
    }

    public static class TextAndBinaryDecoderEndpoint extends Endpoint {

        @Override
        public void onOpen(final Session session, EndpointConfig config) {
            session.addMessageHandler(new MessageHandler.Whole<Message>() {
                @Override
                public void onMessage(Message message) {
                    try {
                        session.getBasicRemote().sendText(message.toString());
                    } catch (IOException e) {
                        e.printStackTrace();
                        // do nothing.
                    }
                }
            });
        }
    }

    public static class Message {

        final boolean type;

        public Message(boolean type) {
            this.type = type;
        }

        @Override
        public String toString() {
            if (type) {
                return "binary";
            } else {
                return "text";
            }
        }
    }

    public static class BinaryContainerDecoder extends CoderAdapter implements Decoder.Binary<Message> {
        @Override
        public Message decode(ByteBuffer bytes) throws DecodeException {
            return new Message(true);
        }

        @Override
        public boolean willDecode(ByteBuffer bytes) {
            return true;
        }
    }

    public static class TextContainerDecoder extends CoderAdapter implements Decoder.Text<Message> {
        @Override
        public Message decode(String s) throws DecodeException {
            return new Message(false);
        }

        @Override
        public boolean willDecode(String s) {
            return true;
        }
    }
}
