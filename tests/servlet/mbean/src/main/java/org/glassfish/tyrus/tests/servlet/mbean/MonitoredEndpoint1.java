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
package org.glassfish.tyrus.tests.servlet.mbean;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.List;

import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.glassfish.tyrus.ext.monitoring.jmx.ApplicationMXBean;
import org.glassfish.tyrus.ext.monitoring.jmx.MonitoredEndpointProperties;

/**
 * Endpoint that returns OK in @#onOpen if @MonitoredEndpoint1 and @ MonitoredEndpoint2 are registered in
 * application MBean.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
@ServerEndpoint("/monitoredEndpoint1")
public class MonitoredEndpoint1 {

    @OnOpen
    public void onOpen(Session session) throws IOException {
        if (isEndpointRegistered("/mbean-test", new MonitoredEndpointProperties(MonitoredEndpoint1.class.getName(), "/monitoredEndpoint1"))
                && isEndpointRegistered("/mbean-test", new MonitoredEndpointProperties(MonitoredEndpoint2.class.getName(), "/monitoredEndpoint2"))) {
            session.getBasicRemote().sendText("OK");
            return;
        }
        session.getBasicRemote().sendText("NOK");
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
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
