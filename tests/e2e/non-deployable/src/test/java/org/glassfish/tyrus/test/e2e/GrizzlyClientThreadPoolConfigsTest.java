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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.container.grizzly.GrizzlyClientSocket;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.glassfish.grizzly.threadpool.ThreadPoolConfig;

import org.junit.Assert;
import org.junit.Test;
import static org.junit.Assert.fail;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class GrizzlyClientThreadPoolConfigsTest extends TestContainer {

    @Test
    public void testCustomThreadFactories() throws DeploymentException {
        Server server = startServer(EchoEndpoint.class);

        try {
            server.start();
            final CountDownLatch workerThreadLatch = new CountDownLatch(1);
            final CountDownLatch selectorThreadLatch = new CountDownLatch(1);
            final CountDownLatch messageLatch = new CountDownLatch(1);

            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager client = ClientManager.createClient();

            client.getProperties().put(GrizzlyClientSocket.WORKER_THREAD_POOL_CONFIG, ThreadPoolConfig.defaultConfig().setThreadFactory(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Logger.getLogger(GrizzlyClientThreadPoolConfigsTest.class.getName()).log(Level.INFO, "Worker thread factory called: " + r);
                    workerThreadLatch.countDown();
                    return new Thread(r);
                }
            }));

            client.getProperties().put(GrizzlyClientSocket.SELECTOR_THREAD_POOL_CONFIG, ThreadPoolConfig.defaultConfig().setThreadFactory(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Logger.getLogger(GrizzlyClientThreadPoolConfigsTest.class.getName()).log(Level.INFO, "Selector thread factory called: " + r);
                    selectorThreadLatch.countDown();
                    return new Thread(r);
                }
            }));


            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            messageLatch.countDown();
                        }
                    });

                    try {
                        session.getBasicRemote().sendText("test");
                    } catch (IOException e) {
                        fail();
                    }
                }
            }, cec, getURI(EchoEndpoint.class));

            messageLatch.await(5, TimeUnit.SECONDS);

            Assert.assertEquals(0, messageLatch.getCount());
            Assert.assertEquals(0, workerThreadLatch.getCount());
            Assert.assertEquals(0, selectorThreadLatch.getCount());

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    /**
     * Bean for basic echo test.
     *
     * @author Stepan Kopriva (stepan.kopriva at oracle.com)
     */
    @ServerEndpoint(value = "/echoendpoint")
    public static class EchoEndpoint {

        @OnMessage
        public String doThat(String message, Session session) {

            // TYRUS-141
            if (session.getNegotiatedSubprotocol() != null) {
                return message;
            }

            return null;
        }
    }
}
