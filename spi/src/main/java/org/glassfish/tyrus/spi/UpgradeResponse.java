/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2014 Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import javax.websocket.HandshakeResponse;

/**
 * Abstraction for a HTTP upgrade response. A transport creates an
 * implementation for this and uses {@link WebSocketEngine#upgrade} method
 * to upgrade the request.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public abstract class UpgradeResponse implements HandshakeResponse {

    /**
     * Header containing challenge with authentication scheme and parameters.
     */
    public static final String WWW_AUTHENTICATE = "WWW-Authenticate";

    /**
     * Header containing a new URI when {@link #getStatus()} .
     */
    public static final String LOCATION = "Location";

    /**
     * Header containing period or date in which we can try to connect again.
     */
    public static final String RETRY_AFTER = "Retry-After";

    /**
     * Get the current HTTP status code of this response.
     *
     * @return the current HTTP status code.
     */
    public abstract int getStatus();

    /**
     * Set HTTP status code for this response.
     *
     * @param status HTTP status code for this response.
     */
    public abstract void setStatus(int status);

    /**
     * Get HTTP reason phrase.
     * <p/>
     * TODO remove ?? we are using only for "Switching Protocols" and that is
     * TODO standard status code 101
     */
    public abstract void setReasonPhrase(String reason);

    /**
     * Gets the value of the response header with the given name.
     * <p/>
     * <p>If a response header with the given name exists and contains
     * multiple values, the value that was added first will be returned.
     *
     * @param name header name.
     * @return the value of the response header with the given name,
     * null if no header with the given name has been set
     * on this response.
     * TODO rename to getHeader(String name) ?? similar to
     * TODO HttpServletResponse#getHeader(String)
     */
    public String getFirstHeaderValue(String name) {
        final List<String> stringList = getHeaders().get(name);
        return stringList == null ? null : (stringList.size() > 0 ? stringList.get(0) : null);
    }
}
