/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2016 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.client;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Utility class, which helps to configure ssl context.
 * <p>
 * Used to configure {@link SslEngineConfigurator}, which will be passed to client via configuration properties.
 * Example:
 * <pre>
 *      SslContextConfigurator sslContextConfigurator = new SslContextConfigurator();
 *      sslContextConfigurator.setTrustStoreFile("...");
 *      sslContextConfigurator.setTrustStorePassword("...");
 *      sslContextConfigurator.setTrustStoreType("...");
 *      sslContextConfigurator.setKeyStoreFile("...");
 *      sslContextConfigurator.setKeyStorePassword("...");
 *      sslContextConfigurator.setKeyStoreType("...");
 *      SslEngineConfigurator sslEngineConfigurator = new SslEngineConfigurator(sslContextConfigurator, true, false,
 * false);
 *      client.getProperties().put(ClientManager.SSL_ENGINE_CONFIGURATOR, sslEngineConfigurator);
 * </pre>
 *
 * @author Alexey Stashok
 * @author Hubert Iwaniuk
 * @author Bruno Harbulot
 * @author Marek Potociar (marek.potociar at oracle.com)
 */
public class SslContextConfigurator {
    /**
     * <em>Trust</em> store provider name.
     * <p>
     * The value MUST be a {@code String} representing the name of a <em>trust</em> store provider.
     * <p>
     * No default value is set.
     * <p>
     * The name of the configuration property is <tt>{@value}</tt>.
     */
    public static final String TRUST_STORE_PROVIDER = "javax.net.ssl.trustStoreProvider";
    /**
     * <em>Key</em> store provider name.
     * <p>
     * The value MUST be a {@code String} representing the name of a <em>trust</em> store provider.
     * <p>
     * No default value is set.
     * <p>
     * The name of the configuration property is <tt>{@value}</tt>.
     */
    public static final String KEY_STORE_PROVIDER = "javax.net.ssl.keyStoreProvider";
    /**
     * <em>Trust</em> store file name.
     * <p>
     * The value MUST be a {@code String} representing the name of a <em>trust</em> store file.
     * <p>
     * No default value is set.
     * <p>
     * The name of the configuration property is <tt>{@value}</tt>.
     */
    public static final String TRUST_STORE_FILE = "javax.net.ssl.trustStore";
    /**
     * <em>Key</em> store file name.
     * <p>
     * The value MUST be a {@code String} representing the name of a <em>key</em> store file.
     * <p>
     * No default value is set.
     * <p>
     * The name of the configuration property is <tt>{@value}</tt>.
     */
    public static final String KEY_STORE_FILE = "javax.net.ssl.keyStore";
    /**
     * <em>Trust</em> store file password - the password used to unlock the <em>trust</em> store file.
     * <p>
     * The value MUST be a {@code String} representing the <em>trust</em> store file password.
     * <p>
     * No default value is set.
     * <p>
     * The name of the configuration property is <tt>{@value}</tt>.
     */
    public static final String TRUST_STORE_PASSWORD = "javax.net.ssl.trustStorePassword";
    /**
     * <em>Key</em> store file password - the password used to unlock the <em>trust</em> store file.
     * <p>
     * The value MUST be a {@code String} representing the <em>key</em> store file password.
     * <p>
     * No default value is set.
     * <p>
     * The name of the configuration property is <tt>{@value}</tt>.
     */
    public static final String KEY_STORE_PASSWORD = "javax.net.ssl.keyStorePassword";
    /**
     * <em>Trust</em> store type (see {@link java.security.KeyStore#getType()} for more info).
     * <p>
     * The value MUST be a {@code String} representing the <em>trust</em> store type name.
     * <p>
     * No default value is set.
     * <p>
     * The name of the configuration property is <tt>{@value}</tt>.
     */
    public static final String TRUST_STORE_TYPE = "javax.net.ssl.trustStoreType";
    /**
     * <em>Key</em> store type (see {@link java.security.KeyStore#getType()} for more info).
     * <p>
     * The value MUST be a {@code String} representing the <em>key</em> store type name.
     * <p>
     * No default value is set.
     * <p>
     * The name of the configuration property is <tt>{@value}</tt>.
     */
    public static final String KEY_STORE_TYPE = "javax.net.ssl.keyStoreType";
    /**
     * <em>Key</em> manager factory algorithm name.
     * <p>
     * The value MUST be a {@code String} representing the <em>key</em> manager factory algorithm name.
     * <p>
     * No default value is set.
     * <p>
     * The name of the configuration property is <tt>{@value}</tt>.
     */
    public static final String KEY_FACTORY_MANAGER_ALGORITHM = "ssl.KeyManagerFactory.algorithm";
    /**
     * <em>Trust</em> manager factory algorithm name.
     * <p>
     * The value MUST be a {@code String} representing the <em>trust</em> manager factory algorithm name.
     * <p>
     * No default value is set.
     * <p>
     * The name of the configuration property is <tt>{@value}</tt>.
     */
    public static final String TRUST_FACTORY_MANAGER_ALGORITHM = "ssl.TrustManagerFactory.algorithm";

    /**
     * Default Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(SslContextConfigurator.class.getName());

    /**
     * Default SSL configuration. If you have changed any of
     * {@link System#getProperties()} of javax.net.ssl family you should refresh
     * this configuration by calling {@link #retrieve(java.util.Properties)}.
     */
    public static final SslContextConfigurator DEFAULT_CONFIG = new SslContextConfigurator();

    private String trustStoreProvider;
    private String keyStoreProvider;

    private String trustStoreType;
    private String keyStoreType;

    private char[] trustStorePassword;
    private char[] keyStorePassword;
    private char[] keyPassword;

    private String trustStoreFile;
    private String keyStoreFile;

    private byte[] trustStoreBytes;
    private byte[] keyStoreBytes;

    private String trustManagerFactoryAlgorithm;
    private String keyManagerFactoryAlgorithm;

    private String securityProtocol = "TLS";

    /**
     * Default constructor. Reads configuration properties from
     * {@link System#getProperties()}. Calls {@link #SslContextConfigurator(boolean)} with
     * <code>true</code>.
     */
    public SslContextConfigurator() {
        this(true);
    }

    /**
     * Constructor that allows you creating empty configuration.
     *
     * @param readSystemProperties If <code>true</code> populates configuration from
     *                             {@link System#getProperties()}, else you have empty
     *                             configuration.
     */
    public SslContextConfigurator(boolean readSystemProperties) {
        if (readSystemProperties) {
            retrieve(System.getProperties());
        }
    }

    /**
     * Sets the <em>trust</em> store provider name.
     *
     * @param trustStoreProvider <em>Trust</em> store provider to set.
     * @return updated {@link SslContextConfigurator} instance.
     */
    public SslContextConfigurator setTrustStoreProvider(String trustStoreProvider) {
        this.trustStoreProvider = trustStoreProvider;

        return this;
    }

    /**
     * Sets the <em>key</em> store provider name.
     *
     * @param keyStoreProvider <em>Key</em> store provider to set.
     * @return updated {@link SslContextConfigurator} instance.
     */
    public SslContextConfigurator setKeyStoreProvider(String keyStoreProvider) {
        this.keyStoreProvider = keyStoreProvider;

        return this;
    }

    /**
     * Type of <em>trust</em> store.
     *
     * @param trustStoreType Type of <em>trust</em> store to set.
     * @return updated {@link SslContextConfigurator} instance.
     */
    public SslContextConfigurator setTrustStoreType(String trustStoreType) {
        this.trustStoreType = trustStoreType;

        return this;
    }

    /**
     * Type of <em>key</em> store.
     *
     * @param keyStoreType Type of <em>key</em> store to set.
     * @return updated {@link SslContextConfigurator} instance.
     */
    public SslContextConfigurator setKeyStoreType(String keyStoreType) {
        this.keyStoreType = keyStoreType;

        return this;
    }

    /**
     * Password of <em>trust</em> store.
     *
     * @param trustStorePassword Password of <em>trust</em> store to set.
     * @return updated {@link SslContextConfigurator} instance.
     */
    public SslContextConfigurator setTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword.toCharArray();

        return this;
    }

    /**
     * Password of <em>key</em> store.
     *
     * @param keyStorePassword Password of <em>key</em> store to set.
     * @return updated {@link SslContextConfigurator} instance.
     */
    public SslContextConfigurator setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword.toCharArray();

        return this;
    }

    /**
     * Password of <em>key</em> store.
     *
     * @param keyStorePassword Password of <em>key</em> store to set.
     * @return updated {@link SslContextConfigurator} instance.
     */
    public SslContextConfigurator setKeyStorePassword(char[] keyStorePassword) {
        this.keyStorePassword = keyStorePassword.clone();

        return this;
    }

    /**
     * Password of the key in the <em>key</em> store.
     *
     * @param keyPassword Password of <em>key</em> to set.
     * @return updated {@link SslContextConfigurator} instance.
     */
    public SslContextConfigurator setKeyPassword(String keyPassword) {
        this.keyPassword = keyPassword.toCharArray();

        return this;
    }

    /**
     * Password of the key in the <em>key</em> store.
     *
     * @param keyPassword Password of <em>key</em> to set.
     * @return updated {@link SslContextConfigurator} instance.
     */
    public SslContextConfigurator setKeyPassword(char[] keyPassword) {
        this.keyPassword = keyPassword.clone();

        return this;
    }

    /**
     * Sets trust store file name, also makes sure that if other trust store
     * configuration parameters are not set to set them to default values.
     * Method resets trust store bytes if any have been set before via
     * {@link #setTrustStoreBytes(byte[])}.
     *
     * @param trustStoreFile File name of trust store.
     * @return updated {@link SslContextConfigurator} instance.
     */
    public SslContextConfigurator setTrustStoreFile(String trustStoreFile) {
        this.trustStoreFile = trustStoreFile;
        this.trustStoreBytes = null;

        return this;
    }

    /**
     * Sets trust store payload as byte array.
     * Method resets trust store file if any has been set before via
     * {@link #setTrustStoreFile(java.lang.String)}.
     *
     * @param trustStoreBytes trust store payload.
     * @return updated {@link SslContextConfigurator} instance.
     */
    public SslContextConfigurator setTrustStoreBytes(byte[] trustStoreBytes) {
        this.trustStoreBytes = trustStoreBytes.clone();
        this.trustStoreFile = null;

        return this;
    }

    /**
     * Sets key store file name, also makes sure that if other key store
     * configuration parameters are not set to set them to default values.
     * Method resets key store bytes if any have been set before via
     * {@link #setKeyStoreBytes(byte[])}.
     *
     * @param keyStoreFile File name of key store.
     * @return updated {@link SslContextConfigurator} instance.
     */
    public SslContextConfigurator setKeyStoreFile(String keyStoreFile) {
        this.keyStoreFile = keyStoreFile;
        this.keyStoreBytes = null;

        return this;
    }

    /**
     * Sets key store payload as byte array.
     * Method resets key store file if any has been set before via
     * {@link #setKeyStoreFile(java.lang.String)}.
     *
     * @param keyStoreBytes key store payload.
     * @return updated {@link SslContextConfigurator} instance.
     */
    public SslContextConfigurator setKeyStoreBytes(byte[] keyStoreBytes) {
        this.keyStoreBytes = keyStoreBytes.clone();
        this.keyStoreFile = null;

        return this;
    }

    /**
     * Sets the trust manager factory algorithm.
     *
     * @param trustManagerFactoryAlgorithm the trust manager factory algorithm.
     * @return updated {@link SslContextConfigurator} instance.
     */
    public SslContextConfigurator setTrustManagerFactoryAlgorithm(
            String trustManagerFactoryAlgorithm) {
        this.trustManagerFactoryAlgorithm = trustManagerFactoryAlgorithm;

        return this;
    }

    /**
     * Sets the key manager factory algorithm.
     *
     * @param keyManagerFactoryAlgorithm the key manager factory algorithm.
     * @return updated {@link SslContextConfigurator} instance.
     */
    public SslContextConfigurator setKeyManagerFactoryAlgorithm(String keyManagerFactoryAlgorithm) {
        this.keyManagerFactoryAlgorithm = keyManagerFactoryAlgorithm;

        return this;
    }

    /**
     * Sets the SSLContext protocol. The default value is <code>TLS</code> if
     * this is null.
     *
     * @param securityProtocol Protocol for {@link javax.net.ssl.SSLContext#getProtocol()}.
     * @return updated {@link SslContextConfigurator} instance.
     */
    public SslContextConfigurator setSecurityProtocol(String securityProtocol) {
        this.securityProtocol = securityProtocol;

        return this;
    }

    /**
     * Validates {@link SslContextConfigurator} configuration.
     *
     * @return <code>true</code> if configuration is valid, else
     * <code>false</code>.
     */
    public boolean validateConfiguration() {
        return validateConfiguration(false);
    }

    /**
     * Validates {@link SslContextConfigurator} configuration.
     *
     * @param needsKeyStore forces failure if no keystore is specified.
     * @return <code>true</code> if configuration is valid, else
     * <code>false</code>.
     */
    public boolean validateConfiguration(boolean needsKeyStore) {
        boolean valid = true;

        if (keyStoreBytes != null || keyStoreFile != null) {
            try {
                KeyStore keyStore;
                if (keyStoreProvider != null) {
                    keyStore = KeyStore.getInstance(keyStoreType != null ? keyStoreType : KeyStore.getDefaultType(),
                                                    keyStoreProvider);
                } else {
                    keyStore = KeyStore.getInstance(keyStoreType != null ? keyStoreType : KeyStore.getDefaultType());
                }
                InputStream keyStoreInputStream = null;
                try {
                    if (keyStoreBytes != null) {
                        keyStoreInputStream = new ByteArrayInputStream(keyStoreBytes);
                    } else if (!keyStoreFile.equals("NONE")) {
                        keyStoreInputStream = new FileInputStream(keyStoreFile);
                    }

                    keyStore.load(keyStoreInputStream, keyStorePassword);
                } finally {
                    try {
                        if (keyStoreInputStream != null) {
                            keyStoreInputStream.close();
                        }
                    } catch (IOException e) {
                        LOGGER.log(Level.FINEST, "Could not close key store file", e);
                    }
                }

                String kmfAlgorithm = keyManagerFactoryAlgorithm;
                if (kmfAlgorithm == null) {
                    kmfAlgorithm =
                            System.getProperty(KEY_FACTORY_MANAGER_ALGORITHM, KeyManagerFactory.getDefaultAlgorithm());
                }
                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(kmfAlgorithm);
                keyManagerFactory.init(keyStore, keyPassword != null ? keyPassword : keyStorePassword);
            } catch (KeyStoreException e) {
                LOGGER.log(Level.FINE, "Error initializing key store", e);
                valid = false;
            } catch (CertificateException e) {
                LOGGER.log(Level.FINE, "Key store certificate exception.", e);
                valid = false;
            } catch (UnrecoverableKeyException e) {
                LOGGER.log(Level.FINE, "Key store unrecoverable exception.", e);
                valid = false;
            } catch (FileNotFoundException e) {
                LOGGER.log(Level.FINE, "Can't find key store file: " + keyStoreFile, e);
                valid = false;
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Error loading key store from file: " + keyStoreFile, e);
                valid = false;
            } catch (NoSuchAlgorithmException e) {
                LOGGER.log(Level.FINE, "Error initializing key manager factory (no such algorithm)", e);
                valid = false;
            } catch (NoSuchProviderException e) {
                LOGGER.log(Level.FINE, "Error initializing key store (no such provider)", e);
                valid = false;
            }
        } else {
            valid &= !needsKeyStore;
        }

        if (trustStoreBytes != null || trustStoreFile != null) {
            try {
                KeyStore trustStore;
                if (trustStoreProvider != null) {
                    trustStore =
                            KeyStore.getInstance(trustStoreType != null ? trustStoreType : KeyStore.getDefaultType(),
                                                 trustStoreProvider);
                } else {
                    trustStore =
                            KeyStore.getInstance(trustStoreType != null ? trustStoreType : KeyStore.getDefaultType());
                }
                InputStream trustStoreInputStream = null;
                try {
                    if (trustStoreBytes != null) {
                        trustStoreInputStream = new ByteArrayInputStream(trustStoreBytes);
                    } else if (!trustStoreFile.equals("NONE")) {
                        trustStoreInputStream = new FileInputStream(trustStoreFile);
                    }
                    trustStore.load(trustStoreInputStream, trustStorePassword);
                } finally {
                    try {
                        if (trustStoreInputStream != null) {
                            trustStoreInputStream.close();
                        }
                    } catch (IOException e) {
                        LOGGER.log(Level.FINEST, "Could not close key store file", e);
                    }
                }

                String tmfAlgorithm = trustManagerFactoryAlgorithm;
                if (tmfAlgorithm == null) {
                    tmfAlgorithm = System.getProperty(
                            TRUST_FACTORY_MANAGER_ALGORITHM,
                            TrustManagerFactory.getDefaultAlgorithm());
                }

                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(tmfAlgorithm);
                trustManagerFactory.init(trustStore);
            } catch (KeyStoreException e) {
                LOGGER.log(Level.FINE, "Error initializing trust store", e);
                valid = false;
            } catch (CertificateException e) {
                LOGGER.log(Level.FINE, "Trust store certificate exception.", e);
                valid = false;
            } catch (FileNotFoundException e) {
                LOGGER.log(Level.FINE, "Can't find trust store file: " + trustStoreFile, e);
                valid = false;
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Error loading trust store from file: " + trustStoreFile, e);
                valid = false;
            } catch (NoSuchAlgorithmException e) {
                LOGGER.log(Level.FINE, "Error initializing trust manager factory (no such algorithm)", e);
                valid = false;
            } catch (NoSuchProviderException e) {
                LOGGER.log(Level.FINE, "Error initializing trust store (no such provider)", e);
                valid = false;
            }
        }
        return valid;
    }

    /**
     * Create {@link SSLContext} from current configuration.
     *
     * @return created {@link SSLContext}.
     */
    public SSLContext createSSLContext() {
        SSLContext sslContext = null;

        try {
            TrustManagerFactory trustManagerFactory = null;
            KeyManagerFactory keyManagerFactory = null;

            if (keyStoreBytes != null || keyStoreFile != null) {
                try {
                    KeyStore keyStore;
                    if (keyStoreProvider != null) {
                        keyStore = KeyStore.getInstance(
                                keyStoreType != null ? keyStoreType : KeyStore.getDefaultType(), keyStoreProvider);
                    } else {
                        keyStore =
                                KeyStore.getInstance(keyStoreType != null ? keyStoreType : KeyStore.getDefaultType());
                    }
                    InputStream keyStoreInputStream = null;
                    try {
                        if (keyStoreBytes != null) {
                            keyStoreInputStream = new ByteArrayInputStream(keyStoreBytes);
                        } else if (!keyStoreFile.equals("NONE")) {
                            keyStoreInputStream = new FileInputStream(keyStoreFile);
                        }
                        keyStore.load(keyStoreInputStream, keyStorePassword);
                    } finally {
                        try {
                            if (keyStoreInputStream != null) {
                                keyStoreInputStream.close();
                            }
                        } catch (IOException e) {
                            LOGGER.log(Level.FINEST, "Could not close key store file", e);
                        }
                    }

                    String kmfAlgorithm = keyManagerFactoryAlgorithm;
                    if (kmfAlgorithm == null) {
                        kmfAlgorithm = System.getProperty(KEY_FACTORY_MANAGER_ALGORITHM,
                                                          KeyManagerFactory.getDefaultAlgorithm());
                    }
                    keyManagerFactory = KeyManagerFactory.getInstance(kmfAlgorithm);
                    keyManagerFactory.init(keyStore, keyPassword != null ? keyPassword : keyStorePassword);
                } catch (KeyStoreException e) {
                    LOGGER.log(Level.FINE, "Error initializing key store", e);
                } catch (CertificateException e) {
                    LOGGER.log(Level.FINE, "Key store certificate exception.", e);
                } catch (UnrecoverableKeyException e) {
                    LOGGER.log(Level.FINE, "Key store unrecoverable exception.", e);
                } catch (FileNotFoundException e) {
                    LOGGER.log(Level.FINE, "Can't find key store file: " + keyStoreFile, e);
                } catch (IOException e) {
                    LOGGER.log(Level.FINE, "Error loading key store from file: " + keyStoreFile, e);
                } catch (NoSuchAlgorithmException e) {
                    LOGGER.log(Level.FINE, "Error initializing key manager factory (no such algorithm)", e);
                } catch (NoSuchProviderException e) {
                    LOGGER.log(Level.FINE, "Error initializing key store (no such provider)", e);
                }
            }

            if (trustStoreBytes != null || trustStoreFile != null) {
                try {
                    KeyStore trustStore;
                    if (trustStoreProvider != null) {
                        trustStore = KeyStore.getInstance(
                                trustStoreType != null ? trustStoreType : KeyStore.getDefaultType(),
                                trustStoreProvider);
                    } else {
                        trustStore = KeyStore.getInstance(
                                trustStoreType != null ? trustStoreType : KeyStore.getDefaultType());
                    }
                    InputStream trustStoreInputStream = null;
                    try {
                        if (trustStoreBytes != null) {
                            trustStoreInputStream = new ByteArrayInputStream(trustStoreBytes);
                        } else if (!trustStoreFile.equals("NONE")) {
                            trustStoreInputStream = new FileInputStream(trustStoreFile);
                        }
                        trustStore.load(trustStoreInputStream, trustStorePassword);
                    } finally {
                        try {
                            if (trustStoreInputStream != null) {
                                trustStoreInputStream.close();
                            }
                        } catch (IOException e) {
                            LOGGER.log(Level.FINEST, "Could not close trust store file", e);
                        }
                    }

                    String tmfAlgorithm = trustManagerFactoryAlgorithm;
                    if (tmfAlgorithm == null) {
                        tmfAlgorithm = System.getProperty(TRUST_FACTORY_MANAGER_ALGORITHM,
                                                          TrustManagerFactory.getDefaultAlgorithm());
                    }

                    trustManagerFactory = TrustManagerFactory.getInstance(tmfAlgorithm);
                    trustManagerFactory.init(trustStore);
                } catch (KeyStoreException e) {
                    LOGGER.log(Level.FINE, "Error initializing trust store", e);
                } catch (CertificateException e) {
                    LOGGER.log(Level.FINE, "Trust store certificate exception.", e);
                } catch (FileNotFoundException e) {
                    LOGGER.log(Level.FINE, "Can't find trust store file: " + trustStoreFile, e);
                } catch (IOException e) {
                    LOGGER.log(Level.FINE, "Error loading trust store from file: " + trustStoreFile, e);
                } catch (NoSuchAlgorithmException e) {
                    LOGGER.log(Level.FINE, "Error initializing trust manager factory (no such algorithm)", e);
                } catch (NoSuchProviderException e) {
                    LOGGER.log(Level.FINE, "Error initializing trust store (no such provider)", e);
                }
            }

            String secProtocol = "TLS";
            if (securityProtocol != null) {
                secProtocol = securityProtocol;
            }
            sslContext = SSLContext.getInstance(secProtocol);
            sslContext.init(keyManagerFactory != null ? keyManagerFactory.getKeyManagers() : null,
                            trustManagerFactory != null ? trustManagerFactory.getTrustManagers() : null, null);
        } catch (KeyManagementException e) {
            LOGGER.log(Level.FINE, "Key management error.", e);
        } catch (NoSuchAlgorithmException e) {
            LOGGER.log(Level.FINE, "Error initializing algorithm.", e);
        }

        return sslContext;
    }

    /**
     * Retrieve settings from (system) properties.
     *
     * @param props property map (usually taken from {@link System#getProperties()}).
     * @return updated {@link SslContextConfigurator} instance.
     */
    public SslContextConfigurator retrieve(Properties props) {
        trustStoreProvider = props.getProperty(TRUST_STORE_PROVIDER);
        keyStoreProvider = props.getProperty(KEY_STORE_PROVIDER);

        trustStoreType = props.getProperty(TRUST_STORE_TYPE);
        keyStoreType = props.getProperty(KEY_STORE_TYPE);

        if (props.getProperty(TRUST_STORE_PASSWORD) != null) {
            trustStorePassword = props.getProperty(TRUST_STORE_PASSWORD).toCharArray();
        } else {
            trustStorePassword = null;
        }

        if (props.getProperty(KEY_STORE_PASSWORD) != null) {
            keyStorePassword = props.getProperty(KEY_STORE_PASSWORD).toCharArray();
        } else {
            keyStorePassword = null;
        }

        trustStoreFile = props.getProperty(TRUST_STORE_FILE);
        keyStoreFile = props.getProperty(KEY_STORE_FILE);

        trustStoreBytes = null;
        keyStoreBytes = null;

        securityProtocol = "TLS";

        return this;
    }
}
