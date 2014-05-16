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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A class responsible for processing {@link Task}. It ensures that only one task will be processed at a time,
 * because Grizzly Worker-thread IOStrategy does not wait until one message is processed before
 * dispatching another one.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class TaskProcessor {

    private final Queue<Task> taskQueue = new ConcurrentLinkedQueue<Task>();
    private final Condition condition;
    /**
     * A lock that indicates that a thread is processing a task.
     */
    private final Lock taskLock = new ReentrantLock();

    /**
     * Constructor.
     *
     * @param condition if present, it will be called before processing each {@link Task}. When {@link org.glassfish.tyrus.container.grizzly.client.TaskProcessor.Condition#isValid()}
     *                  returns {@code false}, processing will be terminated. If {@code null},
     *                  all tasks from the queue will be processed.
     */
    public TaskProcessor(Condition condition) {
        this.condition = condition;
    }

    /**
     * Constructor.
     * <p/>
     * There is no condition that has to be checked before processing each task.
     */
    public TaskProcessor() {
        this.condition = null;
    }

    /**
     * Add a task to the task queue and process as much tasks from the task queue as possible.
     *
     * @param task {@link Task} that should be processed.
     */
    public void processTask(Task task) {
        taskQueue.offer(task);
        processTask();
    }

    /**
     * Process as much tasks from task queue as possible.
     */
    public void processTask() {
        if (!taskLock.tryLock()) {
            // there is another thread processing a task it will take care of this task too
            return;
        }

        try {
            while (!taskQueue.isEmpty()) {
                if (condition != null && !condition.isValid()) {
                    return;
                }

                final Task first = taskQueue.poll();
                if (first == null) {
                    continue;
                }

                first.execute();
            }
        } finally {
            taskLock.unlock();
        }

        /*
         * There is a small chance that another thread will manage to add a task to the queue in the moment when the thread
         * processing tasks has left the while cycle, but has not released the lock yet. In that case a task might
         * be added to the queue and stay there indefinitely. It is quite improbable, but the thread that has finished
         * processing tasks should try to process more tasks after releasing the lock.
         */
        if (!taskQueue.isEmpty()) {
            processTask();
        }
    }

    /**
     * Generic task representation.
     */
    public static abstract class Task {
        /**
         * To be overridden.
         */
        public abstract void execute();
    }

    /**
     * Condition used in {@link #processTask(org.glassfish.tyrus.container.grizzly.client.TaskProcessor.Task)}.
     */
    public interface Condition {

        /**
         * Check the condition.
         *
         * @return {@code true} when condition is valid, {@code false otherwise}.
         */
        public boolean isValid();
    }
}
