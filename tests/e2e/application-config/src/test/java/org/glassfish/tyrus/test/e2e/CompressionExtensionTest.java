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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpointConfig;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.core.extension.CompressionExtension;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.server.TyrusServerConfiguration;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class CompressionExtensionTest extends TestContainer {

    public CompressionExtensionTest() {
        this.setContextPath("/e2e-test-appconfig");
    }

    public static class ServerDeployApplicationConfig extends TyrusServerConfiguration {
        public ServerDeployApplicationConfig() {
            super(Collections.<Class<?>>emptySet(), new HashSet<ServerEndpointConfig>() {{
                add(ServerEndpointConfig.Builder.create(EchoEndpoint.class, "/compressionExtensionTest")
                        .extensions(Arrays.<Extension>asList(new CompressionExtension())).build());
            }});
        }
    }

    public static class EchoEndpoint extends Endpoint {

        @Override
        public void onOpen(final Session session, EndpointConfig config) {
            session.addMessageHandler(new MessageHandler.Whole<byte[]>() {
                @Override
                public void onMessage(byte[] message) {
                    try {
                        session.getBasicRemote().sendBinary(ByteBuffer.wrap(message));
                    } catch (IOException e) {
                        // ignore.
                    }
                }
            });
        }
    }

    @Test
    public void testCompressedExtension() throws DeploymentException {
        Server server = startServer(ServerDeployApplicationConfig.class);
        final CountDownLatch messageLatch = new CountDownLatch(5);

        try {
            ArrayList<Extension> extensions = new ArrayList<Extension>();
            extensions.add(new CompressionExtension());

            final ClientEndpointConfig clientConfiguration = ClientEndpointConfig.Builder.create()
                    .extensions(extensions)
                    .configurator(new LoggingClientEndpointConfigurator())
                    .build();

            ClientManager client = ClientManager.createClient();
            final Session session = client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    session.addMessageHandler(new MessageHandler.Whole<byte[]>() {
                        @Override
                        public void onMessage(byte[] message) {
                            System.out.println("client onMessage: " + new String(message, Charset.forName("UTF-8")));
                            messageLatch.countDown();
                        }
                    });
                }
            }, clientConfiguration, getURI("/compressionExtensionTest"));

            assertTrue(session.getNegotiatedExtensions().size() > 0);

            boolean compressionNegotiated = false;
            for (Extension e : session.getNegotiatedExtensions()) {
                if (e instanceof CompressionExtension) {
                    compressionNegotiated = true;
                }
            }

            assertTrue(compressionNegotiated);

            try {
                session.getBasicRemote().sendBinary(ByteBuffer.wrap("Always pass on what you have learned.".getBytes(Charset.forName("UTF-8"))));
                session.getBasicRemote().sendBinary(ByteBuffer.wrap("Always pass on what you have learned.".getBytes(Charset.forName("UTF-8"))));
                session.getBasicRemote().sendBinary(ByteBuffer.wrap("Always pass on what you have learned.".getBytes(Charset.forName("UTF-8"))));
                session.getBasicRemote().sendBinary(ByteBuffer.wrap("Always pass on what you have learned.".getBytes(Charset.forName("UTF-8"))));
                session.getBasicRemote().sendBinary(ByteBuffer.wrap("Always pass on what you have learned.".getBytes(Charset.forName("UTF-8"))));
            } catch (IOException e) {
                e.printStackTrace();
            }

            assertTrue(messageLatch.await(1, TimeUnit.SECONDS));

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }

    }

    public static class LoggingClientEndpointConfigurator extends ClientEndpointConfig.Configurator {
        @Override
        public void beforeRequest(Map<String, List<String>> headers) {
            System.out.println("##### beforeRequest");
            System.out.println(headers);
            System.out.println();
        }

        @Override
        public void afterResponse(HandshakeResponse hr) {
            System.out.println("##### afterResponse");
            System.out.println(hr.getHeaders());
            System.out.println();
        }
    }
}
