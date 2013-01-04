/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
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

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import javax.websocket.ClientEndpointConfiguration;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.Extension;
import javax.websocket.Session;
import javax.websocket.WebSocketClient;
import javax.websocket.WebSocketContainer;

import org.glassfish.tyrus.AnnotatedEndpoint;
import org.glassfish.tyrus.DefaultClientEndpointConfiguration;
import org.glassfish.tyrus.EndpointWrapper;
import org.glassfish.tyrus.TyrusContainerProvider;
import org.glassfish.tyrus.spi.TyrusClientSocket;
import org.glassfish.tyrus.spi.TyrusContainer;

/**
 * ClientManager implementation.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class ClientManager implements WebSocketContainer {
    private static final String ENGINE_PROVIDER_CLASSNAME = "org.glassfish.tyrus.container.grizzly.GrizzlyEngine";

    private final Set<TyrusClientSocket> sockets = new HashSet<TyrusClientSocket>();
    private final TyrusContainer engine;

    private long maxSessionIdleTimeout;
    private long maxBinaryMessageBufferSize;
    private long maxTextMessageBufferSize;

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
            ClientManager cm = new ClientManager((TyrusContainer) engineProviderClazz.newInstance());
            TyrusContainerProvider.getContainerProvider().setContainer(cm);
            return cm;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load provider class: " + engineProviderClassname + ".");
        }
    }

    private ClientManager(TyrusContainer engine) {
        this.engine = engine;
    }

    @Override
    public Session connectToServer(Class annotatedEndpointClass, URI path) throws DeploymentException {
        return connectToServer(annotatedEndpointClass, null, path.toString());
    }

    @Override
    public Session connectToServer(Class<? extends Endpoint> endpointClass, ClientEndpointConfiguration cec, URI path) throws DeploymentException {
        return connectToServer(endpointClass, cec, path.toString());
    }

    public Session connectToServer(Object obj, URI path) throws DeploymentException {
        return connectToServer(obj, null, path.toString());
    }

    public Session connectToServer(Object obj, ClientEndpointConfiguration cec, URI path) throws DeploymentException {
        return connectToServer(obj, cec, path.toString());
    }


    @Override
    public Set<Session> getOpenSessions() {
        return null;
    }

    /**
     * Connects client endpoint o to the specified url.
     *
     * @param o             the endpoint.
     * @param configuration of the endpoint.
     * @param url           to which the client will connect.
     * @return {@link Session}.
     * @throws DeploymentException
     */
    public Session connectToServer(Object o, ClientEndpointConfiguration configuration, String url) throws DeploymentException {
        // TODO use maxSessionIdleTimeout, maxBinaryMessageBufferSize and maxTextMessageBufferSize

        ClientEndpointConfiguration config;
        Endpoint endpoint;

        if (o instanceof Endpoint) {
            endpoint = (Endpoint) o;
            config = configuration == null ? new DefaultClientEndpointConfiguration.Builder().build() : configuration;
        } else if (o instanceof Class && (((Class<?>) o).getAnnotation(WebSocketClient.class) != null)) {
            endpoint = AnnotatedEndpoint.fromClass((Class) o, false);
            config = (ClientEndpointConfiguration) ((AnnotatedEndpoint) endpoint).getEndpointConfiguration();
        } else {
            endpoint = AnnotatedEndpoint.fromInstance(o, false);
            config = (ClientEndpointConfiguration) ((AnnotatedEndpoint) endpoint).getEndpointConfiguration();
        }

        try {
            EndpointWrapper clientEndpoint = new EndpointWrapper(endpoint, config, this, null);
            TyrusClientSocket clientSocket = engine.openClientSocket(url, config, clientEndpoint);
            sockets.add(clientSocket);
            return clientSocket.getSession();
        } catch (Exception e) {
            throw new DeploymentException("Connection failed.", e);
        }
    }

    /**
     * TBD - should be present in {@link WebSocketContainer}.
     */
    public void close() {
        for (TyrusClientSocket s : sockets) {
            s.close();
        }
    }

    @Override
    public long getMaxSessionIdleTimeout() {
        return maxSessionIdleTimeout;
    }

    @Override
    public void setMaxSessionIdleTimeout(long maxSessionIdleTimeout) {
        this.maxSessionIdleTimeout = maxSessionIdleTimeout;
    }

    @Override
    public long getMaxBinaryMessageBufferSize() {
        return maxBinaryMessageBufferSize;
    }

    @Override
    public void setMaxBinaryMessageBufferSize(long maxBinaryMessageBufferSize) {
        this.maxBinaryMessageBufferSize = maxBinaryMessageBufferSize;
    }

    @Override
    public long getMaxTextMessageBufferSize() {
        return maxTextMessageBufferSize;
    }

    @Override
    public void setMaxTextMessageBufferSize(long maxTextMessageBufferSize) {
        this.maxTextMessageBufferSize = maxTextMessageBufferSize;
    }

    @Override
    public Set<Extension> getInstalledExtensions() {
        return null;
    }

    @Override
    public long getDefaultAsyncSendTimeout() {
        return 0;  // TODO: Implement.
    }

    @Override
    public void setAsyncSendTimeout(long timeoutmillis) {
        // TODO: Implement.
    }
}
