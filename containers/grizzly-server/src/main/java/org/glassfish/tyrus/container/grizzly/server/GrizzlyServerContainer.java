/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2014 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.container.grizzly.server;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.websocket.DeploymentException;
import javax.websocket.server.ServerEndpointConfig;

import org.glassfish.tyrus.core.TyrusWebSocketEngine;
import org.glassfish.tyrus.core.Utils;
import org.glassfish.tyrus.core.cluster.ClusterContext;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.server.TyrusServerContainer;
import org.glassfish.tyrus.spi.ServerContainer;
import org.glassfish.tyrus.spi.ServerContainerFactory;
import org.glassfish.tyrus.spi.WebSocketEngine;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.StaticHttpHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.strategies.WorkerThreadIOStrategy;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;

/**
 * Grizzly implementation of {@link ServerContainerFactory} and {@link ServerContainer}.
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class GrizzlyServerContainer extends ServerContainerFactory {

    /**
     * Server-side property to set custom worker {@link ThreadPoolConfig}.
     * <p/>
     * Value is expected to be instance of {@link ThreadPoolConfig}, can be {@code null} (it won't be used).
     */
    public static final String WORKER_THREAD_POOL_CONFIG = "org.glassfish.tyrus.container.grizzly.server.workerThreadPoolConfig";

    /**
     * Server-side property to set custom selector {@link ThreadPoolConfig}.
     * <p/>
     * Value is expected to be instance of {@link ThreadPoolConfig}, can be {@code null} (it won't be used).
     */
    public static final String SELECTOR_THREAD_POOL_CONFIG = "org.glassfish.tyrus.container.grizzly.server.selectorThreadPoolConfig";

    @Override
    public ServerContainer createContainer(Map<String, Object> properties) {

        final Map<String, Object> localProperties;
        // defensive copy
        if (properties == null) {
            localProperties = Collections.emptyMap();
        } else {
            localProperties = new HashMap<String, Object>(properties);
        }

        final Integer incomingBufferSize = Utils.getProperty(localProperties, TyrusWebSocketEngine.INCOMING_BUFFER_SIZE, Integer.class);

        final ClusterContext clusterContext = Utils.getProperty(localProperties, ClusterContext.CLUSTER_CONTEXT, ClusterContext.class);

        return new TyrusServerContainer((Set<Class<?>>) null) {

            private final WebSocketEngine engine = new TyrusWebSocketEngine(this, incomingBufferSize, clusterContext);

            private HttpServer server;
            private String contextPath;

            @Override
            public void register(Class<?> endpointClass) throws DeploymentException {
                engine.register(endpointClass, contextPath);
            }

            @Override
            public void register(ServerEndpointConfig serverEndpointConfig) throws DeploymentException {
                engine.register(serverEndpointConfig, contextPath);
            }

            @Override
            public WebSocketEngine getWebSocketEngine() {
                return engine;
            }

            @Override
            public void start(String rootPath, int port) throws IOException, DeploymentException {
                contextPath = rootPath;
                server = HttpServer.createSimpleServer(rootPath, port);

                ThreadPoolConfig workerThreadPoolConfig = Utils.getProperty(localProperties, WORKER_THREAD_POOL_CONFIG, ThreadPoolConfig.class);
                ThreadPoolConfig selectorThreadPoolConfig = Utils.getProperty(localProperties, SELECTOR_THREAD_POOL_CONFIG, ThreadPoolConfig.class);

                // TYRUS-287: configurable server thread pools
                if (workerThreadPoolConfig != null || selectorThreadPoolConfig != null) {
                    TCPNIOTransportBuilder transportBuilder = TCPNIOTransportBuilder.newInstance();
                    if (workerThreadPoolConfig != null) {
                        transportBuilder.setWorkerThreadPoolConfig(workerThreadPoolConfig);
                    }
                    if (selectorThreadPoolConfig != null) {
                        transportBuilder.setSelectorThreadPoolConfig(selectorThreadPoolConfig);
                    }
                    transportBuilder.setIOStrategy(WorkerThreadIOStrategy.getInstance());
                    server.getListener("grizzly").setTransport(transportBuilder.build());
                } else {
                    // if no configuration is set, just update IO Strategy to worker thread strat.
                    server.getListener("grizzly").getTransport().setIOStrategy(WorkerThreadIOStrategy.getInstance());
                }

                // idle timeout set to indefinite.
                server.getListener("grizzly").getKeepAlive().setIdleTimeoutInSeconds(-1);
                server.getListener("grizzly").registerAddOn(new WebSocketAddOn(this));

                final Object staticContentPath = localProperties.get(Server.STATIC_CONTENT_ROOT);
                if (staticContentPath != null && !staticContentPath.toString().isEmpty()) {
                    StaticHttpHandler staticHttpHandler = new StaticHttpHandler(staticContentPath.toString());
                    server.getServerConfiguration().addHttpHandler(staticHttpHandler, "/");
                }

                server.start();
                super.start(rootPath, port);
            }

            @Override
            public void stop() {
                super.stop();
                server.shutdownNow();
            }
        };
    }
}
