/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.Map;

import javax.websocket.Decoder;
import javax.websocket.Encoder;
import javax.websocket.Extension;
import javax.websocket.server.ServerEndpointConfig;

/**
 * Default tyrus-specific implementation of {@code TyrusServerEndpointConfig}.
 *
 * @author Ondrej Kosatka (ondrej.kosatka at oracle.com)
 */
final class DefaultTyrusServerEndpointConfig implements TyrusServerEndpointConfig {

    /* wrapped origin config */
    private ServerEndpointConfig config;
    /* maximal number of open sessions */
    private int maxSessions;


    // The builder ensures nothing except configurator can be {@code null}.
    DefaultTyrusServerEndpointConfig(ServerEndpointConfig config, int maxSessions) {
        this.config = config;
        this.maxSessions = maxSessions;
    }

    @Override
    public int getMaxSessions() {
        return maxSessions;
    }

    @Override
    public Class<?> getEndpointClass() {
        return config.getEndpointClass();
    }

    @Override
    public List<Class<? extends Encoder>> getEncoders() {
        return config.getEncoders();
    }

    @Override
    public List<Class<? extends Decoder>> getDecoders() {
        return config.getDecoders();
    }

    @Override
    public String getPath() {
        return config.getPath();
    }

    @Override
    public ServerEndpointConfig.Configurator getConfigurator() {
        return config.getConfigurator();
    }

    @Override
    public final Map<String, Object> getUserProperties() {
        return config.getUserProperties();
    }

    @Override
    public final List<String> getSubprotocols() {
        return config.getSubprotocols();
    }

    @Override
    public final List<Extension> getExtensions() {
        return config.getExtensions();
    }

}
