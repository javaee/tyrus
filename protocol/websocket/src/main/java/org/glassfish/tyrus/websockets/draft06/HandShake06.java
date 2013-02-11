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

package org.glassfish.tyrus.websockets.draft06;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.glassfish.tyrus.websockets.HandShake;
import org.glassfish.tyrus.websockets.HandshakeException;
import org.glassfish.tyrus.websockets.WebSocketEngine;
import org.glassfish.tyrus.websockets.WebSocketRequest;
import org.glassfish.tyrus.websockets.WebSocketResponse;

public class HandShake06 extends HandShake {
    private final SecKey secKey;
    private final List<String> enabledExtensions = Collections.emptyList();
    private final List<String> enabledProtocols = Collections.emptyList();

    public HandShake06(URI url) {
        super(url);
        secKey = new SecKey();
    }

    public HandShake06(WebSocketRequest request) {
        super(request);
        final Map<String, String> headers = request.getHeaders();
        String value = headers.get(WebSocketEngine.SEC_WS_EXTENSIONS_HEADER);
        if (value != null) {
            setExtensions(HandShake.fromHeaders(Arrays.asList(value)));
        }
        secKey = SecKey.generateServerKey(new SecKey(headers.get(WebSocketEngine.SEC_WS_KEY_HEADER)));
    }

    public void setHeaders(WebSocketResponse response) {
        response.setReasonPhrase(WebSocketEngine.RESPONSE_CODE_MESSAGE);
        response.getHeaders().put(WebSocketEngine.SEC_WS_ACCEPT, secKey.getSecKey());
        if (!getEnabledExtensions().isEmpty()) {
            response.getHeaders().put(WebSocketEngine.SEC_WS_EXTENSIONS_HEADER, getHeaderFromList(getSubProtocols()));
        }
    }

    @Override
    public WebSocketRequest getRequest() {
        final WebSocketRequest webSocketRequest = super.getRequest();
        webSocketRequest.getHeaders().put(WebSocketEngine.SEC_WS_KEY_HEADER, secKey.toString());
        webSocketRequest.getHeaders().put(WebSocketEngine.SEC_WS_ORIGIN_HEADER, getOrigin());
        webSocketRequest.getHeaders().put(WebSocketEngine.SEC_WS_VERSION, getVersion() + "");
        if (!getExtensions().isEmpty()) {
            webSocketRequest.getHeaders().put(WebSocketEngine.SEC_WS_EXTENSIONS_HEADER, getHeaderFromList(getExtensions()));
        }
        return webSocketRequest;
    }

    protected int getVersion() {
        return 6;
    }

    @Override
    public void validateServerResponse(final WebSocketResponse response) throws HandshakeException {
        super.validateServerResponse(response);
        secKey.validateServerKey(response.getHeaders().get(WebSocketEngine.SEC_WS_ACCEPT));
    }

    List<String> getEnabledExtensions() {
        return enabledExtensions;
    }

    public List<String> getEnabledProtocols() {
        return enabledProtocols;
    }
}
