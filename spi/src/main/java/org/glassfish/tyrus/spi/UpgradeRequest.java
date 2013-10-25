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

    public static final String WEBSOCKET = "websocket";
    public static final String RESPONSE_CODE_MESSAGE = "Switching Protocols";
    public static final String UPGRADE = "Upgrade";
    public static final String CONNECTION = "Connection";
    public static final String HOST = "Host";
    public static final String SERVER_KEY_HASH = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    public static final String SEC_WS_ORIGIN_HEADER = "Sec-WebSocket-Origin";
    public static final String ORIGIN_HEADER = "Origin";

    /**
     * Returns the value of the specified request header name. If there are
     * multiple headers with the same name, this method returns the first
     * header in the request. The header name is case insensitive.
     *
     * @param name a header name.
     * @return value of the specified header name,
     *         null if the request doesn't have a header of that name.
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
     *         false otherwise.
     */
    public abstract boolean isSecure();
}
