/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 - 2012 Oracle and/or its affiliates. All rights reserved.
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
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.tyrus.websockets.DataFrame;
import org.glassfish.tyrus.websockets.FramingException;
import org.glassfish.tyrus.websockets.HandshakeException;
import org.glassfish.tyrus.websockets.WebSocket;
import org.glassfish.tyrus.websockets.WebSocketEngine;
import org.glassfish.tyrus.websockets.WebSocketEngine.WebSocketHolder;
import org.glassfish.tyrus.websockets.WebSocketRequest;
import org.glassfish.tyrus.websockets.WebSocketResponse;
import org.glassfish.tyrus.websockets.draft06.ClosingFrame;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
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
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.utils.IdleTimeoutFilter;

/**
 * WebSocket {@link Filter} implementation, which supposed to be placed into a {@link FilterChain} right after HTTP
 * Filter: {@link HttpServerFilter}, {@link HttpClientFilter}; depending whether it's server or client side. The
 * <tt>WebSocketFilter</tt> handles websocket connection, handshake phases and, when receives a websocket frame -
 * redirects it to appropriate connection ({@link org.glassfish.tyrus.websockets.WebSocketApplication}, {@link org.glassfish.tyrus.websockets.WebSocket}) for processing.
 *
 * @author Alexey Stashok
 */
class WebSocketFilter extends BaseFilter {

    private static final Logger logger = Grizzly.logger(WebSocketFilter.class);
    private static final long DEFAULT_WS_IDLE_TIMEOUT_IN_SECONDS = 15 * 60;
    private final long wsTimeoutMS;

    // ------------------------------------------------------------ Constructors


    /**
     * Constructs a new <code>WebSocketFilter</code> with a default idle connection
     * timeout of 15 minutes;
     */
    public WebSocketFilter()  {
        this(DEFAULT_WS_IDLE_TIMEOUT_IN_SECONDS);
    }

    /**
     * Constructs a new <code>WebSocketFilter</code> with a default idle connection
     * timeout of 15 minutes;
     *
     * @param wsTimeoutInSeconds TODO
     */
    public WebSocketFilter(final long wsTimeoutInSeconds) {
        if (wsTimeoutInSeconds <= 0) {
            this.wsTimeoutMS = IdleTimeoutFilter.FOREVER;
        } else {
            this.wsTimeoutMS = wsTimeoutInSeconds * 1000;
        }
    }

    // ----------------------------------------------------- Methods from Filter
    /**
     * Method handles Grizzly {@link Connection} connect phase. Check if the {@link Connection} is a client-side {@link
     * org.glassfish.tyrus.websockets.WebSocket}, if yes - creates websocket handshake packet and send it to a server. Otherwise, if it's not websocket
     * connection - pass processing to the next {@link Filter} in a chain.
     *
     * @param ctx {@link FilterChainContext}
     * @return {@link NextAction} instruction for {@link FilterChain}, how it should continue the execution
     * @throws IOException TODO
     */
    @Override
    public NextAction handleConnect(FilterChainContext ctx) throws IOException {
        logger.log(Level.FINEST, "handleConnect");

        // Get connection
        final org.glassfish.tyrus.websockets.Connection webSocketConnection =
                getWebSocketConnection(ctx, HttpContent.builder(HttpRequestPacket.builder().build()).build());

        // check if it's websocket connection
        if (!webSocketInProgress(webSocketConnection)) {
            // if not - pass processing to a next filter
            return ctx.getInvokeAction();
        }

        WebSocketHolder webSocketHolder = WebSocketEngine.getEngine().getWebSocketHolder(webSocketConnection);
        WebSocketRequest webSocketRequest = webSocketHolder.handshake.initiate();

        HttpRequestPacket.Builder builder = HttpRequestPacket.builder();

        builder = builder.protocol(Protocol.HTTP_1_1);
        builder = builder.method(Method.GET);
        builder = builder.uri(webSocketRequest.getRequestPath());

        for(Map.Entry<String, String> entry : webSocketRequest.getHeaders().entrySet()) {
            builder = builder.header(entry.getKey(), entry.getValue());
        }

        HttpContent httpContent1 = HttpContent.builder(builder.build()).build();
        ctx.write(httpContent1);
        ctx.flush(null);
        // call the next filter in the chain
        return ctx.getInvokeAction();
    }

    /**
     * Method handles Grizzly {@link Connection} close phase. Check if the {@link Connection} is a {@link org.glassfish.tyrus.websockets.WebSocket}, if
     * yes - tries to close the websocket gracefully (sending close frame) and calls {@link
     * org.glassfish.tyrus.websockets.WebSocket#onClose(org.glassfish.tyrus.websockets.DataFrame)}. If the Grizzly {@link Connection} is not websocket - passes processing to the next
     * filter in the chain.
     *
     * @param ctx {@link FilterChainContext}
     * @return {@link NextAction} instruction for {@link FilterChain}, how it should continue the execution
     * @throws IOException
     */
    @Override
    public NextAction handleClose(FilterChainContext ctx) throws IOException {
        // Get the Connection
        final org.glassfish.tyrus.websockets.Connection connection = getWebSocketConnection(ctx, HttpContent.builder(HttpRequestPacket.builder().build()).build());

        // check if Connection has associated WebSocket (is websocket)
        if (webSocketInProgress(connection)) {
            // if yes - get websocket
            final WebSocket ws = getWebSocket(connection);
            if (ws != null) {
                // if there is associated websocket object (which means handshake was passed)
                // close it gracefully
                ws.close();
            }
        }
        return ctx.getInvokeAction();
    }

    /**
     * Handle Grizzly {@link Connection} read phase. If the {@link Connection} has associated {@link WebSocket} object
     * (websocket connection), we check if websocket handshake has been completed for this connection, if not -
     * initiate/validate handshake. If handshake has been completed - parse websocket {@link org.glassfish.tyrus.websockets.DataFrame}s one by one and
     * pass processing to appropriate {@link WebSocket}: {@link org.glassfish.tyrus.websockets.WebSocketApplication} for server- and client- side
     * connections.
     *
     * @param ctx {@link FilterChainContext}
     *
     * @return {@link NextAction} instruction for {@link FilterChain}, how it should continue the execution
     *
     * @throws IOException TODO
     */
    @Override
    @SuppressWarnings("unchecked")
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
        // Get the parsed HttpContent (we assume prev. filter was HTTP)
        final HttpContent message = (HttpContent) ctx.getMessage();
        // Get the Grizzly Connection
        final org.glassfish.tyrus.websockets.Connection connection = getWebSocketConnection(ctx, message);
        // Get the HTTP header
        final HttpHeader header = message.getHttpHeader();
        // Try to obtain associated WebSocket
        final WebSocketHolder holder = WebSocketEngine.getEngine().getWebSocketHolder(connection);
        WebSocket ws = getWebSocket(connection);
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "handleRead websocket: {0} content-size={1} headers=\n{2}",
                    new Object[]{ws, message.getContent().remaining(), header});
        }
        if (ws == null || !ws.isConnected()) {
            // If websocket is null - it means either non-websocket Connection, or websocket with incomplete handshake
            if (!webSocketInProgress(connection) &&
                    !WebSocketEngine.WEBSOCKET.equalsIgnoreCase(header.getUpgrade())) {
                // if it's not a websocket connection - pass the processing to the next filter
                return ctx.getInvokeAction();
            }
            // Handle handshake
            return handleHandshake(ctx, message);
        }
        // this is websocket with the completed handshake
        if (message.getContent().hasRemaining()) {
            // get the frame(s) content

            Buffer buffer = message.getContent();
            message.recycle();
            ByteBuffer webSocketBuffer = BufferHelper.convertBuffer(buffer);
            // check if we're currently parsing a frame

            try {
                while (webSocketBuffer != null && webSocketBuffer.hasRemaining()) {
                    if (holder.buffer != null) {
                        // TODO
                        buffer = BufferHelper.appendBuffers(
                                /*ctx.getMemoryManager(), */holder.buffer, buffer);

                        holder.buffer = null;
                    }
                    final DataFrame result = holder.handler.unframe(webSocketBuffer);
                    if (result == null) {
                        holder.buffer = webSocketBuffer;
                        break;
                    } else {
                        result.respond(holder.webSocket);
                    }
                }
            } catch (FramingException e) {
                holder.webSocket.onClose(new ClosingFrame(e.getClosingCode(), e.getMessage()));
            } catch (Exception wse) {
                if (holder.application.onError(holder.webSocket, wse)) {
                    holder.webSocket.onClose(new ClosingFrame(1011, wse.getMessage()));
                }
            }
        }
        return ctx.getStopAction();
    }

    /**
     * Handle Grizzly {@link Connection} write phase. If the {@link Connection} has associated {@link WebSocket} object
     * (websocket connection), we assume that message is websocket {@link DataFrame} and serialize it into a {@link
     * Buffer}.
     *
     * @param ctx {@link FilterChainContext}
     *
     * @return {@link NextAction} instruction for {@link FilterChain}, how it should continue the execution
     *
     * @throws IOException TODO
     */
    @Override
    public NextAction handleWrite(FilterChainContext ctx) throws IOException {
        // get the associated websocket
        org.glassfish.tyrus.websockets.Connection connection = getWebSocketConnection(ctx, null);
        final WebSocket websocket = getWebSocket(connection);
        // if there is one
        if (websocket != null) {
            final DataFrame frame = (DataFrame) ctx.getMessage();
            final WebSocketHolder holder = WebSocketEngine.getEngine().getWebSocketHolder(connection);
            final Buffer wrap = Buffers.wrap(ctx.getMemoryManager(), holder.handler.frame(frame));
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
     * @param ctx {@link FilterChainContext}
     * @param content HTTP message
     *
     * @return {@link NextAction} instruction for {@link FilterChain}, how it should continue the execution
     *
     * @throws IOException TODO
     */
    private NextAction handleHandshake(FilterChainContext ctx, HttpContent content) throws IOException {
        // check if it's server or client side handshake
        return content.getHttpHeader().isRequest()
                ? handleServerHandshake(ctx, content)
                : handleClientHandShake(ctx, content);
    }

    private static NextAction handleClientHandShake(FilterChainContext ctx, HttpContent content) {
        final WebSocketHolder holder = WebSocketEngine.getEngine().getWebSocketHolder(getWebSocketConnection(ctx, content));

        holder.handshake.validateServerResponse(getWebSocketResponse((HttpResponsePacket) content.getHttpHeader()));
        holder.webSocket.onConnect();

        if (content.getContent().hasRemaining()) {
            return ctx.getRerunFilterAction();
        } else {
            content.recycle();
            return ctx.getStopAction();
        }
    }

    private static WebSocketResponse getWebSocketResponse(HttpResponsePacket httpResponsePacket) {
        WebSocketResponse webSocketResponse = new WebSocketResponse();

        for(String name : httpResponsePacket.getHeaders().names()) {
            webSocketResponse.getHeaders().put(name, httpResponsePacket.getHeader(name));
        }

        webSocketResponse.setStatus(httpResponsePacket.getStatus());

        return webSocketResponse;
    }

    /**
     * Handle server-side websocket handshake
     *
     * @param ctx {@link FilterChainContext}
     * @param requestContent HTTP message
     *
     * @throws IOException TODO
     * @return TODO
     */
    private NextAction handleServerHandshake(final FilterChainContext ctx,
                                             final HttpContent requestContent)
            throws IOException {

        // get HTTP request headers
        final HttpRequestPacket request = (HttpRequestPacket) requestContent.getHttpHeader();
        final org.glassfish.tyrus.websockets.Connection webSocketConnection = getWebSocketConnection(ctx, requestContent);
        try {
            if (!WebSocketEngine.getEngine().upgrade(
                    webSocketConnection,
                    createWebSocketRequest(ctx, requestContent))) {
                return ctx.getInvokeAction(); // not a WS request, pass to the next filter.
            }
            setIdleTimeout(ctx);
        } catch (HandshakeException e) {
            ctx.write(composeHandshakeError(request, e));
        }
        ctx.flush(null);

        requestContent.recycle();

        return ctx.getStopAction();

    }

    private static WebSocket getWebSocket(final org.glassfish.tyrus.websockets.Connection connection) {
        return WebSocketEngine.getEngine().getWebSocket(connection);
    }

    private static boolean webSocketInProgress(final org.glassfish.tyrus.websockets.Connection connection) {
        return WebSocketEngine.getEngine().webSocketInProgress(connection);
    }

    private static HttpResponsePacket composeHandshakeError(final HttpRequestPacket request,
                                                            final HandshakeException e) {
        final HttpResponsePacket response = request.getResponse();
        response.setStatus(e.getCode());
        response.setReasonPhrase(e.getMessage());
        return response;
    }

    private void setIdleTimeout(final FilterChainContext ctx) {
        final FilterChain filterChain = ctx.getFilterChain();
        if (filterChain.indexOfType(IdleTimeoutFilter.class) >= 0) {
            IdleTimeoutFilter.setCustomTimeout(ctx.getConnection(),
                    wsTimeoutMS, TimeUnit.MILLISECONDS);
        }
    }

    public static class ResultFuture<T> implements Future<T> {

        private T result;

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return result != null;
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            return result;
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return get();
        }

        public void result(T result) {
            this.result = result;
        }

        public void failure(Throwable t) {
            // TODO XXX FIXME
        }

    }

    private static org.glassfish.tyrus.websockets.Connection getWebSocketConnection(final FilterChainContext ctx, final HttpContent httpContent) {
        return new ConnectionImpl(ctx, httpContent);
    }

    private static WebSocketRequest createWebSocketRequest(final FilterChainContext ctx, final HttpContent requestContent) {

        final HttpRequestPacket requestPacket = (HttpRequestPacket) requestContent.getHttpHeader();

        WebSocketRequest request = new WebSocketRequest() {
            @Override
            public String getRequestURI() {
                return requestPacket.getRequestURI();
            }

            @Override
            public String getQueryString() {
                return requestPacket.getQueryString();
            }

            @Override
            public org.glassfish.tyrus.websockets.Connection getConnection() {
                return getWebSocketConnection(ctx, requestContent);
            }

            @Override
            public boolean isSecure() {
                return requestPacket.getRequestURI().toLowerCase().startsWith("wss");
            }
        };

        for(String name : requestPacket.getHeaders().names()) {
            request.getHeaders().put(name, requestPacket.getHeader(name));
        }

        return request;
    }
}
