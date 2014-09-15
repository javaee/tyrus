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

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.container.inmemory.InMemoryClientContainer;
import org.glassfish.tyrus.core.TyrusSession;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.server.TyrusServerConfiguration;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests that unmanaged executor services allocated by Grizzly server container get released after the server has been stopped.
 * <p/>
 * Since executor services cannot be obtained directly, server is started and stopped multiple times and it is tested that
 * the number of active threads does not exceed a given limit.
 * </p>
 * This test is not deployable, because the container on Glassfish uses managed executor services and number of threads
 * on a long-time-running Glassfish could exceed the hard limit checked by this test.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class ServerExecutorsManagementTest extends TestContainer {

    @Test
    public void testExecutorServicesGetClosed() {
        try {
            ClientManager clientManager = createClient();
            for (int i = 0; i < 100; i++) {
                Server server = startServer(BroadcastingEndpoint.class);
                try {
                    final CountDownLatch messageLatch = new CountDownLatch(1);
                    clientManager.connectToServer(new Endpoint() {
                        @Override
                        public void onOpen(Session session, EndpointConfig config) {
                            session.addMessageHandler(new MessageHandler.Whole<String>() {

                                @Override
                                public void onMessage(String message) {
                                    messageLatch.countDown();
                                }
                            });
                        }
                    }, getURI(BroadcastingEndpoint.class));
                    assertTrue(messageLatch.await(5, TimeUnit.SECONDS));


                } finally {
                    stopServer(server);
                }
            }

            int activeThreadsCount = Thread.activeCount();
            assertTrue("Number of active threads is " + activeThreadsCount, activeThreadsCount < 50);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testInMemoryContainerExecutorServicesGetClosed() {
        try {
            ClientManager clientManager = ClientManager.createClient(InMemoryClientContainer.class.getName());
            ServerApplicationConfig serverConfig = new TyrusServerConfiguration(new HashSet<Class<?>>(Arrays.<Class<?>>asList(BroadcastingEndpoint.class)), Collections.<ServerEndpointConfig>emptySet());
            ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();
            cec.getUserProperties().put(InMemoryClientContainer.SERVER_CONFIG, serverConfig);

            for (int i = 0; i < 100; i++) {
                final CountDownLatch messageLatch = new CountDownLatch(1);
                Session session = clientManager.connectToServer(new Endpoint() {
                    @Override
                    public void onOpen(Session session, EndpointConfig config) {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {

                            @Override
                            public void onMessage(String message) {
                                messageLatch.countDown();
                            }
                        });
                    }
                }, cec, URI.create("ws://inmemory/serverExecutorsManagementEndpoint"));

                assertTrue(messageLatch.await(5, TimeUnit.SECONDS));
                session.close();
            }

            int activeThreadsCount = Thread.activeCount();
            assertTrue("Number of active threads is " + activeThreadsCount, activeThreadsCount < 50);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    /* Tyrus broadcast is parallel by default and it uses executor service managed by the server container,
     so using broadcast ensures that executor service on server is invoked */
    @ServerEndpoint("/serverExecutorsManagementEndpoint")
    public static class BroadcastingEndpoint {

        @OnOpen
        public void onOpen(Session session) {
            ((TyrusSession) session).broadcast("Hi");
        }
    }
}
