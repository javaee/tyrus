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
     * @param timeoutHandler handshake timeout handler. {@link org.glassfish.tyrus.spi.ClientEngine.TimeoutHandler#handleTimeout()}
     *                       is invoked if {@link #processResponse(UpgradeResponse, Writer, org.glassfish.tyrus.spi.Connection.CloseListener)}
     *                       is not called within handshake timeout.
     * @return request to be send on the wire.
     */
    public UpgradeRequest createUpgradeRequest(URI uri, TimeoutHandler timeoutHandler);

    /**
     * Process handshake response.
     *
     * @param upgradeResponse response to be processed.
     * @param writer          used for sending dataframes from client endpoint.
     * @param closeListener   will be called when connection is closed, will be set as listener of returned {@link Connection}.
     * @return Tyrus connection representation. Handles all incoming data.
     * @see org.glassfish.tyrus.spi.Connection#getReadHandler()
     * @see ReadHandler
     */
    public Connection processResponse(UpgradeResponse upgradeResponse, final Writer writer, final Connection.CloseListener closeListener);

    /**
     * Indicates to container that handshake timeout was reached.
     */
    public interface TimeoutHandler {
        /**
         * Invoked when timeout is reached. Container is supposed to clean all resources related to {@link ClientEngine} instance.
         */
        public void handleTimeout();
    }
}
