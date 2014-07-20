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
import java.util.concurrent.CountDownLatch;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;

import org.glassfish.tyrus.client.SslEngineConfigurator;
import org.glassfish.tyrus.spi.CompletionHandler;

/**
 * A filter that adds SSL support to the transport.
 * <p/>
 * {@link #write(java.nio.ByteBuffer, org.glassfish.tyrus.spi.CompletionHandler)} and {@link #onRead(java.nio.ByteBuffer)}
 * calls are passed through until {@link #startSsl()} method is called, after which SSL handshake is started.
 * When SSL handshake is being initiated, all data passed in {@link #write(java.nio.ByteBuffer, org.glassfish.tyrus.spi.CompletionHandler)}
 * method are stored until SSL handshake completes, after which they will be encrypted and passed to a downstream filter.
 * After SSL handshake has completed, all data passed in write method will be encrypted and data passed in
 * {@link #onRead(java.nio.ByteBuffer)} method will be decrypted.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
class SslFilter extends Filter {

    private final ByteBuffer applicationInputBuffer;
    private final ByteBuffer networkOutputBuffer;
    private final SSLEngine sslEngine;
    private final HostnameVerifier customHostnameVerifier;
    private final String serverHost;
    private final Filter downstreamFilter;
    /**
     * Lock to ensure that only one thread will work with {@link #sslEngine} state machine during the handshake phase.
     */
    private final Object handshakeLock = new Object();

    private volatile Filter upstreamFilter;
    private volatile boolean sslStarted = false;
    private volatile boolean handshakeCompleted = false;

    /**
     * SSL Filter constructor, takes upstream filter as a parameter.
     *
     * @param downstreamFilter      a filter that is positioned under the SSL filter.
     * @param sslEngineConfigurator configuration of SSL engine.
     * @param serverHost            server host (hostname or IP address), which will be used to verify authenticity of
     *                              the server (the provided host will be compared against the host in the certificate
     *                              provided by the server). IP address and hostname cannot be used interchangeably -
     *                              if a certificate contains hostname and an IP address of the server is provided here,
     *                              the verification will fail.
     */
    SslFilter(Filter downstreamFilter, SslEngineConfigurator sslEngineConfigurator, String serverHost) {
        this.downstreamFilter = downstreamFilter;
        this.serverHost = serverHost;
        sslEngine = sslEngineConfigurator.createSSLEngine(serverHost);
        customHostnameVerifier = sslEngineConfigurator.getHostnameVerifier();

        /**
         * Enable server host verification.
         * This can be moved to {@link SslEngineConfigurator} with the rest of {@link SSLEngine} configuration
         * when {@link SslEngineConfigurator} supports Java 7.
         */
        if (sslEngineConfigurator.isHostVerificationEnabled() && sslEngineConfigurator.getHostnameVerifier() == null) {
            SSLParameters sslParameters = sslEngine.getSSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
            sslEngine.setSSLParameters(sslParameters);
        }

        applicationInputBuffer = ByteBuffer.allocate(sslEngine.getSession().getApplicationBufferSize());
        networkOutputBuffer = ByteBuffer.allocate(sslEngine.getSession().getPacketBufferSize());
    }

    /**
     * SSL Filter constructor, takes upstream filter as a parameter.
     *
     * @param downstreamFilter a filter that is positioned under the SSL filter.
     * @deprecated Please use {@link #SslFilter(Filter, org.glassfish.tyrus.client.SslEngineConfigurator, String)}.
     */
    SslFilter(Filter downstreamFilter, org.glassfish.tyrus.container.jdk.client.SslEngineConfigurator sslEngineConfigurator) {
        this.downstreamFilter = downstreamFilter;
        sslEngine = sslEngineConfigurator.createSSLEngine();
        applicationInputBuffer = ByteBuffer.allocate(sslEngine.getSession().getApplicationBufferSize());
        networkOutputBuffer = ByteBuffer.allocate(sslEngine.getSession().getPacketBufferSize());
        customHostnameVerifier = null;
        serverHost = null;
    }

    @Override
    void write(final ByteBuffer applicationData, final CompletionHandler<ByteBuffer> completionHandler) {
        // before SSL is started write just passes through
        if (!sslStarted) {
            downstreamFilter.write(applicationData, completionHandler);
            return;
        }

        handleWrite(networkOutputBuffer, applicationData, downstreamFilter, completionHandler);
    }

    private void handleWrite(final ByteBuffer networkOutputBuffer, final ByteBuffer applicationData, final Filter downstreamFilter,
                             final CompletionHandler<ByteBuffer> completionHandler) {
        try {
            networkOutputBuffer.clear();
            // TODO: check the result
            sslEngine.wrap(applicationData, networkOutputBuffer);
            networkOutputBuffer.flip();
            downstreamFilter.write(networkOutputBuffer, new CompletionHandler<ByteBuffer>() {
                @Override
                public void completed(ByteBuffer result) {
                    if (applicationData.hasRemaining()) {
                        handleWrite(networkOutputBuffer, applicationData, downstreamFilter, completionHandler);
                    } else {
                        completionHandler.completed(applicationData);
                    }
                }

                @Override
                public void failed(Throwable throwable) {
                    completionHandler.failed(throwable);
                }
            });
        } catch (SSLException e) {
            handleSslError(e);
        }
    }

    @Override
    void close() {
        if (!sslStarted) {
            downstreamFilter.close();
            return;
        }
        sslEngine.closeOutbound();
        write(networkOutputBuffer, new CompletionHandler<ByteBuffer>() {

            @Override
            public void completed(ByteBuffer result) {
                downstreamFilter.close();
                upstreamFilter = null;
            }

            @Override
            public void failed(Throwable throwable) {
                downstreamFilter.close();
                upstreamFilter = null;
            }
        });
    }

    @Override
    void connect(SocketAddress serverAddress, Filter upstreamFilter) {
        this.upstreamFilter = upstreamFilter;
        downstreamFilter.connect(serverAddress, this);
    }

    @Override
    void onConnect() {
        upstreamFilter.onConnect();
    }

    @Override
    void onRead(ByteBuffer networkData) {
        /**
         * {@code upstreamFilter == null} means that there is {@link Filter#close()} propagating from the upper layers.
         */
        if (upstreamFilter == null) {
            return;
        }

        // before SSL is started read just passes through
        if (!sslStarted) {
            upstreamFilter.onRead(networkData);
            return;
        }
        SSLEngineResult.HandshakeStatus hs = sslEngine.getHandshakeStatus();
        try {
            // SSL handshake logic
            if (hs != SSLEngineResult.HandshakeStatus.FINISHED && hs != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                synchronized (handshakeLock) {

                    if (hs != SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
                        return;
                    }

                    applicationInputBuffer.clear();

                    SSLEngineResult result;
                    while (true) {
                        result = sslEngine.unwrap(networkData, applicationInputBuffer);
                        if (result.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                            // needs more data from the network
                            return;
                        }
                        if (result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED) {
                            handshakeCompleted = true;

                            // apply a custom host verifier if present
                            if (customHostnameVerifier != null && !customHostnameVerifier.verify(serverHost, sslEngine.getSession())) {
                                handleSslError(new SSLException("Server host name verification using " + customHostnameVerifier.getClass() + " has failed"));
                            }

                            upstreamFilter.onSslHandshakeCompleted();
                            return;
                        }
                        if (!networkData.hasRemaining() || result.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
                            // all data has been read or the engine needs to do something else than read
                            break;
                        }
                    }
                }
                // write or do tasks (for instance validating certificates)
                doHandshakeStep(downstreamFilter);

            } else {
                // Encrypting received data
                SSLEngineResult result;
                do {
                    applicationInputBuffer.clear();
                    result = sslEngine.unwrap(networkData, applicationInputBuffer);
                    // other statuses are OK or cannot be returned from unwrap.
                    if (result.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                        // needs more data from the network
                        return;
                    }
                    applicationInputBuffer.flip();
                    upstreamFilter.onRead(applicationInputBuffer);
                } while (networkData.hasRemaining());
            }
        } catch (SSLException e) {
            handleSslError(e);
        }
    }

    @Override
    void onConnectionClosed() {
        upstreamFilter.onConnectionClosed();
    }

    private void doHandshakeStep(final Filter filter) {
        try {
            synchronized (handshakeLock) {
                while (true) {
                    SSLEngineResult.HandshakeStatus hs = sslEngine.getHandshakeStatus();

                    if (handshakeCompleted || (hs != SSLEngineResult.HandshakeStatus.NEED_WRAP && hs != SSLEngineResult.HandshakeStatus.NEED_TASK)) {
                        return;
                    }

                    switch (hs) {
                        // needs to write data to the network
                        case NEED_WRAP: {
                            networkOutputBuffer.clear();
                            sslEngine.wrap(networkOutputBuffer, networkOutputBuffer);
                            networkOutputBuffer.flip();
                            /**
                             *  Latch to make the write operation synchronous. If it was asynchronous, the {@link #handshakeLock}
                             *  will be released before the write is completed and another thread arriving form
                             *  {@link #onRead(Filter, java.nio.ByteBuffer)} will be allowed to write resulting in
                             *  {@link java.nio.channels.WritePendingException}. This is only concern during the handshake
                             *  phase as {@link org.glassfish.tyrus.container.jdk.client.TaskQueueFilter} ensures that
                             *  only one write operation is allowed at a time during "data transfer" phase.
                             */
                            final CountDownLatch writeLatch = new CountDownLatch(1);
                            filter.write(networkOutputBuffer, new CompletionHandler<ByteBuffer>() {
                                @Override
                                public void failed(Throwable throwable) {
                                    writeLatch.countDown();
                                    handleSslError(throwable);
                                }

                                @Override
                                public void completed(ByteBuffer result) {
                                    writeLatch.countDown();
                                }
                            });

                            writeLatch.await();
                            break;
                        }
                        // needs to execute long running task (for instance validating certificates)
                        case NEED_TASK: {
                            Runnable delegatedTask;
                            while ((delegatedTask = sslEngine.getDelegatedTask()) != null) {
                                delegatedTask.run();
                            }
                            if (sslEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
                                handleSslError(new SSLException("SSL handshake error has occurred - more data needed for validating the certificate"));
                                return;
                            }
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            handleSslError(e);
        }
    }

    private void handleSslError(Throwable t) {
        upstreamFilter.onError(t);
    }

    @Override
    void onError(Throwable t) {
        upstreamFilter.onError(t);
    }

    @Override
    void startSsl() {
        try {
            sslStarted = true;
            sslEngine.beginHandshake();
            doHandshakeStep(downstreamFilter);
        } catch (SSLException e) {
            handleSslError(e);
        }
    }
}
