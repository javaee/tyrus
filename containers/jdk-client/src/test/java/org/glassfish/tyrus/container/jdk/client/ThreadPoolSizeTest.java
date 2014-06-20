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
package org.glassfish.tyrus.container.jdk.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.websocket.ClientEndpoint;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.client.ThreadPoolConfig;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;

/**
 * Tests that the JDK client thread pool is limited properly.
 * <p/>
 * It blocks client thread in @OnMessage and tests that the number of delivered messages equals maximal thread pool size.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class ThreadPoolSizeTest extends TestContainer {

    public static volatile AtomicInteger messagesCounter = null;
    public static volatile CountDownLatch blockingLatch = null;
    public static volatile CountDownLatch messagesLatch = null;

    @Before
    public void beforeTest() {
        try {
            // thread pool idle timeout is set to 1s for JDK client - we let thread pool created by previous test be destroyed
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testDefaultMaxThreadPoolSize() {
        ClientManager client = ClientManager.createClient(JdkClientContainer.class.getName());
        /**
         * default defined by {@link org.glassfish.tyrus.client.ThreadPoolConfig}
         */
        int maxThreads = Math.max(20, Runtime.getRuntime().availableProcessors());
        testMaxThreadPoolSize(maxThreads, client);
    }

    @Test
    public void testMaxThreadPoolSize() {
        ClientManager client = ClientManager.createClient(JdkClientContainer.class.getName());
        ThreadPoolConfig threadPoolConfig = ThreadPoolConfig.defaultConfig().setMaxPoolSize(15);
        client.getProperties().put(ClientProperties.WORKER_THREAD_POOL_CONFIG, threadPoolConfig);
        testMaxThreadPoolSize(15, client);
    }

    private void testMaxThreadPoolSize(int maxThreadPoolSize, ClientManager client) {
        Server server = null;
        try {
            messagesCounter = new AtomicInteger(0);
            blockingLatch = new CountDownLatch(1);
            messagesLatch = new CountDownLatch(maxThreadPoolSize);
            server = startServer(AnnotatedServerEndpoint.class);

            client.getProperties().put(ClientProperties.SHARED_CONTAINER_IDLE_TIMEOUT, 1);

            List<Session> sessions = new ArrayList<>();

            for (int i = 0; i < maxThreadPoolSize + 10; i++) {
                Session session = client.connectToServer(BlockingClientEndpoint.class, getURI(AnnotatedServerEndpoint.class));
                sessions.add(session);
            }

            for (Session session : sessions) {
                session.getAsyncRemote().sendText("hi");
            }

            // wait for all threads to get blocked
            assertTrue(messagesLatch.await(1, TimeUnit.SECONDS));
            // wait some more time (we test nothing gets delivered in this interval)
            Thread.sleep(300);
            // assert number of delivered messages is equal to the thread pool size
            assertEquals(maxThreadPoolSize, messagesCounter.get());
            // let the blocked threads go
            blockingLatch.countDown();
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
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

        @OnMessage
        public void onMessage(String message) throws InterruptedException {
            if (messagesLatch != null) {
                messagesLatch.countDown();
            }

            if (messagesCounter != null) {
                messagesCounter.incrementAndGet();
            }

            blockingLatch.await(1, TimeUnit.SECONDS);
        }
    }
}
