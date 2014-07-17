/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.client.auth;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Authentication configuration class contains a map of authentication schemes and assigned authenticators. An instance of
 * this class must be create by {@link AuthConfig.Builder}. Basic and Digest Authentication schemes authenticators
 * have been implemented in Tyrus. Other authenticators can be plugged in easily via {@link AuthConfig.Builder}.
 * Whether alternative implementation of Basic and Digest or other not-implemented schemes.
 *
 * @author Ondrej Kosatka (ondrej.kosatka at oracle.com)
 * @see Authenticator
 */
public class AuthConfig {

    /**
     * Encoding used for authentication calculations.
     */
    static final Charset CHARACTER_SET = Charset.forName("iso-8859-1");

    /**
     * Basic authentication scheme key.
     */
    static final String BASIC = "Basic";

    /**
     * Digest authentication scheme key.
     */
    static final String DIGEST = "Digest";

    protected final Map<String, Authenticator> authenticators;


    private AuthConfig(Map<String, Authenticator> authenticators) {
        this.authenticators = authenticators;
    }

    /**
     * Get a map of authenticators, where case insensitive authentication scheme to {@link Authenticator}.
     *
     * @return map of authenticators. Case insensitive authentication scheme is mapped to {@link Authenticator}.
     */
    public Map<String, Authenticator> getAuthenticators() {
        return authenticators;
    }

    /**
     * Create a {@link Builder} instance for constructing {@link AuthConfig}.
     *
     * @return a builder instance.
     */
    public static Builder builder() {
        return Builder.create();
    }

    /**
     * The AuthConfig.Builder is a class used for creating an instance of {@link AuthConfig} for purpose of HTTP Authentication.
     * <p/>
     * Here is an example:
     * <p/>
     * Building an authentication configuration enhanced with user defined NTLM authentication and overridden Basic Authentication
     * <p/>
     * <pre><code>
     * AuthConfig authConfig = AuthConfig.Builder.create().
     *                          registerAuthProvider("NTLM", myAuthenticator).
     *                          registerAuthProvider("Basic", myBasicAuthenticator).
     *                          build();
     * </code></pre>
     * <p/>
     * Building an authentication configuration with Basic scheme disabled
     * <p/>
     * <pre><code>
     * AuthConfig authConfig = AuthConfig.Builder.create().
     *                          disableProvidedBasicAuth().
     *                          build();
     * </code></pre>
     *
     * @see Credentials
     * @see Authenticator
     */
    public final static class Builder {

        private Map<String, Authenticator> authenticators = Collections.synchronizedSortedMap(new TreeMap<String, Authenticator>(String.CASE_INSENSITIVE_ORDER));

        private Builder() {
            authenticators.put(BASIC, new BasicAuthenticator());
            authenticators.put(DIGEST, new DigestAuthenticator());
        }

        /**
         * Create an empty {@link AuthConfig} instance.
         *
         * @return an empty {@link AuthConfig} instance.
         */
        public static Builder create() {
            return new Builder();
        }

        /**
         * Register user defined {@link Authenticator} with scheme as a key. The key is case insensitive.
         *
         * @param userDefinedAuthenticator user defined {@link Authenticator}.
         * @return updated {@link AuthConfig} instance.
         */
        public final Builder registerAuthProvider(final String scheme, final Authenticator userDefinedAuthenticator) {
            this.authenticators.put(scheme, userDefinedAuthenticator);
            return this;
        }

        /**
         * Disable Basic Authenticator provided by Tyrus.
         *
         * @return updated {@link AuthConfig} instance.
         */
        public final Builder disableProvidedBasicAuth() {
            if (authenticators.get(BASIC) != null && authenticators.get(BASIC) instanceof BasicAuthenticator) {
                authenticators.remove(BASIC);
            }
            return this;
        }

        /**
         * Disable Digest Authenticator provided by Tyrus.
         *
         * @return updated {@link AuthConfig} instance.
         */
        public final Builder disableProvidedDigestAuth() {
            if (authenticators.get(DIGEST) != null && authenticators.get(DIGEST) instanceof DigestAuthenticator) {
                authenticators.remove(DIGEST);
            }
            return this;
        }

        /**
         * Build an instance of {@link AuthConfig}.
         *
         * @return an instance of {@link AuthConfig}.
         */
        public AuthConfig build() {
            return new AuthConfig(authenticators);
        }


    }
}
