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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.websocket.Session;

import org.glassfish.tyrus.core.monitoring.MessageEventListener;

/**
 * This {@link org.glassfish.tyrus.ext.monitoring.jmx.EndpointMonitor} implementation creates and holds
 * {@link org.glassfish.tyrus.ext.monitoring.jmx.SessionMonitor}, which collects message statistics for open sessions.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
class SessionAwareEndpointMonitor extends EndpointMonitor {

    private final Map<String, SessionMonitor> sessions = new ConcurrentHashMap<String, SessionMonitor>();

    SessionAwareEndpointMonitor(ApplicationMonitor applicationJmx, ApplicationMXBeanImpl applicationMXBean, String applicationName, String endpointPath, String endpointClassName) {
        super(applicationJmx, applicationMXBean, applicationName, endpointPath, endpointClassName);
    }

    @Override
    public MessageEventListener onSessionOpened(String sessionId) {
        SessionMonitor sessionMonitor = new SessionMonitor(applicationName, endpointClassNamePathPair.getEndpointPath(), sessionId, this, endpointMXBean);
        sessions.put(sessionId, sessionMonitor);

        if (sessions.size() > maxOpenSessionsCount) {
            synchronized (maxOpenSessionsCountLock) {
                if (sessions.size() > maxOpenSessionsCount) {
                    maxOpenSessionsCount = sessions.size();
                }
            }
        }

        applicationMonitor.onSessionOpened();

        return new MessageEventListenerImpl(sessionMonitor);
    }

    @Override
    public void onSessionClosed(String sessionId) {
        SessionMonitor session = sessions.remove(sessionId);
        session.unregister();
        applicationMonitor.onSessionClosed();
    }

    @Override
    protected Callable<Integer> getOpenSessionsCount() {
        return new Callable<Integer>() {
            @Override
            public Integer call() {
                return sessions.size();
            }
        };
    }

    @Override
    public void onError(Session session, Throwable t) {
        if(session != null) {
            SessionMonitor sessionMonitor = sessions.get(session.getId());
            sessionMonitor.onError(t);
        }

        applicationMonitor.onError(t);
    }
}
