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

import java.net.URI;

import org.junit.Test;
import static org.junit.Assert.*;


/**
 * @author Ondrej Kosatka (ondrej.kosatka at oracle.com)
 */
public class AuthConfigTest {

    private static final String PROPRIETARY = "Proprietary";

    @Test
    public void testDefaultBuilder() {
        AuthConfig authConfig = AuthConfig.builder().build();
        assertEquals("Default AuthConfig should have just 2 authenticators", authConfig.getAuthenticators().size(), 2);
        assertTrue("Default AuthConfig should have Basic authenticator", authConfig.getAuthenticators().get(AuthConfig.BASIC) instanceof BasicAuthenticator);
        assertTrue("Default AuthConfig should have Digest authenticator", authConfig.getAuthenticators().get(AuthConfig.DIGEST) instanceof DigestAuthenticator);
    }

    @Test
    public void testDisableBasic() {
        AuthConfig authConfig = AuthConfig.builder().disableProvidedBasicAuth().build();
        assertEquals("AuthConfig should have just 1 authenticators", authConfig.getAuthenticators().size(), 1);
        assertTrue("AuthConfig should have Digest authenticator", authConfig.getAuthenticators().get(AuthConfig.DIGEST) instanceof DigestAuthenticator);
        assertNull("AuthConfig should remove Basic auth", authConfig.getAuthenticators().get(AuthConfig.BASIC));
    }

    @Test
    public void testDisableDigest() {
        AuthConfig authConfig = AuthConfig.builder().disableProvidedDigestAuth().build();
        assertEquals("AuthConfig should have just 1 authenticators", authConfig.getAuthenticators().size(), 1);
        assertTrue("AuthConfig should have Basic authenticator", authConfig.getAuthenticators().get(AuthConfig.BASIC) instanceof BasicAuthenticator);
        assertNull("AuthConfig should remove Digest auth", authConfig.getAuthenticators().get(AuthConfig.DIGEST));
    }

    @Test
    public void testOverrideBasic() {
        AuthConfig authConfig = AuthConfig.builder().registerAuthProvider(AuthConfig.BASIC, new ProprietaryAuthenticator()).build();
        assertEquals("Default AuthConfig should have just 2 authenticators", authConfig.getAuthenticators().size(), 2);
        assertTrue("AuthConfig should have Proprietary authenticator mapped as Basic", authConfig.getAuthenticators().get(AuthConfig.BASIC) instanceof ProprietaryAuthenticator);
    }

    @Test
    public void testAddNewAuthenticator() {
        AuthConfig authConfig = AuthConfig.builder().registerAuthProvider(PROPRIETARY, new ProprietaryAuthenticator()).build();
        assertEquals("AuthConfig should have just 3 authenticators", authConfig.getAuthenticators().size(), 3);
        assertTrue("AuthConfig should have Basic authenticator", authConfig.getAuthenticators().get(AuthConfig.BASIC) instanceof BasicAuthenticator);
        assertTrue("AuthConfig should have Digest authenticator", authConfig.getAuthenticators().get(AuthConfig.DIGEST) instanceof DigestAuthenticator);
        assertTrue("AuthConfig should have Proprietary authenticator", authConfig.getAuthenticators().get(PROPRIETARY) instanceof ProprietaryAuthenticator);
    }

    class ProprietaryAuthenticator extends Authenticator {

        @Override
        public String generateAuthorizationHeader(URI uri, String wwwAuthenticateHeader, Credentials credentials) throws AuthenticationException {
            return "authorize me";
        }
    }
}
