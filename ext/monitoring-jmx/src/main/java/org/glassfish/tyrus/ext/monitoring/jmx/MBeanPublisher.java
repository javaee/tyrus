/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2015 Oracle and/or its affiliates. All rights reserved.
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
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * Tyrus MXBeans publisher.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
class MBeanPublisher {

    private static final Logger LOGGER = Logger.getLogger(MBeanPublisher.class.getName());
    private static final String DOMAIN = "org.glassfish.tyrus";
    private static final String APPLICATION_NAME_BASE = DOMAIN + ":type=";
    private static final String ENDPOINT_KEY = ",endpoint=";
    private static final String SESSION_KEY = ",session=";
    private static final String MESSAGE_TYPE_KEY = ",message_type=";
    private static final String TEXT = "text";
    private static final String BINARY = "binary";
    private static final String CONTROL = "control";
    private static final String SESSIONS_DIRECTORY = ",sessions=sessions";
    private static final String ENDPOINTS_DIRECTORY = ",endpoints=endpoints";
    private static final String MESSAGE_STATISTIC_DIRECTORY = ",message_statistics=message_statistics";

    /**
     * Register {@link org.glassfish.tyrus.ext.monitoring.jmx.ApplicationMXBean} and MXBeans exposing statistics
     * about text, binary and control messages.
     *
     * @param applicationName                name of the application.
     * @param applicationMXBean              MXBean exposing application-level statistics.
     * @param textMessageStatisticsMXBean    MXBean exposing statistics about text messages sent and received by the
     *                                       application.
     * @param binaryMessageStatisticsMXBean  MXBean exposing statistics about binary messages sent and received by the
     *                                       application.
     * @param controlMessageStatisticsMXBean MXBean exposing statistics about control messages sent and received by the
     *                                       application.
     */
    static void registerApplicationMXBeans(String applicationName, ApplicationMXBean applicationMXBean,
                                           MessageStatisticsMXBean textMessageStatisticsMXBean,
                                           MessageStatisticsMXBean binaryMessageStatisticsMXBean,
                                           MessageStatisticsMXBean controlMessageStatisticsMXBean) {
        String nameBase = getApplicationBeansBaseName(applicationName);
        registerStatisticsMXBeans(nameBase, applicationMXBean, textMessageStatisticsMXBean,
                                  binaryMessageStatisticsMXBean, controlMessageStatisticsMXBean);
    }

    /**
     * Register {@link org.glassfish.tyrus.ext.monitoring.jmx.EndpointMXBean} and MXBeans exposing statistics.
     * about text, binary and control messages sent and received by the endpoint.
     *
     * @param applicationName                application name.
     * @param endpointPath                   endpoint path.
     * @param endpointMXBean                 MXBean exposing endpoint-level statistics.
     * @param textMessageStatisticsMXBean    MXBean exposing statistics about text messages sent and received by the
     *                                       endpoint.
     * @param binaryMessageStatisticsMXBean  MXBean exposing statistics about binary messages sent and received by the
     *                                       endpoint.
     * @param controlMessageStatisticsMXBean MXBean exposing statistics about control messages sent and received by the
     *                                       endpoint
     */
    static void registerEndpointMXBeans(String applicationName, String endpointPath, EndpointMXBean endpointMXBean,
                                        MessageStatisticsMXBean textMessageStatisticsMXBean,
                                        MessageStatisticsMXBean binaryMessageStatisticsMXBean,
                                        MessageStatisticsMXBean controlMessageStatisticsMXBean) {
        String nameBase = getEndpointBeansBaseName(applicationName, endpointPath);
        registerStatisticsMXBeans(nameBase, endpointMXBean, textMessageStatisticsMXBean, binaryMessageStatisticsMXBean,
                                  controlMessageStatisticsMXBean);
    }

    /**
     * Register MXBeans exposing statistics about text, binary, control and total messages sent and received by the
     * session.
     *
     * @param applicationName                application name.
     * @param endpointPath                   endpoint path.
     * @param sessionId                      session ID.
     * @param sessionMXBean                  MXBean exposing statistics about total messages sent and received by the
     *                                       session.
     * @param textMessageStatisticsMXBean    MXBean exposing statistics about text messages sent and received by the
     *                                       session.
     * @param binaryMessageStatisticsMXBean  MXBean exposing statistics about binary messages sent and received by the
     *                                       session.
     * @param controlMessageStatisticsMXBean MXBean exposing statistics about control messages sent and received by the
     *                                       session.
     */
    static void registerSessionMXBeans(String applicationName, String endpointPath, String sessionId,
                                       MessageStatisticsMXBean sessionMXBean,
                                       MessageStatisticsMXBean textMessageStatisticsMXBean,
                                       MessageStatisticsMXBean binaryMessageStatisticsMXBean,
                                       MessageStatisticsMXBean controlMessageStatisticsMXBean) {
        String baseName = getSessionBeansBaseName(applicationName, endpointPath, sessionId);
        registerStatisticsMXBeans(baseName, sessionMXBean, textMessageStatisticsMXBean, binaryMessageStatisticsMXBean,
                                  controlMessageStatisticsMXBean);
    }

    /**
     * Unregister MXBeans of the given application including all the endpoint and sessions MXBeans.
     *
     * @param applicationName application name.
     */
    static void unregisterApplicationMXBeans(String applicationName) {
        String name = getApplicationBeansBaseName(applicationName);
        unregisterMXBean(name);
    }

    /**
     * Unregister MXBeans of the given endpoint including all the sessions MXBeans.
     *
     * @param applicationName application name.
     * @param endpointPath    endpoint path.
     */
    static void unregisterEndpointMXBeans(String applicationName, String endpointPath) {
        String name = getEndpointBeansBaseName(applicationName, endpointPath);
        unregisterMXBean(name);
    }

    /**
     * Unregister an MXBeans of the given session.
     *
     * @param applicationName application name.
     * @param endpointPath    endpoint path.
     * @param sessionId       session ID.
     */
    static void unregisterSessionMXBeans(String applicationName, String endpointPath, String sessionId) {
        String name = getSessionBeansBaseName(applicationName, endpointPath, sessionId);
        unregisterMXBean(name);
    }

    private static void registerStatisticsMXBeans(String nameBase, MessageStatisticsMXBean nodeMXBean,
                                                  MessageStatisticsMXBean textMessageStatisticsMXBean,
                                                  MessageStatisticsMXBean binaryMessageStatisticsMXBean,
                                                  MessageStatisticsMXBean controlMessageStatisticsMXBean) {
        registerMXBean(nameBase, nodeMXBean);
        String name = nameBase + MESSAGE_STATISTIC_DIRECTORY + MESSAGE_TYPE_KEY + TEXT;
        registerMXBean(name, textMessageStatisticsMXBean);
        name = nameBase + MESSAGE_STATISTIC_DIRECTORY + MESSAGE_TYPE_KEY + BINARY;
        registerMXBean(name, binaryMessageStatisticsMXBean);
        name = nameBase + MESSAGE_STATISTIC_DIRECTORY + MESSAGE_TYPE_KEY + CONTROL;
        registerMXBean(name, controlMessageStatisticsMXBean);
    }

    private static String getApplicationBeansBaseName(String applicationName) {
        return APPLICATION_NAME_BASE + applicationName;
    }

    private static String getEndpointBeansBaseName(String applicationName, String endpointPath) {
        return getApplicationBeansBaseName(applicationName) + ENDPOINTS_DIRECTORY + ENDPOINT_KEY + endpointPath;
    }

    private static String getSessionBeansBaseName(String applicationName, String endpointPath, String sessionId) {
        return getEndpointBeansBaseName(applicationName, endpointPath) + SESSIONS_DIRECTORY + SESSION_KEY + sessionId;
    }

    private static void registerMXBean(String name, Object mBean) {
        final MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        try {
            synchronized (MBeanPublisher.class) {
                final ObjectName objectName = new ObjectName(name);
                if (mBeanServer.isRegistered(objectName)) {
                    LOGGER.log(Level.WARNING, "MXBean with name " + " already registered");
                } else {
                    mBeanServer.registerMBean(mBean, objectName);
                }
            }
        } catch (JMException e) {
            LOGGER.log(Level.WARNING, "Could not register MXBean with name " + name, e);
        }
    }

    private static void unregisterMXBean(String name) {
        name = name + ",*";
        final MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        try {
            synchronized (MBeanPublisher.class) {
                for (ObjectName objectName : mBeanServer.queryNames(new ObjectName(name), null)) {
                    mBeanServer.unregisterMBean(objectName);
                }
            }
        } catch (JMException e) {
            LOGGER.log(Level.WARNING, "Could not unregister MXBeans with name " + name, e);
        }
    }
}
