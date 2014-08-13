/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2014 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.spi;

import javax.websocket.server.HandshakeRequest;

/**
 * Abstraction for a HTTP upgrade request. A transport creates an implementation
 * for this and uses {@link WebSocketEngine#upgrade} method to upgrade the
 * request.
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public abstract class UpgradeRequest implements HandshakeRequest {

    /**
     * Expected value in HTTP handshake "Upgrade" header.
     * <p/>
     * (Registered in RFC 6455).
     */
    public static final String WEBSOCKET = "websocket";

    /**
     * HTTP reason phrase for successful handshake response.
     */
    public static final String RESPONSE_CODE_MESSAGE = "Switching Protocols";

    /**
     * HTTP "Upgrade" header name and "Connection" header expected value.
     */
    public static final String UPGRADE = "Upgrade";

    /**
     * HTTP "Connection" header name.
     */
    public static final String CONNECTION = "Connection";

    /**
     * HTTP "Host" header name.
     */
    public static final String HOST = "Host";

    /**
     * WebSocket origin header name from previous versions.
     * <p/>
     * Keeping here only for backwards compatibility, not used anymore.
     */
    @SuppressWarnings("UnusedDeclaration")
    public static final String SEC_WS_ORIGIN_HEADER = "Sec-WebSocket-Origin";

    /**
     * HTTP "Origin" header name.
     */
    public static final String ORIGIN_HEADER = "Origin";

    /**
     * Tyrus cluster connection ID header name.
     */
    public static final String CLUSTER_CONNECTION_ID_HEADER = "tyrus-cluster-connection-id";

    /**
     * Server key hash used to compute "Sec-WebSocket-Accept" header value.
     * <p/>
     * Defined in RFC 6455.
     */
    public static final String SERVER_KEY_HASH = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    /**
     * HTTP "Authorization" header name.
     */
    public static final String AUTHORIZATION = "Authorization";

    /**
     * If this header is present in the handshake request and the tracing type is configured to "ON_DEMAND", tracing
     * headers will be sent in the handshake response. The value of the header is no taken into account.
     * <p/>
     * Setting this header does not have any effect if the tracing type is configured to "ALL" or "OFF".
     */
    public static final String ENABLE_TRACING_HEADER = "X-Tyrus-Tracing-Accept";

    /**
     * This header allows temporarily changing tracing threshold. If present in the handshake request, the tracing
     * threshold will be changed for the handshake the request is part of.
     * <p/>
     * The expected values are "SUMMARY" or "TRACE", of which "TRACE" will provide more fine-grained information.
     */
    public static final String TRACING_THRESHOLD = "X-Tyrus-Tracing-Threshold";

    /**
     * Returns the value of the specified request header name. If there are
     * multiple headers with the same name, this method returns the first
     * header in the request. The header name is case insensitive.
     *
     * @param name a header name.
     * @return value of the specified header name,
     * null if the request doesn't have a header of that name.
     */
    public abstract String getHeader(String name);

    /**
     * Get the undecoded request uri (up to the query string) of underlying
     * HTTP handshake request.
     *
     * @return request uri.
     */
    public abstract String getRequestUri();

    /**
     * Indicates whether this request was made using a secure channel
     * (such as HTTPS).
     *
     * @return true if the request was made using secure channel,
     * false otherwise.
     */
    public abstract boolean isSecure();
}
