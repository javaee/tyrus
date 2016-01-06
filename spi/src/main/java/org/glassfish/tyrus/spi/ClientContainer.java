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
package org.glassfish.tyrus.spi;

import java.io.IOException;
import java.util.Map;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;

/**
 * Entry point for client implementation.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public interface ClientContainer {

    /**
     * Property name for maximal incoming buffer size.
     * <p>
     * Can be set in properties map (see {@link #openClientSocket(javax.websocket.ClientEndpointConfig, java.util.Map,
     * ClientEngine)}).
     *
     * @deprecated please use {@code org.glassfish.tyrus.client.ClientProperties#INCOMING_BUFFER_SIZE}.
     */
    String INCOMING_BUFFER_SIZE = "org.glassfish.tyrus.incomingBufferSize";

    /**
     * WLS version of {@link org.glassfish.tyrus.spi.ClientContainer#INCOMING_BUFFER_SIZE}.
     */
    String WLS_INCOMING_BUFFER_SIZE = "weblogic.websocket.tyrus.incoming-buffer-size";

    /**
     * Open client socket - connect to endpoint specified with {@code url} parameter.
     * <p>
     * Called from ClientManager when {@link javax.websocket.WebSocketContainer#connectToServer(Class,
     * javax.websocket.ClientEndpointConfig, java.net.URI)} is invoked.
     *
     * @param cec          endpoint configuration. SPI consumer can access user properties, {@link
     *                     javax.websocket.ClientEndpointConfig.Configurator}, extensions and subprotocol
     *                     configuration,
     *                     etc..
     * @param properties   properties passed from client container. Don't mix up this with {@link
     *                     javax.websocket.ClientEndpointConfig#getUserProperties()}, these are Tyrus proprietary.
     * @param clientEngine one instance equals to one connection, cannot be reused. Implementation is expected to call
     *                     {@link ClientEngine#createUpgradeRequest(ClientEngine.TimeoutHandler)} and {@link
     *                     ClientEngine#processResponse(UpgradeResponse, Writer,
     *                     org.glassfish.tyrus.spi.Connection.CloseListener)} (in that order).
     * @throws javax.websocket.DeploymentException when the client endpoint is invalid or when there is any other (not
     *                                             specified) connection problem.
     * @throws java.io.IOException                 when there is any I/O issue related to opening client socket or
     *                                             connecting to remote endpoint.
     */
    void openClientSocket(ClientEndpointConfig cec, Map<String, Object> properties, ClientEngine clientEngine) throws
            DeploymentException, IOException;
}
