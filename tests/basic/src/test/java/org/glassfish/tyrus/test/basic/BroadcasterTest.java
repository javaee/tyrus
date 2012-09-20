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

import org.glassfish.tyrus.client.WebSocketClient;
import org.glassfish.tyrus.platform.EndpointAdapter;
import org.glassfish.tyrus.platform.main.Server;
import org.glassfish.tyrus.spi.SPIRemoteEndpoint;
import org.glassfish.tyrus.test.basic.bean.BroadcasterTestBean;
import org.junit.Ignore;
import org.junit.Test;

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
            final TestEndpointAdapter ea1 = new TestEndpointAdapter(messageLatch);
            final TestEndpointAdapter ea2 = new TestEndpointAdapter(messageLatch);

            final WebSocketClient client1 = WebSocketClient.createClient();
            client1.openSocket("ws://localhost:8025/websockets/tests/broadcast",10000,ea1);
            final WebSocketClient client2 = WebSocketClient.createClient();
            client2.openSocket("ws://localhost:8025/websockets/tests/broadcast", 10000, ea1);

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

            ea1.peer.send(SENT_MESSAGE);

            assertTrue("Timeout reached. Message latch value: " + messageLatch.getCount(),
                    messageLatch.await(5, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    private static class TestEndpointAdapter extends EndpointAdapter {
        private final CountDownLatch messageLatch;
        public SPIRemoteEndpoint peer;

        TestEndpointAdapter(CountDownLatch messageLatch) {
            this.messageLatch = messageLatch;
        }

        @Override
        public synchronized void onConnect(SPIRemoteEndpoint gs) {
            this.peer = gs;
            notifyAll();
        }

        @Override
        public void onMessage(SPIRemoteEndpoint gs, String messageString) {
            messageLatch.countDown();
        }
    }
}
