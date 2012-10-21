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

import java.util.logging.Logger;
import org.glassfish.tyrus.EndpointWrapper;
import org.glassfish.tyrus.Model;

import javax.net.websocket.ClientContainer;
import javax.net.websocket.ClientEndpointConfiguration;
import javax.net.websocket.Endpoint;
import javax.net.websocket.Session;
import javax.net.websocket.extensions.Extension;

import java.net.ConnectException;
import java.util.HashSet;
import java.util.Set;
import org.glassfish.tyrus.spi.TyrusClientSocket;
import org.glassfish.tyrus.spi.TyrusContainer;

/**
 * ClientManager implementation.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class ClientManager implements ClientContainer {
    private static final String ENGINE_PROVIDER_CLASSNAME = "org.glassfish.tyrus.grizzly.GrizzlyEngine";

    private final Set<TyrusClientSocket> sockets = new HashSet<TyrusClientSocket>();
    private final TyrusContainer engine;

    public static ClientManager createClient() {
        return createClient(ENGINE_PROVIDER_CLASSNAME);
    }

    /**
     * Create new ClientManager instance.
     *
     * @return new ClientManager instance.
     */
    public static ClientManager createClient(String engineProviderClassname) {
        try {
            Class engineProviderClazz = Class.forName(engineProviderClassname);
            Logger.getLogger(ClientManager.class.getName()).info("Provider class loaded: " + engineProviderClassname);
            return new ClientManager((TyrusContainer) engineProviderClazz.newInstance());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load provider class: " + engineProviderClassname + ".");
        }
    }

    private ClientManager(TyrusContainer engine) {
        this.engine = engine;
    }

    @Override
    public void connectToServer(Endpoint endpoint, ClientEndpointConfiguration clc) {
        DefaultClientEndpointConfiguration dcec;

        //TODO change this mechanism once the method signature is modified in API.
        try {
            if (clc instanceof DefaultClientEndpointConfiguration) {
                dcec = (DefaultClientEndpointConfiguration) clc;
            } else {
                throw new ConnectException("Provided configuration is not the supported one.");
            }
            Model model = null;
            try {
                model = new Model(endpoint);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            }

            EndpointWrapper clientEndpoint = new EndpointWrapper(null, model, clc, this);
            TyrusClientSocket clientSocket = engine.openClientSocket(
                    dcec.getUri(), clc, clientEndpoint);
            sockets.add(clientSocket);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
