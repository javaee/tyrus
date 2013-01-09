/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 - 2013 Oracle and/or its affiliates. All rights reserved.
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

import java.net.URI;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.Session;

import org.glassfish.tyrus.DefaultClientEndpointConfiguration;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Tests the ServerContainer.getOpenSessions method.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class ServerContainerGetOpenSessions {

    private String receivedMessage;
    private static final String SENT_MESSAGE = "Hello World";

    @Test
    public void testGetOpenSessions() {
        final CountDownLatch messageLatch = new CountDownLatch(1);
        DefaultClientEndpointConfiguration.Builder builder = new DefaultClientEndpointConfiguration.Builder();
        final DefaultClientEndpointConfiguration dcec = builder.build();

        Server server = new Server(org.glassfish.tyrus.test.e2e.bean.SimpleRemoteTestBean.class, org.glassfish.tyrus.test.e2e.bean.HelloTestBean.class);

        try {
            server.start();
            final ClientManager client = ClientManager.createClient();
            for (int i = 0; i < 2; i++) {
                client.connectToServer(new TestEndpointAdapter() {
                    @Override
                    public void onOpen(Session session) {
                    }

                    @Override
                    public void onMessage(String s) {
                    }
                }, dcec, new URI("ws://localhost:8025/websockets/tests/customremote/hello"));
            }

            for (int i = 0; i < 2; i++) {
                client.connectToServer(new TestEndpointAdapter() {
                    @Override
                    public void onOpen(Session session) {
                    }

                    @Override
                    public void onMessage(String s) {
                    }
                }, dcec, new URI("wss://localhost:8025/websockets/tests/hello"));
            }

            messageLatch.await(1, TimeUnit.SECONDS);
            Set<Session> serverSessions = server.getServerContainer().getOpenSessions();
            assertEquals(4, serverSessions.size());


        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }
}
