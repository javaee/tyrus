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
package org.glassfish.tyrus;

import java.util.Collections;
import java.util.List;

import javax.net.websocket.Decoder;
import javax.net.websocket.Encoder;
import javax.net.websocket.EndpointConfiguration;
import javax.net.websocket.extensions.Extension;

/**
 * Default configuration implementation, immutable.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public abstract class DefaultEndpointConfiguration implements EndpointConfiguration {

    /**
     * List of message encoders.
     */
    private final List<Encoder> encoders;

    /**
     * List of message decoders.
     */
    private final List<Decoder> decoders;

    /**
     * {@link java.util.Collections.UnmodifiableList} of sub-protocols supported by the corresponding {@link javax.net.websocket.Endpoint}.
     */
    protected final List<String> subProtocols;

    /**
     * {@link java.util.Collections.UnmodifiableList} of extensions supported by the corresponding {@link javax.net.websocket.Endpoint}.
     */
    protected final List<Extension> extensions;

    protected DefaultEndpointConfiguration(List<Encoder> encoders, List<Decoder> decoders, List<String> subprotocols, List<Extension> extensions) {
        if (encoders != null) {
            this.encoders = Collections.unmodifiableList(encoders);
        } else {
            List<Encoder> emptyList = Collections.emptyList();
            this.encoders = Collections.unmodifiableList(emptyList);
        }

        if (decoders != null) {
            this.decoders = Collections.unmodifiableList(decoders);
        } else {
            List<Decoder> emptyList = Collections.emptyList();
            this.decoders = Collections.unmodifiableList(emptyList);
        }

        if (subprotocols != null) {
            this.subProtocols = Collections.unmodifiableList(subprotocols);
        } else {
            this.subProtocols = Collections.emptyList();
        }

        if (extensions != null) {
            this.extensions = Collections.unmodifiableList(extensions);
        } else {
            this.extensions = Collections.emptyList();
        }
    }

    /**
     * Encoders used to encode messages.
     *
     * @return {@link java.util.Collections.UnmodifiableList} of {@link Encoder}.
     */
    @Override
    public List<Encoder> getEncoders() {
        return encoders;
    }

    /**
     * Decoders used to decode messages.
     *
     * @return {@link java.util.Collections.UnmodifiableList} of {@link Decoder}.
     */
    @Override
    public List<Decoder> getDecoders() {
        return decoders;
    }

    protected static class Builder<T extends Builder> {
        protected String uri;
        protected List<Encoder> encoders;
        protected List<Decoder> decoders;
        protected List<String> protocols;
        protected List<Extension> extensions;

        /**
         * Create new {@link Builder}.
         *
         * @param uri URI the corresponding {@link javax.xml.ws.Endpoint} will use to connect to.
         */
        public Builder(String uri) {
            this.uri = uri;
        }

        /**
         * Set encoders.
         * The {@link List} has to be ordered in order of preference, favorite first.
         *
         * @param encoders {@link List} of encoders ordered as specified above.
         * @return {@link Builder}.
         */
        @SuppressWarnings({"unchecked"})
        public final T encoders(List<Encoder> encoders) {
            this.encoders = encoders;
            return (T) this;
        }

        /**
         * Set decoders.
         * The {@link List} has to be ordered in order of preference, favorite first.
         *
         * @param decoders {@link List} of decoders ordered as specified above.
         * @return {@link Builder}.
         */
        @SuppressWarnings({"unchecked"})
        public final T decoders(List<Decoder> decoders) {
            this.decoders = decoders;
            return (T) this;
        }

        /**
         * Set preferred sub-protocols that this {@link javax.net.websocket.Endpoint} would like to use for its sessions.
         * The {@link List} has to be ordered in order of preference, favorite first.
         *
         * @param protocols {@link List} of sub-protocols ordered as specified above.
         * @return {@link Builder}.
         */
        @SuppressWarnings({"unchecked"})
        public T protocols(List<String> protocols) {
            this.protocols = protocols;
            return (T) this;
        }

        /**
         * Set of extensions that this {@link javax.net.websocket.Endpoint} would like to use for its sessions.
         * The {@link List} has to be ordered in order of preference, favorite first.
         *
         * @param extensions {@link List} of extensions ordered as specified above.
         * @return {@link Builder}.
         */
        @SuppressWarnings({"unchecked"})
        public T extensions(List<Extension> extensions) {
            this.extensions = extensions;
            return (T) this;
        }
    }
}
