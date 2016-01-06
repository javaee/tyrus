/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2016 Oracle and/or its affiliates. All rights reserved.
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

import javax.websocket.DeploymentException;
import javax.websocket.WebSocketContainer;

import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.core.Beta;
import org.glassfish.tyrus.spi.UpgradeRequest;
import org.glassfish.tyrus.spi.UpgradeResponse;

/**
 * Authenticator provides a way how to plug-in custom authentication provider.
 * <p>
 * Authenticator is called when server-side returns HTTP 401 as a reply to handshake response. Tyrus client then looks
 * for authenticator instance registered to authentication scheme provided by server.
 *
 * @author Ondrej Kosatka (ondrej.kosatka at oracle.com)
 * @see AuthConfig.Builder#registerAuthProvider(String, Authenticator)
 * @see ClientProperties#AUTH_CONFIG
 * @see ClientProperties#CREDENTIALS
 */
@Beta
public abstract class Authenticator {

    /**
     * Generate value used as "{@value UpgradeRequest#AUTHORIZATION}" header value for next request.
     * <p>
     * Thrown {@link AuthenticationException} will be wrapped as {@link DeploymentException} and thrown as a result of
     * {@link WebSocketContainer}.connectToServer(...) method call.
     *
     * @param uri                   Uri of the server endpoint.
     * @param wwwAuthenticateHeader "{@value UpgradeResponse#WWW_AUTHENTICATE}" header value received in a handshake
     *                              response.
     * @param credentials           credentials passed by property {@link ClientProperties#CREDENTIALS}. Can be {@code
     *                              null} when there were no {@link Credentials} registered.
     * @return value for {@value UpgradeRequest#AUTHORIZATION} header which will be put into next handshake request.
     * @throws AuthenticationException when it is not possible to create "{@value UpgradeRequest#AUTHORIZATION}"
     *                                 header.
     */
    public abstract String generateAuthorizationHeader(final URI uri, final String wwwAuthenticateHeader,
                                                       final Credentials credentials) throws AuthenticationException;

}
