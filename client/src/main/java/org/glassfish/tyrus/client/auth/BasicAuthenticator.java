/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2015 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.tyrus.core.Base64Utils;
import org.glassfish.tyrus.core.l10n.LocalizationMessages;

/**
 * Generates a value of {@code Authorization} header of HTTP request for Basic Http Authentication scheme.
 *
 * @author Ondrej Kosatka (ondrej.kosatka at oracle.com)
 */
final class BasicAuthenticator extends Authenticator {

    @Override
    public String generateAuthorizationHeader(final URI uri, final String wwwAuthenticateHeader,
                                              final Credentials credentials) throws AuthenticationException {
        return generateAuthorizationHeader(credentials);
    }

    private String generateAuthorizationHeader(final Credentials credentials) throws AuthenticationException {
        if (credentials == null) {
            throw new AuthenticationException(LocalizationMessages.AUTHENTICATION_CREDENTIALS_MISSING());
        }
        String username = credentials.getUsername();
        byte[] password = credentials.getPassword();

        final byte[] prefix = (username + ":").getBytes(AuthConfig.CHARACTER_SET);
        final byte[] usernamePassword = new byte[prefix.length + password.length];

        System.arraycopy(prefix, 0, usernamePassword, 0, prefix.length);
        System.arraycopy(password, 0, usernamePassword, prefix.length, password.length);

        return "Basic " + Base64Utils.encodeToString(usernamePassword, false);
    }

}
