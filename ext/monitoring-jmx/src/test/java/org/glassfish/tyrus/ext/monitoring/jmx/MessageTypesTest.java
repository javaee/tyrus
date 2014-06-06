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
import java.nio.ByteBuffer;
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
 * Tests that statistics about different types of messages are collected correctly.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class MessageTypesTest extends TestContainer {

    @ServerEndpoint("/jmxServerEndpoint")
    public static class AnnotatedServerEndpoint {

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
        Server server = null;
        try {
            /*
             Latches used to ensure all messages were sent or received by the server, before the statistics are checked.
             For every pong, text and binary message sent, 2 are received. For every ping message sent, 1 pong is received.
             */
            CountDownLatch messageSentLatch = new CountDownLatch(17);
            CountDownLatch messageReceivedLatch = new CountDownLatch(11);

            setContextPath("/jmxTestApp");

            ApplicationMonitor applicationMonitor;
            if (monitorOnSessionLevel) {
                applicationMonitor = new SessionAwareApplicationMonitor();
            } else {
                applicationMonitor = new SessionlessApplicationMonitor();
            }
            ApplicationEventListener applicationEventListener = new TestApplicationEventListener(applicationMonitor, null, null, messageSentLatch, messageReceivedLatch, null);
            getServerProperties().put(ApplicationEventListener.APPLICATION_EVENT_LISTENER, applicationEventListener);
            server = startServer(AnnotatedServerEndpoint.class);

            ClientManager client = createClient();
            Session session = client.connectToServer(AnnotatedClientEndpoint.class, getURI(AnnotatedServerEndpoint.class));

            session.getBasicRemote().sendText("some text");
            session.getBasicRemote().sendText("some text", false);
            session.getBasicRemote().sendText("some text", true);
            session.getAsyncRemote().sendText("some text").get();

            session.getBasicRemote().sendBinary(ByteBuffer.wrap("some text".getBytes()));
            session.getBasicRemote().sendBinary(ByteBuffer.wrap("some text".getBytes()));
            session.getBasicRemote().sendBinary(ByteBuffer.wrap("some text".getBytes()), false);
            session.getBasicRemote().sendBinary(ByteBuffer.wrap("some text".getBytes()), true);
            session.getAsyncRemote().sendBinary(ByteBuffer.wrap("some text".getBytes())).get();

            session.getBasicRemote().sendPing(null);
            session.getBasicRemote().sendPong(null);

            assertTrue(messageSentLatch.await(1, TimeUnit.SECONDS));
            assertTrue(messageReceivedLatch.await(1, TimeUnit.SECONDS));

            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            String endpointMxBeanNameBase = "org.glassfish.tyrus:type=/jmxTestApp,endpoints=endpoints,endpoint=/jmxServerEndpoint";
            String messageStatisticsNameBase = endpointMxBeanNameBase + ",message_statistics=message_statistics";

            EndpointMXBean endpointBean = JMX.newMXBeanProxy(mBeanServer, new ObjectName(endpointMxBeanNameBase), EndpointMXBean.class);
            MessageStatisticsMXBean textBean = JMX.newMXBeanProxy(mBeanServer, new ObjectName(messageStatisticsNameBase + ",message_type=text"), MessageStatisticsMXBean.class);
            MessageStatisticsMXBean binaryBean = JMX.newMXBeanProxy(mBeanServer, new ObjectName(messageStatisticsNameBase + ",message_type=binary"), MessageStatisticsMXBean.class);
            MessageStatisticsMXBean controlBean = JMX.newMXBeanProxy(mBeanServer, new ObjectName(messageStatisticsNameBase + ",message_type=control"), MessageStatisticsMXBean.class);

            assertEquals(11, endpointBean.getReceivedMessagesCount());
            assertEquals(17, endpointBean.getSentMessagesCount());
            assertEquals(4, textBean.getReceivedMessagesCount());
            assertEquals(6, textBean.getSentMessagesCount());
            assertEquals(5, binaryBean.getReceivedMessagesCount());
            assertEquals(8, binaryBean.getSentMessagesCount());
            assertEquals(2, controlBean.getReceivedMessagesCount());
            assertEquals(3, controlBean.getSentMessagesCount());
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
        }
    }
}
