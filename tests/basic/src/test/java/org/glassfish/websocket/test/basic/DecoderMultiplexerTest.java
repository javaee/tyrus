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

package org.glassfish.websocket.test.basic;

import org.glassfish.websocket.client.WebSocketClient;
import org.glassfish.websocket.platform.EndpointAdapter;
import org.glassfish.websocket.platform.main.Server;
import org.glassfish.websocket.spi.SPIRemoteEndpoint;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test of several decoders.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class DecoderMultiplexerTest {

    private CountDownLatch messageLatch;

    private String receivedMessage;

    private static final String MESSAGE_A = "assssssss";

    private static final String MESSAGE_B = "bssssssss";

    @Ignore
    @Test
    public void testMessages() {
        Server server = new Server(org.glassfish.websocket.test.basic.bean.DecoderMultiplexerTestBean.class);
        server.start();
        try {
            messageLatch = new CountDownLatch(1);

            WebSocketClient client = WebSocketClient.createClient();

            TestAdapter ta = new TestAdapter();
            client.openSocket("ws://localhost:8025/websockets/tests/decodermultiplexer", 10000, ta);
            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertTrue("The received message is the same as the sent one", receivedMessage.equals("A: " + MESSAGE_A));

            messageLatch = new CountDownLatch(1);
            ta.sendMessage(MESSAGE_B);
            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertTrue("The received message is the same as the sent one", receivedMessage.equals("B: " + MESSAGE_B));

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    private class TestAdapter extends EndpointAdapter {
        private SPIRemoteEndpoint peer;

        @Override
        public void onConnect(SPIRemoteEndpoint re) {
            try {
                peer = re;
                peer.send(MESSAGE_A);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onMessage(SPIRemoteEndpoint re, String message) {
            receivedMessage = message;
            messageLatch.countDown();
        }

        public void sendMessage(String message) {
            try {
                peer.send(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
