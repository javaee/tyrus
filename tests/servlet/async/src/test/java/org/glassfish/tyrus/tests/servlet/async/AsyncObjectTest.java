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
package org.glassfish.tyrus.tests.servlet.async;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Assert;
import org.junit.Test;

/**
 * Basic test for TyrusRemoteEndpoint.Async.sendObject()
 *
 * @author Jitendra Kotamraju
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class AsyncObjectTest extends TestContainer {

    private static final int MESSAGE_NO = 100;
    private static final String CONTEXT_PATH = "/servlet-test-async";

    public AsyncObjectTest() {
        setContextPath(CONTEXT_PATH);
    }

    @Test
    public void testObjectFuture() throws DeploymentException, InterruptedException, IOException {
        final Server server = startServer(ObjectFutureEndpoint.class, ServiceEndpoint.class);

        CountDownLatch sentLatch = new CountDownLatch(MESSAGE_NO);
        CountDownLatch receivedLatch = new CountDownLatch(MESSAGE_NO);

        try {
            final ClientManager client = createClient();
            client.connectToServer(
                    new AsyncFutureClient(sentLatch, receivedLatch),
                    ClientEndpointConfig.Builder.create().build(),
                    getURI(ObjectFutureEndpoint.class.getAnnotation(ServerEndpoint.class).value()));

            sentLatch.await(5, TimeUnit.SECONDS);
            receivedLatch.await(5, TimeUnit.SECONDS);

            // Check the number of received messages
            Assert.assertEquals("Didn't send all the messages. ", 0, sentLatch.getCount());
            Assert.assertEquals("Didn't receive all the messages. ", 0, receivedLatch.getCount());

            final CountDownLatch serviceLatch = new CountDownLatch(1);
            client.connectToServer(
                    new Endpoint() {
                        @Override
                        public void onOpen(Session session, EndpointConfig config) {
                            session.addMessageHandler(new MessageHandler.Whole<Integer>() {
                                @Override
                                public void onMessage(Integer message) {
                                    Assert.assertEquals("Server callback wasn't called at all cases.", 0,
                                                        message.intValue());
                                    serviceLatch.countDown();
                                }
                            });
                            try {
                                session.getBasicRemote().sendText(ServiceEndpoint.OBJECT_FUTURE);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }, ClientEndpointConfig.Builder.create().build(),
                    getURI(ServiceEndpoint.class.getAnnotation(ServerEndpoint.class).value()));

            serviceLatch.await(5, TimeUnit.SECONDS);
            Assert.assertEquals("Didn't receive all the messages. ", 0, receivedLatch.getCount());
        } finally {
            stopServer(server);
        }
    }

    // Client endpoint that sends messages asynchronously
    public static class AsyncFutureClient extends Endpoint {
        private final CountDownLatch sentLatch;
        private final CountDownLatch receivedLatch;
        private final long noMessages;

        public AsyncFutureClient(CountDownLatch sentLatch, CountDownLatch receivedLatch) {
            noMessages = sentLatch.getCount();
            this.sentLatch = sentLatch;
            this.receivedLatch = receivedLatch;
        }

        @Override
        public void onOpen(Session session, EndpointConfig EndpointConfig) {
            try {
                session.addMessageHandler(new MessageHandler.Whole<Integer>() {
                    @Override
                    public void onMessage(Integer buf) {
                        receivedLatch.countDown();
                    }
                });

                for (int i = 0; i < noMessages; i++) {
                    Future future = session.getAsyncRemote().sendObject(i);
                    future.get();
                    sentLatch.countDown();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void testObjectHandler() throws DeploymentException, InterruptedException, IOException {
        final Server server = startServer(ObjectHandlerEndpoint.class, ServiceEndpoint.class);

        CountDownLatch sentLatch = new CountDownLatch(MESSAGE_NO);
        CountDownLatch receivedLatch = new CountDownLatch(MESSAGE_NO);

        try {
            final ClientManager client = createClient();
            client.connectToServer(
                    new AsyncHandlerClient(sentLatch, receivedLatch),
                    ClientEndpointConfig.Builder.create().build(),
                    getURI(ObjectHandlerEndpoint.class.getAnnotation(ServerEndpoint.class).value()));

            sentLatch.await(5, TimeUnit.SECONDS);
            receivedLatch.await(5, TimeUnit.SECONDS);

            // Check the number of received messages
            Assert.assertEquals("Didn't send all the messages. ", 0, sentLatch.getCount());
            Assert.assertEquals("Didn't receive all the messages. ", 0, receivedLatch.getCount());

            final CountDownLatch serviceLatch = new CountDownLatch(1);
            client.connectToServer(
                    new Endpoint() {
                        @Override
                        public void onOpen(Session session, EndpointConfig config) {
                            session.addMessageHandler(new MessageHandler.Whole<Integer>() {
                                @Override
                                public void onMessage(Integer message) {
                                    Assert.assertEquals("Server callback wasn't called at all cases.", 0,
                                                        message.intValue());
                                    serviceLatch.countDown();
                                }
                            });
                            try {
                                session.getBasicRemote().sendText(ServiceEndpoint.OBJECT_HANDLER);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }, ClientEndpointConfig.Builder.create().build(),
                    getURI(ServiceEndpoint.class.getAnnotation(ServerEndpoint.class).value()));

            serviceLatch.await(5, TimeUnit.SECONDS);
            Assert.assertEquals("Didn't receive all the messages. ", 0, receivedLatch.getCount());
        } finally {
            stopServer(server);
        }
    }

    // Client endpoint that sends messages asynchronously
    public static class AsyncHandlerClient extends Endpoint {
        private final CountDownLatch sentLatch;
        private final CountDownLatch receivedLatch;
        private final long noMessages;

        public AsyncHandlerClient(CountDownLatch sentLatch, CountDownLatch receivedLatch) {
            noMessages = sentLatch.getCount();
            this.sentLatch = sentLatch;
            this.receivedLatch = receivedLatch;
        }

        @Override
        public void onOpen(Session session, EndpointConfig EndpointConfig) {
            try {
                session.addMessageHandler(new MessageHandler.Whole<Integer>() {
                    @Override
                    public void onMessage(Integer message) {
                        receivedLatch.countDown();
                    }
                });

                for (int i = 0; i < noMessages; i++) {
                    session.getAsyncRemote().sendObject(i, new SendHandler() {
                        @Override
                        public void onResult(SendResult result) {
                            if (result.isOK()) {
                                sentLatch.countDown();
                            }
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
