/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2014 Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

/**
 * SSLEngineConfigurator class from Grizzly project.
 * <p/>
 * Utility class, which helps to configure {@link SSLEngine}.
 * Should be passed to client via configuration properties. Example:
 * <pre>
 *      SslContextConfigurator sslContextConfigurator = new SslContextConfigurator();
 *      sslContextConfigurator.setTrustStoreFile("...");
 *      sslContextConfigurator.setTrustStorePassword("...");
 *      sslContextConfigurator.setTrustStoreType("...");
 *      sslContextConfigurator.setKeyStoreFile("...");
 *      sslContextConfigurator.setKeyStorePassword("...");
 *      sslContextConfigurator.setKeyStoreType("...");
 *      SslEngineConfigurator sslEngineConfigurator = new SslEngineConfigurator(sslContextConfigurator, true, false, false);
 *      client.getProperties().put(ClientManager.SSL_ENGINE_CONFIGURATOR, sslEngineConfigurator);
 * </pre>
 *
 * @author Alexey Stashok
 */
public class SslEngineConfigurator {
    private final Object sync = new Object();

    protected volatile SslContextConfigurator sslContextConfiguration;

    protected volatile SSLContext sslContext;

    /**
     * The list of cipher suites.
     */
    protected String[] enabledCipherSuites = null;
    /**
     * The list of protocols.
     */
    protected String[] enabledProtocols = null;
    /**
     * Client mode when handshaking.
     */
    protected boolean clientMode;
    /**
     * Require client Authentication.
     */
    protected boolean needClientAuth;
    /**
     * True when requesting authentication.
     */
    protected boolean wantClientAuth;
    /**
     * Has the enabled protocol configured.
     */
    private boolean isProtocolConfigured = false;
    /**
     * Has the enabled Cipher configured.
     */
    private boolean isCipherConfigured = false;
    /**
     * {@code true} if host should be verified.
     */
    private boolean hostVerificationEnabled = true;

    /**
     * A custom hostname verifier.
     */
    private HostnameVerifier hostnameVerifier = null;

    /**
     * Create SSL Engine configuration basing on passed {@link SSLContext}.
     *
     * @param sslContext {@link SSLContext}.
     */
    public SslEngineConfigurator(SSLContext sslContext) {
        this(sslContext, true, false, false);
    }

    /**
     * Create SSL Engine configuration based on passed {@link SSLContext},
     * using passed client mode, need/want client auth parameters.
     *
     * @param sslContext     {@link SSLContext}.
     * @param clientMode     will be configured to work in client mode.
     * @param needClientAuth client authentication is required.
     * @param wantClientAuth client should authenticate.
     */
    public SslEngineConfigurator(final SSLContext sslContext, final boolean clientMode, final boolean needClientAuth, final boolean wantClientAuth) {
        if (sslContext == null) {
            throw new IllegalArgumentException("SSLContext can not be null");
        }

        this.sslContextConfiguration = null;
        this.sslContext = sslContext;
        this.clientMode = clientMode;
        this.needClientAuth = needClientAuth;
        this.wantClientAuth = wantClientAuth;
    }

    /**
     * Create SSL Engine configuration based on passed {@link SslContextConfigurator}.
     * This constructor makes possible to initialize SSLEngine and SSLContext in lazy
     * fashion on first {@link #createSSLEngine(String)} call.
     *
     * @param sslContextConfiguration {@link SslContextConfigurator}.
     */
    public SslEngineConfigurator(SslContextConfigurator sslContextConfiguration) {
        this(sslContextConfiguration, true, false, false);
    }

    /**
     * Create SSL Engine configuration basing on passed {@link SslContextConfigurator}.
     * This constructor makes possible to initialize SSLEngine and SSLContext in lazy
     * fashion on first {@link #createSSLEngine(String)} call.
     *
     * @param sslContextConfiguration {@link SslContextConfigurator}.
     * @param clientMode              will be configured to work in client mode.
     * @param needClientAuth          client authentication is required.
     * @param wantClientAuth          client should authenticate.
     */
    public SslEngineConfigurator(SslContextConfigurator sslContextConfiguration, boolean clientMode, boolean needClientAuth, boolean wantClientAuth) {
        if (sslContextConfiguration == null) {
            throw new IllegalArgumentException("SSLContextConfigurator can not be null");
        }

        this.sslContextConfiguration = sslContextConfiguration;
        this.clientMode = clientMode;
        this.needClientAuth = needClientAuth;
        this.wantClientAuth = wantClientAuth;
    }

    public SslEngineConfigurator(SslEngineConfigurator pattern) {
        this.sslContextConfiguration = pattern.sslContextConfiguration;
        this.sslContext = pattern.sslContext;
        this.clientMode = pattern.clientMode;
        this.needClientAuth = pattern.needClientAuth;
        this.wantClientAuth = pattern.wantClientAuth;

        this.enabledCipherSuites = pattern.enabledCipherSuites;
        this.enabledProtocols = pattern.enabledProtocols;

        this.isCipherConfigured = pattern.isCipherConfigured;
        this.isProtocolConfigured = pattern.isProtocolConfigured;
    }

    /**
     * Default constructor.
     */
    protected SslEngineConfigurator() {
    }

    /**
     * Create and configure {@link SSLEngine}, based on current settings.
     *
     * @param serverHost server host, which will be used to verify authenticity of the server (the provided host name will
     *                   compared to the host in the certificate provided by the server).
     * @return {@link SSLEngine}.
     */
    public SSLEngine createSSLEngine(String serverHost) {
        if (sslContext == null) {
            synchronized (sync) {
                if (sslContext == null) {
                    sslContext = sslContextConfiguration.createSSLContext();
                }
            }
        }

        /* the port is not part of host name verification, it is present in the constructor because of Kerberos (which is not
        supported by Tyrus) */
        final SSLEngine sslEngine = sslContext.createSSLEngine(serverHost, -1);
        configure(sslEngine);

        return sslEngine;
    }

    /**
     * Configure passed {@link SSLEngine}, using current configurator settings
     *
     * @param sslEngine {@link SSLEngine} to configure.
     * @return configured {@link SSLEngine}.
     */
    public SSLEngine configure(final SSLEngine sslEngine) {
        if (enabledCipherSuites != null) {
            if (!isCipherConfigured) {
                enabledCipherSuites = configureEnabledCiphers(sslEngine, enabledCipherSuites);
                isCipherConfigured = true;
            }
            sslEngine.setEnabledCipherSuites(enabledCipherSuites);
        }

        if (enabledProtocols != null) {
            if (!isProtocolConfigured) {
                enabledProtocols = configureEnabledProtocols(sslEngine, enabledProtocols);
                isProtocolConfigured = true;
            }
            sslEngine.setEnabledProtocols(enabledProtocols);
        }

        sslEngine.setUseClientMode(clientMode);
        if (wantClientAuth) {
            sslEngine.setWantClientAuth(true);
        }
        if (needClientAuth) {
            sslEngine.setNeedClientAuth(true);
        }

        return sslEngine;
    }

    /**
     * Will {@link SSLEngine} be configured to work in client mode.
     *
     * @return <tt>true</tt>, if {@link SSLEngine} will be configured to work
     * in <tt>client</tt> mode, or <tt>false</tt> for <tt>server</tt> mode.
     */
    public boolean isClientMode() {
        return clientMode;
    }

    /**
     * Set {@link SSLEngine} to be configured to work in client mode.
     *
     * @param clientMode <tt>true</tt>, if {@link SSLEngine} will be configured
     *                   to work in <tt>client</tt> mode, or <tt>false</tt> for <tt>server</tt>
     *                   mode.
     */
    public SslEngineConfigurator setClientMode(boolean clientMode) {
        this.clientMode = clientMode;
        return this;
    }


    public boolean isNeedClientAuth() {
        return needClientAuth;
    }

    public SslEngineConfigurator setNeedClientAuth(boolean needClientAuth) {
        this.needClientAuth = needClientAuth;
        return this;
    }

    public boolean isWantClientAuth() {
        return wantClientAuth;
    }

    public SslEngineConfigurator setWantClientAuth(boolean wantClientAuth) {
        this.wantClientAuth = wantClientAuth;
        return this;
    }

    public String[] getEnabledCipherSuites() {
        return enabledCipherSuites.clone();
    }

    public SslEngineConfigurator setEnabledCipherSuites(String[] enabledCipherSuites) {
        this.enabledCipherSuites = enabledCipherSuites.clone();
        return this;
    }

    public String[] getEnabledProtocols() {
        return enabledProtocols.clone();
    }

    public SslEngineConfigurator setEnabledProtocols(String[] enabledProtocols) {
        this.enabledProtocols = enabledProtocols.clone();
        return this;
    }

    public boolean isCipherConfigured() {
        return isCipherConfigured;
    }

    public SslEngineConfigurator setCipherConfigured(boolean isCipherConfigured) {
        this.isCipherConfigured = isCipherConfigured;
        return this;
    }

    public boolean isProtocolConfigured() {
        return isProtocolConfigured;
    }

    public SslEngineConfigurator setProtocolConfigured(boolean isProtocolConfigured) {
        this.isProtocolConfigured = isProtocolConfigured;
        return this;
    }

    /**
     * Get the hostname verification state.
     *
     * @return {@code true} if the hostname verification is enabled, {@code false} otherwise.
     */
    public boolean isHostVerificationEnabled() {
        return hostVerificationEnabled;
    }

    /**
     * Set hostname verification.
     *
     * @param hostVerificationEnabled when {@code true}, servers hostname will be verified using JDK default
     *                                {@link HostnameVerifier}. When {@code false}, hostname verification won't be
     *                                performed unless custom {@link HostnameVerifier} is set.
     * @see #setHostnameVerifier(HostnameVerifier)
     */
    public void setHostVerificationEnabled(boolean hostVerificationEnabled) {
        this.hostVerificationEnabled = hostVerificationEnabled;
    }

    /**
     * Get custom hostname verifier.
     *
     * @return user provided hostname verifier instance.
     */
    public HostnameVerifier getHostnameVerifier() {
        return hostnameVerifier;
    }

    /**
     * Set custom hostname verifier.
     * <p/>
     * When custom {@link HostnameVerifier} instance is registered, it will be used to perform hostname verification,
     * no matter on the state of hostname verification flag (see {@link #isHostVerificationEnabled()}) and JDK default
     * hostname verifier won't be used.
     *
     * @param hostnameVerifier custom hostname verifier.
     */
    public void setHostnameVerifier(HostnameVerifier hostnameVerifier) {
        this.hostnameVerifier = hostnameVerifier;
    }

    public SSLContext getSslContext() {
        if (sslContext == null) {
            synchronized (sync) {
                if (sslContext == null) {
                    sslContext = sslContextConfiguration.createSSLContext();
                }
            }
        }

        return sslContext;
    }

    /**
     * Return the list of allowed protocol.
     *
     * @return String[] an array of supported protocols.
     */
    private static String[] configureEnabledProtocols(SSLEngine sslEngine, String[] requestedProtocols) {

        String[] supportedProtocols = sslEngine.getSupportedProtocols();
        String[] protocols = null;
        ArrayList<String> list = null;
        for (String supportedProtocol : supportedProtocols) {
            /*
             * Check to see if the requested protocol is among the
             * supported protocols, i.e., may be enabled
             */
            for (String protocol : requestedProtocols) {
                protocol = protocol.trim();
                if (supportedProtocol.equals(protocol)) {
                    if (list == null) {
                        list = new ArrayList<String>();
                    }
                    list.add(protocol);
                    break;
                }
            }
        }

        if (list != null) {
            protocols = list.toArray(new String[list.size()]);
        }

        return protocols;
    }

    /**
     * Determines the SSL cipher suites to be enabled.
     *
     * @return Array of SSL cipher suites to be enabled, or null if none of the
     * requested ciphers are supported
     */
    private static String[] configureEnabledCiphers(SSLEngine sslEngine, String[] requestedCiphers) {

        String[] supportedCiphers = sslEngine.getSupportedCipherSuites();
        String[] ciphers = null;
        ArrayList<String> list = null;
        for (String supportedCipher : supportedCiphers) {
            /*
             * Check to see if the requested protocol is among the
             * supported protocols, i.e., may be enabled
             */
            for (String cipher : requestedCiphers) {
                cipher = cipher.trim();
                if (supportedCipher.equals(cipher)) {
                    if (list == null) {
                        list = new ArrayList<String>();
                    }
                    list.add(cipher);
                    break;
                }
            }
        }

        if (list != null) {
            ciphers = list.toArray(new String[list.size()]);
        }

        return ciphers;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("SSLEngineConfigurator");
        sb.append("{clientMode=").append(clientMode);
        sb.append(", enabledCipherSuites=")
                .append(enabledCipherSuites == null ? "null" : Arrays.asList(enabledCipherSuites).toString());
        sb.append(", enabledProtocols=")
                .append(enabledProtocols == null ? "null" : Arrays.asList(enabledProtocols).toString());
        sb.append(", needClientAuth=").append(needClientAuth);
        sb.append(", wantClientAuth=").append(wantClientAuth);
        sb.append(", isProtocolConfigured=").append(isProtocolConfigured);
        sb.append(", isCipherConfigured=").append(isCipherConfigured);
        sb.append(", hostVerificationEnabled=").append(hostVerificationEnabled);
        sb.append('}');
        return sb.toString();
    }

    public SslEngineConfigurator copy() {
        return new SslEngineConfigurator(this);
    }
}
