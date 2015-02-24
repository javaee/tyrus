/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2015 Oracle and/or its affiliates. All rights reserved.
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

import javax.websocket.DeploymentException;
import javax.websocket.server.ServerEndpointConfig;

import static org.glassfish.tyrus.spi.Connection.CloseListener;

/**
 * WebSocket engine is used for upgrading HTTP requests into websocket connections. A transport gets hold of the engine
 * from the {@link ServerContainer} and upgrades HTTP handshake requests.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public interface WebSocketEngine {

    /**
     * A transport calls this method to upgrade a HTTP request.
     *
     * @param request  request to be upgraded.
     * @param response response to the upgrade request.
     * @return info about upgrade status and connection details.
     */
    UpgradeInfo upgrade(UpgradeRequest request, UpgradeResponse response);

    // TODO : constructor? / List<Class<?> / List<ServerEndpointConfig>\
    // (one call instead of iteration).
    // TODO remove ??

    /**
     * Register endpoint class.
     *
     * @param endpointClass endpoint class to be registered.
     * @param contextPath   context path of the registered endpoint.
     * @throws DeploymentException when the endpoint is invalid.
     */
    void register(Class<?> endpointClass, String contextPath) throws DeploymentException;

    /**
     * Register {@link javax.websocket.server.ServerEndpointConfig}.
     *
     * @param serverConfig server endpoint to be registered.
     * @param contextPath  context path of the registered endpoint.
     * @throws DeploymentException when the endpoint is invalid.
     */
    void register(ServerEndpointConfig serverConfig, String contextPath) throws DeploymentException;

    /**
     * Upgrade info that includes status for HTTP request upgrading and connection creation details.
     */
    interface UpgradeInfo {

        /**
         * Returns the status of HTTP request upgrade.
         *
         * @return status of the upgrade.
         */
        UpgradeStatus getStatus();

        /**
         * Creates a connection if the upgrade is successful. Tyrus would call onConnect lifecycle method on the
         * endpoint during the invocation of this method.
         *
         * @param writer        transport writer that actually writes tyrus websocket data to underlying connection.
         * @param closeListener transport listener for receiving tyrus close notifications.
         * @return upgraded connection if the upgrade is successful otherwise null.
         */
        Connection createConnection(Writer writer, CloseListener closeListener);
    }

    /**
     * Upgrade Status for HTTP request upgrading.
     */
    enum UpgradeStatus {
        /**
         * Not a WebSocketRequest or no mapping in the application. This may mean that HTTP request processing should
         * continue (in servlet container, the next filter may be called).
         */
        NOT_APPLICABLE,

        /**
         * Upgrade failed due to version, extensions, origin check etc. Tyrus would set an appropriate HTTP error status
         * code in {@link UpgradeResponse}.
         */
        HANDSHAKE_FAILED,

        /**
         * Upgrade is successful.
         */
        SUCCESS
    }
}
