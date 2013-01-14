/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 - 2013 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.websocket.ClientEndpointConfiguration;
import javax.websocket.Decoder;
import javax.websocket.DefaultClientConfiguration;
import javax.websocket.Encoder;
import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;

/**
 * Configuration used for client endpoints as the default one.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class TyrusClientEndpointConfiguration extends DefaultClientConfiguration implements ClientEndpointConfiguration {

    /**
     * Creates a client configuration that will attempt
     * to connect to the given URI.
     */
    protected TyrusClientEndpointConfiguration() {
        super();
    }

    @Override
    public void beforeRequest(Map<String, List<String>> headers) {
    }

    @Override
    public void afterResponse(HandshakeResponse handshakeResponse) {
    }

    private List<Extension> extensions = Collections.emptyList();

    @Override
    public List<String> getExtensions() {
        return super.getExtensions();    // TODO
    }

    // remove once ClientEndpointConfiguration#getExtensions() returns List<Extension>
    public List<Extension> getExtensions___TODO() {
        return this.extensions;
    }

    // fix once DefaultClientEndpointConfiguration#setExtensions() takes List<Extension> as parameter
    public TyrusClientEndpointConfiguration setExtensions___TODO(List<Extension> extensions) {
        this.extensions = extensions;
        return this;
    }

    /**
     * Builder class used to build the {@link TyrusClientEndpointConfiguration}.
     */
    public static class Builder {

        private List<Encoder> encoders;
        private List<Decoder> decoders;
        private List<String> protocols;
        private List<Extension> extensions;

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
         */
        public Builder() {
        }

        /**
         * Build {@link TyrusServerEndpointConfiguration}.
         *
         * @return new {@link TyrusServerEndpointConfiguration} instance.
         */
        public TyrusClientEndpointConfiguration build() {
            final TyrusClientEndpointConfiguration configuration = new TyrusClientEndpointConfiguration();
            configuration.setEncoders(encoders == null ? Collections.<Encoder>emptyList() : encoders);
            configuration.setDecoders(decoders == null ? Collections.<Decoder>emptyList() : decoders);
            configuration.setExtensions___TODO(extensions == null ? Collections.<Extension>emptyList() : extensions);
            configuration.setPreferredSubprotocols(protocols == null ? Collections.<String>emptyList() : protocols);
            return configuration;
        }
    }
}
