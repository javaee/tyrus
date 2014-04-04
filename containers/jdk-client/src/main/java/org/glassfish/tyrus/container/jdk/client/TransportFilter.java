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
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    private static final int THREAD_POOL_INITIAL_SIZE = 4;
    private static final int CONNECTION_CLOSE_WAIT = 30_000;
    private static final AtomicInteger openedConnections = new AtomicInteger(0);
    private static final ScheduledExecutorService connectionCloseScheduler = Executors.newSingleThreadScheduledExecutor();

    private static volatile AsynchronousChannelGroup channelGroup;
    private static volatile ScheduledFuture<?> closeWaitTask;

    private final Filter upstreamFilter;
    private final int inputBufferSize;

    private volatile AsynchronousSocketChannel socketChannel;

    /**
     * @param upstreamFilter  a {@link org.glassfish.tyrus.container.jdk.client.Filter} positioned on top of this filter.
     * @param inputBufferSize size of buffer to be allocated for reading data from a socket.
     */
    TransportFilter(Filter upstreamFilter, int inputBufferSize) {
        this.upstreamFilter = upstreamFilter;
        this.inputBufferSize = inputBufferSize;
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
        // Thread pool is owned by the channel group and will be shut down when channel group is shut down
        ExecutorService executor = Executors.newCachedThreadPool();
        channelGroup = AsynchronousChannelGroup.withCachedThreadPool(executor, THREAD_POOL_INITIAL_SIZE);
    }

    private void read(final ByteBuffer inputBuffer) {
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
                LOGGER.log(Level.SEVERE, "Reading from a socket has failed", exc.getMessage());
                upstreamFilter.onConnectionClosed();
            }

        });
    }

    private void scheduleClose() {
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
        }, CONNECTION_CLOSE_WAIT, TimeUnit.MILLISECONDS);
    }
}
