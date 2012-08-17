/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 - 2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.websocket.client;

import org.glassfish.websocket.platform.ServerContainerImpl;
import org.glassfish.websocket.platform.Model;
import org.glassfish.websocket.platform.WebSocketEndpointImpl;
import org.glassfish.websocket.spi.SPIEndpoint;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;

/**
 * WebSocketClient implementation.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class WebSocketClient {

    private Set<GrizzlyWebSocket> sockets = new HashSet<GrizzlyWebSocket>();

    /**
     * Create new WebSocketClient instance.
     *
     * @return new WebSocketClient instance.
     */
    public static WebSocketClient createClient() {
        return new WebSocketClient();
    }

    /**
     * Open new WebSocket connection.
     *
     * @param url Address to which the connection connects.
     * @param timeoutMs Connection timeout.
     * @param endpoints Endpoints that will be registered with the socket.
     * @return newly created {@link ClientSocket}.
     */
    public ClientSocket openSocket(String url, long timeoutMs, Object... endpoints) {
        URI uri = null;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        GrizzlyWebSocket gws = new GrizzlyWebSocket(uri,timeoutMs);

        for (Object endpoint : endpoints) {
            if (endpoint instanceof SPIEndpoint) {
                gws.addEndpoint((SPIEndpoint)endpoint);
            } else {
                ServerContainerImpl cci = new ServerContainerImpl(null,uri.getPath(),uri.getPort());
                WebSocketEndpointImpl clientEndpoint = new WebSocketEndpointImpl(cci);
                Model model = null;
                try {
                    model = new Model(endpoint);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InstantiationException e) {
                    e.printStackTrace();
                }
                clientEndpoint.doInit(null,model,false);
                gws.addEndpoint(clientEndpoint);
            }
        }

        gws.connect();
        sockets.add(gws);
        return gws;
    }
}