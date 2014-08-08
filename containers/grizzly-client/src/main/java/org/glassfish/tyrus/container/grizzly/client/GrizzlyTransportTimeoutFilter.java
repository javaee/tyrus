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

package org.glassfish.tyrus.container.grizzly.client;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;

/**
 * Timeout filter used for shared container.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
class GrizzlyTransportTimeoutFilter extends BaseFilter {

    private static final Logger LOGGER = Logger.getLogger(GrizzlyTransportTimeoutFilter.class.getName());
    private static final AtomicInteger connectionCounter = new AtomicInteger(0);
    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName("tyrus-grizzly-container-idle-timeout");
            thread.setDaemon(true);
            return thread;
        }
    });

    /**
     * Should be updated whenever you don't want to the container to be stopped. (lastAccessed + timeout) is used
     * for evaluating timeout condition when there are no ongoing connections.
     */
    private static volatile long lastAccessed;
    private static volatile boolean closed;
    private static volatile ScheduledFuture<?> timeoutTask;

    private final int timeout;

    public GrizzlyTransportTimeoutFilter(int timeout) {
        this.timeout = timeout;
        closed = false;
    }

    /**
     * Update last accessed info.
     */
    public static void touch() {
        lastAccessed = System.currentTimeMillis();
    }

    @Override
    public NextAction handleConnect(FilterChainContext ctx) throws IOException {
        connectionCounter.incrementAndGet();
        touch();
        return super.handleConnect(ctx);
    }

    @Override
    public NextAction handleClose(FilterChainContext ctx) throws IOException {
        final int connectionCount = connectionCounter.decrementAndGet();
        touch();

        if (connectionCount == 0 && timeoutTask == null) {
            LOGGER.log(Level.FINER, "Scheduling IdleTimeoutTransportTask: " + timeout + " seconds.");
            timeoutTask = executorService.schedule(new IdleTimeoutTransportTask(connectionCounter), timeout, TimeUnit.SECONDS);
        }

        return super.handleClose(ctx);
    }

    private class IdleTimeoutTransportTask implements Runnable {

        private final AtomicInteger connectionCounter;

        private IdleTimeoutTransportTask(AtomicInteger connectionCounter) {
            this.connectionCounter = connectionCounter;
        }

        @Override
        public void run() {
            if (connectionCounter.get() == 0 && !closed) {
                final long currentTime = System.currentTimeMillis();
                if ((lastAccessed + (timeout * 1000)) < currentTime) {
                    closed = true;
                    timeoutTask = null;
                    GrizzlyClientSocket.closeSharedTransport();
                } else {
                    final long delay = (lastAccessed + (timeout * 1000)) - currentTime;
                    LOGGER.log(Level.FINER, "Scheduling IdleTimeoutTransportTask: " + delay / 1000 + " seconds.");

                    timeoutTask = executorService.schedule(new IdleTimeoutTransportTask(connectionCounter), delay, TimeUnit.MILLISECONDS);
                }
            }
        }
    }
}
