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
package org.glassfish.tyrus.server;

import java.util.Collections;
import java.util.Set;
import java.util.logging.Logger;

import javax.websocket.DeploymentException;
import javax.websocket.server.ServerEndpointConfig;

import org.glassfish.tyrus.core.OsgiRegistry;
import org.glassfish.tyrus.core.ReflectionHelper;
import org.glassfish.tyrus.spi.ServerContainer;

/**
 * Factory for creating server containers.
 * Taken from Jersey 2.
 *
 * @author Martin Matula (martin.matula at oracle.com)
 */
public class ServerContainerFactory {
    private static OsgiRegistry osgiRegistry = null;

    private static void initOsgiRegistry() {
        try {
            osgiRegistry = OsgiRegistry.getInstance();
            if (osgiRegistry != null) {
                osgiRegistry.hookUp();
            }
        } catch (Throwable e) {
            osgiRegistry = null;
        }
    }

    /**
     * Creates a new server container based on the supplied container provider.
     *
     * @param providerClassName Container provider implementation class name.
     * @param contextPath       URI path at which the websocket server should be exposed at.
     * @param port              Port at which the server should listen.
     * @param classes           Server configuration.
     * @return New instance of {@link TyrusServerContainer}.
     */
    public static ServerContainer create(String providerClassName, String contextPath, int port,
                                         Set<Class<?>> classes) {
        Class<? extends org.glassfish.tyrus.spi.ServerContainerFactory> providerClass;

        initOsgiRegistry();

        try {
            if (osgiRegistry != null) {
                //noinspection unchecked
                providerClass = (Class<org.glassfish.tyrus.spi.ServerContainerFactory>) osgiRegistry.classForNameWithException(providerClassName);
            } else {
                //noinspection unchecked
                providerClass = (Class<org.glassfish.tyrus.spi.ServerContainerFactory>) Class.forName(providerClassName);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load container provider class: " + providerClassName, e);
        }
        Logger.getLogger(ServerContainerFactory.class.getName()).info("Provider class loaded: " + providerClassName);
        return create(providerClass, contextPath, port, classes, Collections.<Class<?>>emptySet(),
                Collections.<ServerEndpointConfig>emptySet());
    }

    /**
     * Creates a new server container based on the supplied container provider.
     *
     * @param providerClass           Container provider implementation class.
     * @param contextPath             URI path at which the websocket server should be exposed at.
     * @param port                    Port at which the server should listen.
     * @param configuration           Server configuration.
     * @param dynamicallyAddedClasses dynamically deployed classes. See {@link javax.websocket.server.ServerContainer#addEndpoint(Class)}.
     * @param dynamicallyAddedEndpointConfigs
     *                                dynamically deployed {@link ServerEndpointConfig ServerEndpointConfigs}. See
     *                                {@link javax.websocket.server.ServerContainer#addEndpoint(ServerEndpointConfig)}.
     * @return New instance of {@link TyrusServerContainer}.
     */
    public static ServerContainer create(Class<? extends org.glassfish.tyrus.spi.ServerContainerFactory> providerClass, String contextPath, int port,
                                         Set<Class<?>> configuration, Set<Class<?>> dynamicallyAddedClasses,
                                         Set<ServerEndpointConfig> dynamicallyAddedEndpointConfigs) {
        org.glassfish.tyrus.spi.ServerContainerFactory containerProvider;
        try {
            containerProvider = ReflectionHelper.getInstance(providerClass);
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate provider class: " + providerClass.getName(), e);
        }
        return create(containerProvider, contextPath, port, configuration, dynamicallyAddedClasses, dynamicallyAddedEndpointConfigs);
    }

    /**
     * Creates a new server container based on the supplied container provider.
     *
     * @param containerProvider       Container provider instance.
     * @param contextPath             URI path at which the websocket server should be exposed at.
     * @param port                    Port at which the server should listen.
     * @param configuration           Server configuration.
     * @param dynamicallyAddedClasses dynamically deployed classes. See {@link javax.websocket.server.ServerContainer#addEndpoint(Class)}.
     * @param dynamicallyAddedEndpointConfigs
     *                                dynamically deployed {@link ServerEndpointConfig ServerEndpointConfigs}. See
     *                                {@link javax.websocket.server.ServerContainer#addEndpoint(ServerEndpointConfig)}.
     * @return New instance of {@link TyrusServerContainer}.
     */
    public static ServerContainer create(final org.glassfish.tyrus.spi.ServerContainerFactory containerProvider, String contextPath, int port,
                                         Set<Class<?>> configuration, Set<Class<?>> dynamicallyAddedClasses,
                                         Set<ServerEndpointConfig> dynamicallyAddedEndpointConfigs) {

        final ServerContainer container = containerProvider.createContainer(Collections.<String, Object>emptyMap());

        for (Class<?> clazz : configuration) {
            try {
                container.addEndpoint(clazz);
            } catch (DeploymentException e) {
                // TODO
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }

        return container;

        // TODO
//        return new TyrusServerContainer(containerProvider.createServerContainer(contextPath, port), contextPath, configuration,
//                dynamicallyAddedClasses, dynamicallyAddedEndpointConfigs);
    }
}
