/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 - 2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.test.basic;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.platform.configuration.DefaultClientEndpointConfiguration;
import org.glassfish.tyrus.platform.main.Server;
import org.glassfish.tyrus.test.basic.bean.BroadcasterTestBean;
import org.junit.Ignore;
import org.junit.Test;

import javax.net.websocket.RemoteEndpoint;
import javax.net.websocket.Session;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

/**
 * Tests broadcasting to several clients.
 *
 * @author Martin Matula (martin.matula at oracle.com)
 */
public class BroadcasterTest {
    private static final String SENT_MESSAGE = "Hello World";

    @Ignore
    @Test
    public void testBroadcaster() {
        final CountDownLatch messageLatch = new CountDownLatch(2);
        Server server = new Server(BroadcasterTestBean.class);
        server.start();
        try {
            final TEndpointAdapter ea1 = new TEndpointAdapter(messageLatch);
            final TEndpointAdapter ea2 = new TEndpointAdapter(messageLatch);

            final DefaultClientEndpointConfiguration.Builder builder = new DefaultClientEndpointConfiguration.Builder(new URI("ws://localhost:8025/websockets/tests/broadcast"));
            final DefaultClientEndpointConfiguration dcec = builder.build();

            final ClientManager client1 = ClientManager.createClient();
            client1.connectToServer(ea1, dcec);
            final ClientManager client2 = ClientManager.createClient();
            client1.connectToServer(ea2, dcec);

            synchronized (ea1) {
                if (ea1.peer == null) {
                    ea1.wait();
                }
            }

            synchronized (ea2) {
                if (ea2.peer == null) {
                    ea2.wait();
                }
            }

            ea1.peer.sendString(SENT_MESSAGE);

            assertTrue("Timeout reached. Message latch value: " + messageLatch.getCount(),
                    messageLatch.await(5, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    private static class TEndpointAdapter extends TestEndpointAdapter {
        private final CountDownLatch messageLatch;
        public RemoteEndpoint peer;

        TEndpointAdapter(CountDownLatch messageLatch) {
            this.messageLatch = messageLatch;
        }

        @Override
        public synchronized void onOpen(Session session) {
            this.peer = session.getRemote();
            notifyAll();
        }

        @Override
        public void onMessage(String message) {
            messageLatch.countDown();
        }
    }
}
