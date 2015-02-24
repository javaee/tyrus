/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2015 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.container.jdk.client;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpoint;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.client.ThreadPoolConfig;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

import junit.framework.Assert;
import static junit.framework.Assert.fail;

/**
 * When the capacity of a thread pool has been exhausted, tasks get queued. This tests test that enqueued tasks get
 * executed when threads are not busy anymore.
 * <p/>
 * The default queue and a queue provided by the user is tested.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class EnqueuedTasksTest extends TestContainer {

    @Test
    public void testUserProvidedQueue() {
        ThreadPoolConfig threadPoolConfig = ThreadPoolConfig.defaultConfig();
        CountDownLatch enqueueLatch = new CountDownLatch(10);
        threadPoolConfig.setQueue(new CountingQueue(enqueueLatch));
        testEnqueuedTasksGetExecuted(threadPoolConfig, enqueueLatch);
    }

    @Test
    public void testDefaultQueue() {
        ThreadPoolConfig threadPoolConfig = ThreadPoolConfig.defaultConfig();
        testEnqueuedTasksGetExecuted(threadPoolConfig, null);
    }

    private void testEnqueuedTasksGetExecuted(ThreadPoolConfig threadPoolConfig, CountDownLatch enqueueLatch) {
        CountDownLatch blockingLatch = new CountDownLatch(1);
        CountDownLatch totalMessagesLatch = new CountDownLatch(20);
        CountDownLatch sessionCloseLatch = new CountDownLatch(20);
        Server server = null;
        try {
            server = startServer(AnnotatedServerEndpoint.class);

            ClientManager client = ClientManager.createClient(JdkClientContainer.class.getName());
            threadPoolConfig.setMaxPoolSize(10);
            client.getProperties().put(ClientProperties.WORKER_THREAD_POOL_CONFIG, threadPoolConfig);
            client.getProperties().put(ClientProperties.SHARED_CONTAINER_IDLE_TIMEOUT, 1);

            List<Session> sessions = new ArrayList<>();

            BlockingClientEndpoint clientEndpoint =
                    new BlockingClientEndpoint(blockingLatch, totalMessagesLatch, sessionCloseLatch);

            for (int i = 0; i < 20; i++) {
                Session session = client.connectToServer(clientEndpoint, getURI(AnnotatedServerEndpoint.class));
                sessions.add(session);
            }

            for (Session session : sessions) {
                session.getAsyncRemote().sendText("hi");
            }

            if (enqueueLatch == null) {
                // if latch counting enqueued tasks is not present (case when using default queue), just wait some time
                Thread.sleep(2000);
            } else {
                // 10 tasks got enqueued
                assertTrue(enqueueLatch.await(5, TimeUnit.SECONDS));
            }
            // let the blocked threads go
            blockingLatch.countDown();
            // check everything got delivered
            assertTrue(totalMessagesLatch.await(5, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            // just to be sure there are no blocked threads left.
            blockingLatch.countDown();
            stopServer(server);
            try {
                /* Tests in the package are sensitive to freeing resources. Unclosed sessions might hinder the next test
                (if the next test requires a fresh client thread pool) */
                Assert.assertTrue(sessionCloseLatch.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @ServerEndpoint("/ThreadPoolTestServerEndpoint")
    public static class AnnotatedServerEndpoint {

        @OnMessage
        public String onMessage(String message) {
            return message;
        }
    }

    @ClientEndpoint
    public static class BlockingClientEndpoint {

        private final CountDownLatch blockingLatch;
        private final CountDownLatch totalMessagesLatch;
        private final CountDownLatch sessionCloseLatch;

        BlockingClientEndpoint(CountDownLatch blockingLatch, CountDownLatch totalMessagesLatch,
                               CountDownLatch sessionCloseLatch) {
            this.blockingLatch = blockingLatch;
            this.totalMessagesLatch = totalMessagesLatch;
            this.sessionCloseLatch = sessionCloseLatch;
        }

        @OnMessage
        public void onMessage(String message) throws InterruptedException {
            blockingLatch.await();

            if (totalMessagesLatch != null) {
                totalMessagesLatch.countDown();
            }
        }

        @OnClose
        public void onClose(Session session) {
            sessionCloseLatch.countDown();
        }
    }

    /**
     * A wrapper of {@link java.util.LinkedList} that counts enqueued elements.
     */
    private static class CountingQueue extends LinkedList<Runnable> implements Queue<Runnable> {

        private static final long serialVersionUID = -1356740236369553900L;
        private final CountDownLatch enqueueLatch;

        CountingQueue(CountDownLatch enqueueLatch) {
            this.enqueueLatch = enqueueLatch;
        }

        @Override
        public boolean offer(Runnable runnable) {
            enqueueLatch.countDown();
            return super.offer(runnable);
        }
    }
}
