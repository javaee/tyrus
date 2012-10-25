/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.server;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import javax.net.websocket.ClientEndpointConfiguration;
import javax.net.websocket.Endpoint;
import javax.net.websocket.ServerEndpointConfiguration;
import javax.net.websocket.Session;
import javax.net.websocket.annotations.WebSocketEndpoint;
import javax.net.websocket.extensions.Extension;
import org.glassfish.tyrus.EndpointWrapper;
import org.glassfish.tyrus.Model;
import org.glassfish.tyrus.WithProperties;
import org.glassfish.tyrus.spi.SPIRegisteredEndpoint;
import org.glassfish.tyrus.spi.TyrusServer;

/**
 * Server Container Implementation.
 *
 * @author Martin Matula (martin.matula at oracle.com)
 */
public class TyrusServerContainer extends WithProperties implements ServerContainer,
        javax.net.websocket.ServerContainer {
    private final TyrusServer server;
    private final String contextPath;
    private final ServerConfiguration configuration;
    private final Set<SPIRegisteredEndpoint> endpoints = new HashSet<SPIRegisteredEndpoint>();

    public TyrusServerContainer(TyrusServer server, String contextPath, ServerConfiguration configuration) {
        this.server = server;
        this.contextPath = contextPath;
        this.configuration = configuration;
    }

    @Override
    public ServerConfiguration getConfiguration() {
        return configuration;
    }

    public void start() throws IOException {
        // start the underlying server
        server.start();

        // deploy all the class-based endpoints
        for (Class<?> endpointClass : configuration.getEndpointClasses()) {
            // introspect the bean and find all the paths....
            WebSocketEndpoint wseAnnotation = endpointClass.getAnnotation(WebSocketEndpoint.class);
            if (wseAnnotation == null) {
                Logger.getLogger(getClass().getName()).warning("Endpoint class " + endpointClass.getName() + " not " +
                        "annotated with @WebSocketEndpoint annotation, so will be ignored.");
                continue;
            }

            String nextPath = wseAnnotation.value();
            Model model = new Model(endpointClass);
            String wrapperBeanPath = (contextPath.endsWith("/") ? contextPath.substring(0, contextPath.length() - 1) : contextPath)
                    + "/" + (nextPath.startsWith("/") ? nextPath.substring(1) : nextPath);

            DefaultServerEndpointConfiguration.Builder builder = new DefaultServerEndpointConfiguration.Builder(wrapperBeanPath);
            DefaultServerEndpointConfiguration dsec = builder.encoders(model.getEncoders()).decoders(model.getDecoders()).build();
            EndpointWrapper endpoint = new EndpointWrapper(wrapperBeanPath, model, dsec, this);

            SPIRegisteredEndpoint ge = server.register(endpoint);
            endpoints.add(ge);
            Logger.getLogger(getClass().getName()).info("Registered a " + endpoint.getClass() + " at " + endpoint.getPath());
        }

        // TODO: deploy programmatic endpoints
    }

    @Override
    public void stop() {
        for (SPIRegisteredEndpoint wsa : this.endpoints) {
            wsa.remove();
            this.server.unregister(wsa);
            Logger.getLogger(getClass().getName()).info("Closing down : " + wsa);
        }
        server.stop();
    }

    @Override
    public void publishServer(Endpoint endpoint, ServerEndpointConfiguration ilc) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void connectToServer(Endpoint endpoint, ClientEndpointConfiguration olc) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Session> getActiveSessions() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getMaxSessionIdleTimeout() {
        return configuration.getMaxSessionIdleTimeout();
    }

    @Override
    public void setMaxSessionIdleTimeout(long timeout) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getMaxBinaryMessageBufferSize() {
        return configuration.getMaxBinaryMessageBufferSize();
    }

    @Override
    public void setMaxBinaryMessageBufferSize(long max) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getMaxTextMessageBufferSize() {
        return configuration.getMaxTextMessageBufferSize();
    }

    @Override
    public void setMaxTextMessageBufferSize(long max) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Extension> getInstalledExtensions() {
        throw new UnsupportedOperationException();
    }
}
