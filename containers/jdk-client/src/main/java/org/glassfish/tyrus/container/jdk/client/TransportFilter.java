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

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.tyrus.client.ThreadPoolConfig;

/**
 * Writes and reads data to and from a socket. Only one {@link #write(java.nio.ByteBuffer, org.glassfish.tyrus.spi.CompletionHandler)}
 * method call can be processed at a time. Only one {@link #read(java.nio.ByteBuffer)} operation is supported at a time,
 * another one is started only after the previous one has completed. Blocking in {@link #onRead(Filter, java.nio.ByteBuffer)}
 * or {@link #onConnect(Filter)} method will result in data not being read from a socket until these methods have completed.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
class TransportFilter extends Filter {

    private static final Logger LOGGER = Logger.getLogger(TransportFilter.class.getName());
    private static final int DEFAULT_CONNECTION_CLOSE_WAIT = 30;
    private static final AtomicInteger openedConnections = new AtomicInteger(0);
    private static final ScheduledExecutorService connectionCloseScheduler = Executors.newSingleThreadScheduledExecutor();

    private static volatile AsynchronousChannelGroup channelGroup;
    private static volatile ScheduledFuture<?> closeWaitTask;

    private final Filter upstreamFilter;
    private final int inputBufferSize;
    private final ThreadPoolConfig threadPoolConfig;
    private final Integer containerIdleTimeout;

    private volatile AsynchronousSocketChannel socketChannel;

    /**
     * @param upstreamFilter       a {@link org.glassfish.tyrus.container.jdk.client.Filter} positioned on top of this filter.
     * @param inputBufferSize      size of buffer to be allocated for reading data from a socket.
     * @param threadPoolConfig     thread pool configuration used for creating thread pool.
     * @param containerIdleTimeout idle time after which the shared thread pool will be destroyed.
     *                             If {@code null} default value will be used. The default value is 30 seconds.
     */
    TransportFilter(Filter upstreamFilter, int inputBufferSize, ThreadPoolConfig threadPoolConfig, Integer containerIdleTimeout) {
        this.upstreamFilter = upstreamFilter;
        this.inputBufferSize = inputBufferSize;
        this.threadPoolConfig = threadPoolConfig;
        this.containerIdleTimeout = containerIdleTimeout;
    }

    @Override
    void write(ByteBuffer data, final org.glassfish.tyrus.spi.CompletionHandler<ByteBuffer> completionHandler) {
        socketChannel.write(data, data, new CompletionHandler<Integer, ByteBuffer>() {

            @Override
            public void completed(Integer result, ByteBuffer buffer) {
                if (buffer.hasRemaining()) {
                    write(buffer, completionHandler);
                    return;
                }
                completionHandler.completed(buffer);
            }

            @Override
            public void failed(Throwable exc, ByteBuffer buffer) {
                completionHandler.failed(exc);
            }
        });
    }

    @Override
    void close() {
        if (!socketChannel.isOpen()) {
            return;
        }
        try {
            socketChannel.close();
        } catch (IOException e) {
            LOGGER.log(Level.INFO, "Could not close a connection", e);
        }
        synchronized (TransportFilter.class) {
            openedConnections.decrementAndGet();
            if (openedConnections.get() == 0 && channelGroup != null) {
                scheduleClose();
            }
        }
    }

    @Override
    void startSsl() {
        upstreamFilter.onSslHandshakeCompleted();
    }

    /**
     * Initiates connection to a server.
     *
     * @param serverAddress     an address of the server the client should connect to.
     * @param completionHandler a {@link org.glassfish.tyrus.spi.CompletionHandler} to be called when connection
     *                          succeeds or fails.
     * @throws IOException if I/O error occurs.
     */
    public void connect(SocketAddress serverAddress, final CompletionHandler<Void, Void> completionHandler) throws IOException {
        synchronized (TransportFilter.class) {
            initializeChannelGroup();
            socketChannel = AsynchronousSocketChannel.open(channelGroup);
            openedConnections.incrementAndGet();
        }
        socketChannel.connect(serverAddress, null, new CompletionHandler<Void, Void>() {

            @Override
            public void completed(Void result, Void result2) {
                final ByteBuffer inputBuffer = ByteBuffer.allocate(inputBufferSize);
                upstreamFilter.onConnect(TransportFilter.this);
                read(inputBuffer);
                if (completionHandler != null) {
                    completionHandler.completed(null, null);
                }
            }

            @Override
            public void failed(Throwable exc, Void result) {
                LOGGER.log(Level.INFO, "Connection failed", exc.getMessage());
                if (completionHandler != null) {
                    completionHandler.failed(exc, null);
                }
                try {
                    socketChannel.close();
                } catch (IOException e) {
                    LOGGER.log(Level.FINE, "Could not close connection", exc.getMessage());
                }
            }
        });
    }

    private void initializeChannelGroup() throws IOException {
        if (closeWaitTask != null) {
            closeWaitTask.cancel(true);
            closeWaitTask = null;
        }
        if (channelGroup != null) {
            return;
        }

        ThreadFactory threadFactory = threadPoolConfig.getThreadFactory();
        if (threadFactory == null) {
            threadFactory = new TransportThreadFactory(threadPoolConfig);
        }
        ExecutorService executor;
        if (threadPoolConfig.getQueue() != null) {
            executor = new SynchronizedQueuingExecutor(threadPoolConfig.getCorePoolSize(), threadPoolConfig.getMaxPoolSize(), threadPoolConfig.getKeepAliveTime(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS, threadPoolConfig.getQueue(), threadFactory);
        } else {
            int taskQueueLimit = threadPoolConfig.getQueueLimit();
            if (taskQueueLimit == -1) {
                taskQueueLimit = Integer.MAX_VALUE;
            }

            executor = new QueuingExecutor(threadPoolConfig.getCorePoolSize(), threadPoolConfig.getMaxPoolSize(), threadPoolConfig.getKeepAliveTime(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS, taskQueueLimit, threadFactory);
        }

        // Thread pool is owned by the channel group and will be shut down when channel group is shut down
        channelGroup = AsynchronousChannelGroup.withCachedThreadPool(executor, threadPoolConfig.getCorePoolSize());
    }

    private void read(final ByteBuffer inputBuffer) {
        /**
         * It must be checked that the channel has not been closed by {@link #close()} method.
         */
        if (!socketChannel.isOpen()) {
            return;
        }

        socketChannel.read(inputBuffer, null, new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer bytesRead, Void result) {
                // connection closed by the server
                if (bytesRead == -1) {
                    TransportFilter.this.upstreamFilter.onConnectionClosed();
                    return;
                }
                inputBuffer.flip();
                TransportFilter.this.upstreamFilter.onRead(TransportFilter.this, inputBuffer);
                inputBuffer.compact();
                read(inputBuffer);
            }

            @Override
            public void failed(Throwable exc, Void result) {
                /**
                 * Reading from the channel will fail if it is closing. In such cases {@link AsynchronousCloseException}
                 * is thrown. This should not be logged and no action undertaken.
                 */
                if (exc instanceof AsynchronousCloseException) {
                    return;
                }
                
                LOGGER.log(Level.SEVERE, "Reading from a socket has failed", exc.getMessage());
                upstreamFilter.onConnectionClosed();
            }
        });
    }

    private void scheduleClose() {
        int closeWait = containerIdleTimeout == null ? DEFAULT_CONNECTION_CLOSE_WAIT : containerIdleTimeout;
        closeWaitTask = connectionCloseScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                synchronized (TransportFilter.class) {
                    if (closeWaitTask == null) {
                        return;
                    }
                    channelGroup.shutdown();
                    channelGroup = null;
                    closeWaitTask = null;
                }
            }
        }, closeWait, TimeUnit.SECONDS);
    }

    /**
     * A default thread factory that gets used if {@link org.glassfish.tyrus.client.ThreadPoolConfig#getThreadFactory()} is not specified.
     */
    private static class TransportThreadFactory implements ThreadFactory {

        private static final String THREAD_NAME_BASE = " tyrus-jdk-client-";
        private static final AtomicInteger threadCounter = new AtomicInteger(0);

        private final ThreadPoolConfig threadPoolConfig;

        TransportThreadFactory(ThreadPoolConfig threadPoolConfig) {
            this.threadPoolConfig = threadPoolConfig;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName(THREAD_NAME_BASE + threadCounter.incrementAndGet());
            thread.setPriority(threadPoolConfig.getPriority());
            thread.setDaemon(threadPoolConfig.isDaemon());

            if (threadPoolConfig.getInitialClassLoader() == null) {
                thread.setContextClassLoader(this.getClass().getClassLoader());
            } else {
                thread.setContextClassLoader(threadPoolConfig.getInitialClassLoader());
            }

            return thread;
        }
    }

    /**
     * A thread pool executor that prefers creating new working threads over queueing tasks until the maximum poll size
     * has been reached.
     * </p>
     * The difference from {@link org.glassfish.tyrus.container.jdk.client.TransportFilter.QueuingExecutor}, is that
     * a it is used when an user-provided queue is used for queuing the tasks. The access to the Queue has to be synchronized,
     * because it is not guaranteed tha the user has provided a thread safe {@link java.util.Queue} implementation.
     */
    private static class SynchronizedQueuingExecutor extends ThreadPoolExecutor {

        private final Queue<Runnable> taskQueue;

        SynchronizedQueuingExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, Queue<Runnable> taskQueue, ThreadFactory threadFactory) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, new SynchronousQueue<Runnable>(), threadFactory);
            this.taskQueue = taskQueue;
        }

        @Override
        public void execute(Runnable task) {
            submitTask(task);
        }

        /**
         * Submit a task for execution, if the maximum thread limit has been reached and all the threads are occupied,
         * enqueue the task. The task is not executed by the current thread, but by a thread from the thread pool.
         *
         * @param task to be executed.
         */
        private void submitTask(Runnable task) {
            synchronized (taskQueue) {
                try {
                    super.execute(task);
                } catch (RejectedExecutionException e) {
                    /* All threads are occupied, try enqueuing the task.
                     * Each thread from the thread pool checks the queue after it has finished executing a task.
                     */
                    if (!taskQueue.offer(task)) {
                        throw new RejectedExecutionException("A limit of Tyrus client thread pool queue has been reached.", e);
                    }
                }
            }
        }

        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            super.afterExecute(r, t);
            synchronized (taskQueue) {
                // Try if a task has been enqueued while all threads were busy.
                Runnable queuedTask = taskQueue.poll();
                if (queuedTask != null) {
                    submitTask(queuedTask);
                }
            }
        }
    }

    /**
     * A thread pool executor that prefers creating new working threads over queueing tasks until the maximum poll size
     * has been reached.
     * <p/>
     * The difference from {@link org.glassfish.tyrus.container.jdk.client.TransportFilter.SynchronizedQueuingExecutor}
     * is that it uses {@link java.util.concurrent.LinkedBlockingDeque} as the task queue implementation and therefore
     * the access to the task queue does not have to be synchronized.
     */
    private static class QueuingExecutor extends ThreadPoolExecutor {

        private final BlockingQueue<Runnable> taskQueue;

        QueuingExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, int queueCapacity, ThreadFactory threadFactory) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, new SynchronousQueue<Runnable>(), threadFactory);
            this.taskQueue = new LinkedBlockingDeque<>(queueCapacity);
        }

        @Override
        public void execute(Runnable task) {
            submitTask(task);
        }

        /**
         * Submit a task for execution, if the maximum thread limit has been reached and all the threads are occupied,
         * enqueue the task. The task is not executed by the current thread, but by a thread from the thread pool.
         *
         * @param task to be executed.
         */
        private void submitTask(Runnable task) {
            try {
                super.execute(task);
            } catch (RejectedExecutionException e) {
                /* All threads are occupied, try enqueuing the task.
                 * Each thread from the thread pool checks the queue after it has finished executing a task.
                 */
                if (!taskQueue.offer(task)) {
                    throw new RejectedExecutionException("A limit of Tyrus client thread pool queue has been reached.", e);
                }

               /*
                * There is one improbable situation that could possibly cause that a task could stay in the queue indefinitely,
                * if all the threads have finished their tasks in the interval between this tasks has been rejected and it has
                * been successfully enqueued. If this has happened the task must be dequeued and an attempt to execute it should
                * be repeated.
                */
                if (getActiveCount() == 0) {
                    /*
                     * There is no guarantee that the same tasks that has been enqueued above will be dequeued,
                     * but trying to execute one arbitrary task by everyone in this situation is enough to clear the queue.
                     */
                    Runnable dequeuedTask = taskQueue.poll();

                    if (dequeuedTask != null) {
                        submitTask(dequeuedTask);
                    }
                }
            }
        }

        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            super.afterExecute(r, t);
            // Try if a task has been enqueued while all threads were busy.
            Runnable queuedTask = taskQueue.poll();
            if (queuedTask != null) {
                submitTask(queuedTask);
            }
        }
    }
}
