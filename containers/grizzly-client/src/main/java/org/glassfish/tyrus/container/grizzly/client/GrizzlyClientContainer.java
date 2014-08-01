/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2014 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.spi.ClientContainer;
import org.glassfish.tyrus.spi.ClientEngine;

/**
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class GrizzlyClientContainer implements ClientContainer {

    /**
     * @deprecated please use {@link org.glassfish.tyrus.client.ClientProperties#SSL_ENGINE_CONFIGURATOR}.
     */
    @SuppressWarnings("UnusedDeclaration")
    public static final String SSL_ENGINE_CONFIGURATOR = ClientProperties.SSL_ENGINE_CONFIGURATOR;

    /**
     * When set to {@code true} (boolean value), client runtime preserves used container and reuses it for outgoing
     * connections.
     *
     * @see ClientProperties#SHARED_CONTAINER_IDLE_TIMEOUT
     * @deprecated please use {@link org.glassfish.tyrus.client.ClientProperties#SHARED_CONTAINER}.
     */
    @SuppressWarnings("UnusedDeclaration")
    public static final String SHARED_CONTAINER = ClientProperties.SHARED_CONTAINER;

    /**
     * Container idle timeout in seconds (Integer value).
     *
     * @see ClientProperties#SHARED_CONTAINER
     * @deprecated please use {@link org.glassfish.tyrus.client.ClientProperties#SHARED_CONTAINER_IDLE_TIMEOUT}.
     */
    @SuppressWarnings("UnusedDeclaration")
    public static final String SHARED_CONTAINER_IDLE_TIMEOUT = ClientProperties.SHARED_CONTAINER_IDLE_TIMEOUT;

    //The same value Grizzly is using for socket timeout.
    private static final long CLIENT_SOCKET_TIMEOUT = 30000;

    @Override
    public void openClientSocket(ClientEndpointConfig cec,
                                 Map<String, Object> properties,
                                 ClientEngine clientEngine
    ) throws DeploymentException, IOException {


        new GrizzlyClientSocket(CLIENT_SOCKET_TIMEOUT, clientEngine, properties).connect();
    }
}
