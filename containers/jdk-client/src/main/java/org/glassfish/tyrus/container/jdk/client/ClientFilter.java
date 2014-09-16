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
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.core.Base64Utils;
import org.glassfish.tyrus.core.CloseReasons;
import org.glassfish.tyrus.core.TyrusUpgradeResponse;
import org.glassfish.tyrus.core.Utils;
import org.glassfish.tyrus.spi.ClientEngine;
import org.glassfish.tyrus.spi.CompletionHandler;
import org.glassfish.tyrus.spi.Connection;
import org.glassfish.tyrus.spi.Connection.CloseListener;
import org.glassfish.tyrus.spi.UpgradeRequest;
import org.glassfish.tyrus.spi.Writer;

/**
 * A filter that interacts with Tyrus SPI and handles proxy.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
class ClientFilter extends Filter {

    private static final Logger LOGGER = Logger.getLogger(ClientFilter.class.getName());

    private final ClientEngine clientEngine;
    private final HttpResponseParser responseParser = new HttpResponseParser();
    private final Map<String, String> proxyHeaders;
    private final UpgradeRequest upgradeRequest;
    private final Callable<Void> jdkConnector;

    private volatile boolean proxy;
    private volatile Connection wsConnection;
    private volatile boolean connectedToProxy = false;
    private volatile CompletionHandler<Void> connectCompletionHandler;

    /**
     * Constructor.
     *
     * @param downstreamFilter a filer that is positioned directly under this filter.
     * @param clientEngine     client engine instance.
     * @param properties       client properties.
     * @param jdkConnector     callback to connecting with modified {@link UpgradeRequest} if necessary.
     * @param upgradeRequest   upgrade request to be used for this client session.
     */
    // * @param proxyHeaders     map representing headers to be added to request sent to proxy (HTTP CONNECT).
    ClientFilter(Filter downstreamFilter, ClientEngine clientEngine, Map<String, Object> properties, Callable<Void> jdkConnector, UpgradeRequest upgradeRequest)
            throws DeploymentException {
        super(downstreamFilter);
        this.clientEngine = clientEngine;
        this.proxyHeaders = getProxyHeaders(properties);
        this.jdkConnector = jdkConnector;
        this.upgradeRequest = upgradeRequest;
    }

    /**
     * @param address                  an address where to connect (server or proxy).
     * @param proxy                    {@code true} if the connection will be established via proxy, {@code false} otherwise.
     * @param connectCompletionHandler completion handler.
     */
    void connect(SocketAddress address, boolean proxy, CompletionHandler<Void> connectCompletionHandler) {
        this.connectCompletionHandler = connectCompletionHandler;
        this.proxy = proxy;
        downstreamFilter.connect(address, this);
    }

    @Override
    public void processConnect() {
        final JdkUpgradeRequest handshakeUpgradeRequest;

        if (proxy) {
            handshakeUpgradeRequest = createProxyUpgradeRequest(upgradeRequest.getRequestURI());
        } else {
            handshakeUpgradeRequest = getJdkUpgradeRequest(upgradeRequest, downstreamFilter);
        }

        sendRequest(downstreamFilter, handshakeUpgradeRequest);
    }

    private void sendRequest(final Filter downstreamFilter, JdkUpgradeRequest handshakeUpgradeRequest) {
        downstreamFilter.write(HttpRequestBuilder.build(handshakeUpgradeRequest), new CompletionHandler<ByteBuffer>() {
            @Override
            public void failed(Throwable throwable) {
                onError(throwable);
            }
        });
    }

    private JdkUpgradeRequest getJdkUpgradeRequest(final UpgradeRequest upgradeRequest, final Filter downstreamFilter) {
        downstreamFilter.startSsl();
        return createHandshakeUpgradeRequest(upgradeRequest);
    }

    @Override
    public boolean processRead(ByteBuffer data) {
        if (wsConnection == null) {

            TyrusUpgradeResponse tyrusUpgradeResponse;
            try {
                responseParser.appendData(data);

                if (!responseParser.isComplete()) {
                    return false;
                }

                try {
                    tyrusUpgradeResponse = responseParser.parseUpgradeResponse();
                } finally {
                    responseParser.clear();
                }
            } catch (ParseException e) {
                clientEngine.processError(e);
                closeConnection();
                return false;
            }

            if (proxy && !connectedToProxy) {
                if (tyrusUpgradeResponse.getStatus() != 200) {
                    LOGGER.log(Level.SEVERE, "Could not connect to proxy: " + tyrusUpgradeResponse.getStatus());
                    closeConnection();
                    return false;
                }
                connectedToProxy = true;
                downstreamFilter.startSsl();
                sendRequest(downstreamFilter, createHandshakeUpgradeRequest(upgradeRequest));
                return false;
            }

            JdkWriter writer = new JdkWriter(downstreamFilter);

            ClientEngine.ClientUpgradeInfo clientUpgradeInfo = clientEngine.processResponse(
                    tyrusUpgradeResponse,
                    writer,
                    new CloseListener() {

                        @Override
                        public void close(CloseReason reason) {
                            closeConnection();
                        }
                    }
            );

            switch (clientUpgradeInfo.getUpgradeStatus()) {
                case ANOTHER_UPGRADE_REQUEST_REQUIRED:
                    closeConnection();
                    try {
                        jdkConnector.call();
                    } catch (Exception e) {
                        closeConnection();
                        clientEngine.processError(e);
                    }
                    break;
                case SUCCESS:
                    wsConnection = clientUpgradeInfo.createConnection();

                    if (data.hasRemaining()) {
                        wsConnection.getReadHandler().handle(data);
                    }

                    break;
                case UPGRADE_REQUEST_FAILED:
                    closeConnection();
                    break;
                default:
                    break;
            }
        } else {
            wsConnection.getReadHandler().handle(data);
        }

        return false;
    }

    @Override
    public void processConnectionClosed() {
        LOGGER.log(Level.FINE, "Connection has been closed by the server");

        if (wsConnection == null) {
            return;
        }

        wsConnection.close(CloseReasons.CLOSED_ABNORMALLY.getCloseReason());
    }

    @Override
    void processError(Throwable t) {
        // connectCompletionHandler != null means that we are still in "connecting state".
        if (connectCompletionHandler != null) {
            downstreamFilter.close();
            connectCompletionHandler.failed(t);
            return;
        }

        LOGGER.log(Level.SEVERE, "Connection error has occurred", t);
        if (wsConnection != null) {
            wsConnection.close(CloseReasons.CLOSED_ABNORMALLY.getCloseReason());
        }

        downstreamFilter.close();
    }

    @Override
    void processSslHandshakeCompleted() {
        // the connection is considered established at this point
        connectCompletionHandler.completed(null);
        connectCompletionHandler = null;
    }

    @Override
    void close() {
        closeConnection();
    }

    private void closeConnection() {
        downstreamFilter.close();
    }

    private static class JdkWriter extends Writer {

        private final Filter downstreamFilter;

        JdkWriter(Filter downstreamFilter) {
            this.downstreamFilter = downstreamFilter;
        }

        @Override
        public void close() throws IOException {
            downstreamFilter.close();
        }

        @Override
        public void write(ByteBuffer buffer, CompletionHandler<ByteBuffer> completionHandler) {
            downstreamFilter.write(buffer, completionHandler);
        }
    }

    private JdkUpgradeRequest createHandshakeUpgradeRequest(final UpgradeRequest upgradeRequest) {
        return new JdkUpgradeRequest(upgradeRequest) {

            @Override
            public String getHttpMethod() {
                return "GET";
            }

            @Override
            public String getRequestUri() {
                StringBuilder sb = new StringBuilder();
                final URI uri = URI.create(upgradeRequest.getRequestUri());
                sb.append(uri.getPath());
                final String query = uri.getQuery();
                if (query != null) {
                    sb.append('?').append(query);
                }
                if (sb.length() == 0) {
                    sb.append('/');
                }
                return sb.toString();
            }
        };
    }

    private JdkUpgradeRequest createProxyUpgradeRequest(final URI uri) {
        return new JdkUpgradeRequest(null) {

            @Override
            public String getHttpMethod() {
                return "CONNECT";
            }

            @Override
            public String getRequestUri() {
                final int requestPort = Utils.getWsPort(uri);
                return String.format("%s:%d", uri.getHost(), requestPort);
            }

            @Override
            public Map<String, List<String>> getHeaders() {
                Map<String, List<String>> headers = new HashMap<>();
                if (proxyHeaders != null) {
                    for (Map.Entry<String, String> entry : proxyHeaders.entrySet()) {
                        headers.put(entry.getKey(), Collections.singletonList(entry.getValue()));
                    }
                }
                headers.put("Host", Collections.singletonList(uri.getHost()));
                headers.put("ProxyConnection", Collections.singletonList("keep-alive"));
                headers.put("Connection", Collections.singletonList("keep-alive"));
                return headers;
            }
        };
    }


    private static Map<String, String> getProxyHeaders(Map<String, Object> properties) throws DeploymentException {
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
                proxyHeaders = new HashMap<>();
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
