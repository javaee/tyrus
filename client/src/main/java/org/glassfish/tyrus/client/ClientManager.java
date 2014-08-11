/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2014 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.client;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.websocket.ClientEndpoint;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.Extension;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.glassfish.tyrus.core.AnnotatedEndpoint;
import org.glassfish.tyrus.core.BaseContainer;
import org.glassfish.tyrus.core.ComponentProviderService;
import org.glassfish.tyrus.core.ErrorCollector;
import org.glassfish.tyrus.core.ReflectionHelper;
import org.glassfish.tyrus.core.TyrusEndpointWrapper;
import org.glassfish.tyrus.core.TyrusFuture;
import org.glassfish.tyrus.core.TyrusSession;
import org.glassfish.tyrus.core.Utils;
import org.glassfish.tyrus.core.monitoring.EndpointEventListener;
import org.glassfish.tyrus.spi.ClientContainer;
import org.glassfish.tyrus.spi.ClientEngine;

/**
 * ClientManager implementation.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class ClientManager extends BaseContainer implements WebSocketContainer {

    /**
     * Property usable in {@link #getProperties()}.
     * <p/>
     * Value must be {@code int} and represents handshake timeout in milliseconds. Default value is 30000 (30 seconds).
     *
     * @deprecated please use {@link org.glassfish.tyrus.client.ClientProperties#HANDSHAKE_TIMEOUT}.
     */
    @SuppressWarnings("UnusedDeclaration")
    public static final String HANDSHAKE_TIMEOUT = ClientProperties.HANDSHAKE_TIMEOUT;

    /**
     * Property usable in {@link #getProperties()}.
     * <p/>
     * Value must be {@link org.glassfish.tyrus.client.ClientManager.ReconnectHandler} instance.
     *
     * @deprecated please use {@link org.glassfish.tyrus.client.ClientProperties#RECONNECT_HANDLER}.
     */
    @SuppressWarnings("UnusedDeclaration")
    public static final String RECONNECT_HANDLER = ClientProperties.RECONNECT_HANDLER;

    /**
     * Client-side user property to set proxy URI.
     * <p/>
     * Value is expected to be {@link String} and represent proxy URI. Protocol part is currently ignored
     * but must be present ({@link URI#URI(String)} is used for parsing).
     * <p/>
     * <pre>
     *     client.getProperties().put(ClientManager.PROXY_URI, "http://my.proxy.com:80");
     *     client.connectToServer(...);
     * </pre>
     *
     * @see javax.websocket.ClientEndpointConfig#getUserProperties()
     * @deprecated please use {@link org.glassfish.tyrus.client.ClientProperties#PROXY_URI}.
     */
    @SuppressWarnings("UnusedDeclaration")
    public static final String PROXY_URI = ClientProperties.PROXY_URI;

    /**
     * Client-side user property to set additional proxy headers.
     * <p/>
     * Value is expected to be {@link Map}&lt{@link String}, {@link String}&gt and represent raw http headers
     * to be added to initial request which is sent to proxy. Key corresponds to header name, value is header
     * value.
     * <p/>
     * Sample below demonstrates use of this feature to set preemptive basic proxy authentication:
     * <pre>
     *     final HashMap<String, String> proxyHeaders = new HashMap<String, String>();
     *     proxyHeaders.put("Proxy-Authorization", "Basic " + Base64Utils.encodeToString("username:password".getBytes(Charset.forName("UTF-8")), false));
     *
     *     client.getProperties().put(GrizzlyClientSocket.PROXY_HEADERS, proxyHeaders);
     *     client.connectToServer(...);
     * </pre>
     * Please note that these headers will be used only when establishing proxy connection, for modifying
     * WebSocket handshake headers, see {@link javax.websocket.ClientEndpointConfig.Configurator#beforeRequest(java.util.Map)}.
     *
     * @see javax.websocket.ClientEndpointConfig#getUserProperties()
     * @deprecated please use {@link org.glassfish.tyrus.client.ClientProperties#PROXY_HEADERS}.
     */
    @SuppressWarnings("UnusedDeclaration")
    public static final String PROXY_HEADERS = ClientProperties.PROXY_HEADERS;

    /**
     * Property usable in {@link #getProperties()} as a key for SSL configuration.
     * <p/>
     * Value is expected to be either {@link org.glassfish.grizzly.ssl.SSLEngineConfigurator}
     * when configuring Grizzly client or
     * {@link org.glassfish.tyrus.client.SslEngineConfigurator}
     * when configuring JDK client.
     * <p/>
     * Example configuration for JDK client:
     * <pre>
     *      SslContextConfigurator sslContextConfigurator = new SslContextConfigurator();
     *      sslContextConfigurator.setTrustStoreFile("...");
     *      sslContextConfigurator.setTrustStorePassword("...");
     *      sslContextConfigurator.setTrustStoreType("...");
     *      sslContextConfigurator.setKeyStoreFile("...");
     *      sslContextConfigurator.setKeyStorePassword("...");
     *      sslContextConfigurator.setKeyStoreType("...");
     *      SslEngineConfigurator sslEngineConfigurator = new SslEngineConfigurator(sslContextConfigurator, true, false, false);
     *      client.getProperties().put(ClientManager.SSL_ENGINE_CONFIGURATOR, sslEngineConfigurator);
     * </pre>
     *
     * @deprecated please use {@link org.glassfish.tyrus.client.ClientProperties#SSL_ENGINE_CONFIGURATOR}.
     */
    @SuppressWarnings({"UnusedDeclaration", "JavadocReference"})
    public static final String SSL_ENGINE_CONFIGURATOR = ClientProperties.SSL_ENGINE_CONFIGURATOR;

    /**
     * Default {@link org.glassfish.tyrus.spi.ServerContainerFactory} class name.
     * <p/>
     * Uses Grizzly as transport implementation.
     */
    private static final String CONTAINER_PROVIDER_CLASSNAME = "org.glassfish.tyrus.container.grizzly.client.GrizzlyClientContainer";

    public static final String WLS_PROXY_HOST = "weblogic.websocket.client.PROXY_HOST";
    public static final String WLS_PROXY_PORT = "weblogic.websocket.client.PROXY_PORT";
    public static final String WLS_PROXY_USERNAME = "weblogic.websocket.client.PROXY_USERNAME";
    public static final String WLS_PROXY_PASSWORD = "weblogic.websocket.client.PROXY_PASSWORD";
    public static final String WLS_SSL_PROTOCOLS_PROPERTY = "weblogic.websocket.client.SSL_PROTOCOLS";
    public static final String WLS_SSL_TRUSTSTORE_PROPERTY = "weblogic.websocket.client.SSL_TRUSTSTORE";
    public static final String WLS_SSL_TRUSTSTORE_PWD_PROPERTY = "weblogic.websocket.client.SSL_TRUSTSTORE_PWD";
    public static final String WLS_MAX_THREADS = "weblogic.websocket.client.max-aio-threads";

    private static final Logger LOGGER = Logger.getLogger(ClientManager.class.getName());
    private final WebSocketContainer webSocketContainer;
    private final ClientContainer container;
    private final ComponentProviderService componentProvider;
    private final Map<String, Object> properties = new HashMap<String, Object>();

    private volatile long defaultAsyncSendTimeout;
    private volatile long defaultMaxSessionIdleTimeout;
    private volatile int maxBinaryMessageBufferSize = Integer.MAX_VALUE;
    private volatile int maxTextMessageBufferSize = Integer.MAX_VALUE;

    /**
     * Create new {@link ClientManager} instance.
     * <p/>
     * Uses {@link ClientManager#CONTAINER_PROVIDER_CLASSNAME} as container implementation, thus relevant module needs to
     * be on classpath. Setting different container is possible via {@link ClientManager#createClient(String)}.
     *
     * @see ClientManager#createClient(String)
     */
    public static ClientManager createClient() {
        return createClient(CONTAINER_PROVIDER_CLASSNAME);
    }

    /**
     * Create new ClientManager instance on top of provided {@link WebSocketContainer} instance.
     * <p/>
     * Uses {@link ClientManager#CONTAINER_PROVIDER_CLASSNAME} as container implementation, thus relevant module needs to
     * be on classpath. Setting different container is possible via {@link ClientManager#createClient(String)}.
     *
     * @see ClientManager#createClient(String)
     */
    public static ClientManager createClient(WebSocketContainer webSocketContainer) {
        return createClient(CONTAINER_PROVIDER_CLASSNAME, webSocketContainer);
    }

    /**
     * Create new ClientManager instance.
     *
     * @return new ClientManager instance.
     */
    public static ClientManager createClient(String containerProviderClassName) {
        return new ClientManager(containerProviderClassName, null);
    }

    /**
     * Create new ClientManager instance on top of provided {@link WebSocketContainer} instance.
     *
     * @return new ClientManager instance.
     */
    public static ClientManager createClient(String containerProviderClassName, WebSocketContainer webSocketContainer) {
        return new ClientManager(containerProviderClassName, webSocketContainer);
    }

    /**
     * Create new {@link ClientManager} instance.
     * <p/>
     * Uses {@link ClientManager#CONTAINER_PROVIDER_CLASSNAME} as container implementation, thus relevant module needs to
     * be on classpath. Setting different container is possible via {@link ClientManager#createClient(String)}}.
     *
     * @see ClientManager#createClient(String)
     */
    public ClientManager() {
        this(CONTAINER_PROVIDER_CLASSNAME, null);
    }

    private ClientManager(String containerProviderClassName, WebSocketContainer webSocketContainer) {
        final ErrorCollector collector = new ErrorCollector();
        componentProvider = ComponentProviderService.createClient();
        Class engineProviderClazz;
        try {
            engineProviderClazz = ReflectionHelper.classForNameWithException(containerProviderClassName);
        } catch (ClassNotFoundException e) {
            collector.addException(e);
            throw new RuntimeException(collector.composeComprehensiveException());
        }
        LOGGER.config(String.format("Provider class loaded: %s", containerProviderClassName));
        this.container = (ClientContainer) ReflectionHelper.getInstance(engineProviderClazz, collector);
        if (!collector.isEmpty()) {
            throw new RuntimeException(collector.composeComprehensiveException());
        }
        this.webSocketContainer = webSocketContainer;
    }

    @Override
    public Session connectToServer(Class annotatedEndpointClass, URI path) throws DeploymentException, IOException {
        if (annotatedEndpointClass.getAnnotation(ClientEndpoint.class) == null) {
            throw new DeploymentException(String.format("Class argument in connectToServer(Class, URI) is to be annotated endpoint class." +
                    "Class %s does not have @ClientEndpoint", annotatedEndpointClass.getName()));
        }
        try {
            return connectToServer(annotatedEndpointClass, null, path.toString(), new SameThreadExecutorService()).get();
        } catch (InterruptedException e) {
            throw new DeploymentException(e.getMessage(), e);
        } catch (ExecutionException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof DeploymentException) {
                throw (DeploymentException) cause;
            } else if (cause instanceof IOException) {
                throw (IOException) cause;
            } else {
                throw new DeploymentException(cause.getMessage(), cause);
            }
        }
    }

    @Override
    public Session connectToServer(Class<? extends Endpoint> endpointClass, ClientEndpointConfig cec, URI path) throws DeploymentException, IOException {
        try {
            return connectToServer(endpointClass, cec, path.toString(), new SameThreadExecutorService()).get();
        } catch (InterruptedException e) {
            throw new DeploymentException(e.getMessage(), e);
        } catch (ExecutionException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof DeploymentException) {
                throw (DeploymentException) cause;
            } else if (cause instanceof IOException) {
                throw (IOException) cause;
            } else {
                throw new DeploymentException(cause.getMessage(), cause);
            }
        }
    }

    @Override
    public Session connectToServer(Endpoint endpointInstance, ClientEndpointConfig cec, URI path) throws DeploymentException, IOException {
        try {
            return connectToServer(endpointInstance, cec, path.toString(), new SameThreadExecutorService()).get();
        } catch (InterruptedException e) {
            throw new DeploymentException(e.getMessage(), e);
        } catch (ExecutionException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof DeploymentException) {
                throw (DeploymentException) cause;
            } else if (cause instanceof IOException) {
                throw (IOException) cause;
            } else {
                throw new DeploymentException(cause.getMessage(), cause);
            }
        }
    }

    @Override
    public Session connectToServer(Object obj, URI path) throws DeploymentException, IOException {
        try {
            return connectToServer(obj, null, path.toString(), new SameThreadExecutorService()).get();
        } catch (InterruptedException e) {
            throw new DeploymentException(e.getMessage(), e);
        } catch (ExecutionException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof DeploymentException) {
                throw (DeploymentException) cause;
            } else if (cause instanceof IOException) {
                throw (IOException) cause;
            } else {
                throw new DeploymentException(cause.getMessage(), cause);
            }
        }
    }

    /**
     * Non-blocking version of {@link WebSocketContainer#connectToServer(Class, java.net.URI)}.
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
        if (annotatedEndpointClass.getAnnotation(ClientEndpoint.class) == null) {
            throw new DeploymentException(String.format("Class argument in connectToServer(Class, URI) is to be annotated endpoint class." +
                    "Class %s does not have @ClientEndpoint", annotatedEndpointClass.getName()));
        }
        return connectToServer(annotatedEndpointClass, null, path.toString(), getExecutorService());
    }

    /**
     * Non-blocking version of {@link WebSocketContainer#connectToServer(Class, javax.websocket.ClientEndpointConfig, java.net.URI)}.
     * <p/>
     * Only simple checks are performed in the main thread; client container is created in different thread, same
     * applies to connecting etc.
     *
     * @param endpointClass the programmatic client endpoint class {@link Endpoint}.
     * @param path          the complete path to the server endpoint.
     * @param cec           the configuration used to configure the programmatic endpoint.
     * @return the Session created if the connection is successful.
     * @throws DeploymentException if the configuration is not valid
     * @see WebSocketContainer#connectToServer(Class, javax.websocket.ClientEndpointConfig, java.net.URI)
     */
    public Future<Session> asyncConnectToServer(Class<? extends Endpoint> endpointClass, ClientEndpointConfig cec, URI path) throws DeploymentException {
        return connectToServer(endpointClass, cec, path.toString(), getExecutorService());
    }

    /**
     * Non-blocking version of {@link WebSocketContainer#connectToServer(javax.websocket.Endpoint, javax.websocket.ClientEndpointConfig, java.net.URI)}.
     * <p/>
     * Only simple checks are performed in the main thread; client container is created in different thread, same
     * applies to connecting etc.
     *
     * @param endpointInstance the programmatic client endpoint instance {@link Endpoint}.
     * @param path             the complete path to the server endpoint.
     * @param cec              the configuration used to configure the programmatic endpoint.
     * @return the Session created if the connection is successful.
     * @throws DeploymentException if the configuration is not valid
     * @see WebSocketContainer#connectToServer(javax.websocket.Endpoint, javax.websocket.ClientEndpointConfig, java.net.URI)
     */
    public Future<Session> asyncConnectToServer(Endpoint endpointInstance, ClientEndpointConfig cec, URI path) throws DeploymentException {
        return connectToServer(endpointInstance, cec, path.toString(), getExecutorService());
    }

    /**
     * Non-blocking version of {@link WebSocketContainer#connectToServer(Object, java.net.URI)}.
     * <p/>
     * Only simple checks are performed in the main thread; client container is created in different thread, same
     * applies to connecting etc.
     *
     * @param obj  the annotated websocket client endpoint
     *             instance.
     * @param path the complete path to the server endpoint.
     * @return the Session created if the connection is successful.
     * @throws DeploymentException if the annotated endpoint instance is not valid.
     * @see WebSocketContainer#connectToServer(Object, java.net.URI)
     */
    public Future<Session> asyncConnectToServer(Object obj, URI path) throws DeploymentException {
        return connectToServer(obj, null, path.toString(), getExecutorService());
    }

    /**
     * Connects client endpoint o to the specified url.
     *
     * @param o             the endpoint.
     * @param configuration of the endpoint.
     * @param url           to which the client will connect.
     * @return Future which will return {@link Session} instance when available.
     * @throws DeploymentException if the endpoint or provided URL is not valid.
     */
    Future<Session> connectToServer(final Object o, final ClientEndpointConfig configuration, final String url, final ExecutorService executorService) throws DeploymentException {
        final Map<String, Object> copiedProperties = new HashMap<String, Object>(properties);
        final TyrusFuture<Session> future = new TyrusFuture<Session>();

        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equals("ws") || scheme.equals("wss"))) {
                throw new DeploymentException("Incorrect scheme in WebSocket endpoint URI=" + url);
            }
        } catch (URISyntaxException e) {
            throw new DeploymentException("Incorrect WebSocket endpoint URI=" + url, e);
        }

        final int handshakeTimeout = getHandshakeTimeout();

        executorService.submit(new Runnable() {
            @Override
            public void run() {

                final ErrorCollector collector = new ErrorCollector();
                final ClientEndpointConfig config;
                final Endpoint endpoint;

                // incoming buffer size - max frame size possible to receive.
                Integer tyrusIncomingBufferSize = Utils.getProperty(properties, ClientProperties.INCOMING_BUFFER_SIZE, Integer.class);
                Integer wlsIncomingBufferSize = configuration == null ? null : Utils.getProperty(configuration.getUserProperties(), ClientContainer.WLS_INCOMING_BUFFER_SIZE, Integer.class);
                final int incomingBufferSize;
                if (tyrusIncomingBufferSize == null && wlsIncomingBufferSize == null) {
                    incomingBufferSize = TyrusClientEngine.DEFAULT_INCOMING_BUFFER_SIZE;
                } else if (wlsIncomingBufferSize != null) {
                    incomingBufferSize = wlsIncomingBufferSize;
                } else {
                    incomingBufferSize = tyrusIncomingBufferSize;
                }

                try {
                    if (o instanceof Endpoint) {
                        endpoint = (Endpoint) o;
                        config = configuration == null ? ClientEndpointConfig.Builder.create().build() : configuration;
                    } else if (o instanceof Class) {
                        if (Endpoint.class.isAssignableFrom((Class<?>) o)) {
                            //noinspection unchecked
                            endpoint = ReflectionHelper.getInstance(((Class<Endpoint>) o), collector);
                            config = configuration == null ? ClientEndpointConfig.Builder.create().build() : configuration;
                        } else if ((((Class<?>) o).getAnnotation(ClientEndpoint.class) != null)) {
                            endpoint = AnnotatedEndpoint.fromClass((Class) o, componentProvider, false, incomingBufferSize, collector, EndpointEventListener.NO_OP);
                            config = (ClientEndpointConfig) ((AnnotatedEndpoint) endpoint).getEndpointConfig();
                        } else {
                            collector.addException(new DeploymentException(String.format("Class %s in not Endpoint descendant and does not have @ClientEndpoint", ((Class<?>) o).getName())));
                            endpoint = null;
                            config = null;
                        }
                    } else {
                        endpoint = AnnotatedEndpoint.fromInstance(o, componentProvider, false, incomingBufferSize, collector);
                        config = (ClientEndpointConfig) ((AnnotatedEndpoint) endpoint).getEndpointConfig();
                    }

                    // fail fast when there is some issue with client endpoint.
                    if (!collector.isEmpty()) {
                        future.setFailure(collector.composeComprehensiveException());
                        return;
                    }
                } catch (Exception e) {
                    future.setFailure(e);
                    return;
                }

                final Object o = copiedProperties.get(ClientProperties.RECONNECT_HANDLER);
                final ReconnectHandler originReconnectHandler;
                if (o != null && o instanceof ReconnectHandler) {
                    originReconnectHandler = (ReconnectHandler) o;
                } else {
                    originReconnectHandler = new ReconnectHandler();
                }

                final boolean retryAfterEnabled = Utils.getProperty(properties, ClientProperties.RETRY_AFTER_SERVICE_UNAVAILABLE_ENABLED, Boolean.class, false);

                Runnable connector = new Runnable() {

                    final ReconnectHandler reconnectHandler = new RetryAfterReconnectHandler(this, future, originReconnectHandler, retryAfterEnabled);

                    @Override
                    public void run() {

                        do {
                            final CountDownLatch responseLatch = new CountDownLatch(1);

                            final ClientManagerHandshakeListener listener = new ClientManagerHandshakeListener() {

                                private volatile Session session;
                                private volatile Throwable throwable;

                                @Override
                                public void onSessionCreated(Session session) {
                                    this.session = session;
                                    responseLatch.countDown();
                                }

                                @Override
                                public void onError(Throwable exception) {
                                    throwable = exception;
                                    responseLatch.countDown();
                                }

                                @Override
                                public Session getSession() {
                                    return session;
                                }

                                @Override
                                public Throwable getThrowable() {
                                    return throwable;
                                }
                            };

                            try {
                                TyrusEndpointWrapper clientEndpoint = new TyrusEndpointWrapper(endpoint, config, componentProvider,
                                        webSocketContainer == null ? ClientManager.this : webSocketContainer, url, null, new TyrusEndpointWrapper.SessionListener() {

                                    @Override
                                    public void onClose(TyrusSession session, CloseReason closeReason) {
                                        if (reconnectHandler.onDisconnect(closeReason)) {
                                            try {
                                                run();
                                            } catch (Exception e) {
                                                // do nothing (exception is already handled in call() method itself).
                                            }
                                        }
                                    }
                                }, null, null
                                );

                                final URI uri;
                                try {
                                    uri = new URI(url);
                                } catch (URISyntaxException e) {
                                    throw new DeploymentException("Invalid URI.", e);
                                }

                                TyrusClientEngine clientEngine = new TyrusClientEngine(clientEndpoint, listener, copiedProperties, uri);

                                container.openClientSocket(config, copiedProperties, clientEngine);

                                try {
                                    final boolean countedDown = responseLatch.await(handshakeTimeout, TimeUnit.MILLISECONDS);
                                    if (countedDown) {
                                        final Throwable exception = listener.getThrowable();
                                        if (exception != null) {
                                            if(exception instanceof DeploymentException) {
                                                throw (DeploymentException)exception;
                                            } else {
                                                throw new DeploymentException("Handshake error.", exception);
                                            }
                                        }

                                        future.setResult(listener.getSession());
                                        return;
                                    } else {
                                        // timeout!
                                        final ClientEngine.TimeoutHandler timeoutHandler = clientEngine.getTimeoutHandler();
                                        if (timeoutHandler != null) {
                                            timeoutHandler.handleTimeout();
                                        }
                                    }
                                } catch (DeploymentException e) {
                                    throw e;
                                } catch (Exception e) {
                                    throw new DeploymentException("Handshake response not received.", e);
                                }

                                throw new DeploymentException("Handshake response not received.");
                            } catch (Exception e) {
                                if (!reconnectHandler.onConnectFailure(e)) {
                                    return;
                                }
                            }

                        } while (true);
                    }
                };

                connector.run();
            }
        });

        return future;
    }

    private int getHandshakeTimeout() {
        final Object o = properties.get(ClientProperties.HANDSHAKE_TIMEOUT);
        if (o != null && o instanceof Integer) {
            return (Integer) o;
        } else {
            // default value
            return 30000;
        }
    }

    private interface ClientManagerHandshakeListener extends TyrusClientEngine.ClientHandshakeListener {
        Session getSession();

        Throwable getThrowable();
    }

    @Override
    public int getDefaultMaxBinaryMessageBufferSize() {
        if (webSocketContainer == null) {
            return maxBinaryMessageBufferSize;
        } else {
            return webSocketContainer.getDefaultMaxBinaryMessageBufferSize();
        }
    }

    @Override
    public void setDefaultMaxBinaryMessageBufferSize(int i) {
        if (webSocketContainer == null) {
            maxBinaryMessageBufferSize = i;
        } else {
            webSocketContainer.setDefaultMaxBinaryMessageBufferSize(i);
        }
    }

    @Override
    public int getDefaultMaxTextMessageBufferSize() {
        if (webSocketContainer == null) {
            return maxTextMessageBufferSize;
        } else {
            return webSocketContainer.getDefaultMaxTextMessageBufferSize();
        }

    }

    @Override
    public void setDefaultMaxTextMessageBufferSize(int i) {
        if (webSocketContainer == null) {
            maxTextMessageBufferSize = i;
        } else {
            webSocketContainer.setDefaultMaxTextMessageBufferSize(i);
        }
    }

    @Override
    public Set<Extension> getInstalledExtensions() {
        if (webSocketContainer == null) {
            // TODO
            return Collections.emptySet();
        } else {
            return webSocketContainer.getInstalledExtensions();
        }
    }

    @Override
    public long getDefaultAsyncSendTimeout() {
        if (webSocketContainer == null) {
            return defaultAsyncSendTimeout;
        } else {
            return webSocketContainer.getDefaultAsyncSendTimeout();
        }
    }

    @Override
    public void setAsyncSendTimeout(long timeoutmillis) {
        if (webSocketContainer == null) {
            defaultAsyncSendTimeout = timeoutmillis;
        } else {
            webSocketContainer.setAsyncSendTimeout(timeoutmillis);
        }
    }

    @Override
    public long getDefaultMaxSessionIdleTimeout() {
        if (webSocketContainer == null) {
            return defaultMaxSessionIdleTimeout;
        } else {
            return webSocketContainer.getDefaultMaxSessionIdleTimeout();
        }
    }

    @Override
    public void setDefaultMaxSessionIdleTimeout(long defaultMaxSessionIdleTimeout) {
        if (webSocketContainer == null) {
            this.defaultMaxSessionIdleTimeout = defaultMaxSessionIdleTimeout;
        } else {
            webSocketContainer.setDefaultMaxSessionIdleTimeout(defaultMaxSessionIdleTimeout);
        }
    }

    /**
     * Container properties.
     * <p/>
     * Used to set container specific configuration as SSL truststore and keystore, HTTP Proxy configuration and
     * maximum incoming buffer size. These properties cannot be shared among various containers due to constraints in
     * WebSocket API, so if you need to have multiple configurations, you will need to create multiple ClientManager
     * instances or synchronize connectToServer method invocations.
     *
     * @return map containing container properties.
     * @see ClientProperties
     */
    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * Executor service which just executes provided {@link Runnable} in the very same thread.
     */
    private static class SameThreadExecutorService extends AbstractExecutorService {
        @Override
        public void shutdown() {
            // do nothing.
        }

        @Override
        public List<Runnable> shutdownNow() {
            // do nothing.
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return false;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }

    /**
     * Reconnect handler.
     * <p/>
     * When implementing, be sure that you do have enough logic behind cancelling reconnect feature - even {@link javax.websocket.Session#close()}
     * call will be treated just like any other disconnect resulting in reconnect.
     */
    public static class ReconnectHandler {

        /**
         * Called after {@link javax.websocket.OnClose} annotated method (or {@link Endpoint#onClose(javax.websocket.Session, javax.websocket.CloseReason)}
         * is invoked.
         *
         * @param closeReason close reason passed to onClose method.
         * @return When {@code true} is returned, client container will reconnect.
         */
        public boolean onDisconnect(CloseReason closeReason) {
            return false;
        }

        /**
         * Called when there is a connection failure.
         * <p/>
         * Type of the failure is indicated by {@link Exception} parameter. Be cautious when implementing this method,
         * you might easily cause DDoS like behaviour.
         *
         * @param exception Exception thrown during connection phase.
         * @return When {@code true} is returned, client container will reconnect.
         */
        public boolean onConnectFailure(Exception exception) {
            return false;
        }
    }

    private class RetryAfterReconnectHandler extends ReconnectHandler {

        public static final int RETRY_AFTER_THRESHOLD = 5;
        static final int RETRY_AFTER_MAX_DELAY = 120;
        private final AtomicInteger retryCounter = new AtomicInteger(0);
        private final ReconnectHandler userReconnectHandler;
        private final Runnable reconnectRunnable;
        private final TyrusFuture<Session> future;
        private final boolean retryAfterEnabled;


        RetryAfterReconnectHandler(final Runnable reconnectRunnable, TyrusFuture<Session> future, final ReconnectHandler userReconnectHandler, boolean retryAfterEnabled) {
            assert userReconnectHandler != null;
            this.userReconnectHandler = userReconnectHandler;
            this.reconnectRunnable = reconnectRunnable;
            this.future = future;
            this.retryAfterEnabled = retryAfterEnabled;
        }

        @Override
        public boolean onDisconnect(CloseReason closeReason) {
            return userReconnectHandler.onDisconnect(closeReason);
        }

        @Override
        public boolean onConnectFailure(final Exception exception) {
            if (retryAfterEnabled) {
                Throwable t = exception;
                if (t instanceof DeploymentException) {
                    t = t.getCause();
                    if (t != null && t instanceof RetryAfterException && ((RetryAfterException) t).getDelay() != null) {
                        if (retryCounter.getAndIncrement() < RETRY_AFTER_THRESHOLD && ((RetryAfterException) t).getDelay() <= RETRY_AFTER_MAX_DELAY) {
                            long delay = ((RetryAfterException) t).getDelay() < 0 ? 0 : ((RetryAfterException) t).getDelay();
                            getScheduledExecutorService().schedule(reconnectRunnable, delay, TimeUnit.SECONDS);
                            return false;
                        }
                    }
                }
            }
            boolean result = userReconnectHandler.onConnectFailure(exception);
            if (!result) {
                future.setFailure(exception);
            }
            return result;
        }

    }
}
