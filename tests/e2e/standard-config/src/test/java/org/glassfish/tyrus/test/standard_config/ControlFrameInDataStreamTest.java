/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2015 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.test.standard_config;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.PongMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests that a control frame (in the test represented by pong and close) sent within a stream of partial text or
 * binary messages gets delivered.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class ControlFrameInDataStreamTest extends TestContainer {

    private static final String CLOSE_PHRASE = "Just checking this gets delivered";

    /**
     * Test that a pong message inside a stream of text partial messages gets delivered.
     */
    @Test
    public void testPongMessageInTextStream() {
        Server server = null;
        try {
            final CountDownLatch pongLatch = new CountDownLatch(1);

            server = startServer(PongInTextServerEndpoint.class);
            ClientManager client = createClient();
            client.connectToServer(new AnnotatedClientEndpoint(pongLatch, null),
                                   getURI(PongInTextServerEndpoint.class));

            assertTrue(pongLatch.await(1, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
        }
    }

    /**
     * Test that a close message inside a stream of text partial messages gets delivered.
     */
    @Test
    public void testCloseMessageInTextStream() {
        Server server = null;
        try {
            final CountDownLatch closeLatch = new CountDownLatch(1);

            server = startServer(CloseInTextServerEndpoint.class);
            ClientManager client = createClient();
            client.connectToServer(new AnnotatedClientEndpoint(null, closeLatch),
                                   getURI(CloseInTextServerEndpoint.class));

            assertTrue(closeLatch.await(1, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
        }
    }

    /**
     * Test that a pong message inside a stream of binary partial messages gets delivered.
     */
    @Test
    public void testPongMessageInBinaryStream() {
        Server server = null;
        try {
            final CountDownLatch pongLatch = new CountDownLatch(1);

            server = startServer(PongInBinaryServerEndpoint.class);
            ClientManager client = createClient();
            client.connectToServer(new AnnotatedClientEndpoint(pongLatch, null),
                                   getURI(PongInBinaryServerEndpoint.class));

            assertTrue(pongLatch.await(1, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
        }
    }

    /**
     * Test that a close message inside a stream of binary partial messages gets delivered.
     */
    @Test
    public void testCloseMessageInBinaryStream() {
        Server server = null;
        try {
            final CountDownLatch closeLatch = new CountDownLatch(1);

            server = startServer(CloseInBinaryServerEndpoint.class);
            ClientManager client = createClient();
            client.connectToServer(new AnnotatedClientEndpoint(null, closeLatch),
                                   getURI(CloseInBinaryServerEndpoint.class));

            assertTrue(closeLatch.await(1, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint("/closeInTextEndpoint")
    public static class CloseInTextServerEndpoint {

        @OnOpen
        public void onOpen(Session session) throws IOException {
            session.getBasicRemote().sendText("Hi, wait for more ...", false);
            session.close(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, CLOSE_PHRASE));
        }
    }

    @ServerEndpoint("/pongInTextEndpoint")
    public static class PongInTextServerEndpoint {

        @OnOpen
        public void onOpen(Session session) throws IOException {
            session.getBasicRemote().sendText("Hi, wait for more ...", false);
            session.getBasicRemote().sendPong(null);
        }
    }

    @ServerEndpoint("/pongInBinaryEndpoint")
    public static class PongInBinaryServerEndpoint {

        @OnOpen
        public void onOpen(Session session) throws IOException {
            session.getBasicRemote().sendBinary(ByteBuffer.wrap("Hi, wait for more ...".getBytes()), false);
            session.getBasicRemote().sendPong(null);
        }
    }

    @ServerEndpoint("/closeInBinaryEndpoint")
    public static class CloseInBinaryServerEndpoint {

        @OnOpen
        public void onOpen(Session session) throws IOException {
            session.getBasicRemote().sendBinary(ByteBuffer.wrap("Hi, wait for more ...".getBytes()), false);
            session.close(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, CLOSE_PHRASE));
        }
    }

    @ClientEndpoint
    public static class AnnotatedClientEndpoint {

        private final CountDownLatch pongLatch;
        private final CountDownLatch closeLatch;

        public AnnotatedClientEndpoint(CountDownLatch pongLatch, CountDownLatch closeLatch) {
            this.pongLatch = pongLatch;
            this.closeLatch = closeLatch;
        }

        @OnMessage
        public void onMessage(Session session, ByteBuffer message, boolean last) {
        }

        @OnMessage
        public void onMessage(Session session, String message, boolean last) {
        }

        @OnMessage
        public void onMessage(PongMessage message, Session session) {
            pongLatch.countDown();
        }

        @OnClose
        public void onClose(CloseReason closeReason, Session session) {
            if (closeLatch != null && closeReason.getReasonPhrase().equals(CLOSE_PHRASE)) {
                closeLatch.countDown();
            }
        }
    }
}
