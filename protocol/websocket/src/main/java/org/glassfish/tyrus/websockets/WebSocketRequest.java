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

package org.glassfish.tyrus.websockets;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Request representation.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public interface WebSocketRequest {

    /**
     * Get request headers.
     *
     * @return request headers. List items represent headers from HTTP request.
     */
    public Map<String, List<String>> getHeaders();

    /**
     * Return the header values corresponding to the name.
     *
     * @param name header name.
     * @return {@link List} of header values iff found, {@code null} otherwise.
     */
    public String getHeader(String name);

    /**
     * Get the first header value from the {@link List} of header values corresponding to the name.
     *
     * @param name header name.
     * @return {@link String} value iff it exists, {@code null} otherwise.
     */
    public String getFirstHeaderValue(String name);

    /**
     * Put single header value into headers map.
     *
     * @param headerName  header name.
     * @param headerValue header value.
     */
    public void putSingleHeader(String headerName, String headerValue);

    /**
     * Get request path.
     *
     * @return request path.
     */
    public String getRequestPath();

    /**
     * Set request path.
     *
     * @param requestPath request path to be set.
     */
    public void setRequestPath(String requestPath);

    /**
     * Get request URI.
     *
     * @return request URI.
     */
    public URI getRequestURI();

    /**
     * Get query string.
     *
     * @return unparsed query string.
     */
    public String getQueryString();

    /**
     * Get {@link Connection}.
     *
     * @return underlying connection.
     */
    public Connection getConnection();

    /**
     * Get information about connection secure state.
     *
     * @return {@code true} if connection is secure, {@code false} otherwise.
     */
    public boolean isSecure();

    /**
     * Return the request parameters associated with the request.
     *
     * @return the unmodifiable map of the request parameters.
     */
    public Map<String, List<String>> getParameterMap();
}
