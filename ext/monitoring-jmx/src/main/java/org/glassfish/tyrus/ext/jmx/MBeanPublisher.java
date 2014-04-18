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
package org.glassfish.tyrus.ext.jmx;

import java.lang.management.ManagementFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;


/**
 * Publishes Tyrus MBeans, so that they are accessible from
 * MBean clients e.g. jConsole.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
class MBeanPublisher {

    private static final Logger LOGGER = Logger.getLogger(MBeanPublisher.class.getName());
    private static final String NAME_BASE = "org.glassfish.tyrus:type=application,appName=";

    /**
     * Register an MBean in MBeanServer.
     *
     * @param mBean MBean monitoring application events.
     * @param applicationName name under which the MBean will be registered.
     */
    static void registerApplicationMBean(ApplicationMXBean mBean, String applicationName) {
        final MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        final String name = NAME_BASE + applicationName;
        try {
            synchronized (MBeanPublisher.class) {
                final ObjectName objectName = new ObjectName(name);
                if (mBeanServer.isRegistered(objectName)) {
                    LOGGER.log(Level.WARNING, "MBean with name " + " already registered");
                    mBeanServer.unregisterMBean(objectName);
                }
                mBeanServer.registerMBean(mBean, objectName);
            }
        } catch (JMException e) {
            LOGGER.log(Level.WARNING, "Could not register MBean with name " + name, e);
        }
    }

    /**
     * Unregister an MBean in MBeanServer.
     *
     * @param applicationName name under which the MBean is registered.
     */
    static void unregisterApplicationMBean(String applicationName) {
        final MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        final String name = NAME_BASE + applicationName;
        try {
            synchronized (MBeanPublisher.class) {
                final ObjectName objectName = new ObjectName(name);
                if (!mBeanServer.isRegistered(objectName)) {
                    LOGGER.log(Level.WARNING, "MBean with name " + " is not registered at MBean Server");
                }
                mBeanServer.unregisterMBean(objectName);
            }
        } catch (JMException e) {
            LOGGER.log(Level.WARNING, "Could not unregister MBean with name " + name, e);
        }
    }

}
