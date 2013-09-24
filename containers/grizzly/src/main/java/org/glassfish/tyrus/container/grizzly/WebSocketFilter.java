/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.container.grizzly;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.CloseReason;

import org.glassfish.tyrus.core.DataFrame;
import org.glassfish.tyrus.core.HandshakeException;
import org.glassfish.tyrus.core.RequestContext;
import org.glassfish.tyrus.core.TyrusWebSocketEngine;
import org.glassfish.tyrus.core.TyrusWebSocketEngine.WebSocketHolder;
import org.glassfish.tyrus.core.Utils;
import org.glassfish.tyrus.core.WebSocket;
import org.glassfish.tyrus.core.WebSocketRequest;
import org.glassfish.tyrus.core.WebSocketResponse;
import org.glassfish.tyrus.spi.ReadHandler;
import org.glassfish.tyrus.spi.UpgradeRequest;
import org.glassfish.tyrus.spi.UpgradeResponse;
import org.glassfish.tyrus.spi.WebSocketEngine;
import org.glassfish.tyrus.spi.Writer;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CloseListener;
import org.glassfish.grizzly.Closeable;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.ICloseType;
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
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.utils.IdleTimeoutFilter;

/**
 * WebSocket {@link Filter} implementation, which supposed to be placed into a {@link FilterChain} right after HTTP
 * Filter: {@link HttpServerFilter}, {@link HttpClientFilter}; depending whether it's server or client side. The
 * <tt>WebSocketFilter</tt> handles websocket connection, handshake phases and, when receives a websocket frame -
 * redirects it to appropriate connection ({@link org.glassfish.tyrus.core.WebSocketApplication}, {@link org.glassfish.tyrus.core.WebSocket}) for processing.
 *
 * @author Alexey Stashok
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
class WebSocketFilter extends BaseFilter {

    private static final Logger logger = Grizzly.logger(WebSocketFilter.class);

    private static final String TYRUS_CONNECTION = WebSocketFilter.class.getName() + ".Connection";

    private final Map<Connection, org.glassfish.tyrus.spi.Connection> connectionMap =
            new ConcurrentHashMap<Connection, org.glassfish.tyrus.spi.Connection>();


    static final long DEFAULT_WS_IDLE_TIMEOUT_IN_SECONDS = 15 * 60;

    private final long wsTimeoutMS;
    private final boolean proxy;
    private final Filter sslFilter;
    private final WebSocketEngine engine;

    private UpgradeRequest webSocketRequest;

    // ------------------------------------------------------------ Constructors

    /**
     * Constructs a new {@link WebSocketFilter} with a default idle connection
     * timeout of 15 minutes and with proxy turned off.
     */
    public WebSocketFilter(WebSocketEngine engine) {
        this(engine, DEFAULT_WS_IDLE_TIMEOUT_IN_SECONDS, false);
    }

    /**
     * Constructs a new {@link WebSocketFilter}.
     *
     * @param wsTimeoutInSeconds TODO
     * @param proxy              true when client initiated connection has proxy in the way.
     */
    public WebSocketFilter(WebSocketEngine engine, final long wsTimeoutInSeconds, boolean proxy) {
        this(engine, wsTimeoutInSeconds, proxy, null);
    }

    /**
     * Constructs a new {@link WebSocketFilter}.
     *
     * @param wsTimeoutInSeconds TODO
     * @param proxy              true when client initiated connection has proxy in the way.
     * @param sslFilter          filter to be "enabled" in case connection is created via proxy.
     */
    public WebSocketFilter(WebSocketEngine engine, final long wsTimeoutInSeconds, boolean proxy, Filter sslFilter) {
        if (wsTimeoutInSeconds <= 0) {
            this.wsTimeoutMS = IdleTimeoutFilter.FOREVER;
        } else {
            this.wsTimeoutMS = wsTimeoutInSeconds * 1000;
        }

        this.engine = engine;
        this.proxy = proxy;
        this.sslFilter = sslFilter;
    }

    // ----------------------------------------------------- Methods from Filter

    /**
     * Method handles Grizzly {@link Connection} connect phase. Check if the {@link Connection} is a client-side {@link
     * org.glassfish.tyrus.core.WebSocket}, if yes - creates websocket handshake packet and send it to a server. Otherwise, if it's not websocket
     * connection - pass processing to the next {@link Filter} in a chain.
     *
     * @param ctx {@link FilterChainContext}
     * @return {@link NextAction} instruction for {@link FilterChain}, how it should continue the execution
     * @throws IOException TODO
     */
    @Override
    public NextAction handleConnect(final FilterChainContext ctx) throws IOException {
        logger.log(Level.FINEST, "handleConnect");

        // Get connection
        final Writer webSocketWriter = WebSocketFilter.getWebSocketConnection(ctx);

        // TODO - remove?
        // check if it's websocket connection
//        if (webSocketWriter == null) {
//            // if not - pass processing to a next filter
//            return ctx.getInvokeAction();
//        }

        // TODO
        final WebSocketHolder webSocketHolder = ((TyrusWebSocketEngine) engine).getWebSocketHolder(webSocketWriter);
        webSocketRequest = webSocketHolder.handshake.initiate();

        HttpRequestPacket.Builder builder = HttpRequestPacket.builder();

        if (proxy) {
            final URI requestURI = URI.create(webSocketRequest.getRequestUri());
            final int requestPort = requestURI.getPort() == -1 ? (requestURI.getScheme().equals("wss") ? 443 : 80) : requestURI.getPort();

            builder = builder.uri(String.format("%s:%d", requestURI.getHost(), requestPort));
            builder = builder.protocol(Protocol.HTTP_1_1);
            builder = builder.method(Method.CONNECT);
            builder = builder.header(Header.Host, requestURI.getHost());
            builder = builder.header(Header.ProxyConnection, "keep-alive");
            builder = builder.header(Header.Connection, "keep-alive");
            ctx.write(HttpContent.builder(builder.build()).build());
            ctx.flush(null);
        } else {
            ctx.write(getHttpContent(webSocketRequest));
            ctx.flush(null);
        }

        // call the next filter in the chain
        return ctx.getInvokeAction();
    }

    /**
     * Method handles Grizzly {@link Connection} close phase. Check if the {@link Connection} is a {@link org.glassfish.tyrus.core.WebSocket}, if
     * yes - tries to close the websocket gracefully (sending close frame) and calls {@link
     * org.glassfish.tyrus.core.WebSocket#onClose(javax.websocket.CloseReason)}. If the Grizzly {@link Connection} is not websocket - passes processing to the next
     * filter in the chain.
     *
     * @param ctx {@link FilterChainContext}
     * @return {@link NextAction} instruction for {@link FilterChain}, how it should continue the execution
     * @throws IOException
     */
    @Override
    public NextAction handleClose(FilterChainContext ctx) throws IOException {

        final org.glassfish.tyrus.spi.Connection connection = getConnection(ctx);
        if (connection != null) {
            connection.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, null));
            connectionMap.remove(ctx.getConnection());
        }
        return ctx.getStopAction();
    }

    /**
     * Handle Grizzly {@link Connection} read phase. If the {@link Connection} has associated {@link WebSocket} object
     * (websocket connection), we check if websocket handshake has been completed for this connection, if not -
     * initiate/validate handshake. If handshake has been completed - parse websocket {@link org.glassfish.tyrus.core.DataFrame}s one by one and
     * pass processing to appropriate {@link WebSocket}: {@link org.glassfish.tyrus.core.WebSocketApplication} for server- and client- side
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


        org.glassfish.tyrus.spi.Connection tyrusConnection = getConnection(ctx);

        // Get the HTTP header
        final HttpHeader header = message.getHttpHeader();

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "handleRead websocket: {0} content-size={1} headers=\n{2}",
                    new Object[]{tyrusConnection, message.getContent().remaining(), header});
        }

        // client
        if (tyrusConnection != null && !message.getHttpHeader().isRequest()) {
            final HttpStatus httpStatus = ((HttpResponsePacket) message.getHttpHeader()).getHttpStatus();

            if (httpStatus != HttpStatus.SWITCHING_PROTOCOLS_101) {
                if (proxy) {
                    if (httpStatus == HttpStatus.OK_200) {

                        // TYRUS-221: Proxy handshake is complete, we need to enable SSL layer for secure ("wss")
                        // connections now.
                        if (sslFilter != null) {
                            ((GrizzlyClientSocket.FilterWrapper) sslFilter).enable();
                        }

                        ctx.write(getHttpContent(webSocketRequest));
                        ctx.flush(null);

                    } else {
                        throw new HandshakeException(String.format("Proxy error. %s: %s", httpStatus.getStatusCode(),
                                new String(httpStatus.getReasonPhraseBytes(), "UTF-8")));
                    }

                    return ctx.getInvokeAction();
                }
            }
        }

        if (tyrusConnection == null) {

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

        // tyrusConnection is not null
        // this is websocket with the completed handshake
        if (message.getContent().hasRemaining()) {
            // get the frame(s) content

            Buffer buffer = message.getContent();
            message.recycle();
            ByteBuffer webSocketBuffer = BufferHelper.convertBuffer(buffer);
            // check if we're currently parsing a frame

            tyrusConnection.getReadHandler().handle(webSocketBuffer);
        }
        return ctx.getStopAction();
    }

    private org.glassfish.tyrus.spi.Connection getConnection(FilterChainContext ctx) {
        // TODO - use attributes
//        final Object o = ctx.getAttributes().getAttribute(TYRUS_CONNECTION);
//        if (o != null && o instanceof org.glassfish.tyrus.spi.Connection) {
//            return (org.glassfish.tyrus.spi.Connection) o;
//        }
        return connectionMap.get(ctx.getConnection());
    }

    /**
     * Handle Grizzly {@link Connection} write phase. If the {@link Connection} has associated {@link WebSocket} object
     * (websocket connection), we assume that message is websocket {@link DataFrame} and serialize it into a {@link
     * Buffer}.
     *
     * @param ctx {@link FilterChainContext}
     * @return {@link NextAction} instruction for {@link FilterChain}, how it should continue the execution
     * @throws IOException TODO
     */
    @Override
    public NextAction handleWrite(FilterChainContext ctx) throws IOException {
        // get the associated websocket
        final Object tyrusConnection = ctx.getAttributes().getAttribute(TYRUS_CONNECTION);
        // if there is one
        if (tyrusConnection != null) {
            final byte[] bytes = ctx.getMessage();
            final Buffer wrap = Buffers.wrap(ctx.getMemoryManager(), bytes);
            ctx.setMessage(wrap);
            ctx.flush(null);
        }
        // invoke next filter in the chain
        return ctx.getInvokeAction();
    }


    // --------------------------------------------------------- Private Methods

    /**
     * Handle websocket handshake
     *
     * @param ctx     {@link FilterChainContext}
     * @param content HTTP message
     * @return {@link NextAction} instruction for {@link FilterChain}, how it should continue the execution
     * @throws IOException TODO
     */
    private NextAction handleHandshake(FilterChainContext ctx, HttpContent content) throws IOException {
        // check if it's server or client side handshake
        return content.getHttpHeader().isRequest()
                ? handleServerHandshake(ctx, content)
                : handleClientHandShake(ctx, content);
    }

    private NextAction handleClientHandShake(final FilterChainContext ctx, final HttpContent content) {
        // TODO
        final WebSocketHolder holder = ((TyrusWebSocketEngine) engine).getWebSocketHolder(getWebSocketConnection(ctx));

        if (holder == null) {
            content.recycle();
            return ctx.getStopAction();
        }

        try {
            final UpgradeResponse upgradeResponse = getWebSocketResponse((HttpResponsePacket) content.getHttpHeader());
            holder.handshake.validateServerResponse(upgradeResponse);
            holder.handshake.getResponseListener().onHandShakeResponse(upgradeResponse);
            holder.webSocket.onConnect();
        } catch (HandshakeException e) {
            holder.handshake.getResponseListener().onError(e);
            content.getContent().clear();
            return ctx.getStopAction();
        }

//        ctx.getAttributes().setAttribute(TYRUS_CONNECTION, new org.glassfish.tyrus.spi.Connection() {
        connectionMap.put(ctx.getConnection(), new org.glassfish.tyrus.spi.Connection() {

            final GrizzlyWriter writer = WebSocketFilter.getWebSocketConnection(ctx);

            @Override
            public ReadHandler getReadHandler() {
                return ((TyrusWebSocketEngine) engine).getReadHandler(holder.handler.getWriter());
            }

            @Override
            public Writer getWriter() {
                return writer;
            }

            @Override
            public CloseListener getCloseListener() {
                return new CloseListener() {
                    @Override
                    public void close(CloseReason reason) {

                    }
                };
            }

            @Override
            public void open() {
                // TODO: Implement.
            }

            @Override
            public void close(CloseReason reason) {
                writer.close();
            }
        });

        if (content.getContent().hasRemaining()) {
            return ctx.getRerunFilterAction();
        } else {
            content.recycle();
            return ctx.getStopAction();
        }
    }

    private static WebSocketResponse getWebSocketResponse(HttpResponsePacket httpResponsePacket) {
        WebSocketResponse webSocketResponse = new WebSocketResponse();

        for (String name : httpResponsePacket.getHeaders().names()) {
            final List<String> values = webSocketResponse.getHeaders().get(name);
            if (values == null) {
                webSocketResponse.getHeaders().put(name, Utils.parseHeaderValue(httpResponsePacket.getHeader(name)));
            } else {
                values.addAll(Utils.parseHeaderValue(httpResponsePacket.getHeader(name)));
            }
        }

        webSocketResponse.setStatus(httpResponsePacket.getStatus());

        return webSocketResponse;
    }

    /**
     * Handle server-side websocket handshake
     *
     * @param ctx            {@link FilterChainContext}
     * @param requestContent HTTP message
     * @return TODO
     * @throws IOException TODO
     */
    private NextAction handleServerHandshake(final FilterChainContext ctx,
                                             final HttpContent requestContent)
            throws IOException {


        final UpgradeRequest upgradeRequest = createWebSocketRequest(ctx, requestContent);
        // TODO: final UpgradeResponse upgradeResponse = GrizzlyUpgradeResponse(HttpResponsePacket)
        final UpgradeResponse upgradeResponse = new WebSocketResponse();
        final WebSocketEngine.UpgradeInfo upgradeInfo = engine.upgrade(upgradeRequest, upgradeResponse);

        switch (upgradeInfo.getStatus()) {
            case SUCCESS:

                final Connection grizzlyConnection = ctx.getConnection();

                final org.glassfish.tyrus.spi.Connection connection = upgradeInfo.createConnection(new GrizzlyWriter(ctx.getConnection()), new org.glassfish.tyrus.spi.Connection.CloseListener() {
                    @Override
                    public void close(CloseReason reason) {
                        grizzlyConnection.close();
                    }
                });

                ctx.getConnection().addCloseListener(new CloseListener() {
                    @Override
                    public void onClosed(Closeable closeable, ICloseType type) throws IOException {
                        connection.close(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "Close detected on connection"));
                        if (WebSocketFilter.this.connectionMap.containsKey(grizzlyConnection)) {
                            WebSocketFilter.this.connectionMap.remove(grizzlyConnection);
                        }
                    }
                });

                connectionMap.put(grizzlyConnection, connection);
//                ctx.getAttributes().setAttribute(TYRUS_CONNECTION, connection);

                write(ctx, upgradeRequest, upgradeResponse);
                ctx.flush(null);

                connection.open();

                return ctx.getStopAction();

            case HANDSHAKE_FAILED:

                write(ctx, upgradeRequest, upgradeResponse);
                ctx.flush(null);
                requestContent.recycle();

                return ctx.getStopAction();

            case NOT_APPLICABLE:

                return ctx.getInvokeAction();
        }


        return ctx.getStopAction();
    }

    private void write(FilterChainContext ctx, UpgradeRequest request, UpgradeResponse response) {
        final HttpResponsePacket responsePacket = ((HttpRequestPacket) ((HttpContent) ctx.getMessage()).getHttpHeader()).getResponse();
        responsePacket.setProtocol(Protocol.HTTP_1_1);
        responsePacket.setStatus(response.getStatus());

        // TODO
//        responsePacket.setReasonPhrase(response.getReasonPhrase());

        for (Map.Entry<String, List<String>> entry : response.getHeaders().entrySet()) {
            responsePacket.setHeader(entry.getKey(), Utils.getHeaderFromList(entry.getValue()));
        }

        ctx.write(HttpContent.builder(responsePacket).build());
    }

    /**
     * TODO ? - remove?
     *
     * @param ctx TODO
     */
    private void setIdleTimeout(final FilterChainContext ctx) {
        final FilterChain filterChain = ctx.getFilterChain();
        if (filterChain.indexOfType(IdleTimeoutFilter.class) >= 0) {
            IdleTimeoutFilter.setCustomTimeout(ctx.getConnection(),
                    wsTimeoutMS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Create HttpContent (Grizzly request representation) from {@link WebSocketRequest}.
     *
     * @param request original request.
     * @return Grizzly representation of provided request.
     */
    private HttpContent getHttpContent(UpgradeRequest request) {
        HttpRequestPacket.Builder builder = HttpRequestPacket.builder();
        builder = builder.protocol(Protocol.HTTP_1_1);
        builder = builder.method(Method.GET);
        builder = builder.uri(request.getRequestPath());
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

    private static GrizzlyWriter getWebSocketConnection(final FilterChainContext ctx) {
        return new GrizzlyWriter(ctx.getConnection());
    }

    private static UpgradeRequest createWebSocketRequest(final FilterChainContext ctx, final HttpContent requestContent) {

        final HttpRequestPacket requestPacket = (HttpRequestPacket) requestContent.getHttpHeader();

        final RequestContext requestContext = RequestContext.Builder.create()
                .requestURI(URI.create(requestPacket.getRequestURI()))
                .queryString(requestPacket.getQueryString())
                .secure(requestPacket.isSecure())
                .build();

        for (String name : requestPacket.getHeaders().names()) {
            final List<String> values = requestContext.getHeaders().get(name);
            if (values == null) {
                requestContext.getHeaders().put(name, Utils.parseHeaderValue(requestPacket.getHeader(name).trim()));
            } else {
                values.addAll(Utils.parseHeaderValue(requestPacket.getHeader(name).trim()));
            }
        }

        return requestContext;
    }
}
