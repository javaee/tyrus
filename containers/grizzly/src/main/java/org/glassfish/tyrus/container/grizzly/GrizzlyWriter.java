/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2013 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Map;

import org.glassfish.tyrus.spi.SPIHandshakeResponse;
import org.glassfish.tyrus.spi.Writer;

import org.glassfish.grizzly.Closeable;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.ICloseType;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.Protocol;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
class GrizzlyWriter extends Writer {

    private final FilterChainContext ctx;
    private final HttpContent httpContent;
    private final org.glassfish.grizzly.Connection connection;

    public GrizzlyWriter(final FilterChainContext ctx, final HttpContent httpContent) {
        this.ctx = ctx;
        this.connection = ctx.getConnection();
        this.httpContent = httpContent;
    }

    public GrizzlyWriter(final org.glassfish.grizzly.Connection connection) {
        this.connection = connection;
        this.ctx = null;
        this.httpContent = null;
    }

    @Override
    public void write(final byte[] bytes, final CompletionHandler<byte[]> completionHandler) {
        if (!connection.isOpen()) {
            return;
        }

        //noinspection unchecked
        connection.write(bytes, new EmptyCompletionHandler() {
            @Override
            public void cancelled() {
                if (completionHandler != null) {
                    completionHandler.cancelled();
                }
            }

            @Override
            public void completed(Object result) {
                if (completionHandler != null) {
                    completionHandler.completed(bytes);
                }
            }

            @Override
            public void failed(Throwable throwable) {
                if (completionHandler != null) {
                    completionHandler.failed(throwable);
                }
            }
        });
    }

    @Override
    public void write(SPIHandshakeResponse response) {
        if (ctx == null) {
            throw new UnsupportedOperationException("not supported on client side");
        }

        final HttpResponsePacket responsePacket = ((HttpRequestPacket) httpContent.getHttpHeader()).getResponse();
        responsePacket.setProtocol(Protocol.HTTP_1_1);
        responsePacket.setStatus(response.getStatus());

        for (Map.Entry<String, String> entry : response.getHeaders().entrySet()) {
            responsePacket.setHeader(entry.getKey(), entry.getValue());
        }

        ctx.write(HttpContent.builder(responsePacket).build());
    }

    @Override
    public void addCloseListener(final CloseListener closeListener) {

        final Writer webSocketWriter = this;

        connection.addCloseListener(new org.glassfish.grizzly.CloseListener() {
            @Override
            public void onClosed(Closeable closeable, ICloseType iCloseType) throws IOException {
                closeListener.onClose(webSocketWriter);
            }
        });
    }

    @Override
    public void closeSilently() {
        connection.closeSilently();
    }

    @Override
    public int hashCode() {
        return connection.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Writer && connection.equals(((Writer) obj).getUnderlyingConnection());
    }

    @Override
    public Object getUnderlyingConnection() {
        return connection;
    }

    public String toString() {
        return this.getClass().getName() + " " + connection.toString() + " " + connection.hashCode();
    }
}