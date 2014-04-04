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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.DeploymentException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;

import org.glassfish.tyrus.spi.CompletionHandler;

/**
 * A filter that adds SSL support to the transport.
 * <p/>
 * {@link #write(java.nio.ByteBuffer, org.glassfish.tyrus.spi.CompletionHandler)} and {@link #onRead(Filter, java.nio.ByteBuffer)}
 * calls are passed through until {@link #startSsl()} method is called, after which SSL handshake is started.
 * When SSL handshake is being initiated, all data passed in {@link #write(java.nio.ByteBuffer, org.glassfish.tyrus.spi.CompletionHandler)}
 * method are stored until SSL handshake completes, after which they will be encrypted and passed to a downstream filter.
 * After SSL handshake has completed, all data passed in write method will be encrypted and data passed in
 * {@link #onRead(Filter, java.nio.ByteBuffer)} method will be decrypted.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
class SslFilter extends Filter {

    private static final Logger LOGGER = Logger.getLogger(SslFilter.class.getName());
    private static final String TRUST_STORE_FILE = "javax.net.ssl.trustStore";
    private static final String KEY_STORE_FILE = "javax.net.ssl.keyStore";
    private static final String TRUST_STORE_PASSWORD = "javax.net.ssl.trustStorePassword";
    private static final String KEY_STORE_PASSWORD = "javax.net.ssl.keyStorePassword";
    private static final String TRUST_STORE_TYPE = "javax.net.ssl.trustStoreType";
    private static final String KEY_STORE_TYPE = "javax.net.ssl.keyStoreType";
    private static final String DEFAULT_SECURITY_ALGORITHM = "TLS";

    private final ByteBuffer applicationInputBuffer;
    private final ByteBuffer networkOutputBuffer;
    private final Filter upstreamFilter;

    private String keyStoreFile;
    private String keyStoreType;
    private char[] keyStorePassword;
    private String keyStoreManagerAlgorithm;

    private String trustStoreFile;
    private String trustStoreType;
    private char[] trustStorePassword;
    private String trustStoreManagerAlgorithm;

    private volatile SSLEngine sslEngine;
    private volatile Filter downstreamFilter;
    private volatile boolean sslStarted = false;

    /**
     * SSL Filter constructor, takes upstream filter as a parameter.
     *
     * @param upstreamFilter a filter that is positioned above the SSL filter.
     * @throws DeploymentException when SSL context could not have been initialized.
     */
    SslFilter(Filter upstreamFilter) throws DeploymentException {
        this.upstreamFilter = upstreamFilter;
        SSLContext sslContext = initializeSslContext();
        if (sslContext == null) {
            throw new DeploymentException("Could not initialize SSL context");
        }

        sslEngine = sslContext.createSSLEngine();
        sslEngine.setUseClientMode(true);
        if (keyStoreFile == null) {
            sslEngine.setNeedClientAuth(false);
        } else {
            sslEngine.setNeedClientAuth(true);
        }

        applicationInputBuffer = ByteBuffer.allocate(sslEngine.getSession().getApplicationBufferSize());
        networkOutputBuffer = ByteBuffer.allocate(sslEngine.getSession().getPacketBufferSize());
    }

    @Override
    void write(ByteBuffer applicationData, CompletionHandler<ByteBuffer> completionHandler) {
        // before SSL is started write just passes through
        if (!sslStarted) {
            downstreamFilter.write(applicationData, completionHandler);
            return;
        }
        try {
            networkOutputBuffer.clear();
            sslEngine.wrap(applicationData, networkOutputBuffer);
            networkOutputBuffer.flip();
            downstreamFilter.write(networkOutputBuffer, completionHandler);
        } catch (SSLException e) {
            handleSslError(e);
        }
    }

    @Override
    void close() {
        if (!sslStarted) {
            downstreamFilter.close();
            downstreamFilter = null;
            return;
        }
        sslEngine.closeOutbound();
        write(networkOutputBuffer, new CompletionHandler<ByteBuffer>() {

            @Override
            public void completed(ByteBuffer result) {
                downstreamFilter.close();
                downstreamFilter = null;
            }

            @Override
            public void failed(Throwable throwable) {
                downstreamFilter.close();
                downstreamFilter = null;
            }
        });
    }

    @Override
    void onConnect(final Filter downstreamFilter) {
        this.downstreamFilter = downstreamFilter;
        upstreamFilter.onConnect(this);
    }

    @Override
    void onRead(Filter downstreamFilter, ByteBuffer networkData) {
        // before SSL is started read just passes through
        if (!sslStarted) {
            upstreamFilter.onRead(this, networkData);
            return;
        }
        SSLEngineResult.HandshakeStatus hs = sslEngine.getHandshakeStatus();
        try {
            // SSL handshake logic
            if (hs != SSLEngineResult.HandshakeStatus.FINISHED && hs != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                if (hs != SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
                    return;
                }
                SSLEngineResult result;
                while (true) {
                    result = sslEngine.unwrap(networkData, applicationInputBuffer);
                    if (result.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                        // needs more data from the network
                        return;
                    }
                    if (result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED) {
                        upstreamFilter.onSslHandshakeCompleted();
                        return;
                    }
                    if (!networkData.hasRemaining() || result.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
                        // all data has been read or the engine needs to do something else than read
                        break;
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
                    if (result.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                        // needs more data from the network
                        return;
                    }
                    applicationInputBuffer.flip();
                    upstreamFilter.onRead(downstreamFilter, applicationInputBuffer);
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

    private SSLContext initializeSslContext() {
        retrieveSystemProperties();
        // initialize key store
        KeyStore keyStore = null;
        try {
            keyStore = setUpKeyStore(keyStoreType, keyStoreFile, keyStorePassword);

        } catch (KeyStoreException | CertificateException e) {
            LOGGER.log(Level.SEVERE, "Could not initialize key store", e);
        } catch (NoSuchAlgorithmException e) {
            LOGGER.log(Level.SEVERE, "Could not initialize key store - incorrect algorithm ", e);
        } catch (FileNotFoundException e) {
            LOGGER.log(Level.SEVERE, "Could not find key store file " + keyStoreFile, e);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not load key sore form file " + keyStoreFile, e);
        }
        KeyManagerFactory keyManagerFactory = null;
        try {
            keyManagerFactory = KeyManagerFactory.getInstance(keyStoreManagerAlgorithm != null ? keyStoreManagerAlgorithm : KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, keyStorePassword);
        } catch (KeyStoreException | UnrecoverableKeyException e) {
            LOGGER.log(Level.SEVERE, "Could not initialize key store", e);
        } catch (NoSuchAlgorithmException e) {
            LOGGER.log(Level.SEVERE, "Could not initialize key store - incorrect algorithm ", e);
        }

        //initialize trust store
        KeyStore trustStore = null;
        try {
            trustStore = setUpKeyStore(trustStoreType, trustStoreFile, trustStorePassword);
        } catch (FileNotFoundException e) {
            LOGGER.log(Level.SEVERE, "Could not find trust store file " + trustStoreFile, e);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not load trust sore form file " + trustStoreFile, e);
        } catch (NoSuchAlgorithmException e) {
            LOGGER.log(Level.SEVERE, "Could not initialize trust store - incorrect algorithm ", e);
        } catch (KeyStoreException | CertificateException e) {
            LOGGER.log(Level.SEVERE, "Could not initialize trust store", e);
        }
        TrustManagerFactory trustManagerFactory = null;
        try {
            trustManagerFactory = TrustManagerFactory.getInstance(trustStoreManagerAlgorithm != null ? trustStoreManagerAlgorithm : TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
        } catch (KeyStoreException e) {
            LOGGER.log(Level.SEVERE, "Could not initialize trust store", e);
        } catch (NoSuchAlgorithmException e) {
            LOGGER.log(Level.SEVERE, "Could not initialize trust store - incorrect algorithm ", e);
        }

        try {
            SSLContext sslContext = SSLContext.getInstance(DEFAULT_SECURITY_ALGORITHM);
            sslContext.init(keyManagerFactory != null ? keyManagerFactory.getKeyManagers() : null, trustManagerFactory != null ? trustManagerFactory.getTrustManagers() : null, null);
            return sslContext;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            LOGGER.log(Level.SEVERE, "SSL could not have been initialized", e);
        }
        return null;
    }

    private KeyStore setUpKeyStore(String keyStoreType, String keyStoreFile, char[] keyStorePassword) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        KeyStore keyStore = KeyStore.getInstance(keyStoreType != null ? keyStoreType : KeyStore.getDefaultType());
        if (keyStoreFile == null) {
            return null;
        }

        try (InputStream keyStoreInputStream = new FileInputStream(keyStoreFile)) {
            keyStore.load(keyStoreInputStream, keyStorePassword);
            return keyStore;
        }
    }

    private void retrieveSystemProperties() {
        keyStoreFile = System.getProperty(KEY_STORE_FILE);
        keyStoreType = System.getProperty(KEY_STORE_TYPE);
        String keyStorePasswordStr = System.getProperty(KEY_STORE_PASSWORD);
        if (keyStorePasswordStr != null) {
            keyStorePassword = keyStorePasswordStr.toCharArray();
        }
        keyStoreManagerAlgorithm = System.getProperty(KEY_STORE_TYPE);
        trustStoreFile = System.getProperty(TRUST_STORE_FILE);
        trustStoreType = System.getProperty(TRUST_STORE_TYPE);
        String trustStorePasswordStr = System.getProperty(TRUST_STORE_PASSWORD);
        if (trustStorePasswordStr != null) {
            trustStorePassword = trustStorePasswordStr.toCharArray();
        }
        trustStoreManagerAlgorithm = System.getProperty(TRUST_STORE_TYPE);
    }

    private void doHandshakeStep(final Filter filter) {
        SSLEngineResult.HandshakeStatus hs = sslEngine.getHandshakeStatus();
        try {
            switch (hs) {
                // needs to write data to the network
                case NEED_WRAP: {
                    networkOutputBuffer.clear();
                    sslEngine.wrap(networkOutputBuffer, networkOutputBuffer);
                    networkOutputBuffer.flip();
                    filter.write(networkOutputBuffer, new CompletionHandler<ByteBuffer>() {
                        @Override
                        public void failed(Throwable throwable) {
                            handleSslError(throwable);
                        }

                        @Override
                        public void completed(ByteBuffer result) {
                            if (sslEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
                                return;
                            }
                            doHandshakeStep(filter);
                        }
                    });
                    break;
                }
                // needs to execute long running task (for instance validating certificates)
                case NEED_TASK: {
                    Runnable delegatedTask;
                    while ((delegatedTask = sslEngine.getDelegatedTask()) != null) {
                        delegatedTask.run();
                    }
                    if (sslEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
                        LOGGER.log(Level.SEVERE, "SSL handshake error has occurred - more data needed for validating the certificate");
                        upstreamFilter.onConnectionClosed();
                        return;
                    }
                    doHandshakeStep(filter);
                    break;
                }
            }
        } catch (Exception e) {
            handleSslError(e);
        }
    }

    private void handleSslError(Throwable e) {
        LOGGER.log(Level.SEVERE, "SSL error has occurred", e);
        upstreamFilter.onConnectionClosed();
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
