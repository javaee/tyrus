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
package org.glassfish.tyrus;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.websocket.Decoder;
import javax.websocket.Encoder;
import javax.websocket.HandshakeRequest;
import javax.websocket.HandshakeResponse;
import javax.websocket.ServerEndpointConfiguration;

/**
 * Provides the default {@link ServerEndpointConfiguration}.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class DefaultServerEndpointConfiguration extends DefaultEndpointConfiguration implements ServerEndpointConfiguration {

    /**
     * List of allowed origins. If not set, the test origin test will be always successful.
     */
    private final List<String> origins;

    /**
     * Creates new configuration for {@link javax.websocket.Endpoint} which is used on the server side.
     *
     * @param encoders message encoders.
     * @param decoders message decoders.
     * @param subprotocols supported sub - protocols.
     * @param extensions supported extensions.
     * @param origins accepted origins.
     */
    protected DefaultServerEndpointConfiguration(String uri, List<Encoder> encoders, List<Decoder> decoders,
                                                 List<String> subprotocols, List<String> extensions,
                                                 List<String> origins) {
        super(uri, encoders, decoders, subprotocols, extensions);
        this.origins = origins == null ? Collections.<String>emptyList() : Collections.unmodifiableList(origins);
    }

    @Override
    public String getNegotiatedSubprotocol(List<String> requestedSubprotocols) {
        for (String serverProtocol : subProtocols) {
            if (requestedSubprotocols.contains(serverProtocol)) {
                return serverProtocol;
            }
        }

        return null;
    }

    @Override
    public List<String> getNegotiatedExtensions(List<String> requestedExtensions) {
        List<String> result = new ArrayList<String>();

        for (String requestedExtension : requestedExtensions) {
            for (String extension : extensions) {
                if(extension.equals(requestedExtension)){
                    result.add(requestedExtension);
                }
            }
        }

        return result;
    }

    @Override
    public boolean checkOrigin(String originHeaderValue) {
        return origins.isEmpty() || origins.contains(originHeaderValue);
    }

    @Override
    public boolean matchesURI(URI uri) {
        // TODO: this method has now way of returning path parameters! using getPath() instead and implementing
        // TODO: the matching algorithm in the runtime
        throw new UnsupportedOperationException();
    }

    @Override
    public void modifyHandshake(HandshakeRequest request, HandshakeResponse response) {

    }

    /**
     * Builder class used to build the {@link DefaultServerEndpointConfiguration}.
     */
    public static class Builder extends DefaultEndpointConfiguration.Builder<Builder> {

        private List<String> origins;

        /**
         * Create new {@link Builder}.
         *
         * @param uri at which the {@link javax.websocket.Endpoint} will be deployed.
         */
        public Builder(String uri) {
            super(uri);
        }

        /**
         * Set the allowed origins.
         *
         * @param origins from which the connection is allowed.
         * @return Builder.
         */
        public Builder origins(List<String> origins) {
            this.origins = origins;
            return this;
        }

        /**
         * Build {@link DefaultServerEndpointConfiguration}.
         *
         * @return new {@link DefaultServerEndpointConfiguration} instance.
         */
        public DefaultServerEndpointConfiguration build() {
            return new DefaultServerEndpointConfiguration(uri, encoders, decoders, protocols, extensions, origins);
        }
    }
}
