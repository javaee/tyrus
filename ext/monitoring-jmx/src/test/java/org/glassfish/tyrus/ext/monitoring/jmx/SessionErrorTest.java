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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpoint;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
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

import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

/**
 * Tests that error statistics collected at session level are accessible.
 * Complementary test to {@link org.glassfish.tyrus.ext.monitoring.jmx.ErrorStatisticsTest}
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class SessionErrorTest extends TestContainer {

    @ServerEndpoint("/annotatedServerEndpoint")
    public static class AnnotatedServerEndpoint {

        @OnMessage
        public void onTextMessage(String message, Session session) throws Exception {
            throw new Exception();
        }

        @OnError
        public void onError(Throwable t) {
        }
    }

    @ClientEndpoint
    public static class AnnotatedClientEndpoint {
    }

    @Test
    public void test() {
        Server server = null;
        try {
            setContextPath("/errorInSessionTestApp");

            CountDownLatch errorCountDownLatch = new CountDownLatch(1);

            ApplicationEventListener applicationEventListener = new TestApplicationEventListener(new SessionAwareApplicationMonitor(), null, null, null, null, errorCountDownLatch);
            getServerProperties().put(ApplicationEventListener.APPLICATION_EVENT_LISTENER, applicationEventListener);
            server = startServer(AnnotatedServerEndpoint.class);

            ClientManager client = createClient();
            Session session = client.connectToServer(AnnotatedClientEndpoint.class, getURI(AnnotatedServerEndpoint.class));
            session.getBasicRemote().sendText("Hello");

            assertTrue(errorCountDownLatch.await(1, TimeUnit.SECONDS));

            String applicationMXBeanName = "org.glassfish.tyrus:type=/errorInSessionTestApp";
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            ApplicationMXBean applicationMXBean = JMX.newMXBeanProxy(mBeanServer, new ObjectName(applicationMXBeanName), ApplicationMXBean.class);

            List<EndpointMXBean> endpointMXBeans = applicationMXBean.getEndpointMXBeans();
            assertEquals(1, endpointMXBeans.size());
            List<SessionMXBean> sessionMXBeans = endpointMXBeans.get(0).getSessionMXBeans();
            assertEquals(1, sessionMXBeans.size());

            SessionMXBean sessionMXBean = sessionMXBeans.get(0);
            assertEquals(1, sessionMXBean.getErrorCounts().size());

        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
        }
    }
}
