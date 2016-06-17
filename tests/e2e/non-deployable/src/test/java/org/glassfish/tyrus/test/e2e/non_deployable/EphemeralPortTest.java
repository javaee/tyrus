/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.server.Server;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * Ephemeral port test.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class EphemeralPortTest {

    private static final String MESSAGE = "It's a trap!";
    private static final Logger LOGGER = Logger.getLogger(EphemeralPortTest.class.getName());

    @Test
    public void testEphemeralPort() throws DeploymentException, IOException, InterruptedException {
        Server server = new Server("localhost", -1, null, null, EphemeralPortTestEndpoint.class);
        server.start();

        final int port = server.getPort();
        final CountDownLatch latch = new CountDownLatch(1);

        try {
            ContainerProvider.getWebSocketContainer().connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {

                    session.addMessageHandler(String.class, new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            LOGGER.info("Session [" + session.getId() + "] RECEIVED: " + message);
                            if (MESSAGE.equals(message)) {
                                latch.countDown();
                            }
                        }
                    });

                    try {
                        session.getBasicRemote().sendText(MESSAGE);
                        LOGGER.info("Session [" + session.getId() + "] SENT: " + MESSAGE);
                    } catch (IOException e) {
                        // ignore.
                    }
                }


            }, URI.create("ws://localhost:" + port));

            assertTrue(latch.await(3, TimeUnit.SECONDS));
        } finally {
            server.stop();
        }
    }

    @Test
    public void testEphemeralPortParallel() throws InterruptedException {

        final AtomicBoolean failed = new AtomicBoolean(false);
        ArrayList<Thread> threads = new ArrayList<Thread>();

        for (int i = 0; i < 10; i++) {
            Thread thread = new Thread() {
                @Override
                public void run() {
                    try {
                        testEphemeralPort();
                    } catch (DeploymentException | IOException | InterruptedException e) {
                        failed.set(true);
                    }
                }
            };
            threads.add(thread);
            thread.start();
        }

        for (Thread t : threads) {
            t.join();
        }
    }

    @ServerEndpoint("/")
    public static class EphemeralPortTestEndpoint {

        @OnMessage
        public String onMessage(String message) {
            return message;
        }
    }
}
