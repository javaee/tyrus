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
package org.glassfish.tyrus.client;

import org.glassfish.tyrus.platform.configuration.DefaultClientEndpointConfiguration;
import org.glassfish.tyrus.platform.EndpointWrapper;
import org.glassfish.tyrus.platform.Model;

import javax.net.websocket.ClientContainer;
import javax.net.websocket.ClientEndpointConfiguration;
import javax.net.websocket.Endpoint;
import javax.net.websocket.Session;
import javax.net.websocket.extensions.Extension;
import java.util.HashSet;
import java.util.Set;

/**
 * ClientManager implementation.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class ClientManager implements ClientContainer {

    private Set<GrizzlyWebSocket> sockets = new HashSet<GrizzlyWebSocket>();

    private long timeout;

    /**
     * Create new ClientManager instance.
     *
     * @return new ClientManager instance.
     */
    public static ClientManager createClient() {
        return new ClientManager();
    }


    @Override
    public void connectToServer(Endpoint endpoint, ClientEndpointConfiguration clc) {
        DefaultClientEndpointConfiguration dcec = null;
        if (clc instanceof DefaultClientEndpointConfiguration) {
            dcec = (DefaultClientEndpointConfiguration) clc;
        }
        GrizzlyWebSocket gws = new GrizzlyWebSocket(dcec.getUri(), 10000);

        Model model = null;
        try {
            model = new Model(endpoint);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }
        dcec.setModel(model);
        EndpointWrapper clientEndpoint = new EndpointWrapper(null, model, clc, this);
        gws.addEndpoint(clientEndpoint);

        gws.connect();
        sockets.add(gws);
    }

    @Override
    public Set<Session> getActiveSessions() {
        return null;
    }

    @Override
    public long getMaxSessionIdleTimeout() {
        return 0;
    }

    @Override
    public void setMaxSessionIdleTimeout(long timeout) {

    }

    @Override
    public long getMaxBinaryMessageBufferSize() {
        return 0;
    }

    @Override
    public void setMaxBinaryMessageBufferSize(long max) {

    }

    @Override
    public long getMaxTextMessageBufferSize() {
        return 0;
    }

    @Override
    public void setMaxTextMessageBufferSize(long max) {

    }

    @Override
    public Set<Extension> getInstalledExtensions() {
        return null;
    }
}