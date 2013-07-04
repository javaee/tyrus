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
package org.glassfish.tyrus.test.e2e.async.basic;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.ContainerProvider;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnMessage;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.server.Server;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Basic test for RemoteEndpoint.Async.sendObject()
 *
 * @author Jitendra Kotamraju
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class AsyncObjectTest {

    private static final int MESSAGE_NO = 100;

    @Ignore
    @Test
    public void testObjectFuture() throws Exception {

        Server server = new Server(AsyncEchoFutureServer.class);
        try {
            server.start();

            CountDownLatch messageLatch = new CountDownLatch(MESSAGE_NO);
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(
                    new AsyncFutureClient(messageLatch),
                    ClientEndpointConfig.Builder.create().build(),
                    new URI("ws://localhost:8025/websockets/tests/async-basic-echo-object-future"));
            messageLatch.await(5, TimeUnit.SECONDS);

            // Check the number of received messages
            Assert.assertEquals("Didn't receive all the messages. ", 0, messageLatch.getCount());
            Assert.assertEquals("All messages were not sent from server", MESSAGE_NO,AsyncEchoFutureServer.counter.get());
        } finally {
            server.stop();
        }
    }

    // Server endpoint that just echos messages asynchronously
    @ServerEndpoint(value = "/async-basic-echo-object-future")
    public static class AsyncEchoFutureServer {

        private static AtomicInteger counter = new AtomicInteger(0);

        @OnMessage
        public void echo(int message, Session session) throws Exception {
            Future<Void> f = session.getAsyncRemote().sendObject(message);
            f.get();
            counter.incrementAndGet();
        }
    }

    public static class AsyncFutureClient extends Endpoint {
        private final CountDownLatch messageLatch;
        private final long noMessages;

        public AsyncFutureClient(CountDownLatch messageLatch) {
            noMessages = messageLatch.getCount();
            this.messageLatch = messageLatch;
        }

        public void onOpen(Session session, EndpointConfig EndpointConfig) {
            try {
                session.addMessageHandler(new MessageHandler.Whole<Integer>() {
                    public void onMessage(Integer message) {
                        messageLatch.countDown();
                    }
                });

                for(int i=0; i < noMessages; i++) {
                    session.getAsyncRemote().sendObject(i);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Ignore
    @Test
    public void testObjectHandler() throws Exception {

        Server server = new Server(AsyncEchoHandlerServer.class);
        try {
            server.start();

            CountDownLatch messageLatch = new CountDownLatch(MESSAGE_NO);
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(
                    new AsyncHandlerClient(messageLatch),
                    ClientEndpointConfig.Builder.create().build(),
                    new URI("ws://localhost:8025/websockets/tests/async-basic-echo-object-handler"));
            messageLatch.await(5, TimeUnit.SECONDS);

            // Check the number of received messages
            Assert.assertEquals("Didn't receive all the messages. ", 0, messageLatch.getCount());
            Assert.assertEquals("All messages were not sent from server", MESSAGE_NO,AsyncEchoHandlerServer.counter.get());
        } finally {
            server.stop();
        }
    }

    // Server endpoint that just echos messages asynchronously
    @ServerEndpoint(value = "/async-basic-echo-object-handler")
    public static class AsyncEchoHandlerServer {

        private static AtomicInteger counter = new AtomicInteger(0);

        @OnMessage
        public void echo(int message, Session session) throws Exception {
            session.getAsyncRemote().sendObject(message, new SendHandler() {
                @Override
                public void onResult(SendResult result) {
                    counter.incrementAndGet();
                }
            });
        }
    }

    // Client endpoint that sends messages asynchronously
    public static class AsyncHandlerClient extends Endpoint {
        private final CountDownLatch messageLatch;
        private final long noMessages;

        public AsyncHandlerClient(CountDownLatch messageLatch) {
            noMessages = messageLatch.getCount();
            this.messageLatch = messageLatch;
        }

        public void onOpen(Session session, EndpointConfig EndpointConfig) {
            try {
                session.addMessageHandler(new MessageHandler.Whole<Integer>() {
                    public void onMessage(Integer message) {
                        messageLatch.countDown();
                    }
                });

                for(int i=0; i < noMessages; i++) {
                    session.getAsyncRemote().sendObject(i, new SendHandler() {
                        @Override
                        public void onResult(SendResult result) {

                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
