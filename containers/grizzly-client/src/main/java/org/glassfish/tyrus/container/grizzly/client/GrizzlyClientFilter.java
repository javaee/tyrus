/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.container.grizzly.client;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.CloseReason;

import org.glassfish.tyrus.core.CloseReasons;
import org.glassfish.tyrus.core.TyrusUpgradeResponse;
import org.glassfish.tyrus.core.Utils;
import org.glassfish.tyrus.spi.ClientEngine;
import org.glassfish.tyrus.spi.ReadHandler;
import org.glassfish.tyrus.spi.UpgradeRequest;
import org.glassfish.tyrus.spi.UpgradeResponse;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.HttpServerFilter;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HttpStatus;

/**
 * WebSocket {@link Filter} implementation, which supposed to be placed into a {@link FilterChain} right after HTTP
 * Filter: {@link HttpServerFilter}, {@link HttpClientFilter}; depending whether it's server or client side. The
 * <tt>WebSocketFilter</tt> handles websocket connection, handshake phases and, when receives a websocket frame -
 * redirects it to appropriate connection ({@link org.glassfish.tyrus.core.TyrusEndpointWrapper}, {@link org.glassfish.tyrus.core.TyrusWebSocket}) for processing.
 *
 * @author Alexey Stashok
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
class GrizzlyClientFilter extends BaseFilter {

    private static final Logger LOGGER = Grizzly.logger(GrizzlyClientFilter.class);

    private static final Attribute<org.glassfish.tyrus.spi.Connection> TYRUS_CONNECTION = Grizzly.DEFAULT_ATTRIBUTE_BUILDER
            .createAttribute(GrizzlyClientFilter.class.getName() + ".Connection");

    private static final Attribute<UpgradeRequest> UPGRADE_REQUEST = Grizzly.DEFAULT_ATTRIBUTE_BUILDER
            .createAttribute(GrizzlyClientFilter.class.getName() + ".UpgradeRequest");

    private final boolean proxy;
    private final Filter sslFilter;
    private final HttpCodecFilter httpCodecFilter;
    private final ClientEngine engine;
    private final URI uri;
    private final ClientEngine.TimeoutHandler timeoutHandler;
    private final boolean sharedTransport;
    private final Map<String, String> proxyHeaders;

    private final Queue<TaskProcessor.Task> taskQueue = new ConcurrentLinkedQueue<TaskProcessor.Task>();

    // ------------------------------------------------------------ Constructors

    /**
     * Constructs a new {@link GrizzlyClientFilter}.
     *
     * @param proxy     true when client initiated connection has proxy in the way.
     * @param sslFilter filter to be "enabled" in case connection is created via proxy.
     */
    /* package */ GrizzlyClientFilter(ClientEngine engine, boolean proxy,
                                      Filter sslFilter, HttpCodecFilter httpCodecFilter,
                                      URI uri, ClientEngine.TimeoutHandler timeoutHandler, boolean sharedTransport,
                                      Map<String, String> proxyHeaders) {
        this.engine = engine;
        this.proxy = proxy;
        this.sslFilter = sslFilter;
        this.httpCodecFilter = httpCodecFilter;
        this.uri = uri;
        this.timeoutHandler = timeoutHandler;
        this.sharedTransport = sharedTransport;
        this.proxyHeaders = proxyHeaders;
    }

    // ----------------------------------------------------- Methods from Filter

    /**
     * Method handles Grizzly {@link Connection} connect phase. Check if the {@link Connection} is a client-side {@link
     * org.glassfish.tyrus.core.TyrusWebSocket}, if yes - creates websocket handshake packet and send it to a server. Otherwise, if it's not websocket
     * connection - pass processing to the next {@link Filter} in a chain.
     *
     * @param ctx {@link FilterChainContext}
     * @return {@link NextAction} instruction for {@link FilterChain}, how it should continue the execution
     */
    @Override
    public NextAction handleConnect(final FilterChainContext ctx) {
        LOGGER.log(Level.FINEST, "handleConnect");

        final UpgradeRequest upgradeRequest = engine.createUpgradeRequest(uri, timeoutHandler);

        HttpRequestPacket.Builder builder = HttpRequestPacket.builder();

        if (proxy) {
            UPGRADE_REQUEST.set(ctx.getConnection(), upgradeRequest);

            final URI requestURI = URI.create(upgradeRequest.getRequestUri());
            final int requestPort = requestURI.getPort() == -1 ? (requestURI.getScheme().equals("wss") ? 443 : 80) : requestURI.getPort();

            builder = builder.uri(String.format("%s:%d", requestURI.getHost(), requestPort));
            builder = builder.protocol(Protocol.HTTP_1_1);
            builder = builder.method(Method.CONNECT);

            if (proxyHeaders != null && proxyHeaders.size() > 0) {
                for (Map.Entry<String, String> entry : proxyHeaders.entrySet()) {
                    builder.header(entry.getKey(), entry.getValue());
                }
            }

            builder = builder.header(Header.Host, requestURI.getHost());
            builder = builder.header(Header.ProxyConnection, "keep-alive");
            builder = builder.header(Header.Connection, "keep-alive");
            ctx.write(HttpContent.builder(builder.build()).build());
            ctx.flush(null);
        } else {
            ctx.write(getHttpContent(upgradeRequest));
        }

        // call the next filter in the chain
        return ctx.getInvokeAction();
    }

    /**
     * Method handles Grizzly {@link Connection} close phase. Check if the {@link Connection} is a {@link org.glassfish.tyrus.core.TyrusWebSocket}, if
     * yes - tries to close the websocket gracefully (sending close frame) and calls {@link
     * org.glassfish.tyrus.core.TyrusWebSocket#onClose(org.glassfish.tyrus.core.frame.CloseFrame)}. If the Grizzly {@link Connection} is not websocket - passes processing to the next
     * filter in the chain.
     *
     * @param ctx {@link FilterChainContext}
     * @return {@link NextAction} instruction for {@link FilterChain}, how it should continue the execution
     * @throws IOException
     */
    @Override
    public NextAction handleClose(FilterChainContext ctx) throws IOException {

        final org.glassfish.tyrus.spi.Connection connection = TYRUS_CONNECTION.get(ctx.getConnection());
        if (connection != null) {
            taskQueue.add(new CloseTask(connection, CloseReasons.CLOSED_ABNORMALLY.getCloseReason(), ctx.getConnection()));
            TaskProcessor.processQueue(taskQueue, null);
        }
        return ctx.getStopAction();
    }

    /**
     * Handle Grizzly {@link Connection} read phase. If the {@link Connection} has associated {@link org.glassfish.tyrus.core.TyrusWebSocket} object
     * (websocket connection), we check if websocket handshake has been completed for this connection, if not -
     * initiate/validate handshake. If handshake has been completed - parse websocket {@link org.glassfish.tyrus.core.frame.Frame}s one by one and
     * pass processing to appropriate {@link org.glassfish.tyrus.core.TyrusWebSocket}: {@link org.glassfish.tyrus.core.TyrusEndpointWrapper} for server- and client- side
     * connections.
     *
     * @param ctx {@link FilterChainContext}
     * @return {@link NextAction} instruction for {@link FilterChain}, how it should continue the execution
     * @throws IOException TODO
     */
    @Override
    @SuppressWarnings("unchecked")
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
        // Get the parsed HttpContent (we assume prev. filter was HTTP)
        final HttpContent message = ctx.getMessage();

        final Connection grizzlyConnection = ctx.getConnection();
        final org.glassfish.tyrus.spi.Connection tyrusConnection = TYRUS_CONNECTION.get(grizzlyConnection);

        // Get the HTTP header
        final HttpHeader header = message.getHttpHeader();

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "handleRead websocket: {0} content-size={1} headers=\n{2}",
                    new Object[]{tyrusConnection, message.getContent().remaining(), header});
        }

        // client
        if (tyrusConnection != null) {
            // this is websocket with completed handshake
            if (message.getContent().hasRemaining()) {

                // get the frame(s) content
                Buffer buffer = message.getContent();
                final ByteBuffer webSocketBuffer = buffer.toByteBuffer();
                message.recycle();
                final ReadHandler readHandler = tyrusConnection.getReadHandler();

                taskQueue.add(new ProcessTask(webSocketBuffer, readHandler));

                TaskProcessor.processQueue(taskQueue, null);
            }
            return ctx.getStopAction();
        }

        // tyrusConnection == null

        // proxy
        final HttpStatus httpStatus = ((HttpResponsePacket) message.getHttpHeader()).getHttpStatus();

        if (httpStatus.getStatusCode() != 101) {
            if (proxy) {
                if (httpStatus == HttpStatus.OK_200) {

                    // TYRUS-221: Proxy handshake is complete, we need to enable SSL layer for secure ("wss")
                    // connections now.
                    if (sslFilter != null) {
                        ((GrizzlyClientSocket.FilterWrapper) sslFilter).enable();
                    }

                    httpCodecFilter.resetResponseProcessing(grizzlyConnection);

                    final UpgradeRequest upgradeRequest = UPGRADE_REQUEST.get(grizzlyConnection);
                    ctx.write(getHttpContent(upgradeRequest));
                    UPGRADE_REQUEST.remove(grizzlyConnection);
                } else {
                    throw new IOException(String.format("Proxy error. %s: %s", httpStatus.getStatusCode(),
                            new String(httpStatus.getReasonPhraseBytes(), "UTF-8")));
                }

                return ctx.getInvokeAction();
            }
        }

        // If websocket is null - it means either non-websocket Connection
        if (!UpgradeRequest.WEBSOCKET.equalsIgnoreCase(header.getUpgrade()) && message.getHttpHeader().isRequest()) {
            // if it's not a websocket connection - pass the processing to the next filter
            return ctx.getInvokeAction();
        }

        final String ATTR_NAME = "org.glassfish.tyrus.container.grizzly.WebSocketFilter.HANDSHAKE_PROCESSED";

        final AttributeHolder attributeHolder = ctx.getAttributes();
        if (attributeHolder != null) {
            final Object attribute = attributeHolder.getAttribute(ATTR_NAME);
            if (attribute != null) {
                // handshake was already performed on this context.
                return ctx.getInvokeAction();
            } else {
                attributeHolder.setAttribute(ATTR_NAME, true);
            }
        }
        // Handle handshake
        return handleHandshake(ctx, message);
    }

    // --------------------------------------------------------- Private Methods

    /**
     * Handle websocket handshake
     *
     * @param ctx     {@link FilterChainContext}
     * @param content HTTP message
     * @return {@link NextAction} instruction for {@link FilterChain}, how it should continue the execution
     */
    private NextAction handleHandshake(final FilterChainContext ctx, HttpContent content) {

        final GrizzlyWriter grizzlyWriter = new GrizzlyWriter(ctx.getConnection()) {
            @Override
            public void close() {
                super.close();
                try {
                    if (sharedTransport) {
                        connection.close();
                    } else {
                        connection.getTransport().shutdownNow();
                    }
                } catch (IOException e) {
                    Logger.getLogger(GrizzlyClientFilter.class.getName()).log(Level.INFO, "Exception thrown during shutdown.", e);
                }
            }
        };

        final org.glassfish.tyrus.spi.Connection tyrusConnection = engine.processResponse(
                getUpgradeResponse((HttpResponsePacket) content.getHttpHeader()),
                grizzlyWriter,
                new org.glassfish.tyrus.spi.Connection.CloseListener() {
                    @Override
                    public void close(CloseReason reason) {
                        // TODO?
                        // Writer.close() should be called anyway, so is there a need for a CloseListener on client side?
                        grizzlyWriter.close();
                    }
                }
        );

        if (tyrusConnection == null) {
            return ctx.getStopAction();
        }

        TYRUS_CONNECTION.set(ctx.getConnection(), tyrusConnection);

        if (content.getContent().hasRemaining()) {
            return ctx.getRerunFilterAction();
        } else {
            content.recycle();
            return ctx.getStopAction();
        }
    }

    private static UpgradeResponse getUpgradeResponse(HttpResponsePacket httpResponsePacket) {
        TyrusUpgradeResponse tyrusUpgradeResponse = new TyrusUpgradeResponse();

        for (String name : httpResponsePacket.getHeaders().names()) {
            final List<String> values = tyrusUpgradeResponse.getHeaders().get(name);
            if (values == null) {
                tyrusUpgradeResponse.getHeaders().put(name, Utils.parseHeaderValue(httpResponsePacket.getHeader(name)));
            } else {
                values.addAll(Utils.parseHeaderValue(httpResponsePacket.getHeader(name)));
            }
        }

        tyrusUpgradeResponse.setStatus(httpResponsePacket.getStatus());

        return tyrusUpgradeResponse;
    }

    /**
     * Create HttpContent (Grizzly request representation) from {@link UpgradeRequest}.
     *
     * @param request original request.
     * @return Grizzly representation of provided request.
     */
    private HttpContent getHttpContent(UpgradeRequest request) {
        HttpRequestPacket.Builder builder = HttpRequestPacket.builder();
        builder = builder.protocol(Protocol.HTTP_1_1);
        builder = builder.method(Method.GET);

        StringBuilder sb = new StringBuilder();
        final URI uri = URI.create(request.getRequestUri());
        sb.append(uri.getPath());
        final String query = uri.getQuery();
        if (query != null) {
            sb.append('?').append(query);
        }
        if (sb.length() == 0) {
            sb.append('/');
        }
        builder = builder.uri(sb.toString());

        for (Map.Entry<String, List<String>> headerEntry : request.getHeaders().entrySet()) {
            StringBuilder finalHeaderValue = new StringBuilder();

            for (String headerValue : headerEntry.getValue()) {
                if (finalHeaderValue.length() != 0) {
                    finalHeaderValue.append(", ");
                }

                finalHeaderValue.append(headerValue);
            }

            builder.header(headerEntry.getKey(), finalHeaderValue.toString());
        }
        return HttpContent.builder(builder.build()).build();
    }

    private class ProcessTask extends TaskProcessor.Task {
        private final ByteBuffer buffer;
        private final ReadHandler readHandler;

        private ProcessTask(ByteBuffer buffer, ReadHandler readHandler) {
            this.buffer = buffer;
            this.readHandler = readHandler;
        }

        @Override
        public void execute() {
            readHandler.handle(buffer);
        }
    }

    private class CloseTask extends TaskProcessor.Task {
        private final org.glassfish.tyrus.spi.Connection connection;
        private final CloseReason closeReason;
        private final Connection grizzlyConnection;

        private CloseTask(org.glassfish.tyrus.spi.Connection connection, CloseReason closeReason, Connection grizzlyConnection) {
            this.connection = connection;
            this.closeReason = closeReason;
            this.grizzlyConnection = grizzlyConnection;
        }

        @Override
        public void execute() {
            connection.close(closeReason);
            TYRUS_CONNECTION.remove(grizzlyConnection);
        }
    }
}
