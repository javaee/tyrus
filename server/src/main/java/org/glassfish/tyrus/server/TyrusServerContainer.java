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

package org.glassfish.tyrus.server;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.Extension;
import javax.websocket.Session;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpointConfig;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.core.BaseContainer;
import org.glassfish.tyrus.core.ErrorCollector;
import org.glassfish.tyrus.spi.ServerContainer;

/**
 * Server Container Implementation.
 *
 * @author Martin Matula (martin.matula at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public abstract class TyrusServerContainer extends BaseContainer implements ServerContainer {
    private final ErrorCollector collector;

    private final Set<Class<?>> dynamicallyAddedClasses;
    private final Set<ServerEndpointConfig> dynamicallyAddedEndpointConfigs;
    private final Set<Class<?>> classes;
    private final ServerApplicationConfig serverApplicationConfig;

    private boolean canDeploy = true;
    private long defaultMaxSessionIdleTimeout = 0;
    private long defaultAsyncSendTimeout = 0;
    private int maxTextMessageBufferSize = Integer.MAX_VALUE;
    private int maxBinaryMessageBufferSize = Integer.MAX_VALUE;

    private ClientManager clientManager = null;

    /**
     * Create new {@link TyrusServerContainer}.
     *
     * @param classes classes to be included in this application instance. Can contain any combination of annotated
     *                endpoints (see {@link javax.websocket.server.ServerEndpoint}) or {@link javax.websocket.Endpoint} descendants.
     */
    public TyrusServerContainer(Set<Class<?>> classes) {
        this.collector = new ErrorCollector();
        this.classes = classes == null ? Collections.<Class<?>>emptySet() : new HashSet<Class<?>>(classes);
        this.dynamicallyAddedClasses = new HashSet<Class<?>>();
        this.dynamicallyAddedEndpointConfigs = new HashSet<ServerEndpointConfig>();
        this.serverApplicationConfig = null;
    }

    /**
     * Create new {@link TyrusServerContainer} using already created {@link ServerApplicationConfig} instance.
     *
     * @param serverApplicationConfig provided application config.
     */
    public TyrusServerContainer(ServerApplicationConfig serverApplicationConfig) {
        this.collector = new ErrorCollector();
        this.classes = new HashSet<Class<?>>();
        this.dynamicallyAddedClasses = new HashSet<Class<?>>();
        this.dynamicallyAddedEndpointConfigs = new HashSet<ServerEndpointConfig>();
        this.serverApplicationConfig = serverApplicationConfig;
    }

    /**
     * Start container.
     *
     * @throws IOException         when any IO related issues emerge during {@link org.glassfish.tyrus.spi.ServerContainer#start(String, int)}.
     * @throws DeploymentException when any deployment related error is found; should contain list of all found issues.
     */
    @Override
    public void start(String rootPath, int port) throws IOException, DeploymentException {
        ServerApplicationConfig configuration = new TyrusServerConfiguration(classes, dynamicallyAddedClasses, dynamicallyAddedEndpointConfigs, this.collector);

        // start the underlying server
        try {
            // deploy all the annotated endpoints
            for (Class<?> endpointClass : configuration.getAnnotatedEndpointClasses(null)) {
                register(endpointClass);
            }

            // deploy all the programmatic endpoints
            for (ServerEndpointConfig serverEndpointConfiguration : configuration.getEndpointConfigs(null)) {
                if (serverEndpointConfiguration != null) {
                    register(serverEndpointConfiguration);
                }
            }

            if (serverApplicationConfig != null) {
                // deploy all the annotated endpoints
                for (Class<?> endpointClass : serverApplicationConfig.getAnnotatedEndpointClasses(null)) {
                    register(endpointClass);
                }

                // deploy all the programmatic endpoints
                for (ServerEndpointConfig serverEndpointConfiguration : serverApplicationConfig.getEndpointConfigs(null)) {
                    if (serverEndpointConfiguration != null) {
                        register(serverEndpointConfiguration);
                    }
                }
            }
        } catch (DeploymentException de) {
            collector.addException(de);
        }

        if (!collector.isEmpty()) {
            this.stop();
            throw collector.composeComprehensiveException();
        }
    }

    /**
     * Undeploy all endpoints and stop underlying {@link org.glassfish.tyrus.spi.ServerContainer}.
     * <p/>
     * Release all created threadpools / executor services.
     */
    @Override
    public void stop() {

        // release executor services managed by {@link BaseContainer}.
        shutdown();
    }

    public abstract void register(Class<?> endpointClass) throws DeploymentException;

    public abstract void register(ServerEndpointConfig serverEndpointConfig) throws DeploymentException;

    @Override
    public void addEndpoint(Class<?> endpointClass) throws DeploymentException {
        if (canDeploy) {
            dynamicallyAddedClasses.add(endpointClass);
        } else {
            throw new IllegalStateException("Not in 'deploy' scope.");
        }
    }

    @Override
    public void addEndpoint(ServerEndpointConfig serverEndpointConfig) throws DeploymentException {
        if (canDeploy) {
            dynamicallyAddedEndpointConfigs.add(serverEndpointConfig);
        } else {
            throw new IllegalStateException("Not in 'deploy' scope.");
        }
    }

    /**
     * Can be overridden to provide own {@link ClientManager} implementation or instance.
     *
     * @return {@link ClientManager} associated with this server container.
     */
    protected synchronized ClientManager getClientManager() {
        if (clientManager == null) {
            clientManager = ClientManager.createClient(this);
        }

        return clientManager;
    }

    @Override
    public Session connectToServer(Class annotatedEndpointClass, URI path) throws DeploymentException, IOException {
        return getClientManager().connectToServer(annotatedEndpointClass, path);
    }

    @Override
    public Session connectToServer(Class<? extends Endpoint> endpointClass, ClientEndpointConfig cec, URI path) throws DeploymentException, IOException {
        return getClientManager().connectToServer(endpointClass, cec, path);
    }

    @Override
    public Session connectToServer(Object annotatedEndpointInstance, URI path) throws DeploymentException, IOException {
        return getClientManager().connectToServer(annotatedEndpointInstance, path);
    }

    @Override
    public Session connectToServer(Endpoint endpointInstance, ClientEndpointConfig cec, URI path) throws DeploymentException, IOException {
        return getClientManager().connectToServer(endpointInstance, cec, path);
    }

    /**
     * Non-blocking version of {@link javax.websocket.WebSocketContainer#connectToServer(Class, java.net.URI)}.
     * <p/>
     * Only simple checks are performed in the main thread; client container is created in different thread, same
     * applies to connecting etc.
     *
     * @param annotatedEndpointClass the annotated websocket client endpoint.
     * @param path                   the complete path to the server endpoint.
     * @return Future for the Session created if the connection is successful.
     * @throws DeploymentException if the class is not a valid annotated endpoint class.
     */
    public Future<Session> asyncConnectToServer(Class<?> annotatedEndpointClass, URI path) throws DeploymentException {
        return getClientManager().asyncConnectToServer(annotatedEndpointClass, path);
    }

    /**
     * Non-blocking version of {@link javax.websocket.WebSocketContainer#connectToServer(Class, javax.websocket.ClientEndpointConfig, java.net.URI)}.
     * <p/>
     * Only simple checks are performed in the main thread; client container is created in different thread, same
     * applies to connecting etc.
     *
     * @param endpointClass the programmatic client endpoint class {@link Endpoint}.
     * @param path          the complete path to the server endpoint.
     * @param cec           the configuration used to configure the programmatic endpoint.
     * @return the Session created if the connection is successful.
     * @throws DeploymentException if the configuration is not valid
     * @see javax.websocket.WebSocketContainer#connectToServer(Class, javax.websocket.ClientEndpointConfig, java.net.URI)
     */
    public Future<Session> asyncConnectToServer(Class<? extends Endpoint> endpointClass, ClientEndpointConfig cec, URI path) throws DeploymentException {
        return getClientManager().asyncConnectToServer(endpointClass, cec, path);
    }

    /**
     * Non-blocking version of {@link javax.websocket.WebSocketContainer#connectToServer(javax.websocket.Endpoint, javax.websocket.ClientEndpointConfig, java.net.URI)}.
     * <p/>
     * Only simple checks are performed in the main thread; client container is created in different thread, same
     * applies to connecting etc.
     *
     * @param endpointInstance the programmatic client endpoint instance {@link Endpoint}.
     * @param path             the complete path to the server endpoint.
     * @param cec              the configuration used to configure the programmatic endpoint.
     * @return the Session created if the connection is successful.
     * @throws DeploymentException if the configuration is not valid
     * @see javax.websocket.WebSocketContainer#connectToServer(javax.websocket.Endpoint, javax.websocket.ClientEndpointConfig, java.net.URI)
     */
    public Future<Session> asyncConnectToServer(Endpoint endpointInstance, ClientEndpointConfig cec, URI path) throws DeploymentException {
        return getClientManager().asyncConnectToServer(endpointInstance, cec, path);
    }

    /**
     * Non-blocking version of {@link javax.websocket.WebSocketContainer#connectToServer(Object, java.net.URI)}.
     * <p/>
     * Only simple checks are performed in the main thread; client container is created in different thread, same
     * applies to connecting etc.
     *
     * @param obj  the annotated websocket client endpoint
     *             instance.
     * @param path the complete path to the server endpoint.
     * @return the Session created if the connection is successful.
     * @throws DeploymentException if the annotated endpoint instance is not valid.
     * @see javax.websocket.WebSocketContainer#connectToServer(Object, java.net.URI)
     */
    public Future<Session> asyncConnectToServer(Object obj, URI path) throws DeploymentException {
        return getClientManager().asyncConnectToServer(obj, path);
    }

    @Override
    public int getDefaultMaxBinaryMessageBufferSize() {
        return maxBinaryMessageBufferSize;
    }

    @Override
    public void setDefaultMaxBinaryMessageBufferSize(int max) {
        this.maxBinaryMessageBufferSize = max;
    }

    @Override
    public int getDefaultMaxTextMessageBufferSize() {
        return maxTextMessageBufferSize;
    }

    @Override
    public void setDefaultMaxTextMessageBufferSize(int max) {
        this.maxTextMessageBufferSize = max;
    }

    @Override
    public Set<Extension> getInstalledExtensions() {
        // TODO
        // return Collections.unmodifiableSet(new HashSet<String>(configuration.parseExtensionsHeader()));

        return Collections.emptySet();
    }

    @Override
    public long getDefaultAsyncSendTimeout() {
        return defaultAsyncSendTimeout;
    }

    @Override
    public void setAsyncSendTimeout(long timeoutmillis) {
        defaultAsyncSendTimeout = timeoutmillis;
    }

    @Override
    public long getDefaultMaxSessionIdleTimeout() {
        return defaultMaxSessionIdleTimeout;
    }

    @Override
    public void setDefaultMaxSessionIdleTimeout(long defaultMaxSessionIdleTimeout) {
        this.defaultMaxSessionIdleTimeout = defaultMaxSessionIdleTimeout;
    }

    /**
     * Container is no longer required to accept {@link #addEndpoint(javax.websocket.server.ServerEndpointConfig)} and
     * {@link #addEndpoint(Class)} calls.
     */
    public void doneDeployment() {
        canDeploy = false;
    }
}
