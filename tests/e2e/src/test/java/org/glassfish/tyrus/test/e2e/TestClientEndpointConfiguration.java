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

package org.glassfish.tyrus.test.e2e;

import java.util.List;

import javax.websocket.Decoder;
import javax.websocket.Encoder;
import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;

import org.glassfish.tyrus.DefaultClientEndpointConfiguration;

/**
 * Configuration that enables to get the {@link javax.websocket.HandshakeResponse}.
 * Used for test purposes.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class TestClientEndpointConfiguration extends DefaultClientEndpointConfiguration {

    private HandshakeResponse handshakeResponse;

    /**
     * Creates a test configuration that will attempt
     * to connect to the given URI.
     */
    private TestClientEndpointConfiguration(List<Encoder> encoders, List<Decoder> decoders, List<String> subprotocols, List<Extension> extensions) {
        super(encoders, decoders, subprotocols, extensions);
    }

    @Override
    public void afterResponse(HandshakeResponse handshakeResponse) {
        this.handshakeResponse = handshakeResponse;
    }

    /**
     * Gets the {@link HandshakeResponse} which was received in the afterResponse method.
     *
     * @return handshakeResponse.
     */
    public HandshakeResponse getHandshakeResponse() {
        return handshakeResponse;
    }

    /**
     * Builder class used to build the {@link TestClientEndpointConfiguration}.
     */
    public static class Builder extends DefaultClientEndpointConfiguration.Builder {
        @Override
        public TestClientEndpointConfiguration build() {
            return new TestClientEndpointConfiguration(encoders, decoders, protocols, extensions);
        }
    }
}
