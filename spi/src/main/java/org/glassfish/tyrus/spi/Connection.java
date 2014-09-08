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

import java.net.InetAddress;
import java.util.Map;

import javax.websocket.CloseReason;

/**
 * A logical websocket connection. Tyrus creates this connection after
 * successful upgrade and gets data from {@link ReadHandler} and writes data
 * to {@link Writer}.
 */
public interface Connection {

    /**
     * Connection property map key.
     *
     * @see ClientEngine.ClientUpgradeInfo#createConnection(Map)
     * @see WebSocketEngine.UpgradeInfo#createConnection(Writer, CloseListener, Map)
     */
    public static enum ConnectionPropertyKey {
        /**
         * {@link InetAddress} instance representing a remote address.
         * <p/>
         * An expected value is {@link InetAddress}.
         * Property should be passed in {@code connectionProperties} map.
         */
        REMOTE_INET_ADDRESS("org.glassfish.tyrus.core.remoteInetAddress"),

        /**
         * Remote IP address.
         * <p/>
         * An expected value is non-empty {@link String}.
         * Property should be passed in {@code connectionProperties} map.
         */
        REMOTE_ADDR("org.glassfish.tyrus.core.remoteAddr"),

        /**
         * Remote hostname.
         * <p/>
         * An expected value is non-empty {@link String}.
         * Property should be passed in {@code connectionProperties} map.
         */
        REMOTE_HOSTNAME("org.glassfish.tyrus.core.remoteHostName"),

        /**
         * Remote port number.
         * <p/>
         * An expected value is {@link Integer} greater than 0.
         * Property should be passed in {@code connectionProperties} map.
         */
        REMOTE_PORT("org.glassfish.tyrus.core.remotePort"),

        /**
         * {@link InetAddress} instance representing a local address.
         * <p/>
         * An expected value is {@link InetAddress}.
         * Property should be passed in {@code connectionProperties} map.
         */

        LOCAL_INET_ADDRESS("org.glassfish.tyrus.core.localInetAddress"),

        /**
         * Local IP address.
         * <p/>
         * An expected value is non-empty {@link String}.
         * Property should be passed in {@code connectionProperties} map.
         */
        LOCAL_ADDR("org.glassfish.tyrus.core.localAddr"),

        /**
         * Local hostname.
         * <p/>
         * An expected value is non-empty {@link String}.
         * Property should be passed in {@code connectionProperties} map.
         */
        LOCAL_HOSTNAME("org.glassfish.tyrus.core.localHostName"),

        /**
         * Local port number.
         * <p/>
         * An expected value is {@link Integer} greater than 0.
         * Property should be passed in {@code connectionProperties} map.
         */
        LOCAL_PORT("org.glassfish.tyrus.core.localPort");

        private final String key;

        private ConnectionPropertyKey(final String key) {
            this.key = key;
        }

        @Override
        public String toString() {
            return this.key;
        }

    }

    /**
     * Returns a read handler. A transport can pass websocket data to
     * tyrus using the handler.
     *
     * @return tyrus read handler that handles websocket data.
     */
    ReadHandler getReadHandler();

    /**
     * Returns the same writer that is passed for creating connection in
     * {@link WebSocketEngine.UpgradeInfo#createConnection(Writer, CloseListener, Map)}
     * The transport writer that actually writes websocket data
     * to underlying connection.
     *
     * @return transport writer that actually writes websocket data
     * to underlying connection.
     */
    Writer getWriter();

    /**
     * Returns the same close listener that is passed for creating connection in
     * {@link WebSocketEngine.UpgradeInfo#createConnection(Writer, CloseListener, Map)}.
     * <p/>
     * This transport close listener receives connection close notifications
     * from Tyrus.
     */
    CloseListener getCloseListener();

    /**
     * Notifies tyrus that underlying transport is closing the connection.
     *
     * @param reason for closing the actual connection.
     */
    void close(CloseReason reason);

    /**
     * Transport close listener that receives connection close
     * notifications from Tyrus.
     */
    interface CloseListener {
        /**
         * Tyrus notifies that logical connection is closed.
         *
         * @param reason for closing the connection.
         */
        void close(CloseReason reason);
    }
}
