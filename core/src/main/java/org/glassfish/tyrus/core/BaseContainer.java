/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2014 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.core;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.WebSocketContainer;

/**
 * Base WebSocket container.
 * <p/>
 * Client and Server containers extend this to provide additional functionality.
 *
 * @author Jitendra Kotamraju
 */
public abstract class BaseContainer extends ExecutorServiceProvider implements WebSocketContainer {

    private static final Logger LOGGER = Logger.getLogger(BaseContainer.class.getName());

    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutorService;

    private boolean shutdownExecutorService = true;
    private boolean shutdownScheduledExecutorService = true;

    private ThreadFactory threadFactory = null;

    public BaseContainer() {
        this.executorService = newExecutorService();
        this.scheduledExecutorService = newScheduledExecutorService();
    }

    @Override
    public ExecutorService getExecutorService() {
        return executorService;
    }

    @Override
    public ScheduledExecutorService getScheduledExecutorService() {
        return scheduledExecutorService;
    }

    /**
     * Release executor services managed by this instance. Executor services obtained via JNDI lookup won't be
     * shut down.
     */
    public void shutdown() {
        if (shutdownExecutorService) {
            executorService.shutdown();
        }

        if (shutdownScheduledExecutorService) {
            scheduledExecutorService.shutdown();
        }
    }

    private ExecutorService newExecutorService() {
        ExecutorService es = null;

        // Get the default ManagedExecutorService, if available
        try {
            // TYRUS-256: Tyrus client on Android
            final Class<?> aClass = Class.forName("javax.naming.InitialContext");
            final Object o = aClass.newInstance();

            final Method lookupMethod = aClass.getMethod("lookup", String.class);
            es = (ExecutorService) lookupMethod.invoke(o, "java:comp/DefaultManagedExecutorService");
            shutdownExecutorService = false;
        } catch (Exception e) {
            // ignore
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, e.getMessage(), e);
            }
        } catch (LinkageError error) {
            // ignore - JDK8 compact2 profile - http://openjdk.java.net/jeps/161
        }

        if (es == null) {
            if (threadFactory == null) {
                threadFactory = new DaemonThreadFactory();
            }
            es = Executors.newCachedThreadPool(threadFactory);
        }

        return es;
    }

    private ScheduledExecutorService newScheduledExecutorService() {
        ScheduledExecutorService service = null;

        try {
            // TYRUS-256: Tyrus client on Android
            final Class<?> aClass = Class.forName("javax.naming.InitialContext");
            final Object o = aClass.newInstance();

            final Method lookupMethod = aClass.getMethod("lookup", String.class);
            service = (ScheduledExecutorService) lookupMethod.invoke(o, "java:comp/DefaultManagedScheduledExecutorService");
            shutdownScheduledExecutorService = false;
        } catch (Exception e) {
            // ignore
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, e.getMessage(), e);
            }
        } catch (LinkageError error) {
            // ignore - JDK8 compact2 profile - http://openjdk.java.net/jeps/161
        }

        if (service == null) {
            if (threadFactory == null) {
                threadFactory = new DaemonThreadFactory();
            }

            service = Executors.newScheduledThreadPool(10, threadFactory);
        }

        return service;
    }

    private static class DaemonThreadFactory implements ThreadFactory {
        static final AtomicInteger poolNumber = new AtomicInteger(1);
        final AtomicInteger threadNumber = new AtomicInteger(1);
        final String namePrefix;

        DaemonThreadFactory() {
            namePrefix = "tyrus-" + poolNumber.getAndIncrement() + "-thread-";
        }

        @Override
        public Thread newThread(@SuppressWarnings("NullableProblems") Runnable r) {
            Thread t = new Thread(null, r, namePrefix + threadNumber.getAndIncrement(), 0);
            if (!t.isDaemon()) {
                t.setDaemon(true);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }
}
