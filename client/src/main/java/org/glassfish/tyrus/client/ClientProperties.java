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
package org.glassfish.tyrus.client;

/**
 * Tyrus client configuration properties.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public final class ClientProperties {

    /**
     * Property usable in {@link ClientManager#getProperties()}.
     * <p/>
     * Value must be {@code int} and represents handshake timeout in milliseconds. Default value is 30000 (30 seconds).
     */
    public static final String HANDSHAKE_TIMEOUT = "org.glassfish.tyrus.client.ClientManager.ContainerTimeout";

    /**
     * Property usable in {@link ClientManager#getProperties()}.
     * <p/>
     * Value must be {@link org.glassfish.tyrus.client.ClientManager.ReconnectHandler} instance.
     */
    public static final String RECONNECT_HANDLER = "org.glassfish.tyrus.client.ClientManager.ReconnectHandler";

    /**
     * Client-side user property to set proxy URI.
     * <p/>
     * Value is expected to be {@link String} and represent proxy URI. Protocol part is currently ignored
     * but must be present ({@link java.net.URI#URI(String)} is used for parsing).
     * <p/>
     * <pre>
     *     client.getProperties().put(ClientProperties.PROXY_URI, "http://my.proxy.com:80");
     *     client.connectToServer(...);
     * </pre>
     *
     * @see javax.websocket.ClientEndpointConfig#getUserProperties()
     */
    public static final String PROXY_URI = "org.glassfish.tyrus.client.proxy";

    /**
     * Client-side user property to set additional proxy headers.
     * <p/>
     * Value is expected to be {@link java.util.Map}&lt{@link String}, {@link String}&gt and represent raw http headers
     * to be added to initial request which is sent to proxy. Key corresponds to header name, value is header
     * value.
     * <p/>
     * Sample below demonstrates use of this feature to set preemptive basic proxy authentication:
     * <pre>
     *     final HashMap<String, String> proxyHeaders = new HashMap<String, String>();
     *     proxyHeaders.put("Proxy-Authorization", "Basic " + Base64Utils.encodeToString("username:password".getBytes(Charset.forName("UTF-8")), false));
     *
     *     client.getProperties().put(ClientProperties.PROXY_HEADERS, proxyHeaders);
     *     client.connectToServer(...);
     * </pre>
     * Please note that these headers will be used only when establishing proxy connection, for modifying
     * WebSocket handshake headers, see {@link javax.websocket.ClientEndpointConfig.Configurator#beforeRequest(java.util.Map)}.
     *
     * @see javax.websocket.ClientEndpointConfig#getUserProperties()
     */
    public static final String PROXY_HEADERS = "org.glassfish.tyrus.client.proxy.headers";

    /**
     * Property usable in {@link ClientManager#getProperties()} as a key for SSL configuration.
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
     *      client.getProperties().put(ClientProperties.SSL_ENGINE_CONFIGURATOR, sslEngineConfigurator);
     * </pre>
     */
    public static final String SSL_ENGINE_CONFIGURATOR = "org.glassfish.tyrus.client.sslEngineConfigurator";

    /**
     * Property name for maximal incoming buffer size.
     * <p/>
     * Can be set in properties map (see {@link org.glassfish.tyrus.spi.ClientContainer#openClientSocket(String, javax.websocket.ClientEndpointConfig, java.util.Map, org.glassfish.tyrus.spi.ClientEngine)}).
     */
    public static final String INCOMING_BUFFER_SIZE = "org.glassfish.tyrus.incomingBufferSize";

    /**
     * When set to {@code true} (boolean value), client runtime preserves used container and reuses it for outgoing
     * connections.
     * <p/>
     * A single thread pool is reused by all clients with this property set to {@code true}.
     * JDK client supports only shared container option, so setting this property has no effect.
     *
     * @see #SHARED_CONTAINER_IDLE_TIMEOUT
     */
    public static final String SHARED_CONTAINER = "org.glassfish.tyrus.client.sharedContainer";

    /**
     * Container idle timeout in seconds (Integer value).
     * <p/>
     * When the timeout elapses, the shared thread pool will be destroyed.
     *
     * @see #SHARED_CONTAINER
     */
    public static final String SHARED_CONTAINER_IDLE_TIMEOUT = "org.glassfish.tyrus.client.sharedContainerIdleTimeout";

    /**
     * Client-side user property to set worker thread pool configuration.
     * <p/>
     * An instance of {@link org.glassfish.tyrus.client.ThreadPoolConfig} is expected for both JDK
     * and Grizzly client. Instance of {@link org.glassfish.grizzly.threadpool.ThreadPoolConfig}, can be used
     * for Grizzly client.
     * <p/>
     * Sample bellow demonstrates how to use this property:
     * <pre>
     *     client.getProperties().put(ClientProperties.WORKER_THREAD_POOL_CONFIG, ThreadPoolConfig.defaultConfig());
     * </pre>
     */
    public static final String WORKER_THREAD_POOL_CONFIG = "org.glassfish.tyrus.client.workerThreadPoolConfig";
}
