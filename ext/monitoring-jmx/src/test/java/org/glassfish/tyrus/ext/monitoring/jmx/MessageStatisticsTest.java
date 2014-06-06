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
package org.glassfish.tyrus.ext.monitoring.jmx;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpoint;
import javax.websocket.OnMessage;
import javax.websocket.PongMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.core.monitoring.ApplicationEventListener;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests that statistics about sent and received messages are collected correctly on application level.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class MessageStatisticsTest extends TestContainer {

    @ServerEndpoint("/jmxStatisticsServerEndpoint1")
    public static class ServerEndpoint1 {

        @OnMessage
        public void messageReceived(Session session, String text) throws IOException {
            session.getBasicRemote().sendText(text);
            session.getBasicRemote().sendText(text);
        }

        @OnMessage
        public void messageReceived(Session session, ByteBuffer data) throws IOException {
            session.getBasicRemote().sendBinary(data);
            session.getBasicRemote().sendBinary(data);
        }

        @OnMessage
        public void messageReceived(Session session, PongMessage pong) throws IOException {
            session.getBasicRemote().sendPong(pong.getApplicationData());
            session.getBasicRemote().sendPong(pong.getApplicationData());
        }

    }

    @ServerEndpoint("/jmxStatisticsServerEndpoint2")
    public static class ServerEndpoint2 {

        @OnMessage
        public void messageReceived(Session session, String text) throws IOException {
            session.getBasicRemote().sendText(text);
            session.getBasicRemote().sendText(text);
        }

        @OnMessage
        public void messageReceived(Session session, ByteBuffer data) throws IOException {
            session.getBasicRemote().sendBinary(data);
            session.getBasicRemote().sendBinary(data);
        }

        @OnMessage
        public void messageReceived(Session session, PongMessage pong) throws IOException {
            session.getBasicRemote().sendPong(pong.getApplicationData());
            session.getBasicRemote().sendPong(pong.getApplicationData());
        }
    }

    @ServerEndpoint("/jmxStatisticsServerEndpoint3")
    public static class ServerEndpoint3 {

        @OnMessage
        public void messageReceived(Session session, String text) throws IOException {
            session.getBasicRemote().sendText(text);
            session.getBasicRemote().sendText(text);
        }

        @OnMessage
        public void messageReceived(Session session, ByteBuffer data) throws IOException {
            session.getBasicRemote().sendBinary(data);
            session.getBasicRemote().sendBinary(data);
        }

        @OnMessage
        public void messageReceived(Session session, PongMessage pong) throws IOException {
            session.getBasicRemote().sendPong(pong.getApplicationData());
            session.getBasicRemote().sendPong(pong.getApplicationData());
        }
    }

    @ClientEndpoint
    public static class AnnotatedClientEndpoint {

        @OnMessage
        public void messageReceived(Session session, String text) {
        }

        @OnMessage
        public void messageReceived(Session session, ByteBuffer data) {
        }

        @OnMessage
        public void messageReceived(Session session, PongMessage pong) {
        }
    }

    @Test
    public void monitoringOnSessionLevelTest() {
        test(true);
    }

    @Test
    public void monitoringOnEndpointLevelTest() {
        test(false);
    }

    public void test(boolean monitorOnSessionLevel) {
        Server server1 = null;
        Server server2 = null;
        try {

            /*
             Latches used to ensure all messages were sent or received by the servers, before the statistics are checked.
             For every pong, text and binary message sent, 2 are received. For every ping message sent, 1 pong is received.
             The expected number of sent messages is 2 * 6 + 2 * 6 + 2 * 6 + 6 = 42
             */
            CountDownLatch messageSentLatch = new CountDownLatch(42);
            CountDownLatch messageReceivedLatch = new CountDownLatch(24);

            Map<String, Object> server1Properties = new HashMap<String, Object>();
            ApplicationMonitor applicationMonitor;
            if (monitorOnSessionLevel) {
                applicationMonitor = new SessionAwareApplicationMonitor();
            } else {
                applicationMonitor = new SessionlessApplicationMonitor();
            }
            ApplicationEventListener application1EventListener = new TestApplicationEventListener(applicationMonitor, null, null, messageSentLatch, messageReceivedLatch, null);
            server1Properties.put(ApplicationEventListener.APPLICATION_EVENT_LISTENER, application1EventListener);
            server1 = new Server("localhost", 8025, "/jmxTestApp", server1Properties, ServerEndpoint1.class, ServerEndpoint2.class);
            server1.start();

            Map<String, Object> server2Properties = new HashMap<String, Object>();
            ApplicationEventListener application2EventListener = new TestApplicationEventListener(new ApplicationMonitor(monitorOnSessionLevel), null, null, messageSentLatch, messageReceivedLatch, null);
            server2Properties.put(ApplicationEventListener.APPLICATION_EVENT_LISTENER, application2EventListener);
            server2 = new Server("localhost", 8026, "/jmxTestApp2", server2Properties, ServerEndpoint2.class, ServerEndpoint3.class);
            server2.start();

            ClientManager client = createClient();
            Session session1 = client.connectToServer(AnnotatedClientEndpoint.class, new URI("ws", null, "localhost", 8025, "/jmxTestApp/jmxStatisticsServerEndpoint1", null, null));
            Session session2 = client.connectToServer(AnnotatedClientEndpoint.class, new URI("ws", null, "localhost", 8025, "/jmxTestApp/jmxStatisticsServerEndpoint2", null, null));
            Session session3 = client.connectToServer(AnnotatedClientEndpoint.class, new URI("ws", null, "localhost", 8025, "/jmxTestApp/jmxStatisticsServerEndpoint2", null, null));
            Session session4 = client.connectToServer(AnnotatedClientEndpoint.class, new URI("ws", null, "localhost", 8026, "/jmxTestApp2/jmxStatisticsServerEndpoint2", null, null));
            Session session5 = client.connectToServer(AnnotatedClientEndpoint.class, new URI("ws", null, "localhost", 8026, "/jmxTestApp2/jmxStatisticsServerEndpoint3", null, null));
            Session session6 = client.connectToServer(AnnotatedClientEndpoint.class, new URI("ws", null, "localhost", 8026, "/jmxTestApp2/jmxStatisticsServerEndpoint3", null, null));

            session1.getBasicRemote().sendText(getText(1));
            session2.getBasicRemote().sendText(getText(2));
            session3.getBasicRemote().sendText(getText(3));
            session4.getBasicRemote().sendText(getText(4));
            session5.getBasicRemote().sendText(getText(5));
            session6.getBasicRemote().sendText(getText(6));

            session1.getBasicRemote().sendBinary(getBytes(2));
            session2.getBasicRemote().sendBinary(getBytes(3));
            session3.getBasicRemote().sendBinary(getBytes(4));
            session4.getBasicRemote().sendBinary(getBytes(5));
            session5.getBasicRemote().sendBinary(getBytes(6));
            session6.getBasicRemote().sendBinary(getBytes(7));

            session1.getBasicRemote().sendPing(getBytes(3));
            session2.getBasicRemote().sendPing(getBytes(4));
            session3.getBasicRemote().sendPing(getBytes(5));
            session4.getBasicRemote().sendPing(getBytes(6));
            session5.getBasicRemote().sendPing(getBytes(7));
            session6.getBasicRemote().sendPing(getBytes(8));

            session1.getBasicRemote().sendPong(getBytes(4));
            session2.getBasicRemote().sendPong(getBytes(5));
            session3.getBasicRemote().sendPong(getBytes(6));
            session4.getBasicRemote().sendPong(getBytes(7));
            session5.getBasicRemote().sendPong(getBytes(8));
            session6.getBasicRemote().sendPong(getBytes(9));

            assertTrue(messageSentLatch.await(1, TimeUnit.SECONDS));
            assertTrue(messageReceivedLatch.await(1, TimeUnit.SECONDS));

            checkApplicationStatisticsCollectedCorrectly("/jmxTestApp", 3, 21, 72, 12, 42, 6, 1, 6, 1);
            checkApplicationStatisticsCollectedCorrectly("/jmxTestApp2", 3, 21, 135, 12, 78, 9, 4, 9, 4);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            if (server1 != null) {
                server1.stop();
            }

            if (server2 != null) {
                server2.stop();
            }
        }
    }

    private void checkApplicationStatisticsCollectedCorrectly(String applicationName, int openSessionsCount, int sentMessagesCount, int sentMessagesSize,
                                                              int receivedMessagesCount, int receivedMessagesSize, int maxSentMessageSize,
                                                              int minSentMessageSize, int maxReceivedMessageSize, int minReceivedMessageSize) {
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        String fullApplicationMxBeanName = "org.glassfish.tyrus:type=" + applicationName;
        try {
            ApplicationMXBean applicationBean = JMX.newMXBeanProxy(mBeanServer, new ObjectName(fullApplicationMxBeanName), ApplicationMXBean.class);

            assertEquals(openSessionsCount, applicationBean.getOpenSessionsCount());
            assertEquals(openSessionsCount, applicationBean.getMaximalOpenSessionsCount());
            assertEquals(sentMessagesCount, applicationBean.getSentMessagesCount());
            assertEquals(sentMessagesSize / sentMessagesCount, applicationBean.getAverageSentMessageSize());
            assertEquals(receivedMessagesCount, applicationBean.getReceivedMessagesCount());
            assertEquals(receivedMessagesSize / receivedMessagesCount, applicationBean.getAverageReceivedMessageSize());
            assertEquals(maxSentMessageSize, applicationBean.getMaximalSentMessageSize());
            assertEquals(minSentMessageSize, applicationBean.getMinimalSentMessageSize());
            assertEquals(maxReceivedMessageSize, applicationBean.getMaximalReceivedMessageSize());
            assertEquals(minReceivedMessageSize, applicationBean.getMinimalReceivedMessageSize());
        } catch (Exception e) {
            // do nothing false will be returned
            e.printStackTrace();
            fail();
        }
    }

    private String getText(int length) {
        String s = "AAAAAAAAA";
        return s.substring(0, length);
    }

    private ByteBuffer getBytes(int length) {
        return ByteBuffer.wrap(getText(length).getBytes());
    }
}
