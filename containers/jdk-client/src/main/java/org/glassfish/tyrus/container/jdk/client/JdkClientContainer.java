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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;

import org.glassfish.tyrus.client.ClientManager;
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
    private static final Logger logger = Logger.getLogger(JdkClientContainer.class.getName());

    private final List<Proxy> proxies = new ArrayList<>();

    @Override
    public void openClientSocket(String url, ClientEndpointConfig cec, Map<String, Object> properties, ClientEngine clientEngine) throws DeploymentException, IOException {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new DeploymentException("Invalid URI.", e);
        }

        boolean sslEnabled = false;
        TransportFilter transportFilter;

        if (uri.getScheme().equalsIgnoreCase("wss")) {
            sslEnabled = true;
        }
        boolean proxy = false;
        String proxyUri = null;
        if (properties.get(ClientManager.PROXY_URI) != null) {
            proxy = true;
            if (!(properties.get(ClientManager.PROXY_URI) instanceof String)) {
                throw new DeploymentException("Proxy URI must be passed as String");
            }
            proxyUri = (String) properties.get(ClientManager.PROXY_URI);
        }

        final ClientFilter clientFilter = new ClientFilter(clientEngine, uri, proxy);
        final TaskQueueFilter writeQueue = new TaskQueueFilter(clientFilter);
        if (sslEnabled) {
            SslFilter sslConnection = new SslFilter(writeQueue);
            transportFilter = new TransportFilter(sslConnection, SSL_INPUT_BUFFER_SIZE);
        } else {
            transportFilter = new TransportFilter(writeQueue, INPUT_BUFFER_SIZE);
        }

        if (proxy) {
            processProxy(proxyUri, uri);
            connectThroughProxy(transportFilter, uri);
            return;
        }

        transportFilter.connect(getServerAddress(uri), null);
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

    private void connectThroughProxy(TransportFilter transportFilter, URI uri) throws DeploymentException {
        for (Proxy proxy : proxies) {
            if (proxy.type() == Proxy.Type.DIRECT) {
                throw new DeploymentException("No proxy found");
            }
            logger.log(Level.CONFIG, String.format("Connecting to '%s' via proxy '%s'.", uri, proxy));
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
                        connectLatch.countDown();
                        success.set(true);
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
                logger.log(Level.FINE, "Connecting to " + proxyAddress + " failed", e);
            }
        }
        throw new DeploymentException("Failed to connect to all proxies.");
    }

    private void processProxy(String proxyString, URI uri) {
        URI proxyUri;
        try {
            if (proxyString != null) {
                proxyUri = new URI(proxyString);
                if (proxyUri.getHost() == null) {
                    logger.log(Level.WARNING, String.format("Invalid proxy '%s'.", proxyString));
                } else {
                    // proxy set via properties
                    int proxyPort = proxyUri.getPort() == -1 ? 80 : proxyUri.getPort();
                    proxies.add(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyUri.getHost(), proxyPort)));
                }
            }
        } catch (URISyntaxException e) {
            logger.log(Level.WARNING, String.format("Invalid proxy '%s'.", proxyString), e);
        }

        // ProxySelector
        final ProxySelector proxySelector = ProxySelector.getDefault();

        // see WebSocket Protocol RFC, chapter 4.1.3: http://tools.ietf.org/html/rfc6455#section-4.1
        addProxies(proxySelector, uri, "socket", proxies);
        addProxies(proxySelector, uri, "https", proxies);
        addProxies(proxySelector, uri, "http", proxies);
    }

    private void addProxies(ProxySelector proxySelector, URI uri, String scheme, List<Proxy> proxies) {
        for (Proxy p : proxySelector.select(getProxyUri(uri, scheme))) {
            switch (p.type()) {
                case HTTP:
                    logger.log(Level.FINE, String.format("Found proxy: '%s'", p));
                    proxies.add(p);
                    break;
                case SOCKS:
                    logger.log(Level.INFO, String.format("Socks proxy is not supported, please file new issue at https://java.net/jira/browse/TYRUS. Proxy '%s' will be ignored.", p));
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
            logger.log(Level.WARNING, String.format("Exception during generating proxy URI '%s'", wsUri), e);
            return wsUri;
        }
    }
}
