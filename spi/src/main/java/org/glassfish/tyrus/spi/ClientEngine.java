/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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

import java.net.URI;

/**
 * Facade for handling client operations from containers.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public interface ClientEngine {

    /**
     * Create upgrade request and register {@link TimeoutHandler}.
     *
     * @param uri            URI of remote endpoint.
     * @param timeoutHandler handshake timeout handler. {@link TimeoutHandler#handleTimeout()}
     *                       is invoked if {@link #processResponse(UpgradeResponse, Writer, Connection.CloseListener)}
     *                       is not called within handshake timeout.
     * @return request to be send on the wire.
     */
    public UpgradeRequest createUpgradeRequest(URI uri, TimeoutHandler timeoutHandler);

    /**
     * Process handshake and return {@link ClientEngine.UpgradeInfo} with handshake status ({@link ClientEngine.UpgradeStatus}).
     *
     * @param upgradeResponse response to be processed.
     * @param writer          used for sending dataframes from client endpoint.
     * @param closeListener   will be called when connection is closed, will be set as listener of returned {@link Connection}.
     * @return info with upgrade status.
     */
    public UpgradeInfo processResponse(UpgradeResponse upgradeResponse, final Writer writer, final Connection.CloseListener closeListener);

    /**
     * Indicates to container that handshake timeout was reached.
     */
    public interface TimeoutHandler {
        /**
         * Invoked when timeout is reached. Container is supposed to clean all resources related to {@link ClientEngine} instance.
         */
        public void handleTimeout();
    }

    /**
     * Upgrade process status holder.
     * <p/>
     * Provides information about handshake/upgrade process ({@link #getUpgradeStatus()}), creates {@link UpgradeRequest}
     * ({@link #getUpgradeRequest()}) which should be send
     * (when {@link #getUpgradeStatus()} returns {@link ClientEngine.UpgradeStatus#NEXT_UPGRADE_REQUEST_REQUIRED})
     * and finally creates {@link Connection} ({@link #createConnection()}) when {@link ClientEngine.UpgradeStatus}
     * is {@link ClientEngine.UpgradeStatus#SUCCESS}.
     */
    public interface UpgradeInfo {

        /**
         * Get {@link ClientEngine.UpgradeStatus}.
         *
         * @return {@link ClientEngine.UpgradeStatus}.
         */
        UpgradeStatus getUpgradeStatus();

        /**
         * Get {@link UpgradeRequest} when {@link #getUpgradeStatus()} returns
         * {@link ClientEngine.UpgradeStatus#NEXT_UPGRADE_REQUEST_REQUIRED}, otherwise return
         * {@code null}.
         *
         * @return {@link org.glassfish.tyrus.spi.UpgradeRequest} or {@code null}.
         */
        UpgradeRequest getUpgradeRequest();

        /**
         * Create {@link Connection} when {@link #getUpgradeStatus()} returns {@link ClientEngine.UpgradeStatus#SUCCESS},
         * otherwise return {@code null}.
         *
         * @return websocket connection or {@code null}.
         */
        Connection createConnection();

    }

    /**
     * Status of upgrade process.
     * Returned by {@link #processResponse(UpgradeResponse, Writer, Connection.CloseListener)}.
     */
    public enum UpgradeStatus {

        /**
         * If upgrade process requires another {@code upgrade request}. New instance of
         * {@link UpgradeRequest} could be obtain by calling
         * {@link ClientEngine.UpgradeInfo#getUpgradeRequest()}.
         */
        NEXT_UPGRADE_REQUEST_REQUIRED,

        /**
         * Upgrade process failed.
         */
        UPGRADE_REQUEST_FAILED,

        /**
         * Upgrade process was successful. Client can create connection ({@link ClientEngine.UpgradeInfo#createConnection()}).
         *
         * @see Connection
         */
        SUCCESS,
    }

}
