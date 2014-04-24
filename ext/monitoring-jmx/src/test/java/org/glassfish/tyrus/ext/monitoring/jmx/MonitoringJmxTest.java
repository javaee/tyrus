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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.websocket.DeploymentException;
import javax.websocket.server.ServerEndpoint;

import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.glassfish.tyrus.core.monitoring.ApplicationEventListener;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests that MXBeans exposing information about registered endpoints
 * get registered and contain information about deployed endpoint classes
 * and paths.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class MonitoringJmxTest extends TestContainer {

    @ServerEndpoint("/jmxServerEndpoint1")
    public static class AnnotatedServerEndpoint1 {
    }

    @ServerEndpoint("/jmxServerEndpoint2")
    public static class AnnotatedServerEndpoint2 {
    }

    @ServerEndpoint("/jmxServerEndpoint3")
    public static class AnnotatedServerEndpoint3 {
    }

    @Test
    public void testJmx() {
        Server server1 = null;
        Server server2 = null;
        try {
            Map<String, Object> server1Properties = new HashMap<String, Object>();
            ApplicationEventListener application1EventListener = new ApplicationJmx();
            server1Properties.put(ApplicationEventListener.APPLICATION_EVENT_LISTENER, application1EventListener);
            server1 = new Server("localhost", 8025, "/jmxTestApp", server1Properties, AnnotatedServerEndpoint1.class, AnnotatedServerEndpoint2.class);
            server1.start();

            Map<String, Object> server2Properties = new HashMap<String, Object>();
            server2Properties.put(ApplicationEventListener.APPLICATION_EVENT_LISTENER, new ApplicationJmx());
            server2 = new Server("localhost", 8026, "/jmxTestApp2", server2Properties, AnnotatedServerEndpoint2.class, AnnotatedServerEndpoint3.class);
            server2.start();

            // test all endpoints are registered
            assertTrue(isEndpointRegistered("/jmxTestApp", new MonitoredEndpointProperties(AnnotatedServerEndpoint1.class.getName(), "/jmxServerEndpoint1")));
            assertTrue(isEndpointRegistered("/jmxTestApp", new MonitoredEndpointProperties(AnnotatedServerEndpoint2.class.getName(), "/jmxServerEndpoint2")));
            assertTrue(isEndpointRegistered("/jmxTestApp2", new MonitoredEndpointProperties(AnnotatedServerEndpoint2.class.getName(), "/jmxServerEndpoint2")));
            assertTrue(isEndpointRegistered("/jmxTestApp2", new MonitoredEndpointProperties(AnnotatedServerEndpoint3.class.getName(), "/jmxServerEndpoint3")));

            // test endpoint gets unregistered
            application1EventListener.onEndpointUnregistered("/jmxServerEndpoint2");
            assertTrue(isEndpointRegistered("/jmxTestApp", new MonitoredEndpointProperties(AnnotatedServerEndpoint1.class.getName(), "/jmxServerEndpoint1")));
            assertFalse(isEndpointRegistered("/jmxTestApp", new MonitoredEndpointProperties(AnnotatedServerEndpoint2.class.getName(), "/jmxServerEndpoint2")));
            assertTrue(isEndpointRegistered("/jmxTestApp2", new MonitoredEndpointProperties(AnnotatedServerEndpoint2.class.getName(), "/jmxServerEndpoint2")));
            assertTrue(isEndpointRegistered("/jmxTestApp2", new MonitoredEndpointProperties(AnnotatedServerEndpoint3.class.getName(), "/jmxServerEndpoint3")));

            // test jmx of one applications is terminated
            server2.stop();
            assertTrue(isEndpointRegistered("/jmxTestApp", new MonitoredEndpointProperties(AnnotatedServerEndpoint1.class.getName(), "/jmxServerEndpoint1")));
            assertFalse(isEndpointRegistered("/jmxTestApp", new MonitoredEndpointProperties(AnnotatedServerEndpoint2.class.getName(), "/jmxServerEndpoint2")));
            assertFalse(isEndpointRegistered("/jmxTestApp2", new MonitoredEndpointProperties(AnnotatedServerEndpoint2.class.getName(), "/jmxServerEndpoint2")));
            assertFalse(isEndpointRegistered("/jmxTestApp2", new MonitoredEndpointProperties(AnnotatedServerEndpoint3.class.getName(), "/jmxServerEndpoint3")));

        } catch (DeploymentException e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server1);
            stopServer(server2);
        }
    }

    private boolean isEndpointRegistered(String applicationName, MonitoredEndpointProperties endpoint) {
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        String fullMxBeanName = "org.glassfish.tyrus:type=application,appName=" + applicationName;
        ApplicationMXBean proxy;
        try {
            proxy = JMX.newMXBeanProxy(mBeanServer, new ObjectName(fullMxBeanName), ApplicationMXBean.class);
            List<MonitoredEndpointProperties> registeredEndpoints = proxy.getEndpoints();
            for (MonitoredEndpointProperties registeredEndpoint : registeredEndpoints) {
                if (registeredEndpoint.getEndpointPath().equals(endpoint.getEndpointPath()) && registeredEndpoint.getEndpointClassName().equals(endpoint.getEndpointClassName())) {
                    return true;
                }
            }
        } catch (MalformedObjectNameException e) {
            System.out.print("Could not retrieve MXBean for application " + applicationName + ": " + e.getMessage());
        } catch (Exception e) {
            // do nothing false will be returned
        }
        return false;
    }
}
