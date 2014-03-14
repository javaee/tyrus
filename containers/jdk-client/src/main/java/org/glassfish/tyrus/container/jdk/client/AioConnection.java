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
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.tyrus.spi.ClientEngine;

/**
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
class AioConnection {

    private static final Logger LOGGER = Logger.getLogger(AioConnection.class.getName());
    private static final int INPUT_BUFFER_SIZE = 2048;
    private static final int THREAD_POOL_INITIAL_SIZE = 2;
    private static final int CONNECTION_CLOSE_WAIT = 30_000;
    private static final AtomicInteger openedConnections = new AtomicInteger(0);
    private static final ScheduledExecutorService connectionCloseScheduler = Executors.newSingleThreadScheduledExecutor();

    private static volatile AsynchronousChannelGroup channelGroup;
    private static volatile ScheduledFuture<?> closeWaitTask;

    private final Queue<WriteRecord> writeQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean writeLock = new AtomicBoolean(false);
    private final ClientEventListener eventListener;

    private volatile AsynchronousSocketChannel socketChannel;

    AioConnection(ClientEngine clientEngine, URI uri) {
        this.eventListener = new ClientEventListener(clientEngine, uri, new ClientEngine.TimeoutHandler() {

            @Override
            public void handleTimeout() {
                try {
                    AioConnection.this.close();
                } catch (IOException e) {
                    Logger.getLogger(JdkClientContainer.class.getName()).log(Level.INFO, "Could not close connection", e);
                }
            }

        });
    }

    void connect(SocketAddress serverAddress) throws IOException {
        synchronized (AioConnection.class) {
            initializeChannelGroup();
            socketChannel = AsynchronousSocketChannel.open(channelGroup);
            openedConnections.incrementAndGet();
        }
        socketChannel.connect(serverAddress, this, new CompletionHandler<Void, AioConnection>() {

            @Override
            public void completed(Void result, AioConnection connection) {
                final ByteBuffer inputBuffer = ByteBuffer.allocate(INPUT_BUFFER_SIZE);
                read(inputBuffer);
                eventListener.onConnect(connection);
            }

            @Override
            public void failed(Throwable exc, AioConnection connection) {
                LOGGER.log(Level.INFO, "Connection failed", exc);
                try {
                    connection.close();
                } catch (IOException e) {
                    logConnectionCloseFailed(e);
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
        ExecutorService executor = Executors.newCachedThreadPool();
        channelGroup = AsynchronousChannelGroup.withCachedThreadPool(executor, THREAD_POOL_INITIAL_SIZE);
    }

    private void read(final ByteBuffer inputBuffer) {
        socketChannel.read(inputBuffer, this, new CompletionHandler<Integer, AioConnection>() {

            @Override
            public void completed(Integer bytesRead, AioConnection connection) {
                if (bytesRead == -1) {
                    connection.getEventListener().onConnectionClosed();
                    try {
                        connection.close();
                    } catch (IOException e) {
                        logConnectionCloseFailed(e);
                    }
                    return;
                }
                inputBuffer.flip();
                connection.getEventListener().onRead(connection, inputBuffer);
                inputBuffer.clear();
                read(inputBuffer);
            }

            @Override
            public void failed(Throwable exc, AioConnection connection) {
                try {
                    connection.close();
                } catch (IOException e) {
                    logConnectionCloseFailed(e);
                }
            }

        });
    }

    void write(ByteBuffer data, org.glassfish.tyrus.spi.CompletionHandler<ByteBuffer> completionHandler) {
        writeQueue.add(new WriteRecord(data, completionHandler));
        if (writeLock.compareAndSet(false, true)) {
            processWrite();
        }
    }

    private void processWrite() {
        final WriteRecord writeRecord = writeQueue.peek();
        if (writeRecord == null) {
            writeLock.set(false);
            return;
        }
        socketChannel.write(writeRecord.getData(), writeRecord, new CompletionHandler<Integer, WriteRecord>() {

            @Override
            public void completed(Integer result, WriteRecord writeRecord) {
                if (!writeRecord.getData().hasRemaining()) {
                    writeQueue.poll();
                    writeRecord.getCompletionHandler().completed(writeRecord.getData());
                }
                processWrite();
            }

            @Override
            public void failed(Throwable exc, WriteRecord writeRecord) {
                writeLock.set(false);
                writeRecord.getCompletionHandler().failed(exc);
            }
        });
    }

    ClientEventListener getEventListener() {
        return eventListener;
    }

    void close() throws IOException {
        if (!socketChannel.isOpen()) {
            return;
        }
        socketChannel.close();
        synchronized (AioConnection.class) {
            openedConnections.decrementAndGet();
            if (openedConnections.get() == 0 && channelGroup != null) {
                scheduleClose();
            }
        }
    }

    private void scheduleClose() {
        closeWaitTask = connectionCloseScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                synchronized (AioConnection.class) {
                    if (closeWaitTask == null) {
                        return;
                    }
                    channelGroup.shutdown();
                    channelGroup = null;
                    closeWaitTask = null;
                }
            }
        }, CONNECTION_CLOSE_WAIT, TimeUnit.MILLISECONDS);
    }

    private void logConnectionCloseFailed(Throwable t) {
        LOGGER.log(Level.INFO, "Could not close a connection", t);
    }

    static class WriteRecord {

        private final ByteBuffer data;
        private final org.glassfish.tyrus.spi.CompletionHandler<ByteBuffer> completionHandler;

        WriteRecord(ByteBuffer data, org.glassfish.tyrus.spi.CompletionHandler<ByteBuffer> completionHandler) {
            this.data = data;
            this.completionHandler = completionHandler;
        }

        ByteBuffer getData() {
            return data;
        }

        org.glassfish.tyrus.spi.CompletionHandler<ByteBuffer> getCompletionHandler() {
            return completionHandler;
        }
    }
}
