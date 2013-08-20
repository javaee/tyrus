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

package org.glassfish.tyrus.servlet;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import javax.websocket.Endpoint;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpoint;

import javax.servlet.FilterRegistration;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.HandlesTypes;

/**
 * Registers a filter for upgrade handshake.
 * <p/>
 * All requests will be handled by registered filter if not specified otherwise.
 *
 * @author Jitendra Kotamraju
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
@HandlesTypes({ServerEndpoint.class, ServerApplicationConfig.class, Endpoint.class})
public class TyrusServletContainerInitializer implements ServletContainerInitializer {
    private static final Logger LOGGER =
            Logger.getLogger(TyrusServletContainerInitializer.class.getName());

    /**
     * Tyrus classes scanned by container will be filtered.
     */
    private static final Set<Class<?>> FILTERED_CLASSES = new HashSet<Class<?>>() {{
        add(org.glassfish.tyrus.server.TyrusServerConfiguration.class);
    }};

    @Override
    public void onStartup(Set<Class<?>> classes, ServletContext ctx) throws ServletException {
        if (classes == null || classes.isEmpty()) {
            return;
        }

        classes.removeAll(FILTERED_CLASSES);
        TyrusServletFilter filter = ctx.createFilter(TyrusServletFilter.class);
        filter.setClasses(classes);

        // HttpSessionListener registration
        ctx.addListener(filter);

        // Filter registration
        final FilterRegistration.Dynamic reg = ctx.addFilter("WebSocket filter", filter);
        reg.setAsyncSupported(true);
        reg.addMappingForUrlPatterns(null, true, "/*");
        LOGGER.info("Registering WebSocket filter for url pattern /*");

        final TyrusServletServerContainer serverContainer = new TyrusServletServerContainer(filter);
        ctx.setAttribute(TyrusServletServerContainer.SERVER_CONTAINER_ATTRIBUTE, serverContainer);
    }
}