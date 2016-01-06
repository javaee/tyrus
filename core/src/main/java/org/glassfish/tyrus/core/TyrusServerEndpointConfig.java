/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2016 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.websocket.Decoder;
import javax.websocket.Encoder;
import javax.websocket.Extension;
import javax.websocket.server.ServerEndpointConfig;

/**
 * Configuration {@link javax.websocket.server.ServerEndpointConfig} enhanced
 * to offer tyrus specific attributes like maxSessions.
 * Declarative way to define maxSessions is also available using
 * annotation {@link MaxSessions}.
 *
 * @author Ondrej Kosatka (ondrej.kosatka at oracle.com)
 * @see MaxSessions
 */
public interface TyrusServerEndpointConfig extends ServerEndpointConfig {

    /**
     * Returns configured maximal number of open sessions.
     *
     * @return tne maximal number of open sessions.
     */
    public int getMaxSessions();

    /**
     * The TyrusServerEndpointConfig.Builder is a class used for creating
     * {@link TyrusServerEndpointConfig.Builder} objects for the purposes of
     * deploying a server endpoint.
     * <p>
     * Here are some examples:
     * <p>
     * Building a plain configuration for an endpoint with just a path.
     * <p>
     * {@code ServerEndpointConfig config = TyrusServerEndpointConfig.Builder.create(ProgrammaticEndpoint.class,
     * "/foo").build();}
     * <p>
     * Building a configuration with no subprotocols, limited number of sessions (100) and a custom configurator.
     * <pre><code>
     * ServerEndpointConfig config = TyrusServerEndpointConfig.Builder.create(ProgrammaticEndpoint.class, "/bar")
     *         .subprotocols(subprotocols)
     *         .maxSessions(100)
     *         .configurator(new MyServerConfigurator())
     *         .build();
     * </code></pre>
     *
     * @author dannycoward
     */
    public final class Builder {
        private String path;
        private Class<?> endpointClass;
        private List<String> subprotocols = Collections.emptyList();
        private List<Extension> extensions = Collections.emptyList();
        private List<Class<? extends Encoder>> encoders = Collections.emptyList();
        private List<Class<? extends Decoder>> decoders = Collections.emptyList();
        private Configurator serverEndpointConfigurator;
        private int maxSessions = 0;

        /**
         * Creates the builder with the mandatory information of the endpoint class
         * (programmatic or annotated), the relative URI or URI-template to use,
         * and with no subprotocols, extensions, encoders, decoders or custom
         * configurator.
         *
         * @param endpointClass the class of the endpoint to configure
         * @param path          The URI or URI template where the endpoint will be deployed.
         *                      A trailing "/" will be ignored and the path must begin with /.
         * @return a new instance of TyrusServerEndpointConfig.Builder .
         */
        public static Builder create(Class<?> endpointClass, String path) {
            return new Builder(endpointClass, path);
        }

        // only one way to build them
        private Builder() {

        }

        /**
         * Builds the configuration object using the current attributes
         * that have been set on this builder object.
         *
         * @return a new TyrusServerEndpointConfig object.
         */
        public TyrusServerEndpointConfig build() {

            final ServerEndpointConfig serverEndpointConfig =
                    ServerEndpointConfig.Builder.create(endpointClass, path).subprotocols(subprotocols)
                                                .extensions(extensions).encoders(encoders).decoders(decoders)
                                                .configurator(serverEndpointConfigurator).build();

            return new DefaultTyrusServerEndpointConfig(
                    serverEndpointConfig,
                    this.maxSessions
            );
        }

        private Builder(Class endpointClass, String path) {
            if (endpointClass == null) {
                throw new IllegalArgumentException("endpointClass cannot be null");
            }
            this.endpointClass = endpointClass;
            if (path == null || !path.startsWith("/")) {
                throw new IllegalStateException("Path cannot be null and must begin with /");
            }
            this.path = path;
        }

        /**
         * Sets the list of encoder implementation classes for this builder.
         *
         * @param encoders the encoders.
         * @return this builder instance.
         */
        public TyrusServerEndpointConfig.Builder encoders(List<Class<? extends Encoder>> encoders) {
            this.encoders = (encoders == null) ? new ArrayList<Class<? extends Encoder>>() : encoders;
            return this;
        }

        /**
         * Sets the decoder implementation classes to use in the configuration.
         *
         * @param decoders the decoders.
         * @return this builder instance.
         */
        public TyrusServerEndpointConfig.Builder decoders(List<Class<? extends Decoder>> decoders) {
            this.decoders = (decoders == null) ? new ArrayList<Class<? extends Decoder>>() : decoders;
            return this;
        }

        /**
         * Sets the subprotocols to use in the configuration.
         *
         * @param subprotocols the subprotocols.
         * @return this builder instance.
         */
        public TyrusServerEndpointConfig.Builder subprotocols(List<String> subprotocols) {
            this.subprotocols = (subprotocols == null) ? new ArrayList<String>() : subprotocols;
            return this;
        }


        /**
         * Sets the extensions to use in the configuration.
         *
         * @param extensions the extensions to use.
         * @return this builder instance.
         */
        public TyrusServerEndpointConfig.Builder extensions(List<Extension> extensions) {
            this.extensions = (extensions == null) ? new ArrayList<Extension>() : extensions;
            return this;
        }

        /**
         * Sets the custom configurator to use on the configuration
         * object built by this builder.
         *
         * @param serverEndpointConfigurator the configurator.
         * @return this builder instance
         */
        public TyrusServerEndpointConfig.Builder configurator(Configurator serverEndpointConfigurator) {
            this.serverEndpointConfigurator = serverEndpointConfigurator;
            return this;
        }

        /**
         * Sets maximal number of open sessions.
         *
         * @param maxSessions maximal number of open session.
         * @return this builder instance.
         */
        public TyrusServerEndpointConfig.Builder maxSessions(final int maxSessions) {
            this.maxSessions = maxSessions;
            return this;
        }
    }
}
