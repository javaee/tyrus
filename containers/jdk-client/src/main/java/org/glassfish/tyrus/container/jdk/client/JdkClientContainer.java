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
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.client.SslContextConfigurator;
import org.glassfish.tyrus.client.SslEngineConfigurator;
import org.glassfish.tyrus.client.ThreadPoolConfig;
import org.glassfish.tyrus.core.Base64Utils;
import org.glassfish.tyrus.core.Utils;
import org.glassfish.tyrus.spi.ClientContainer;
import org.glassfish.tyrus.spi.ClientEngine;

/**
 * {@link org.glassfish.tyrus.spi.ClientContainer} implementation based on Java 7 NIO API.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class JdkClientContainer implements ClientContainer {

    private static final int SSL_INPUT_BUFFER_SIZE = 16_384;
    private static final int INPUT_BUFFER_SIZE = 2048;
    private static final Logger LOGGER = Logger.getLogger(JdkClientContainer.class.getName());

    private final List<Proxy> proxies = new ArrayList<>();

    @Override
    public void openClientSocket(String url, ClientEndpointConfig cec, Map<String, Object> properties, ClientEngine clientEngine) throws DeploymentException, IOException {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new DeploymentException("Invalid URI.", e);
        }

        TransportFilter transportFilter;

        final ClientFilter clientFilter = new ClientFilter(clientEngine, uri, getProxyHeaders(properties));
        final TaskQueueFilter writeQueue = new TaskQueueFilter(clientFilter);

        ThreadPoolConfig threadPoolConfig = Utils.getProperty(properties, ClientProperties.WORKER_THREAD_POOL_CONFIG, ThreadPoolConfig.class);
        if (threadPoolConfig == null) {
            threadPoolConfig = ThreadPoolConfig.defaultConfig();
        }
        // weblogic.websocket.client.max-aio-threads has priority over what is in thread pool config
        String wlsMaxThreadsStr = System.getProperty(ClientManager.WLS_MAX_THREADS);
        if (wlsMaxThreadsStr != null) {
            try {
                int wlsMaxThreads = Integer.valueOf(wlsMaxThreadsStr);
                threadPoolConfig.setMaxPoolSize(wlsMaxThreads);
            } catch (Exception e) {
                LOGGER.log(Level.CONFIG, String.format("Invalid type of configuration property of %s , %s cannot be cast to Integer", ClientManager.WLS_MAX_THREADS, wlsMaxThreadsStr));
            }
        }

        Integer containerIdleTimeout = Utils.getProperty(properties, ClientProperties.SHARED_CONTAINER_IDLE_TIMEOUT, Integer.class);

        if (uri.getScheme().equalsIgnoreCase("wss")) {
            Object sslEngineConfiguratorObject = properties.get(ClientProperties.SSL_ENGINE_CONFIGURATOR);

            SslFilter sslFilter = null;

            if (sslEngineConfiguratorObject != null) {
                // property is set, we need to figure out whether new or deprecated one is used and act accordingly.
                if (sslEngineConfiguratorObject instanceof SslEngineConfigurator) {
                    sslFilter = new SslFilter(writeQueue, (SslEngineConfigurator) sslEngineConfiguratorObject);
                } else if (sslEngineConfiguratorObject instanceof org.glassfish.tyrus.container.jdk.client.SslEngineConfigurator) {
                    sslFilter = new SslFilter(writeQueue, (org.glassfish.tyrus.container.jdk.client.SslEngineConfigurator) sslEngineConfiguratorObject);
                } else {
                    LOGGER.log(Level.WARNING, "Invalid '" + ClientProperties.SSL_ENGINE_CONFIGURATOR + "' property value: " + sslEngineConfiguratorObject +
                            ". Using system defaults.");
                }
            }

            // if we are trying to access "wss" scheme and we don't have sslEngineConfigurator instance
            // we should try to create ssl connection using JVM properties.
            if (sslFilter == null) {
                SslContextConfigurator defaultConfig = new SslContextConfigurator();
                defaultConfig.retrieve(System.getProperties());

                String wlsSslTrustStore = (String) cec.getUserProperties().get(ClientManager.WLS_SSL_TRUSTSTORE_PROPERTY);
                String wlsSslTrustStorePassword = (String) cec.getUserProperties().get(ClientManager.WLS_SSL_TRUSTSTORE_PWD_PROPERTY);

                if (wlsSslTrustStore != null) {
                    defaultConfig.setTrustStoreFile(wlsSslTrustStore);

                    if (wlsSslTrustStorePassword != null) {
                        defaultConfig.setTrustStorePassword(wlsSslTrustStorePassword);
                    }
                }

                // client mode = true, needClientAuth = false, wantClientAuth = false
                SslEngineConfigurator sslEngineConfigurator = new SslEngineConfigurator(defaultConfig, true, false, false);
                String wlsSslProtocols = (String) cec.getUserProperties().get(ClientManager.WLS_SSL_PROTOCOLS_PROPERTY);
                if (wlsSslProtocols != null) {
                    sslEngineConfigurator.setEnabledProtocols(wlsSslProtocols.split(","));
                }
                sslFilter = new SslFilter(writeQueue, sslEngineConfigurator);
            }

            // sslFilter is never null at this point.
            transportFilter = new TransportFilter(sslFilter, SSL_INPUT_BUFFER_SIZE, threadPoolConfig, containerIdleTimeout);
        } else {
            transportFilter = new TransportFilter(writeQueue, INPUT_BUFFER_SIZE, threadPoolConfig, containerIdleTimeout);
        }

        processProxy(properties, uri);
        connect(clientFilter, transportFilter, uri);
    }

    private SocketAddress getServerAddress(URI uri) {
        int port = uri.getPort();
        if (port == -1) {
            String scheme = uri.getScheme();
            assert scheme != null && (scheme.equals("ws") || scheme.equals("wss"));
            if (scheme.equals("ws")) {
                port = 80;
            } else if (scheme.equals("wss")) {
                port = 443;
            }
        }
        return new InetSocketAddress(uri.getHost(), port);
    }

    private void connect(ClientFilter clientFilter, TransportFilter transportFilter, URI uri) throws DeploymentException, IOException {
        for (Proxy proxy : proxies) {
            // Proxy.Type.DIRECT is always present and is always last.
            if (proxy.type() == Proxy.Type.DIRECT) {
                clientFilter.setProxy(false);
                transportFilter.connect(getServerAddress(uri), null);
                return;
            }
            clientFilter.setProxy(true);
            LOGGER.log(Level.CONFIG, String.format("Connecting to '%s' via proxy '%s'.", uri, proxy));
            // default ProxySelector always returns proxies with unresolved addresses.
            SocketAddress proxyAddress = proxy.address();
            if (proxyAddress instanceof InetSocketAddress) {
                InetSocketAddress inetSocketAddress = (InetSocketAddress) proxyAddress;
                if (inetSocketAddress.isUnresolved()) {
                    // resolve the address.
                    proxyAddress = new InetSocketAddress(inetSocketAddress.getHostName(), inetSocketAddress.getPort());
                }
            }
            final AtomicBoolean success = new AtomicBoolean(false);
            final CountDownLatch connectLatch = new CountDownLatch(1);
            try {
                transportFilter.connect(proxyAddress, new CompletionHandler<Void, Void>() {
                    @Override
                    public void completed(Void result, Void attachment) {
                        success.set(true);
                        connectLatch.countDown();
                    }

                    @Override
                    public void failed(Throwable exc, Void attachment) {
                        connectLatch.countDown();
                    }
                });
                connectLatch.await();
                if (success.get()) {
                    return;
                }
            } catch (IOException | InterruptedException e) {
                LOGGER.log(Level.FINE, "Connecting to " + proxyAddress + " failed", e);
            }
        }
    }

    private void processProxy(Map<String, Object> properties, URI uri) throws DeploymentException {
        String wlsProxyHost = null;
        Integer wlsProxyPort = null;

        Object value = properties.get(ClientManager.WLS_PROXY_HOST);
        if (value != null) {
            if (value instanceof String) {
                wlsProxyHost = (String) value;
            } else {
                throw new DeploymentException(ClientManager.WLS_PROXY_HOST + " only accept String values.");
            }
        }

        value = properties.get(ClientManager.WLS_PROXY_PORT);
        if (value != null) {
            if (value instanceof Integer) {
                wlsProxyPort = (Integer) value;
            } else {
                throw new DeploymentException(ClientManager.WLS_PROXY_PORT + " only accept Integer values.");
            }
        }

        if (wlsProxyHost != null) {
            proxies.add(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(wlsProxyHost, wlsProxyPort == null ? 80 : wlsProxyPort)));
        } else {
            Object proxyString = properties.get(ClientProperties.PROXY_URI);
            try {
                URI proxyUri;
                if (proxyString != null) {
                    proxyUri = new URI(proxyString.toString());
                    if (proxyUri.getHost() == null) {
                        LOGGER.log(Level.WARNING, String.format("Invalid proxy '%s'.", proxyString));
                    } else {
                        // proxy set via properties
                        int proxyPort = proxyUri.getPort() == -1 ? 80 : proxyUri.getPort();
                        proxies.add(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyUri.getHost(), proxyPort)));
                    }
                }
            } catch (URISyntaxException e) {
                LOGGER.log(Level.WARNING, String.format("Invalid proxy '%s'.", proxyString), e);
            }
        }

        // ProxySelector
        final ProxySelector proxySelector = ProxySelector.getDefault();

        // see WebSocket Protocol RFC, chapter 4.1.3: http://tools.ietf.org/html/rfc6455#section-4.1
        addProxies(proxySelector, uri, "socket", proxies);
        addProxies(proxySelector, uri, "https", proxies);
        addProxies(proxySelector, uri, "http", proxies);
        proxies.add(Proxy.NO_PROXY);
    }

    private void addProxies(ProxySelector proxySelector, URI uri, String scheme, List<Proxy> proxies) {
        for (Proxy p : proxySelector.select(getProxyUri(uri, scheme))) {
            switch (p.type()) {
                case HTTP:
                    LOGGER.log(Level.FINE, String.format("Found proxy: '%s'", p));
                    proxies.add(p);
                    break;
                case SOCKS:
                    LOGGER.log(Level.INFO, String.format("Socks proxy is not supported, please file new issue at https://java.net/jira/browse/TYRUS. Proxy '%s' will be ignored.", p));
                    break;
                default:
                    break;
            }
        }
    }

    private URI getProxyUri(URI wsUri, String scheme) {
        try {
            return new URI(scheme, wsUri.getUserInfo(), wsUri.getHost(), wsUri.getPort(), wsUri.getPath(), wsUri.getQuery(), wsUri.getFragment());
        } catch (URISyntaxException e) {
            LOGGER.log(Level.WARNING, String.format("Exception during generating proxy URI '%s'", wsUri), e);
            return wsUri;
        }
    }

    private Map<String, String> getProxyHeaders(Map<String, Object> properties) throws DeploymentException {
        //noinspection unchecked
        Map<String, String> proxyHeaders = Utils.getProperty(properties, ClientProperties.PROXY_HEADERS, Map.class);

        String wlsProxyUsername = null;
        String wlsProxyPassword = null;

        Object value = properties.get(ClientManager.WLS_PROXY_USERNAME);
        if (value != null) {
            if (value instanceof String) {
                wlsProxyUsername = (String) value;
            } else {
                throw new DeploymentException(ClientManager.WLS_PROXY_USERNAME + " only accept String values.");
            }
        }

        value = properties.get(ClientManager.WLS_PROXY_PASSWORD);
        if (value != null) {
            if (value instanceof String) {
                wlsProxyPassword = (String) value;
            } else {
                throw new DeploymentException(ClientManager.WLS_PROXY_PASSWORD + " only accept String values.");
            }
        }

        if (proxyHeaders == null) {
            if (wlsProxyUsername != null && wlsProxyPassword != null) {
                proxyHeaders = new HashMap<String, String>();
                proxyHeaders.put("Proxy-Authorization", "Basic " +
                        Base64Utils.encodeToString((wlsProxyUsername + ":" + wlsProxyPassword).getBytes(Charset.forName("UTF-8")), false));
            }
        } else {
            boolean proxyAuthPresent = false;
            for (Map.Entry<String, String> entry : proxyHeaders.entrySet()) {
                if (entry.getKey().equalsIgnoreCase("Proxy-Authorization")) {
                    proxyAuthPresent = true;
                }
            }

            // if (proxyAuthPresent == true) then do nothing, proxy authorization header is already added.
            if (!proxyAuthPresent && wlsProxyUsername != null && wlsProxyPassword != null) {
                proxyHeaders.put("Proxy-Authorization", "Basic " +
                        Base64Utils.encodeToString((wlsProxyUsername + ":" + wlsProxyPassword).getBytes(Charset.forName("UTF-8")), false));
            }
        }
        return proxyHeaders;
    }
}
