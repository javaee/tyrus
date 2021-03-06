/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2015 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.tests.qa.lifecycle.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpointConfig;

import org.glassfish.tyrus.tests.qa.lifecycle.LifeCycleDeployment;
import org.glassfish.tyrus.tests.qa.lifecycle.ProgrammaticWholeMessageEndpoint;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.StringSessionImpl;
import org.glassfish.tyrus.tests.qa.tools.SessionController;

/**
 * @author Michal Čonos (michal.conos at oracle.com)
 */
public class CustomConfigurationProtocolsProgrammatic implements ServerApplicationConfig {

    public static class StringLifeCycle extends ProgrammaticWholeMessageEndpoint<String>
            implements MessageHandler.Whole<String> {

        @Override
        public void createLifeCycle() {
            lifeCycle = new StringSessionImpl(false);
        }

        @Override
        public void onOpen(Session session, EndpointConfig ec) {
            if (this.session == null) {
                this.session = session;
            }
            logger.log(Level.INFO, "ProgrammaticEndpoint: onOpen");
            this.sc = new SessionController(session);
            createLifeCycle();

            logger.log(Level.INFO, "Get Subprotocols: {0}", ((ServerEndpointConfig) ec).getSubprotocols());
            logger.log(Level.INFO, "Get negotiated subprotocol: {0}", session.getNegotiatedSubprotocol());
            lifeCycle.setSessionController(sc);
            session.addMessageHandler(this);
            lifeCycle.onServerOpen(session, ec);
        }
    }

    @Override
    public Set<ServerEndpointConfig> getEndpointConfigs(Set<Class<? extends Endpoint>> set) {
        Set<ServerEndpointConfig> configSet = new HashSet<ServerEndpointConfig>();
        List<String> protocols = Arrays.asList(LifeCycleDeployment.serverProtoOrder);
        ServerEndpointConfig config = ServerEndpointConfig.Builder.create(
                StringLifeCycle.class,
                LifeCycleDeployment.LIFECYCLE_ENDPOINT_PATH).subprotocols(protocols).build();
        configSet.add(config);
        return configSet;
    }

    @Override
    public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> set) {
        return Collections.EMPTY_SET;
    }
}
