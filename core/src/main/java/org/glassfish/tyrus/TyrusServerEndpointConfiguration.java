/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2013 Oracle and/or its affiliates. All rights reserved.
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
import javax.websocket.Endpoint;
import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.DefaultServerConfiguration;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfiguration;

/**
 * Provides the default {@link ServerEndpointConfiguration}.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class TyrusServerEndpointConfiguration extends DefaultServerConfiguration implements ServerEndpointConfiguration {

    /**
     * List of allowed origins. If not set, the test origin test will be always successful.
     */
    private final List<String> origins;

    // TODO: remove when DefaultServerConfiguration implements getNegotiatedSubprotocol & getNegotiatedExtensions
    private List<String> subProtocols = Collections.emptyList();
    private List<Extension> extensions = Collections.emptyList();

    private final String uri;

    /**
     * Creates new configuration for {@link javax.websocket.Endpoint} which is used on the server side.
     *
     * @param uri           uri.
     * @param endpointClass endpoint class.
     */
    protected TyrusServerEndpointConfiguration(Class<? extends Endpoint> endpointClass, String uri) {
        this(endpointClass, uri, Collections.<String>emptyList());
    }

    /**
     * Creates new configuration for {@link javax.websocket.Endpoint} which is used on the server side.
     *
     * @param uri           uri.
     * @param endpointClass endpoint class.
     * @param origins       accepted origins.
     */
    TyrusServerEndpointConfiguration(Class<? extends Endpoint> endpointClass, String uri, List<String> origins) {
        super(endpointClass, uri);
        this.uri = uri;
        this.origins = origins == null ? Collections.<String>emptyList() : Collections.unmodifiableList(origins);
    }

    // TODO: remove when DefaultServerConfiguration implements getNegotiatedSubprotocol & getNegotiatedExtensions
    @Override
    public DefaultServerConfiguration setSubprotocols(List<String> subprotocols) {
        super.setSubprotocols(subprotocols);
        this.subProtocols = (subprotocols == null ? Collections.<String>emptyList() : subprotocols);
        return this;
    }

    // TODO: remove when DefaultServerConfiguration implements getNegotiatedSubprotocol & getNegotiatedExtensions
    @Override
    public DefaultServerConfiguration setExtensions(List<Extension> extensions) {
        super.setExtensions(extensions);
        this.extensions = (extensions == null ? Collections.<Extension>emptyList() : extensions);
        return this;
    }

    // TODO: remove when DefaultServerConfiguration implements getNegotiatedSubprotocol & getNegotiatedExtensions
    @Override
    public String getNegotiatedSubprotocol(List<String> requestedSubprotocols) {
        if (requestedSubprotocols != null) {
            for (String serverProtocol : subProtocols) {
                if (requestedSubprotocols.contains(serverProtocol)) {
                    return serverProtocol;
                }
            }
        }

        return null;
    }

    // TODO: remove when DefaultServerConfiguration implements getNegotiatedSubprotocol & getNegotiatedExtensions
    @Override
    public List<Extension> getNegotiatedExtensions(List<Extension> requestedExtensions) {
        List<Extension> result = new ArrayList<Extension>();

        if (requestedExtensions != null) {
            for (Extension requestedExtension : requestedExtensions) {
                for (Extension extension : extensions) {
                    if (extension.equals(requestedExtension)) {
                        result.add(requestedExtension);
                    }
                }
            }
        }

        return result;
    }

    @Override
    public Class<? extends Endpoint> getEndpointClass() {
        return null;
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

    @Override
    public String getPath() {
        return uri;
    }

    /**
     * Builder class used to build the {@link TyrusServerEndpointConfiguration}.
     */
    public static class Builder {

        private List<Encoder> encoders;
        private List<Decoder> decoders;
        private List<String> protocols;
        private List<Extension> extensions;

        private List<String> origins;
        private final String uri;


        /**
         * Set encoders.
         * The {@link List} has to be ordered in order of preference, favorite first.
         *
         * @param encoders {@link List} of encoders ordered as specified above.
         * @return {@link Builder}.
         */
        @SuppressWarnings({"unchecked"})
        public final Builder encoders(List<Encoder> encoders) {
            this.encoders = encoders;
            return this;
        }

        /**
         * Set decoders.
         * The {@link List} has to be ordered in order of preference, favorite first.
         *
         * @param decoders {@link List} of decoders ordered as specified above.
         * @return {@link Builder}.
         */
        @SuppressWarnings({"unchecked"})
        public final Builder decoders(List<Decoder> decoders) {
            this.decoders = decoders;
            return this;
        }

        /**
         * Set preferred sub-protocols that this {@link javax.websocket.Endpoint} would like to use for its sessions.
         * The {@link List} has to be ordered in order of preference, favorite first.
         *
         * @param protocols {@link List} of sub-protocols ordered as specified above.
         * @return {@link Builder}.
         */
        @SuppressWarnings({"unchecked"})
        public Builder protocols(List<String> protocols) {
            this.protocols = protocols;
            return this;
        }

        /**
         * Set of extensions that this {@link javax.websocket.Endpoint} would like to use for its sessions.
         * The {@link List} has to be ordered in order of preference, favorite first.
         *
         * @param extensions {@link List} of extensions ordered as specified above.
         * @return {@link Builder}.
         */
        @SuppressWarnings({"unchecked"})
        public Builder extensions(List<Extension> extensions) {
            this.extensions = extensions;
            return this;
        }

        /**
         * Create new {@link Builder}.
         *
         * @param uri at which the {@link javax.websocket.Endpoint} will be deployed.
         */
        public Builder(String uri) {
            this.uri = uri;
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
         * Build {@link TyrusServerEndpointConfiguration}.
         *
         * @return new {@link TyrusServerEndpointConfiguration} instance.
         */
        public TyrusServerEndpointConfiguration build() {
            final TyrusServerEndpointConfiguration configuration = new TyrusServerEndpointConfiguration(null, uri, origins);
            configuration.setEncoders(encoders == null ? Collections.<Encoder>emptyList() : encoders);
            configuration.setDecoders(decoders == null ? Collections.<Decoder>emptyList() : decoders);
            configuration.setExtensions(extensions == null ? Collections.<Extension>emptyList() : extensions);
            configuration.setSubprotocols(protocols == null ? Collections.<String>emptyList() : protocols);
            return configuration;
        }
    }
}
