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

package org.glassfish.tyrus.server;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.glassfish.tyrus.EndpointWrapper;
import org.glassfish.tyrus.Model;
import org.glassfish.tyrus.spi.SPIRegisteredEndpoint;
import org.glassfish.tyrus.spi.TyrusContainer;
import org.glassfish.tyrus.spi.TyrusServer;

/**
 * Server that processes the client applications and creates respective {@link org.glassfish.tyrus.spi.SPIEndpoint}.
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com).
 */

public class BeanServer {

    /**
     * Endpoints registered with the server.
     */
    Set<SPIRegisteredEndpoint> endpoints = Collections.newSetFromMap(new ConcurrentHashMap<SPIRegisteredEndpoint, Boolean>());

    /**
     * Container context.
     */
    private ServerContainerImpl containerContext;

    /**
     * WebSocket engine.
     */
    private TyrusContainer engine;
    final static Logger logger = Logger.getLogger("wsplatform");

    /**
     * Create new BeanServer.
     *
     * @param engineProviderClassname engine provider.
     */
    public BeanServer(String engineProviderClassname) {
        try {
            Class engineProviderClazz = Class.forName(engineProviderClassname);
            this.setEngine((TyrusContainer) engineProviderClazz.newInstance());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load provider class: " + engineProviderClassname + ". The provider class defaults to"
                    + "the grizzly provider. If you wish to provide your own implementation of the provider SPI, you can configure"
                    + "the provider class in the web.xml of the application using a"
                    + "context initialization parameter with key org.glassfish.websocket.provider.class, and using the full classname as the value.");
        }
        logger.info("Provider class loaded: " + engineProviderClassname);
    }

    public TyrusServer createServer(String rootUri, int port) {
        return engine.createServer(rootUri, port);
    }

    private void setEngine(TyrusContainer engine) {
        this.engine = engine;
        logger.info("Provider class instance: " + engine + " of class " + this.engine.getClass() + " assigned in the BeanServer");
    }

    /**
     * Returns the ContainerContext.
     *
     * @return ContainerContext
     */
    public ServerContainerImpl getContainerContext() {
        return containerContext;
    }

    /**
     * Stop the bean server.
     */
    public void closeWebSocketServer() {
        for (SPIRegisteredEndpoint wsa : this.endpoints) {
            wsa.remove();
            this.engine.unregister(wsa);
            logger.info("Closing down : " + wsa);
        }
    }

    /**
     * Inits the server.
     *
     * @param wsPath        address.
     * @param port          port.
     * @param fqWSBeanNames application classes.
     * @throws Exception
     */
    public void initWebSocketServer(String wsPath, int port, Set<Class<?>> fqWSBeanNames) throws Exception {
        this.containerContext = new ServerContainerImpl(this, wsPath, port);
        for (Class webSocketApplicationBeanClazz : fqWSBeanNames) {
            this.containerContext.setApplicationLevelClassLoader(webSocketApplicationBeanClazz.getClassLoader());

            // introspect the bean and find all the paths....
            Map<Method, String> methodPathMap = this.getMethodToPathMap(webSocketApplicationBeanClazz);

            if (methodPathMap.isEmpty()) {
                logger.warning(webSocketApplicationBeanClazz + " has no path mappings");
            }

            Set<String> allPathsForBean = new HashSet<String>(methodPathMap.values());

            // create one adapter per path. So each class may have multiple adapters.
            for (String nextPath : allPathsForBean) {
                Model model = new Model(webSocketApplicationBeanClazz);
                String wrapperBeanPath = (wsPath.endsWith("/") ? wsPath.substring(0, wsPath.length() - 1) : wsPath)
                        + "/" + (nextPath.startsWith("/") ? nextPath.substring(1) : nextPath);

                DefaultServerEndpointConfiguration.Builder builder = new DefaultServerEndpointConfiguration.Builder(wrapperBeanPath);
                DefaultServerEndpointConfiguration dsec = builder.encoders(model.getEncoders()).decoders(model.getDecoders()).build();
                EndpointWrapper endpoint = new EndpointWrapper(wrapperBeanPath, model, dsec, this.containerContext);
                this.deploy(endpoint);
            }
        }
    }

    void deploy(EndpointWrapper wsa) {
        SPIRegisteredEndpoint ge = this.engine.register(wsa);
        this.endpoints.add(ge);
        logger.info("Registered a " + wsa.getClass() + " at " + wsa.getPath());
    }

    private Map<Method, String> getMethodToPathMap(Class beanClazz) throws Exception {
        Map<Method, String> pathMappings = new HashMap<Method, String>();
        Method[] methods = beanClazz.getDeclaredMethods();
        for (Method method : methods) {
            javax.net.websocket.annotations.WebSocketEndpoint wsClass = (javax.net.websocket.annotations.WebSocketEndpoint) beanClazz.getAnnotation(javax.net.websocket.annotations.WebSocketEndpoint.class);
            pathMappings.put(method, wsClass.value());
        }
        return pathMappings;
    }
}
