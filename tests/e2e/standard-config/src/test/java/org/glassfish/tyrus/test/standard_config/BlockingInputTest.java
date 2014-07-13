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
package org.glassfish.tyrus.test.standard_config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpoint;
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
 * Tests that threads blocked in {@link java.io.Reader} and {@link java.io.InputStream} are unblocked after the session
 * has been closed. Also tests that attempt to read from {@link java.io.Reader} and {@link java.io.InputStream}
 * will fail when the session has been closed.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class BlockingInputTest extends TestContainer {

    /**
     * Test that a thread blocked in {@link java.io.Reader} on the client side gets released if the session is closed by the client.
     */
    @Test
    public void testReaderCloseByClient() {
        Server server = null;
        try {
            CountDownLatch threadReleasedLatch = new CountDownLatch(1);
            CountDownLatch messageLatch = new CountDownLatch(1);

            server = startServer(AnnotatedServerTextEndpoint.class);
            ClientManager client = createClient();
            Session session = client.connectToServer(new CloseByClientEndpoint(threadReleasedLatch, messageLatch), getURI(AnnotatedServerTextEndpoint.class));

            assertTrue(messageLatch.await(1, TimeUnit.SECONDS));
            // give the client endpoint some time to get blocked
            Thread.sleep(100);
            session.close();
            assertTrue(threadReleasedLatch.await(1, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
        }
    }

    /**
     * Test that a thread blocked in {@link java.io.InputStream} on the client side gets released if the session is closed by the client.
     */
    @Test
    public void testInputStreamCloseByClient() {
        Server server = null;
        try {
            CountDownLatch threadReleasedLatch = new CountDownLatch(1);
            CountDownLatch messageLatch = new CountDownLatch(1);

            server = startServer(AnnotatedServerBinaryEndpoint.class);
            ClientManager client = createClient();
            Session session = client.connectToServer(new CloseByClientEndpoint(threadReleasedLatch, messageLatch), getURI(AnnotatedServerBinaryEndpoint.class));

            assertTrue(messageLatch.await(1, TimeUnit.SECONDS));
            // give the client endpoint some time to get blocked
            Thread.sleep(100);
            session.close();
            assertTrue(threadReleasedLatch.await(1, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
        }
    }

    /**
     * Test that a thread blocked in {@link java.io.Reader} on the client side gets released if the session is closed by the server.
     */
    @Test
    public void testReaderCloseByServer() {
        Server server = null;
        try {
            CountDownLatch threadReleasedLatch = new CountDownLatch(1);

            server = startServer(AnnotatedServerTextEndpoint.class);
            ClientManager client = createClient();
            client.connectToServer(new CloseByServerEndpoint(threadReleasedLatch), getURI(AnnotatedServerTextEndpoint.class));

            assertTrue(threadReleasedLatch.await(1, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
        }
    }

    /**
     * Test that a thread blocked in {@link java.io.InputStream} on the client side gets released if the session is closed by the server.
     */
    @Test
    public void testInputStreamCloseByServer() {
        Server server = null;
        try {
            CountDownLatch threadReleasedLatch = new CountDownLatch(1);

            server = startServer(AnnotatedServerBinaryEndpoint.class);
            ClientManager client = createClient();
            client.connectToServer(new CloseByServerEndpoint(threadReleasedLatch), getURI(AnnotatedServerBinaryEndpoint.class));

            assertTrue(threadReleasedLatch.await(1, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
        }
    }

    /**
     * Test that an attempt to read from {@link java.io.Reader} will throw {@link java.io.IOException} if the session has been closed.
     */
    @Test
    public void testReaderWithClosedSession() {
        Server server = null;
        try {
            CountDownLatch threadReleasedLatch = new CountDownLatch(1);

            server = startServer(AnnotatedServerTextEndpoint.class);
            ClientManager client = createClient();
            client.connectToServer(new ReadFromClosedSessionEndpoint(threadReleasedLatch), getURI(AnnotatedServerTextEndpoint.class));

            assertTrue(threadReleasedLatch.await(1, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
        }
    }

    /**
     * Test that an attempt to read from {@link java.io.InputStream} will throw {@link java.io.IOException} if the session has been closed.
     */
    @Test
    public void testInputStreamWithClosedSession() {
        Server server = null;
        try {
            CountDownLatch threadReleasedLatch = new CountDownLatch(1);

            server = startServer(AnnotatedServerBinaryEndpoint.class);
            ClientManager client = createClient();
            client.connectToServer(new ReadFromClosedSessionEndpoint(threadReleasedLatch), getURI(AnnotatedServerBinaryEndpoint.class));

            assertTrue(threadReleasedLatch.await(1, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint("/blockingTextInputEndpoint")
    public static class AnnotatedServerTextEndpoint {

        @OnOpen
        public void onOpen(Session session) throws IOException {
            session.getBasicRemote().sendText("A", false);
        }

        @OnMessage
        public void OnMessage(PongMessage message, Session session) throws IOException, InterruptedException {
            // give the client endpoint some time to get blocked
            Thread.sleep(100);
            session.close();
        }
    }

    @ServerEndpoint("/blockingBinaryInputEndpoint")
    public static class AnnotatedServerBinaryEndpoint {

        @OnOpen
        public void onOpen(Session session) throws IOException {
            session.getBasicRemote().sendBinary(ByteBuffer.wrap("A".getBytes()), false);
        }

        @OnMessage
        public void OnMessage(PongMessage message, Session session) throws IOException, InterruptedException {
            // give the client endpoint some time to get blocked
            Thread.sleep(100);
            session.close();
        }
    }

    @ClientEndpoint
    public static class CloseByServerEndpoint {

        /**
         * Latch waiting for the blocked thread to be released.
         */
        private final CountDownLatch threadReleasedLatch;

        public CloseByServerEndpoint(CountDownLatch threadReleasedLatch) {
            this.threadReleasedLatch = threadReleasedLatch;
        }

        @OnMessage
        public void onMessage(Session session, Reader reader) throws IOException {
            reader.read();
            //server will close the session upon receiving a pong
            session.getAsyncRemote().sendPong(null);
            try {
                reader.read();
            } catch (IOException e) {
                threadReleasedLatch.countDown();
            }
        }

        @OnMessage
        public void onMessage(Session session, InputStream stream) throws IOException {
            stream.read();
            //server will close the session upon receiving a pong
            session.getAsyncRemote().sendPong(null);
            try {
                stream.read();
            } catch (Exception e) {
                threadReleasedLatch.countDown();
            }
        }
    }

    @ClientEndpoint
    public static class CloseByClientEndpoint {

        /**
         * Latch waiting for the blocked thread to be released.
         */
        private final CountDownLatch threadReleasedLatch;
        /**
         * Latch waiting for a message from the server.
         */
        private final CountDownLatch messageLatch;

        public CloseByClientEndpoint(CountDownLatch threadReleasedLatch, CountDownLatch messageLatch) {
            this.threadReleasedLatch = threadReleasedLatch;
            this.messageLatch = messageLatch;
        }

        @OnMessage
        public void onMessage(Session session, Reader reader) throws IOException {
            reader.read();
            messageLatch.countDown();
            try {
                reader.read();
            } catch (IOException e) {
                threadReleasedLatch.countDown();
            }
        }

        @OnMessage
        public void onMessage(Session session, InputStream stream) throws IOException {
            stream.read();
            messageLatch.countDown();
            try {
                stream.read();
            } catch (IOException e) {
                threadReleasedLatch.countDown();
            }
        }
    }

    @ClientEndpoint
    public static class ReadFromClosedSessionEndpoint {

        /**
         * Latch waiting for the blocked thread to be released.
         */
        private final CountDownLatch threadReleasedLatch;

        public ReadFromClosedSessionEndpoint(CountDownLatch threadReleasedLatch) {
            this.threadReleasedLatch = threadReleasedLatch;
        }

        @OnMessage
        public void onMessage(Session session, Reader reader) throws IOException {
            reader.read();
            session.close();
            try {
                reader.read();
            } catch (IOException e) {
                threadReleasedLatch.countDown();
            }
        }

        @OnMessage
        public void onMessage(Session session, InputStream stream) throws IOException {
            stream.read();
            session.close();
            try {
                stream.read();
            } catch (IOException e) {
                threadReleasedLatch.countDown();
            }
        }
    }
}
