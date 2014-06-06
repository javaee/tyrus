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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpoint;
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

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

/**
 * Tests that Session related MXBeans are cleaned after session is closed.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class SessionCleanUpTest extends TestContainer {

    @ServerEndpoint("/serverEndpoint")
    public static class AnnotatedServerEndpoint {

    }

    @ClientEndpoint
    public static class AnnotatedClientEndpoint {

    }

    @Test
    public void test() {
        Server server = null;
        try {
            setContextPath("/serializationTestApp");

            CountDownLatch sessionOpenedLatch = new CountDownLatch(1);
            CountDownLatch sessionClosedLatch = new CountDownLatch(1);
            ApplicationEventListener applicationEventListener = new TestApplicationEventListener(new SessionAwareApplicationMonitor(), sessionOpenedLatch, sessionClosedLatch, null, null, null);
            getServerProperties().put(ApplicationEventListener.APPLICATION_EVENT_LISTENER, applicationEventListener);
            server = startServer(AnnotatedServerEndpoint.class);

            ClientManager client = createClient();
            Session session = client.connectToServer(AnnotatedClientEndpoint.class, getURI(AnnotatedServerEndpoint.class));
            assertTrue(sessionOpenedLatch.await(1, TimeUnit.SECONDS));

            String applicationMxBeanName = "org.glassfish.tyrus:type=/serializationTestApp";
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            ApplicationMXBean applicationMXBean = JMX.newMXBeanProxy(mBeanServer, new ObjectName(applicationMxBeanName), ApplicationMXBean.class);

            assertEquals(1, applicationMXBean.getEndpointMXBeans().size());
            EndpointMXBean endpointMXBean = applicationMXBean.getEndpointMXBeans().get(0);

            assertEquals(1, endpointMXBean.getSessionMXBeans().size());
            SessionMXBean sessionMXBean = endpointMXBean.getSessionMXBeans().get(0);

            Set<String> registeredMXBeanNames = getRegisteredMXBeanNames();
            String sessionMXBeanName = "org.glassfish.tyrus:type=/serializationTestApp,endpoints=endpoints,endpoint=/serverEndpoint,sessions=sessions,session=" + sessionMXBean.getSessionId();
            String sessionMessageTypesNameBase = sessionMXBeanName + ",message_statistics=message_statistics,message_type=";
            assertTrue(registeredMXBeanNames.contains(sessionMXBeanName));
            assertTrue(registeredMXBeanNames.contains(sessionMessageTypesNameBase + "text"));
            assertTrue(registeredMXBeanNames.contains(sessionMessageTypesNameBase + "binary"));
            assertTrue(registeredMXBeanNames.contains(sessionMessageTypesNameBase + "control"));

            session.close();
            assertTrue(sessionClosedLatch.await(1, TimeUnit.SECONDS));

            assertTrue(endpointMXBean.getSessionMXBeans().isEmpty());
            registeredMXBeanNames = getRegisteredMXBeanNames();
            assertFalse(registeredMXBeanNames.contains(sessionMXBeanName));
            assertFalse(registeredMXBeanNames.contains(sessionMessageTypesNameBase + "text"));
            assertFalse(registeredMXBeanNames.contains(sessionMessageTypesNameBase + "binary"));
            assertFalse(registeredMXBeanNames.contains(sessionMessageTypesNameBase + "control"));
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
        }
    }

    private Set<String> getRegisteredMXBeanNames() {
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        Set<String> result = new HashSet<String>();
        for (ObjectName name : mBeanServer.queryNames(null, null)) {
            result.add(name.toString());
        }
        return result;
    }
}
