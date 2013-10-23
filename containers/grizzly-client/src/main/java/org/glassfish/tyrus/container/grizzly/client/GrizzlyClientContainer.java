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
package org.glassfish.tyrus.container.grizzly.client;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;

import org.glassfish.tyrus.core.TyrusWebSocketEngine;
import org.glassfish.tyrus.spi.ClientContainer;
import org.glassfish.tyrus.spi.ClientSocket;
import org.glassfish.tyrus.spi.EndpointWrapper;

import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;

/**
 * @author Danny Coward (danny.coward at oracle.com)
 */
public class GrizzlyClientContainer implements ClientContainer {

    public static final String SSL_ENGINE_CONFIGURATOR = "org.glassfish.tyrus.client.sslEngineConfigurator";

    //The same value Grizzly is using for socket timeout.
    private static final long CLIENT_SOCKET_TIMEOUT = 30000;

    @Override
    public ClientSocket openClientSocket(String url, ClientEndpointConfig cec, EndpointWrapper endpoint,
                                         ClientContainer.ClientHandshakeListener listener, Map<String, Object> properties) throws DeploymentException, IOException {
        URI uri;

        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new DeploymentException("Invalid URI.", e);
        }

        SSLEngineConfigurator sslEngineConfigurator = (properties == null ? null : (SSLEngineConfigurator) properties.get(SSL_ENGINE_CONFIGURATOR));
        // if we are trying to access "wss" scheme and we don't have sslEngineConfigurator instance
        // we should try to create ssl connection using JVM properties.
        if (uri.getScheme().equalsIgnoreCase("wss") && sslEngineConfigurator == null) {
            SSLContextConfigurator defaultConfig = new SSLContextConfigurator();
            defaultConfig.retrieve(System.getProperties());
            sslEngineConfigurator = new SSLEngineConfigurator(defaultConfig, true, false, false);
        }

        GrizzlyClientSocket clientSocket = new GrizzlyClientSocket(endpoint, uri, cec, CLIENT_SOCKET_TIMEOUT, listener,
                new TyrusWebSocketEngine(endpoint.getWebSocketContainer(), properties == null ? null : (Integer) properties.get(TyrusWebSocketEngine.INCOMING_BUFFER_SIZE)),
                properties == null ? null : sslEngineConfigurator,
                properties == null ? null : (String) properties.get(GrizzlyClientSocket.PROXY_URI),
                properties == null ? null : (ThreadPoolConfig) properties.get(GrizzlyClientSocket.WORKER_THREAD_POOL_CONFIG),
                properties == null ? null : (ThreadPoolConfig) properties.get(GrizzlyClientSocket.SELECTOR_THREAD_POOL_CONFIG));
        clientSocket.connect();
        return clientSocket;
    }
}
