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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import javax.net.websocket.ClientEndpointConfiguration;
import javax.net.websocket.Decoder;
import javax.net.websocket.Encoder;
import javax.net.websocket.Endpoint;
import javax.net.websocket.ServerEndpointConfiguration;
import javax.net.websocket.Session;
import javax.net.websocket.annotations.WebSocketEndpoint;
import org.glassfish.tyrus.AnnotatedEndpoint;
import org.glassfish.tyrus.EndpointWrapper;
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

    public TyrusServerContainer(final TyrusServer server, final String contextPath,
                                final ServerConfiguration configuration) {
        this.server = server;
        this.contextPath = contextPath;
        // make a read-only copy of the configuration
        this.configuration = new ServerConfiguration() {
            private final Set<Class<?>> endpointClasses =
                    Collections.unmodifiableSet(new HashSet<Class<?>>(configuration.getEndpointClasses()));
            private final Set<EndpointWithConfiguration> endpointInstances =
                    Collections.unmodifiableSet(new HashSet<EndpointWithConfiguration>(configuration.getEndpointInstances()));
            private final long maxSessionIdleTimeout = configuration.getMaxSessionIdleTimeout();
            private final long maxBinaryMessageBufferSize = configuration.getMaxBinaryMessageBufferSize();
            private final long maxTextMessageBufferSize = configuration.getMaxTextMessageBufferSize();
            private final List<String> extensions =
                    Collections.unmodifiableList(new ArrayList<String>(configuration.getExtensions()));

            @Override
            public Set<Class<?>> getEndpointClasses() {
                return endpointClasses;
            }

            @Override
            public Set<EndpointWithConfiguration> getEndpointInstances() {
                return endpointInstances;
            }

            @Override
            public long getMaxSessionIdleTimeout() {
                return maxSessionIdleTimeout;
            }

            @Override
            public long getMaxBinaryMessageBufferSize() {
                return maxBinaryMessageBufferSize;
            }

            @Override
            public long getMaxTextMessageBufferSize() {
                return maxTextMessageBufferSize;
            }

            @Override
            public List<String> getExtensions() {
                return extensions;
            }
        };
    }

    @Override
    public ServerConfiguration getConfiguration() {
        return configuration;
    }

    public void start() throws IOException {
        // start the underlying server
        server.start();

        Set<ServerConfiguration.EndpointWithConfiguration> allEndpoints =
                new HashSet<ServerConfiguration.EndpointWithConfiguration>(configuration.getEndpointInstances());

        // deploy all the class-based endpoints
        for (Class<?> endpointClass : configuration.getEndpointClasses()) {
            // introspect the bean and find all the paths....
            final WebSocketEndpoint wseAnnotation = endpointClass.getAnnotation(WebSocketEndpoint.class);
            if (wseAnnotation == null) {
                Logger.getLogger(getClass().getName()).warning("Endpoint class " + endpointClass.getName() + " not " +
                        "annotated with @WebSocketEndpoint annotation, so will be ignored.");
                continue;
            }

            String endpointPath = wseAnnotation.value();
            AnnotatedEndpoint annotatedEndpoint = new AnnotatedEndpoint(endpointClass);

            // TODO: use lifecycle provider to get instances of encoders/decoders
            List<Encoder> encoders = new ArrayList<Encoder>();
            if (wseAnnotation.encoders() != null) {
                //noinspection unchecked
                for (Class<? extends Encoder> encoderClass : (Class<? extends Encoder>[]) wseAnnotation.encoders()) {
                    try {
                        encoders.add(encoderClass.newInstance());
                    } catch (Exception e) {
                        throw new RuntimeException("Unable to instantiate encoder: " + encoderClass.getName(), e);
                    }
                }
            }
            List<Decoder> decoders = new ArrayList<Decoder>();
            if (wseAnnotation.decoders() != null) {
                //noinspection unchecked
                for (Class<? extends Decoder> decoderClass : (Class<? extends Decoder>[]) wseAnnotation.decoders()) {
                    try {
                        decoders.add(decoderClass.newInstance());
                    } catch (Exception e) {
                        throw new RuntimeException("Unable to instantiate decoder: " + decoderClass.getName(), e);
                    }
                }
            }

            ServerEndpointConfiguration config = new DefaultServerEndpointConfiguration.Builder(endpointPath)
                    .encoders(encoders).decoders(decoders).protocols(wseAnnotation.subprotocols() == null ?
                            Collections.<String>emptyList() : Arrays.asList(wseAnnotation.subprotocols()))
                    .extensions(configuration.getExtensions())
                    // TODO: fix once origins is added to the @WebSocketEndpoint annotation
                    .origins(Collections.<String>emptyList()).build();

            allEndpoints.add(new ServerConfiguration.EndpointWithConfiguration(annotatedEndpoint, config));
            Logger.getLogger(getClass().getName()).info("Registered a " + endpointClass + " at " + endpointPath);
        }

        for (ServerConfiguration.EndpointWithConfiguration endpoint : allEndpoints) {
            EndpointWrapper ew = new EndpointWrapper(endpoint.getEndpoint(), endpoint.getConfiguration(), this, contextPath);
            SPIRegisteredEndpoint ge = server.register(ew);
            endpoints.add(ge);
        }
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
    public Set<String> getInstalledExtensions() {
        return Collections.unmodifiableSet(new HashSet<String>(configuration.getExtensions()));
    }
}
