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
package org.glassfish.tyrus.platform.configuration;

import javax.net.websocket.Endpoint;
import javax.net.websocket.HandshakeRequest;
import javax.net.websocket.HandshakeResponse;
import javax.net.websocket.ServerConfiguration;
import javax.net.websocket.ServerEndpointConfiguration;
import javax.net.websocket.extensions.Extension;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides the default {@link ServerConfiguration} used by the {@link org.glassfish.tyrus.platform.BeanServer}.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class DefaultServerEndpointConfiguration extends DefaultEndpointConfiguration implements ServerEndpointConfiguration {

    /**
     * List of allowed origins. If not set, the test origin test will be always successful.
     */
    private List<String> origins;

    /**
     * Creates new configuration for {@link Endpoint} which is used on the server side.
     *
     * @param
     */
    private DefaultServerEndpointConfiguration(Builder builder){
        super(builder.encoders, builder.decoders, builder.protocols, builder.extensions);
        this.origins = builder.origins;
    }

    @Override
    public String getNegotiatedSubprotocol(List<String> requestedSubprotocols) {
        for (String serverProtocol : subProtocols) {
            if(requestedSubprotocols.contains(serverProtocol)){
                return serverProtocol;
            }
        }

        return null;
    }

    @Override
    public List<Extension> getNegotiatedExtensions(List<Extension> requestedExtensions) {
        List<Extension> result = new ArrayList<Extension>();

        for (Extension requestedExtension : requestedExtensions) {
            if(extensions.contains(requestedExtension)){
                result.add(requestedExtension);
            }
        }

        return result;
    }

    @Override
    public boolean checkOrigin(String originHeaderValue) {
        return origins == null || origins.contains(originHeaderValue);
    }

    @Override
    public boolean matchesURI(URI uri) {
        return false;
    }

    @Override
    public void modifyHandshake(HandshakeRequest request, HandshakeResponse response) {

    }

    /**
     * Builder class used to build the {@link DefaultClientEndpointConfiguration}.
     */
    public static class Builder extends DefaultEndpointConfiguration.Builder<Builder> {

        private List<String> origins;

        /**
         * Create new {@link Builder}.
         *
         * @param uri at which the {@link Endpoint} will be deployed.
         */
        public Builder(URI uri) {
            super(uri);
        }

        /**
         * Set the allowed origins.
         *
         * @param origins from which the connection is allowed.
         * @return
         */
        public  Builder origins(List<String> origins){
            this.origins = origins;
            return this;
        }

        /**
         * Build {@link DefaultServerEndpointConfiguration}.
         *
         * @return new {@link DefaultServerEndpointConfiguration} instance.
         */
        public DefaultServerEndpointConfiguration build(){
            return new DefaultServerEndpointConfiguration(this);
        }
    }
}
