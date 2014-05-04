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
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.glassfish.tyrus.core.monitoring.ApplicationEventListener;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests that endpoint path and class name are accessible through Endpoint MXBean for both programmatic and annotated endpoint.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class EndpointPropertiesTest extends TestContainer {

    @ServerEndpoint("/annotatedEndpoint")
    public static class AnnotatedServerEndpoint {

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
        setContextPath("/jmxSessionTestApp");
        Server server = null;
        try {
            getServerProperties().put(ApplicationEventListener.APPLICATION_EVENT_LISTENER, new ApplicationMonitor(monitorOnSessionLevel));
            server = startServer(ApplicationConfig.class);
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();

            String fullAnnotatedEndpointBeanName = "org.glassfish.tyrus:type=/jmxSessionTestApp,endpoints=endpoints,endpoint=/annotatedEndpoint";
            EndpointMXBean annotatedEndpointBean = JMX.newMXBeanProxy(mBeanServer, new ObjectName(fullAnnotatedEndpointBeanName), EndpointMXBean.class);
            assertEquals(AnnotatedServerEndpoint.class.getName(), annotatedEndpointBean.getEndpointClassName());
            assertEquals("/annotatedEndpoint", annotatedEndpointBean.getEndpointPath());

            String fullProgrammaticEndpointBeanName = "org.glassfish.tyrus:type=/jmxSessionTestApp,endpoints=endpoints,endpoint=/programmaticEndpoint";
            EndpointMXBean programmaticEndpointBean = JMX.newMXBeanProxy(mBeanServer, new ObjectName(fullProgrammaticEndpointBeanName), EndpointMXBean.class);
            assertEquals(ApplicationConfig.ProgrammaticServerEndpoint.class.getName(), programmaticEndpointBean.getEndpointClassName());
            assertEquals("/programmaticEndpoint", programmaticEndpointBean.getEndpointPath());
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
        }
    }

    public static class ApplicationConfig implements ServerApplicationConfig {

        @Override
        public Set<ServerEndpointConfig> getEndpointConfigs(Set<Class<? extends Endpoint>> endpointClasses) {
            return new HashSet<ServerEndpointConfig>() {{
                add(ServerEndpointConfig.Builder.create(ProgrammaticServerEndpoint.class, "/programmaticEndpoint").build());
            }};
        }

        @Override
        public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> scanned) {
            return new HashSet<Class<?>>(Collections.singleton(AnnotatedServerEndpoint.class));
        }

        public static class ProgrammaticServerEndpoint extends Endpoint {
            @Override
            public void onOpen(final Session session, final EndpointConfig EndpointConfig) {

            }
        }
    }
}
