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
package org.glassfish.tyrus.tests.servlet.maxsessions;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpointConfig;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.core.TyrusServerEndpointConfig;
import org.glassfish.tyrus.core.TyrusWebSocketEngine;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.server.TyrusServerConfiguration;
import org.glassfish.tyrus.test.tools.TestContainer;
import org.glassfish.tyrus.tests.servlet.maxsessions.ApplicationConfig;
import org.glassfish.tyrus.tests.servlet.maxsessions.Echo;
import org.glassfish.tyrus.tests.servlet.maxsessions.ServiceEndpoint;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import junit.framework.Assert;

/**
 * Tests configuration and implementation of maximal number of open sessions.
 *
 * @author Ondrej Kosatka (ondrej.kosatka at oracle.com)
 */
public class MaxSessionsAppTest extends TestContainer {

    private static final String CONTEXT_PATH = "/max-sessions-per-app-test";

    private static final int NUMBER_OF_ENDPOINTS = 3;
    private static final int NUMBER_OF_CLIENTS_OVER_LIMIT = 3;
    private static final int NUMBER_OF_CLIENT_SESSIONS = ApplicationConfig.MAX_SESSIONS + NUMBER_OF_CLIENTS_OVER_LIMIT;

    public MaxSessionsAppTest() {
        setContextPath(CONTEXT_PATH);
    }

    public static class ServerDeployApplicationConfig extends TyrusServerConfiguration {
        public ServerDeployApplicationConfig() {
            super(new HashSet<Class<?>>() {{
                add(ServiceEndpoint.class);
            }}, new HashSet<ServerEndpointConfig>() {{
                for (final String path : ApplicationConfig.PATHS) {
                    add(TyrusServerEndpointConfig.Builder.create(Echo.class, path).build());
                }
            }});
        }
    }

    //@Test
    public void maxSessions() throws DeploymentException, InterruptedException, IOException {
        final ClientManager client = createClient();

        final CountDownLatch closeNormalLatch = new CountDownLatch(ApplicationConfig.MAX_SESSIONS);
        final CountDownLatch closeOverLimitLatch = new CountDownLatch(NUMBER_OF_CLIENTS_OVER_LIMIT);

        final Session[] sessions = new Session[NUMBER_OF_CLIENT_SESSIONS];

        for (int i = 0; i < NUMBER_OF_CLIENT_SESSIONS; i++) {
            URI uri = getURI("/echo" + ((i % NUMBER_OF_ENDPOINTS) + 1));
            System.out.println(uri);
            sessions[i] = client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            System.out.println(message);
                        }
                    });
                    try {
                        session.getBasicRemote().sendText("Do or do not, there is no try.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    System.out.println("Client-side close reason: " + closeReason);
                    if (closeReason.getCloseCode().getCode() == CloseReason.CloseCodes.NORMAL_CLOSURE.getCode()) {
                        closeNormalLatch.countDown();
                    } else if (closeReason.getCloseCode().getCode() == CloseReason.CloseCodes.TRY_AGAIN_LATER.getCode()) {
                        closeOverLimitLatch.countDown();
                    }
                }
            }, ClientEndpointConfig.Builder.create().build(), uri);
        }

        assertTrue(String.format("Session should be closed just %d times with close code 1013 - Try Again Later", NUMBER_OF_CLIENTS_OVER_LIMIT),
                closeOverLimitLatch.await(3, TimeUnit.SECONDS));

        for (int i = 0; i < NUMBER_OF_CLIENT_SESSIONS; i++) {
            System.out.printf("session[%d] is open? %s\n", i, sessions[i].isOpen());
            if (i < ApplicationConfig.MAX_SESSIONS) {
                assertTrue("Session in limit is closed!", sessions[i].isOpen());
                sessions[i].close();
            } else {
                assertFalse("Session over limit should be closed!", sessions[i].isOpen());
            }
        }

        Assert.assertTrue("Number of normal closures should be the same as the limit!", closeNormalLatch.await(1, TimeUnit.SECONDS));

        final CountDownLatch messageReceivedLatch = new CountDownLatch(1);
        Session session = client.connectToServer(new Endpoint() {
            @Override
            public void onOpen(Session session, EndpointConfig EndpointConfig) {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(String message) {
                        messageReceivedLatch.countDown();
                    }
                });
                try {
                    session.getBasicRemote().sendText("New session is available after close all of the opened sessions.");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }, ClientEndpointConfig.Builder.create().build(), getURI("/echo1"));

        assertTrue(messageReceivedLatch.await(1, TimeUnit.SECONDS));

        session.close();

        testViaServiceEndpoint(client, ServiceEndpoint.class, POSITIVE, "/echo");
        testViaServiceEndpoint(client, ServiceEndpoint.class, POSITIVE, "reset");

        Thread.sleep(100);
    }

    @Test
    public void maxSessionsDurable() throws DeploymentException, InterruptedException, IOException {
        getServerProperties().put(TyrusWebSocketEngine.MAX_SESSIONS, ApplicationConfig.MAX_SESSIONS);

        Server server = startServer(ServerDeployApplicationConfig.class);
        try {
            for (int i = 0; i < 10; i++) {
                maxSessions();
            }
        } finally {
            stopServer(server);
        }
    }

}
