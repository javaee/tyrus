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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.websocket.Endpoint;

/**
 * Default mutable implementation of {@link ServerConfiguration} interface. Allows setting all the configuration
 * properties.
 *
 * @author Martin Matula (martin.matula at oracle.com)
 */
public class DefaultServerConfiguration implements ServerConfiguration {
    private long maxSessionIdleTimeout;
    private long maxBinaryMessageBufferSize;
    private long maxTextMessageBufferSize;
    private final Set<Class<?>> endpointClasses = new HashSet<Class<?>>();
    private final Set<Class<?>> endpointClassesView = Collections.unmodifiableSet(endpointClasses);
    private final Set<Endpoint> endpointInstances = new HashSet<Endpoint>();
    private final Set<Endpoint> endpointInstancesView = Collections.unmodifiableSet(endpointInstances);

    @Override
    public Set<Class<?>> getEndpointClasses() {
        return endpointClassesView;
    }

    @Override
    public Set<Endpoint> getEndpointInstances() {
        return endpointInstancesView;
    }

    @Override
    public long getMaxSessionIdleTimeout() {
        return maxSessionIdleTimeout;
    }

    @Override
    public long getMaxBinaryMessageBufferSize() {
        return maxBinaryMessageBufferSize;
    }

    @Override
    public long getMaxTextMessageBufferSize() {
        return maxTextMessageBufferSize;
    }

    @Override
    public List<String> getExtensions() {
        return Collections.emptyList();
    }

    /**
     * Sets the max session idle timeout.
     *
     * @param max timeout in seconds.
     * @return this configuration object.
     */
    public DefaultServerConfiguration maxSessionIdleTimeout(long max) {
        this.maxSessionIdleTimeout = max;
        return this;
    }

    /**
     * Sets the max binary message buffer size.
     *
     * @param max buffer size in bytes.
     * @return this configuration object.
     */
    public DefaultServerConfiguration maxBinaryMessageBufferSize(long max) {
        this.maxBinaryMessageBufferSize = max;
        return this;
    }

    /**
     * Sets the max text message buffer size.
     *
     * @param max buffer size in bytes.
     * @return this configuration object.
     */
    public DefaultServerConfiguration maxTextMessageBufferSize(long max) {
        this.maxTextMessageBufferSize = max;
        return this;
    }

    /**
     * Registers a new endpoint annotated class.
     *
     * @param endpointClass class annotated with {@link javax.websocket.server.WebSocketEndpoint} annotation.
     * @return this configuration object.
     */
    public DefaultServerConfiguration endpoint(Class<?> endpointClass) {
        endpointClasses.add(endpointClass);
        return this;
    }

    /**
     * Registers a new programmatic endpoint.
     *
     *
     * @param endpoint object implementing {@link javax.websocket.Endpoint} interface.
     * @return this configuration object.
     */
    public DefaultServerConfiguration endpoint(Endpoint endpoint) {
        endpointInstances.add(endpoint);
        return this;
    }

    /**
     * Registers new endpoint annotated classes.
     *
     * @param endpointClasses classes annotated with {@link javax.websocket.server.WebSocketEndpoint}
     *                        annotation.
     * @return this configuration object.
     */
    public DefaultServerConfiguration endpoints(Class<?>... endpointClasses) {
        Collections.addAll(this.endpointClasses, endpointClasses);
        return this;
    }

    /**
     * Registers new endpoint instances.
     *
     * @param endpoints endpoints.
     * @return this configuration object.
     */
    public DefaultServerConfiguration endpoints(Endpoint... endpoints) {
        Collections.addAll(this.endpointInstances, endpoints);
        return this;
    }

    /**
     * Registers new endpoint annotated classes.
     *
     * @param endpointClasses classes annotated with {@link javax.websocket.server.WebSocketEndpoint}
     *                        annotation.
     * @return this configuration object.
     */
    public DefaultServerConfiguration endpoints(Set<Class<?>> endpointClasses) {
        this.endpointClasses.addAll(endpointClasses);
        return this;
    }
}
