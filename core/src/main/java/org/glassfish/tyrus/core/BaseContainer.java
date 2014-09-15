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

    private final ExecutorService managedExecutorService;
    private final ScheduledExecutorService managedScheduledExecutorService;
    private final ThreadFactory threadFactory;
    /**
     * This lock ensures that only one instance of each type of executors will be created and it also prevents a situation
     * when a client is given an executor that is just about to be shut down.
     */
    private final Object EXECUTORS_CLEAN_UP_LOCK = new Object();

    private volatile ExecutorService executorService = null;
    private volatile ScheduledExecutorService scheduledExecutorService = null;

    public BaseContainer() {
        this.managedExecutorService = lookupManagedExecutorService();
        this.managedScheduledExecutorService = lookupManagedScheduledExecutorService();

        if (managedExecutorService == null || managedScheduledExecutorService == null) {
            // at least one of the managed executor services is null, a local one will be created instead
            threadFactory = new DaemonThreadFactory();
        } else {
            // only managed executor services will be used, the thread factory won't be needed.
            threadFactory = null;
        }
    }

    /**
     * Returns a container-managed {@link java.util.concurrent.ExecutorService} registered under
     * {@code java:comp/DefaultManagedExecutorService} or if the lookup has failed, it returns a
     * {@link java.util.concurrent.ExecutorService} created and managed by this instance of
     * {@link org.glassfish.tyrus.core.BaseContainer}.
     *
     * @return executor service.
     */
    @Override
    public ExecutorService getExecutorService() {
        if (managedExecutorService != null) {
            return managedExecutorService;
        }

        if (executorService == null) {
            synchronized (EXECUTORS_CLEAN_UP_LOCK) {
                if (executorService == null) {
                    executorService = Executors.newCachedThreadPool(threadFactory);
                }
            }
        }

        return executorService;
    }

    /**
     * Returns a container-managed {@link java.util.concurrent.ScheduledExecutorService} registered under
     * {@code java:comp/DefaultManagedScheduledExecutorService} or if the lookup has failed it returns a
     * {@link java.util.concurrent.ScheduledExecutorService} created and managed by this instance of
     * {@link org.glassfish.tyrus.core.BaseContainer}.
     *
     * @return scheduled executor service.
     */
    @Override
    public ScheduledExecutorService getScheduledExecutorService() {
        if (managedScheduledExecutorService != null) {
            return managedScheduledExecutorService;
        }

        if (scheduledExecutorService == null) {
            synchronized (EXECUTORS_CLEAN_UP_LOCK) {
                if (scheduledExecutorService == null) {
                    scheduledExecutorService = Executors.newScheduledThreadPool(10, threadFactory);
                }
            }
        }

        return scheduledExecutorService;
    }

    /**
     * Release executor services managed by this instance. Executor services obtained via JNDI lookup won't be
     * shut down.
     */
    public void shutdown() {
        if (executorService != null) {
            executorService.shutdown();
            executorService = null;
        }

        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdownNow();
            scheduledExecutorService = null;
        }
    }

    /**
     * Release executor services managed by this instance if the condition passed in the parameter is fulfilled.
     * Executor services obtained via JNDI lookup won't be shut down.
     *
     * @param shutDownCondition condition that will be evaluated before executor services are released and they will be
     *                          released only if the condition is evaluated to {@code true}. The condition will be
     *                          evaluated in a synchronized block in order to make the process of its evaluation
     *                          and executor services release an atomic operation.
     */
    protected void shutdown(ShutDownCondition shutDownCondition) {
        synchronized (EXECUTORS_CLEAN_UP_LOCK) {
            if (shutDownCondition.evaluate()) {
                shutdown();
            }
        }
    }

    private ExecutorService lookupManagedExecutorService() {
        // Get the default ManagedExecutorService, if available
        try {
            // TYRUS-256: Tyrus client on Android
            final Class<?> aClass = Class.forName("javax.naming.InitialContext");
            final Object o = aClass.newInstance();

            final Method lookupMethod = aClass.getMethod("lookup", String.class);
            return (ExecutorService) lookupMethod.invoke(o, "java:comp/DefaultManagedExecutorService");
        } catch (Exception e) {
            // ignore
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, e.getMessage(), e);
            }
        } catch (LinkageError error) {
            // ignore - JDK8 compact2 profile - http://openjdk.java.net/jeps/161
        }

        return null;
    }

    private ScheduledExecutorService lookupManagedScheduledExecutorService() {
        try {
            // TYRUS-256: Tyrus client on Android
            final Class<?> aClass = Class.forName("javax.naming.InitialContext");
            final Object o = aClass.newInstance();

            final Method lookupMethod = aClass.getMethod("lookup", String.class);
            return (ScheduledExecutorService) lookupMethod.invoke(o, "java:comp/DefaultManagedScheduledExecutorService");
        } catch (Exception e) {
            // ignore
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, e.getMessage(), e);
            }
        } catch (LinkageError error) {
            // ignore - JDK8 compact2 profile - http://openjdk.java.net/jeps/161
        }

        return null;
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

    protected static interface ShutDownCondition {

        boolean evaluate();
    }
}
