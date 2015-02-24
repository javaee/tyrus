/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2015 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.tests.servlet.dynamic_deploy;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class DynamicDeployTest extends TestContainer {

    private static final String CONTEXT_PATH = "/dynamic-deploy";

    public DynamicDeployTest() {
        setContextPath(CONTEXT_PATH);
    }

    @Test
    public void testEcho() throws DeploymentException, InterruptedException, IOException, URISyntaxException {
        if (System.getProperty("tyrus.test.host") == null) {
            return;
        }

        final CountDownLatch messageLatch = new CountDownLatch(1);

        URI uri = getURI(EchoEndpoint.class.getAnnotation(ServerEndpoint.class).value());
        // Test for TYRUS-187; works only in servlet case, thus test is here.
        uri = new URI(uri.toString() + "?myParam=myValue");


        final ClientManager client = createClient();
        client.connectToServer(new Endpoint() {
            @Override
            public void onOpen(Session session, EndpointConfig EndpointConfig) {

                try {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            assertEquals(message, "Do or do not, there is no try.");
                            messageLatch.countDown();
                        }
                    });

                    session.getBasicRemote().sendText("Do or do not, there is no try.");
                } catch (IOException e) {
                    // do nothing
                }
            }
        }, ClientEndpointConfig.Builder.create().build(), uri);

        messageLatch.await(1, TimeUnit.SECONDS);
        assertEquals(0, messageLatch.getCount());

    }

    @Test
    public void testAnnotated() throws DeploymentException, InterruptedException, IOException {
        if (System.getProperty("tyrus.test.host") == null) {
            return;
        }

        final CountDownLatch messageLatch = new CountDownLatch(1);

        final ClientManager client = createClient();
        client.connectToServer(
                new Endpoint() {
                    @Override
                    public void onOpen(Session session, EndpointConfig EndpointConfig) {
                        try {
                            session.addMessageHandler(new MessageHandler.Whole<String>() {
                                @Override
                                public void onMessage(String message) {
                                    assertEquals(message, "Do or do not, there is no try.");
                                    messageLatch.countDown();
                                }
                            });

                            session.getBasicRemote().sendText("Do or do not, there is no try.");
                        } catch (IOException e) {
                            // do nothing
                        }
                    }
                }, ClientEndpointConfig.Builder.create().build(),
                getURI(MyServletContextListenerAnnotated.class.getAnnotation(ServerEndpoint.class).value()));

        messageLatch.await(1, TimeUnit.SECONDS);
        assertEquals(0, messageLatch.getCount());

    }

    @Test
    public void testProgrammatic() throws DeploymentException, InterruptedException, IOException {
        if (System.getProperty("tyrus.test.host") == null) {
            return;
        }

        final CountDownLatch messageLatch = new CountDownLatch(1);

        final ClientManager client = createClient();
        client.connectToServer(
                new Endpoint() {
                    @Override
                    public void onOpen(Session session, EndpointConfig EndpointConfig) {
                        try {
                            session.addMessageHandler(new MessageHandler.Whole<String>() {
                                @Override
                                public void onMessage(String message) {
                                    assertEquals(message, "Do or do not, there is no try.");
                                    messageLatch.countDown();
                                }
                            });

                            // TODO: remove when TYRUS-108 is resolved.
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                            session.getBasicRemote().sendText("Do or do not, there is no try.");
                        } catch (IOException e) {
                            // do nothing
                        }
                    }
                }, ClientEndpointConfig.Builder.create().build(), getURI("/programmatic"));

        messageLatch.await(100, TimeUnit.SECONDS);
        assertEquals(0, messageLatch.getCount());

    }
}
