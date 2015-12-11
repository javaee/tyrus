/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.test.tools;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.server.AddOn;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.HttpServerFilter;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.memory.Buffers;

/**
 * Grizzly HTTP Server "hacked", so it behaves like a HTTP proxy.
 * <p/>
 * It is only for tests.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class GrizzlyModProxy {

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Attribute<Socket> tunnelSockets = Grizzly.DEFAULT_ATTRIBUTE_BUILDER
            .createAttribute(ProxyFilter.class.getName() + ".TunnelSocket");

    private final HttpServer server;
    private final ProxyFilter proxyFilter;

    /**
     * Create a HTTP proxy.
     *
     * @param host hostName or IP, where the proxy will listen.
     * @param port port, where the proxy will listen.
     */
    public GrizzlyModProxy(String host, int port) {
        server = HttpServer.createSimpleServer("/", host, port);
        proxyFilter = new ProxyFilter();
        server.getListener("grizzly").registerAddOn(new AddOn() {
            @Override
            public void setup(NetworkListener networkListener, FilterChainBuilder builder) {
                int httpServerFilterIdx = builder.indexOfType(HttpServerFilter.class);

                if (httpServerFilterIdx >= 0) {
                    // Insert the WebSocketFilter right before HttpServerFilter
                    builder.add(httpServerFilterIdx, proxyFilter);
                }
            }
        });
    }

    public void start() throws IOException {
        server.start();
    }

    public void stop() throws IOException {
        server.shutdown();
        executorService.shutdown();
    }

    /**
     * Method invoked when a first massage (Assumed to be HTTP) is received. Normally this would be HTTP CONNECT
     * and this method processes it and opens a connection to the destination (the server that the client wants to access).
     * <p/>
     * This method can be overridden to provide a test-specific handling of the CONNECT method.
     */
    protected NextAction handleConnect(FilterChainContext ctx, HttpContent content) {
        System.out.println("Handle CONNECT start . . .");
        HttpHeader httpHeader = content.getHttpHeader();
        HttpRequestPacket requestPacket = (HttpRequestPacket) httpHeader.getHttpHeader();

        if (!requestPacket.getMethod().matchesMethod("CONNECT")) {
            System.out.println("Received method is not CONNECT");
            writeHttpResponse(ctx, 400);
            return ctx.getStopAction();
        }

        String destinationUri = requestPacket.getRequestURI();

        //We expect URI in form host:port, this is not flexible, but we use it only to test our clients
        int colonIdx = destinationUri.indexOf(':');

        if (colonIdx == -1) {
            System.out.println("Destination URI not in host:port format: " + destinationUri);
            writeHttpResponse(ctx, 400);
            return ctx.getStopAction();
        }

        String hostName = destinationUri.substring(0, colonIdx);
        String portStr = destinationUri.substring(colonIdx + 1);

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (Throwable t) {
            System.out.println("Could not parse destination port: " + portStr);
            writeHttpResponse(ctx, 400);
            return ctx.getStopAction();
        }

        try {
            Socket tunnelSocket = new Socket(hostName, port);

            Connection grizzlyConnection = ctx.getConnection();
            tunnelSockets.set(grizzlyConnection, tunnelSocket);

            final TunnelSocketReader tunnelSocketReader = new TunnelSocketReader(tunnelSocket, grizzlyConnection);
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    tunnelSocketReader.read();
                }
            });
        } catch (IOException e) {
            writeHttpResponse(ctx, 400);
            return ctx.getStopAction();
        }

        // Grizzly does not like CONNECT method and sets "keep alive" to false, if it is present
        // This hacks Grizzly, so it will keep the connection open
        HttpRequestPacket request = getHttpRequest(ctx);
        request.getResponse().getProcessingState().setKeepAlive(true);
        request.getResponse().setContentLength(0);
        request.setMethod("GET");
        // end of hack

        writeHttpResponse(ctx, 200);

        System.out.println("Connection to proxy established.");

        return ctx.getStopAction();
    }

    private void writeHttpResponse(FilterChainContext ctx, int status) {
        HttpResponsePacket responsePacket = getHttpRequest(ctx).getResponse();
        responsePacket.setProtocol(Protocol.HTTP_1_1);
        responsePacket.setStatus(status);
        ctx.write(HttpContent.builder(responsePacket).build());
    }

    private HttpRequestPacket getHttpRequest(FilterChainContext ctx) {
        return (HttpRequestPacket) ((HttpContent) ctx.getMessage()).getHttpHeader();
    }

    private class ProxyFilter extends BaseFilter {

        @Override
        public NextAction handleClose(final FilterChainContext ctx) throws IOException {
            Socket tunnelSocket = tunnelSockets.get(ctx.getConnection());
            if (tunnelSocket != null) {
                tunnelSocket.close();
            }

            return ctx.getStopAction();
        }

        @Override
        public NextAction handleRead(FilterChainContext ctx) throws IOException {
            // Get the parsed HttpContent (we assume prev. filter was HTTP)
            HttpContent message = ctx.getMessage();
            Socket tunnelSocket = tunnelSockets.get(ctx.getConnection());

            if (tunnelSocket == null) {
                // handle connection procedure
                return GrizzlyModProxy.this.handleConnect(ctx, message);
            }

            if (message.getContent().hasRemaining()) {
                // relay the content to the tunnel connection

                Buffer buffer = message.getContent();
                message.recycle();

                tunnelSocket.getOutputStream().write(buffer.array(), buffer.arrayOffset(), buffer.remaining());
            }

            return ctx.getStopAction();
        }
    }

    /**
     * Reads data from proxy to server connection and writes them in client to proxy connection.
     */
    private static class TunnelSocketReader {

        // proxy to server connection
        private final Socket tunnelSocket;
        // client to proxy connection
        private final Connection grizzlyConnection;
        // buffers bytes read from proxy-server connection before it writes them in proxy-client connection
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final InputStream inputStream;

        TunnelSocketReader(Socket tunnelSocket, Connection grizzlyConnection) throws IOException {
            this.tunnelSocket = tunnelSocket;
            this.grizzlyConnection = grizzlyConnection;
            inputStream = tunnelSocket.getInputStream();
        }

        void read() {
            try {
                while (true) {
                    if (tunnelSocket.isClosed()) {
                        flushBufferedData();
                        grizzlyConnection.close();
                        return;
                    }

                    int b = inputStream.read();
                    if (b == -1) {
                        // end of stream -> flush and close client-proxy connection
                        flushBufferedData();
                        grizzlyConnection.close();
                        return;
                    }

                    buffer.write(b);
                    if (inputStream.available() == 0) {
                        // we seem to have read all that was available in the socket buffer -> send it to the client.
                        flushBufferedData();
                    }
                }
            } catch (IOException e) {
                flushBufferedData();
                grizzlyConnection.close();
                if (e.getMessage().contains("Socket closed")) {
                    System.out.println("Connection between the proxy and a server closed by the server.");
                } else {
                    e.printStackTrace();
                }
            }
        }

        private void flushBufferedData() {
            if (buffer.size() == 0) {
                // buffer is empty
                return;
            }

            Buffer message = Buffers.wrap(grizzlyConnection.getMemoryManager(), buffer.toByteArray());
            grizzlyConnection.write(message);
            buffer.reset();
        }
    }
}
