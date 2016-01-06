/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2016 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.core;

import java.net.URI;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.glassfish.tyrus.spi.UpgradeRequest;

/**
 * Implementation of all possible request interfaces. Should be the only point of truth.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public final class RequestContext extends UpgradeRequest {

    private final URI requestURI;
    private final String queryString;
    private final Object httpSession;
    private final boolean secure;
    private final Principal userPrincipal;
    private final Builder.IsUserInRoleDelegate isUserInRoleDelegate;
    private final String remoteAddr;

    private Map<String, List<String>> headers;
    private Map<String, List<String>> parameterMap;

    private RequestContext(URI requestURI, String queryString, Object httpSession, boolean secure, Principal
            userPrincipal, Builder.IsUserInRoleDelegate IsUserInRoleDelegate, String remoteAddr, Map<String,
            List<String>> parameterMap, Map<String, List<String>> headers) {
        this.requestURI = requestURI;
        this.queryString = queryString;
        this.httpSession = httpSession;
        this.secure = secure;
        this.userPrincipal = userPrincipal;
        this.isUserInRoleDelegate = IsUserInRoleDelegate;
        this.remoteAddr = remoteAddr;
        this.parameterMap = parameterMap;
        this.headers = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);

        if (headers != null) {
            this.headers.putAll(headers);
        }
    }

    /**
     * Get headers.
     *
     * @return headers map. List items are corresponding to header declaration in HTTP request.
     */
    @Override
    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    /**
     * Returns the header value corresponding to the name.
     *
     * @param name header name.
     * @return {@link List} of header values iff found, {@code null} otherwise.
     */
    @Override
    public String getHeader(String name) {
        final List<String> stringList = headers.get(name);
        if (stringList == null) {
            return null;
        } else {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (String s : stringList) {
                if (first) {
                    first = false;
                } else {
                    sb.append(", ");
                }
                sb.append(s);
            }

            return sb.toString();
        }
    }

    /**
     * Make headers and parameter map read-only.
     */
    public void lock() {
        this.headers = Collections.unmodifiableMap(headers);
        this.parameterMap = Collections.unmodifiableMap(parameterMap);
    }

    @Override
    public Principal getUserPrincipal() {
        return userPrincipal;
    }

    @Override
    public URI getRequestURI() {
        return requestURI;
    }

    @Override
    public boolean isUserInRole(String role) {
        if (isUserInRoleDelegate != null) {
            return isUserInRoleDelegate.isUserInRole(role);
        }

        return false;
    }

    @Override
    public Object getHttpSession() {
        return httpSession;
    }

    @Override
    public Map<String, List<String>> getParameterMap() {
        return parameterMap;
    }

    @Override
    public String getQueryString() {
        return queryString;
    }

    @Override
    public String getRequestUri() {
        return requestURI.toString();
    }

    @Override
    public boolean isSecure() {
        return secure;
    }

    /**
     * Get the Internet Protocol (IP) address of the client or last proxy that sent the request.
     *
     * @return a {@link String} containing the IP address of the client that sent the request or {@code null} when
     * method is called on client-side.
     */
    public String getRemoteAddr() {
        return remoteAddr;
    }

    /**
     * {@link RequestContext} builder.
     */
    public static final class Builder {

        private URI requestURI;
        private String queryString;
        private Object httpSession;
        private boolean secure;
        private Principal userPrincipal;
        private Builder.IsUserInRoleDelegate isUserInRoleDelegate;
        private Map<String, List<String>> parameterMap;
        private String remoteAddr;
        private Map<String, List<String>> headers;

        /**
         * Create empty builder.
         *
         * @return empty builder instance.
         */
        public static Builder create() {
            return new Builder();
        }

        /**
         * Create builder instance based on provided {@link RequestContext}.
         *
         * @param requestContext request context.
         * @return builder instance.
         */
        public static Builder create(RequestContext requestContext) {
            Builder builder = new Builder();

            builder.requestURI = requestContext.requestURI;
            builder.queryString = requestContext.queryString;
            builder.httpSession = requestContext.httpSession;
            builder.secure = requestContext.secure;
            builder.userPrincipal = requestContext.userPrincipal;
            builder.isUserInRoleDelegate = requestContext.isUserInRoleDelegate;
            builder.parameterMap = requestContext.parameterMap;
            builder.remoteAddr = requestContext.remoteAddr;
            builder.headers = requestContext.headers;

            return builder;
        }

        /**
         * Set request URI.
         *
         * @param requestURI request URI to be set.
         * @return updated {@link RequestContext.Builder} instance.
         */
        public Builder requestURI(URI requestURI) {
            this.requestURI = requestURI;
            return this;
        }

        /**
         * Set query string.
         *
         * @param queryString query string to be set.
         * @return updated {@link RequestContext.Builder} instance.
         */
        public Builder queryString(String queryString) {
            this.queryString = queryString;
            return this;
        }

        /**
         * Set http session.
         *
         * @param httpSession {@code javax.servlet.http.HttpSession} session to be set.
         * @return updated {@link RequestContext.Builder} instance.
         */
        public Builder httpSession(Object httpSession) {
            this.httpSession = httpSession;
            return this;
        }

        /**
         * Set secure state.
         *
         * @param secure secure state to be set.
         * @return updated {@link RequestContext.Builder} instance.
         */
        public Builder secure(boolean secure) {
            this.secure = secure;
            return this;
        }

        /**
         * Set {@link Principal}.
         *
         * @param principal principal to be set.
         * @return updated {@link RequestContext.Builder} instance.
         */
        public Builder userPrincipal(Principal principal) {
            this.userPrincipal = principal;
            return this;
        }

        /**
         * Set delegate for {@link RequestContext#isUserInRole(String)} method.
         *
         * @param isUserInRoleDelegate delegate for {@link RequestContext#isUserInRole(String)}.
         * @return updated {@link RequestContext.Builder} instance.
         */
        public Builder isUserInRoleDelegate(IsUserInRoleDelegate isUserInRoleDelegate) {
            this.isUserInRoleDelegate = isUserInRoleDelegate;
            return this;
        }

        /**
         * Set parameter map.
         *
         * @param parameterMap parameter map. Takes map returned from ServletRequest#getParameterMap.
         * @return updated {@link RequestContext.Builder} instance.
         */
        public Builder parameterMap(Map<String, String[]> parameterMap) {
            if (parameterMap != null) {
                this.parameterMap = new HashMap<String, List<String>>();
                for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
                    this.parameterMap.put(entry.getKey(), Arrays.asList(entry.getValue()));
                }
            } else {
                this.parameterMap = null;
            }

            return this;
        }

        /**
         * Set remote address.
         *
         * @param remoteAddr remote address to be set.
         * @return updated {@link RequestContext.Builder} instance.
         */
        public Builder remoteAddr(String remoteAddr) {
            this.remoteAddr = remoteAddr;
            return this;
        }

        /**
         * Build {@link RequestContext} from given properties.
         *
         * @return created {@link RequestContext}.
         */
        public RequestContext build() {
            return new RequestContext(requestURI, queryString, httpSession, secure, userPrincipal,
                                      isUserInRoleDelegate, remoteAddr,
                                      parameterMap != null ? parameterMap : new HashMap<String,
                                              List<String>>(), headers);
        }

        /**
         * Is user in role delegate.
         * <p>
         * Cannot easily query ServletContext or HttpServletRequest for this information, since it is stored only as
         * object.
         */
        public interface IsUserInRoleDelegate {

            /**
             * Returns a boolean indicating whether the authenticated user is included in the specified logical "role".
             * Roles and role membership can be defined using deployment descriptors. If the user has not been
             * authenticated, the method returns false.
             *
             * @param role a String specifying the name of the role.
             * @return a boolean indicating whether the user making this request belongs to a given role; false if the
             * user has not been authenticated.
             */
            public boolean isUserInRole(String role);
        }
    }
}
