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
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.CloseReason;

import org.glassfish.tyrus.core.CloseReasons;
import org.glassfish.tyrus.core.TyrusUpgradeResponse;
import org.glassfish.tyrus.spi.ClientEngine;
import org.glassfish.tyrus.spi.ClientEngine.TimeoutHandler;
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
    private final ClientEngine engine;
    private final URI uri;
    private final HttpResponseParser responseParser = new HttpResponseParser();
    private final boolean proxy;

    private volatile Connection wsConnection;
    private volatile boolean connectedToProxy = false;
    private volatile UpgradeRequest upgradeRequest;

    /**
     * Constructor.
     *
     * @param engine client engine instance.
     * @param uri    URI to be used for creating {@link org.glassfish.tyrus.spi.UpgradeRequest}.
     * @param proxy  {@code true} if the connection will be established via proxy, {@code false} otherwise.
     */
    ClientFilter(ClientEngine engine, URI uri, boolean proxy) {
        this.engine = engine;
        this.uri = uri;
        this.proxy = proxy;
    }

    @Override
    public void onConnect(final Filter downstreamFilter) {
        upgradeRequest = engine.createUpgradeRequest(uri, new TimeoutHandler() {
            @Override
            public void handleTimeout() {
                downstreamFilter.close();
            }
        });

        final JdkUpgradeRequest handshakeUpgradeRequest;
        if (!proxy) {
            downstreamFilter.startSsl();
            handshakeUpgradeRequest = createHandshakeUpgradeRequest(upgradeRequest);
        } else {
            handshakeUpgradeRequest = createProxyUpgradeRequest(upgradeRequest);
        }

        downstreamFilter.write(HttpRequestBuilder.build(handshakeUpgradeRequest), new CompletionHandler<ByteBuffer>() {
            @Override
            public void failed(Throwable throwable) {
                closeConnection(downstreamFilter);
            }
        });
    }

    @Override
    public void onRead(final Filter downstreamFilter, ByteBuffer data) {
        if (proxy && !connectedToProxy) {
            responseParser.appendData(data);
            if (!responseParser.isComplete()) {
                return;
            }
            TyrusUpgradeResponse tyrusUpgradeResponse;
            try {
                tyrusUpgradeResponse = responseParser.parseUpgradeResponse();
            } catch (ParseException e) {
                LOGGER.log(Level.SEVERE, "Parsing HTTP proxy response failed", e);
                closeConnection(downstreamFilter);
                return;
            }
            responseParser.clear();
            if (tyrusUpgradeResponse.getStatus() != 200) {
                LOGGER.log(Level.SEVERE, "Could not connect to proxy: " + tyrusUpgradeResponse.getStatus());
                closeConnection(downstreamFilter);
                return;
            }
            connectedToProxy = true;
            downstreamFilter.startSsl();
            downstreamFilter.write(HttpRequestBuilder.build(createHandshakeUpgradeRequest(upgradeRequest)), new CompletionHandler<ByteBuffer>() {
                @Override
                public void failed(Throwable throwable) {
                    closeConnection(downstreamFilter);
                }
            });
            return;
        }

        if (wsConnection == null) {
            responseParser.appendData(data);
            if (!responseParser.isComplete()) {
                return;
            }
            TyrusUpgradeResponse tyrusUpgradeResponse;
            try {
                tyrusUpgradeResponse = responseParser.parseUpgradeResponse();
            } catch (ParseException e) {
                LOGGER.log(Level.SEVERE, "Parsing HTTP handshake response failed", e);
                closeConnection(downstreamFilter);
                return;
            }
            responseParser.destroy();
            handleUpgradeResponse(downstreamFilter, tyrusUpgradeResponse);
            if (wsConnection == null) {
                closeConnection(downstreamFilter);
                return;
            }
        }
        wsConnection.getReadHandler().handle(data);
    }

    @Override
    public void onConnectionClosed() {
        if (wsConnection == null) {
            return;
        }
        wsConnection.close(CloseReasons.CLOSED_ABNORMALLY.getCloseReason());
    }

    private void closeConnection(Filter downstreamFilter) {
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

    private void handleUpgradeResponse(final Filter downstreamFilter, TyrusUpgradeResponse tyrusUpgradeResponse) {
        JdkWriter writer = new JdkWriter(downstreamFilter);
        wsConnection = engine.processResponse(
                tyrusUpgradeResponse,
                writer,
                new CloseListener() {

                    @Override
                    public void close(CloseReason reason) {
                        closeConnection(downstreamFilter);
                    }
                }
        );
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

    private JdkUpgradeRequest createProxyUpgradeRequest(final UpgradeRequest upgradeRequest) {
        return new JdkUpgradeRequest(upgradeRequest) {

            @Override
            public String getHttpMethod() {
                return "CONNECT";
            }

            @Override
            public String getRequestUri() {
                URI uri = URI.create(upgradeRequest.getRequestUri());
                final int requestPort = uri.getPort() == -1 ? (uri.getScheme().equals("wss") ? 443 : 80) : uri.getPort();
                return String.format("%s:%d", uri.getHost(), requestPort);
            }

            @Override
            public Map<String, List<String>> getHeaders() {
                URI uri = URI.create(upgradeRequest.getRequestUri());
                Map<String, List<String>> headers = new HashMap<>();
                headers.put("Host", Collections.singletonList(uri.getHost()));
                headers.put("ProxyConnection", Collections.singletonList("keep-alive"));
                headers.put("Connection", Collections.singletonList("keep-alive"));
                return headers;
            }

        };
    }
}
