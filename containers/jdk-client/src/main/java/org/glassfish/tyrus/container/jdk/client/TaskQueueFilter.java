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
package org.glassfish.tyrus.container.jdk.client;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.glassfish.tyrus.spi.CompletionHandler;

/**
 * Queues {@link #write(java.nio.ByteBuffer, org.glassfish.tyrus.spi.CompletionHandler)}, {@link #close()}
 * and {@link #startSsl()} method calls and passes them to a downstream filter one at a time. Both
 * {@link org.glassfish.tyrus.container.jdk.client.SslFilter} and {@link org.glassfish.tyrus.container.jdk.client.TransportFilter}
 * allow {@link #write(java.nio.ByteBuffer, org.glassfish.tyrus.spi.CompletionHandler)}
 * method call only after the previous one has completed. Queueing {@link #close()} method calls ensures that
 * {@link #write(java.nio.ByteBuffer, org.glassfish.tyrus.spi.CompletionHandler)} methods called before
 * {@link #close()} will be processed. Including {@link #startSsl()} methods in the queue ensures that no
 * {@link #write(java.nio.ByteBuffer, org.glassfish.tyrus.spi.CompletionHandler)} method will be passed to
 * {@link org.glassfish.tyrus.container.jdk.client.SslFilter} while it performs SSL handshake.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
class TaskQueueFilter extends Filter {

    private final Queue<Task> taskQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean taskLock = new AtomicBoolean(false);
    private final Filter downstreamFilter;

    private volatile Filter upstreamFilter;

    /**
     * Constructor.
     *
     * @param downstreamFilter a filter that is positioned directly under this filter.
     */
    TaskQueueFilter(Filter downstreamFilter) {
        this.downstreamFilter = downstreamFilter;
    }

    @Override
    void connect(SocketAddress serverAddress, Filter upstreamFilter) {
        this.upstreamFilter = upstreamFilter;
        downstreamFilter.connect(serverAddress, this);
    }

    @Override
    void write(ByteBuffer data, CompletionHandler<ByteBuffer> completionHandler) {
        taskQueue.offer(new WriteTask(data, completionHandler));
        if (taskLock.compareAndSet(false, true)) {
            processTask();
        }
    }

    private void processTask() {
        final Task task = taskQueue.poll();
        if (task == null) {
            taskLock.set(false);
            return;
        }
        task.execute(this);
    }

    @Override
    void close() {
        // close task
        taskQueue.offer(new Task() {
            @Override
            public void execute(TaskQueueFilter queueFilter) {
                if (downstreamFilter != null) {
                    downstreamFilter.close();
                    upstreamFilter = null;
                }
                processTask();
            }
        });

        if (taskLock.compareAndSet(false, true)) {
            processTask();
        }
    }

    @Override
    void startSsl() {
        // start SSL task
        taskQueue.offer(new Task() {

            @Override
            public void execute(TaskQueueFilter queueFilter) {
                downstreamFilter.startSsl();
            }
        });

        if (taskLock.compareAndSet(false, true)) {
            processTask();
        }
    }

    @Override
    void onConnect() {
        upstreamFilter.onConnect();
    }

    @Override
    void onRead(ByteBuffer buffer) {
        /**
         * {@code upstreamFilter == null} means that there is {@link Filter#close()} propagating from the upper layers.
         */
        if (upstreamFilter == null) {
            return;
        }

        upstreamFilter.onRead(buffer);
    }

    @Override
    void onConnectionClosed() {
        upstreamFilter.onConnectionClosed();
    }

    @Override
    void onSslHandshakeCompleted() {
        upstreamFilter.onSslHandshakeCompleted();
        processTask();
    }

    @Override
    void onError(Throwable t) {
        upstreamFilter.onError(t);
    }

    /**
     * A task to be queued in order to be processed one at a time.
     */
    static interface Task {
        /**
         * Execute the task.
         *
         * @param queueFilter write queue filter this task should be executed in.
         */
        void execute(TaskQueueFilter queueFilter);
    }

    /**
     * A task that writes data to the downstreamFilter.
     */
    static class WriteTask implements Task {
        private final ByteBuffer data;
        private final org.glassfish.tyrus.spi.CompletionHandler<ByteBuffer> completionHandler;

        WriteTask(ByteBuffer data, org.glassfish.tyrus.spi.CompletionHandler<ByteBuffer> completionHandler) {
            this.data = data;
            this.completionHandler = completionHandler;
        }

        @Override
        public void execute(final TaskQueueFilter queueFilter) {
            // if downstream filter is null, this task has been enqueued after close task
            if (queueFilter.downstreamFilter == null) {
                getCompletionHandler().failed(new Throwable("Connection has been closed"));
                return;
            }
            queueFilter.downstreamFilter.write(getData(), new CompletionHandler<ByteBuffer>() {

                @Override
                public void failed(Throwable throwable) {
                    getCompletionHandler().failed(throwable);
                    queueFilter.processTask();
                }

                @Override
                public void completed(ByteBuffer result) {
                    if (result.hasRemaining()) {
                        execute(queueFilter);
                        return;
                    }

                    getCompletionHandler().completed(getData());
                    queueFilter.processTask();
                }
            });
        }

        ByteBuffer getData() {
            return data;
        }

        CompletionHandler<ByteBuffer> getCompletionHandler() {
            return completionHandler;
        }

        @Override
        public String toString() {
            return "WriteTask{" +
                    "data=" + data +
                    ", completionHandler=" + completionHandler +
                    '}';
        }
    }
}
