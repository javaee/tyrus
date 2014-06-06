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

import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.util.List;
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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

/**
 * Tests that MXBean tree can be traversed - all MXBeans can be accessed from {@link org.glassfish.tyrus.ext.monitoring.jmx.ApplicationMXBean}
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class MBeanTreeTest extends TestContainer {

    @ServerEndpoint("/serverEndpoint")
    public static class AnnotatedServerEndpoint {

        @OnMessage
        public void onMessage(String message, Session session) {
        }

        @OnMessage
        public void onMessage(ByteBuffer data, Session session) {
        }

        @OnMessage
        public void onMessage(PongMessage pong, Session session) {
        }
    }

    @ClientEndpoint
    public static class AnnotatedClientEndpoint {
    }

    @Test
    public void monitoringOnSessionLevelTest() {
        test(true);
    }

    @Test
    public void monitoringOnEndpointLevelTest() {
        test(false);
    }

    private void test(boolean monitorOnSessionLevel) {
        Server server = null;
        try {
            setContextPath("/mBeanTreeTestApp");

            ApplicationMonitor applicationMonitor;
            if (monitorOnSessionLevel) {
                applicationMonitor = new SessionAwareApplicationMonitor();
            } else {
                applicationMonitor = new SessionlessApplicationMonitor();
            }

            CountDownLatch receivedMessagesLatch = new CountDownLatch(6);

            ApplicationEventListener applicationEventListener = new TestApplicationEventListener(applicationMonitor, null, null, null, receivedMessagesLatch, null);
            getServerProperties().put(ApplicationEventListener.APPLICATION_EVENT_LISTENER, applicationEventListener);
            server = startServer(AnnotatedServerEndpoint.class);

            ClientManager client = createClient();
            Session session = client.connectToServer(AnnotatedClientEndpoint.class, getURI(AnnotatedServerEndpoint.class));

            // send different number of messages of each type so that it can be verified that a correct MXBean is accessed
            session.getBasicRemote().sendBinary(ByteBuffer.wrap("Hello".getBytes()));
            session.getBasicRemote().sendText("Hello 1");
            session.getBasicRemote().sendText("Hello 2");
            session.getBasicRemote().sendPong(null);
            session.getBasicRemote().sendPong(null);
            session.getBasicRemote().sendPong(null);

            assertTrue(receivedMessagesLatch.await(1, TimeUnit.SECONDS));

            String applicationMxBeanName = "org.glassfish.tyrus:type=/mBeanTreeTestApp";
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            ApplicationMXBean applicationMXBean = JMX.newMXBeanProxy(mBeanServer, new ObjectName(applicationMxBeanName), ApplicationMXBean.class);

            assertEquals(1, applicationMXBean.getBinaryMessageStatisticsMXBean().getReceivedMessagesCount());
            assertEquals(2, applicationMXBean.getTextMessageStatisticsMXBean().getReceivedMessagesCount());
            assertEquals(3, applicationMXBean.getControlMessageStatisticsMXBean().getReceivedMessagesCount());

            List<EndpointMXBean> endpointMXBeans = applicationMXBean.getEndpointMXBeans();
            assertEquals(1, endpointMXBeans.size());

            EndpointMXBean endpointMXBean = endpointMXBeans.get(0);

            assertEquals(1, endpointMXBean.getBinaryMessageStatisticsMXBean().getReceivedMessagesCount());
            assertEquals(2, endpointMXBean.getTextMessageStatisticsMXBean().getReceivedMessagesCount());
            assertEquals(3, endpointMXBean.getControlMessageStatisticsMXBean().getReceivedMessagesCount());

            List<SessionMXBean> sessionMXBeans = endpointMXBean.getSessionMXBeans();
            if (!monitorOnSessionLevel) {
                assertTrue(sessionMXBeans.isEmpty());
                return;
            }

            assertEquals(1, sessionMXBeans.size());
            BaseMXBean sessionMXBean = sessionMXBeans.get(0);

            assertEquals(1, sessionMXBean.getBinaryMessageStatisticsMXBean().getReceivedMessagesCount());
            assertEquals(2, sessionMXBean.getTextMessageStatisticsMXBean().getReceivedMessagesCount());
            assertEquals(3, sessionMXBean.getControlMessageStatisticsMXBean().getReceivedMessagesCount());

        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
        }
    }
}
