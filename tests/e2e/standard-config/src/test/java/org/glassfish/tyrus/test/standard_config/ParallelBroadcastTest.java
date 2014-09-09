/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.test.standard_config;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.websocket.ClientEndpoint;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.client.ThreadPoolConfig;
import org.glassfish.tyrus.core.TyrusSession;
import org.glassfish.tyrus.core.TyrusWebSocketEngine;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class ParallelBroadcastTest extends TestContainer {

    /**
     * Number of created sessions.
     */
    public static final int SESSIONS_COUNT = 1000;
    /**
     * Number of threads used for executing the broadcast.
     */
    public static final int THREAD_COUNT = 7;

    /**
     * Test broadcasting by iterating over all sessions.
     */
    @Test
    public void testParallelBroadcast() {
        Server server = null;
        AtomicInteger messageCounter = new AtomicInteger(0);
        CountDownLatch messageLatch = new CountDownLatch(SESSIONS_COUNT);
        try {
            server = startServer(BroadcastServerEndpoint.class);
            ClientManager client = createClient();
            client.getProperties().put(ClientProperties.SHARED_CONTAINER, true);

            // The number of threads has to be limited, because all the clients will receive the broadcast simultaneously,
            // which might lead to creating too many threads and consequently a test failure.
            client.getProperties().put(ClientProperties.WORKER_THREAD_POOL_CONFIG, ThreadPoolConfig.defaultConfig().setMaxPoolSize(20));

            for (int i = 0; i < SESSIONS_COUNT - 1; i++) {
                client.connectToServer(new TextClientEndpoint(messageLatch, messageCounter), getURI(BroadcastServerEndpoint.class));
            }

            Session session = client.connectToServer(new TextClientEndpoint(messageLatch, messageCounter), getURI(BroadcastServerEndpoint.class));
            session.getBasicRemote().sendText("Broadcast request");

            assertTrue(messageLatch.await(30, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            System.out.println("Received messages: " + messageCounter + "/" + SESSIONS_COUNT);
            stopServer(server);
        }
    }

    /**
     * Test Tyrus text broadcast, which is parallel by default.
     * <p/>
     * The number of threads used for the broadcast is {@code Math.min(Runtime.getRuntime().availableProcessors(), sessions.size() / 16)}.
     */
    @Test
    public void testTyrusParallelTextBroadcast() {
        testTyrusTextBroadcast();
    }

    /**
     * Test Tyrus binary broadcast, which is parallel by default.
     * <p/>
     * The number of threads used for the broadcast is {@code Math.min(Runtime.getRuntime().availableProcessors(), sessions.size() / 16)}.
     */
    @Test
    public void testTyrusParallelBinaryBroadcast() {
        testTyrusBinaryBroadcast();
    }

    /**
     * Test Tyrus text broadcast with parallel execution being disabled.
     * <p/>
     * The parallel broadcast is disabled only on Grizzly server.
     */
    @Test
    public void testTyrusNonParallelTextBroadcast() {
        // TODO: test on glassfish (servlet tests)
        if (System.getProperty("tyrus.test.host") != null) {
            return;
        }

        getServerProperties().put(TyrusWebSocketEngine.PARALLEL_BROADCAST_ENABLED, false);
        testTyrusTextBroadcast();
    }

    /**
     * Test Tyrus binary broadcast with parallel execution being disabled.
     * <p/>
     * The parallel broadcast is disabled only on Grizzly server.
     */
    @Test
    public void testTyrusNonParallelBinaryBroadcast() {
        // TODO: test on glassfish (servlet tests)
        if (System.getProperty("tyrus.test.host") != null) {
            return;
        }

        getServerProperties().put(TyrusWebSocketEngine.PARALLEL_BROADCAST_ENABLED, false);
        testTyrusBinaryBroadcast();
    }

    private void testTyrusTextBroadcast() {
        Server server = null;
        AtomicInteger messageCounter = new AtomicInteger(0);
        CountDownLatch messageLatch = new CountDownLatch(SESSIONS_COUNT);
        try {
            server = startServer(TyrusTextBroadcastServerEndpoint.class);
            ClientManager client = createClient();
            client.getProperties().put(ClientProperties.SHARED_CONTAINER, true);

            // The number of threads has to be limited, because all the clients will receive the broadcast simultaneously,
            // which might lead to creating too many threads and consequently a test failure.
            client.getProperties().put(ClientProperties.WORKER_THREAD_POOL_CONFIG, ThreadPoolConfig.defaultConfig().setMaxPoolSize(20));

            for (int i = 0; i < SESSIONS_COUNT - 1; i++) {
                client.connectToServer(new TextClientEndpoint(messageLatch, messageCounter), getURI(TyrusTextBroadcastServerEndpoint.class));
            }

            Session session = client.connectToServer(new TextClientEndpoint(messageLatch, messageCounter), getURI(TyrusTextBroadcastServerEndpoint.class));
            session.getBasicRemote().sendText("Broadcast request");

            assertTrue(messageLatch.await(30, TimeUnit.SECONDS));

            Thread.sleep(2000);

            assertEquals(SESSIONS_COUNT, messageCounter.get());
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            System.out.println("Received messages: " + messageCounter + "/" + SESSIONS_COUNT);
            stopServer(server);
        }
    }

    private void testTyrusBinaryBroadcast() {
        Server server = null;
        AtomicInteger messageCounter = new AtomicInteger(0);
        CountDownLatch messageLatch = new CountDownLatch(SESSIONS_COUNT);
        try {
            server = startServer(TyrusBinaryBroadcastServerEndpoint.class);
            ClientManager client = createClient();
            client.getProperties().put(ClientProperties.SHARED_CONTAINER, true);

            // The number of threads has to be limited, because all the clients will receive the broadcast simultaneously,
            // which might lead to creating too many threads and consequently a test failure.
            client.getProperties().put(ClientProperties.WORKER_THREAD_POOL_CONFIG, ThreadPoolConfig.defaultConfig().setMaxPoolSize(20));

            for (int i = 0; i < SESSIONS_COUNT - 1; i++) {
                client.connectToServer(new BinaryClientEndpoint(messageLatch, messageCounter), getURI(TyrusBinaryBroadcastServerEndpoint.class));
            }

            Session session = client.connectToServer(new BinaryClientEndpoint(messageLatch, messageCounter), getURI(TyrusBinaryBroadcastServerEndpoint.class));
            session.getBasicRemote().sendText("Broadcast request");

            assertTrue(messageLatch.await(30, TimeUnit.SECONDS));

            Thread.sleep(2000);

            assertEquals(SESSIONS_COUNT, messageCounter.get());
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            System.out.println("Received messages: " + messageCounter + "/" + SESSIONS_COUNT);
            stopServer(server);
        }
    }

    @ClientEndpoint
    public static class TextClientEndpoint {

        private final CountDownLatch messageLatch;
        private final AtomicInteger messageCounter;

        public TextClientEndpoint(CountDownLatch messageLatch, AtomicInteger messageCounter) {
            this.messageLatch = messageLatch;
            this.messageCounter = messageCounter;
        }

        @OnMessage
        public void onMessage(String message) {
            messageCounter.incrementAndGet();
            messageLatch.countDown();
        }
    }

    @ClientEndpoint
    public static class BinaryClientEndpoint {

        private final CountDownLatch messageLatch;
        private final AtomicInteger messageCounter;

        public BinaryClientEndpoint(CountDownLatch messageLatch, AtomicInteger messageCounter) {
            this.messageLatch = messageLatch;
            this.messageCounter = messageCounter;
        }

        @OnMessage
        public void onMessage(ByteBuffer message) {
            messageCounter.incrementAndGet();
            messageLatch.countDown();
        }
    }

    @ServerEndpoint("/parallelBroadcastEndpoint")
    public static class BroadcastServerEndpoint {

        @OnMessage
        public void onMessage(final Session session, String message) {
            final List<Session> openSessions = new ArrayList<Session>(session.getOpenSessions());
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

            for (int i = 0; i < THREAD_COUNT; i++) {
                final int partitionId = i;
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        int lowerBound = (SESSIONS_COUNT + THREAD_COUNT - 1) / THREAD_COUNT * partitionId;
                        int upperBound = Math.min((SESSIONS_COUNT + THREAD_COUNT - 1) / THREAD_COUNT * (partitionId + 1), SESSIONS_COUNT);
                        System.out.println(Thread.currentThread().getName() + " <" + lowerBound + ", " + upperBound + ")");

                        for (int j = lowerBound; j < upperBound; j++) {
                            openSessions.get(j).getAsyncRemote().sendText("Hi from " + partitionId);
                        }
                    }
                });
            }
        }
    }

    @ServerEndpoint("/parallelTyrusTextBroadcastEndpoint")
    public static class TyrusTextBroadcastServerEndpoint {

        @OnMessage
        public void onMessage(Session session, String message) {
            ((TyrusSession) session).broadcast("Hi from server");
        }
    }

    @ServerEndpoint("/parallelTyrusBinaryBroadcastEndpoint")
    public static class TyrusBinaryBroadcastServerEndpoint {

        @OnMessage
        public void onMessage(Session session, String message) {
            ((TyrusSession) session).broadcast(ByteBuffer.wrap("Hi from server".getBytes()));
        }
    }
}
