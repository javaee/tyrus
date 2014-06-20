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
package org.glassfish.tyrus.test.e2e.non_deployable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpoint;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.container.grizzly.client.GrizzlyClientProperties;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.glassfish.grizzly.threadpool.ThreadPoolConfig;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test that both {@link GrizzlyClientProperties} and {@link ClientProperties} are supported when configuring Grizzly
 * client worker thread pool.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class GrizzlyClientThreadPoolConfigPropertiesTest extends TestContainer {

    /**
     * Test that {@link GrizzlyClientProperties#WORKER_THREAD_POOL_CONFIG} is supported with {@link org.glassfish.grizzly.threadpool.ThreadPoolConfig}.
     */
    @Test
    public void testGrizzlyThreadPoolConfigGrizzlyProperties() {
        testThreadPoolConfigProperties(GrizzlyClientProperties.WORKER_THREAD_POOL_CONFIG, true);
    }

    /**
     * Test that {@link ClientProperties#WORKER_THREAD_POOL_CONFIG} is supported with {@link org.glassfish.grizzly.threadpool.ThreadPoolConfig}.
     */
    @Test
    public void testGrizzlyThreadPoolConfigClientProperties() {
        testThreadPoolConfigProperties(ClientProperties.WORKER_THREAD_POOL_CONFIG, true);
    }

    /**
     * Test that {@link ClientProperties#WORKER_THREAD_POOL_CONFIG} is supported with {@link org.glassfish.tyrus.client.ThreadPoolConfig}.
     */
    @Test
    public void testTyrusThreadPoolConfigClientProperties() {
        testThreadPoolConfigProperties(ClientProperties.WORKER_THREAD_POOL_CONFIG, false);
    }

    private void testThreadPoolConfigProperties(String workerThreadPoolProperty, boolean useGrizzlyConfig) {
        /*
            Also setting client.getProperties().put(ClientProperties.SHARED_CONTAINER, ... ) is supported - if a test running
            before this test does that, this test might fail.
         */
        if (System.getProperties().getProperty(ClientProperties.SHARED_CONTAINER) != null) {
            // test not valid with shared container.
            return;
        }

        Server server = null;
        try {
            final CountDownLatch workerPoolLatch = new CountDownLatch(1);

            server = startServer(AnnotatedServerEndpoint.class);
            ClientManager client = ClientManager.createClient();

            if (useGrizzlyConfig) {
                ThreadPoolConfig workerThreadPoolConfig = ThreadPoolConfig.defaultConfig()
                        .setThreadFactory(new ThreadFactory() {

                            @Override
                            public Thread newThread(Runnable r) {
                                workerPoolLatch.countDown();
                                return new Thread(r);
                            }
                        });

                client.getProperties().put(workerThreadPoolProperty, workerThreadPoolConfig);
            } else {
                org.glassfish.tyrus.client.ThreadPoolConfig workerThreadPoolConfig = org.glassfish.tyrus.client.ThreadPoolConfig.defaultConfig()
                        .setThreadFactory(new ThreadFactory() {

                            @Override
                            public Thread newThread(Runnable r) {
                                workerPoolLatch.countDown();
                                return new Thread(r);
                            }
                        });

                client.getProperties().put(workerThreadPoolProperty, workerThreadPoolConfig);
            }

            client.connectToServer(AnnotatedClientEndpoint.class, getURI(AnnotatedServerEndpoint.class));

            assertTrue(workerPoolLatch.await(1, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint("/threadPoolConfigEchoEndpoint")
    private static class AnnotatedServerEndpoint {
    }

    @ClientEndpoint
    private static class AnnotatedClientEndpoint {
    }
}
