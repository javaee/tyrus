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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.glassfish.tyrus.core.monitoring.ApplicationEventListener;
import org.glassfish.tyrus.core.monitoring.EndpointEventListener;

/**
 * MBean used for exposing application-level properties.
 * For monitoring in Grizzly server an instance should be passed
 * to the server in server properties.
 * <p/>
 * <pre>
 *     serverProperties.put(ApplicationEventListener.APPLICATION_EVENT_LISTENER, new ApplicationJmx());
 * </pre>
 * <p/>
 * For use in servlet container a class name should be passed as context parameter in web.xml.
 * <p/>
 * <pre>
 *      {@code
 *
 *      <context-param>
 *         <param-name>org.glassfish.tyrus.core.monitoring.ApplicationEventListener</param-name>
 *         <param-value>org.glassfish.tyrus.ext.monitoring.jmx.ApplicationJmx</param-value>
 *      </context-param>
 *      }
 *  <pre/>
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class ApplicationJmx implements ApplicationMXBean, ApplicationEventListener {

    private final Map<String, MonitoredEndpointProperties> endpoints = new ConcurrentHashMap<String, MonitoredEndpointProperties>();
    private String applicationName;

    @Override
    public List<MonitoredEndpointProperties> getEndpoints() {
        return new ArrayList<MonitoredEndpointProperties>(endpoints.values());
    }

    @Override
    public List<String> getEndpointPaths() {
        return new ArrayList<String>(endpoints.keySet());
    }

    @Override
    public void onApplicationInitialized(String applicationName) {
        this.applicationName = applicationName;
        MBeanPublisher.registerApplicationMBean(this, applicationName);
    }

    @Override
    public void onApplicationDestroyed() {
        MBeanPublisher.unregisterApplicationMBean(applicationName);
    }

    @Override
    public EndpointEventListener onEndpointRegistered(Class<?> endpointClass, String endpointPath) {
        endpoints.put(endpointPath, new MonitoredEndpointProperties(endpointClass.getName(), endpointPath));
        return EndpointEventListener.NO_OP;
    }

    @Override
    public void onEndpointUnregistered(String endpointPath) {
        endpoints.remove(endpointPath);
    }
}
