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
import java.util.Set;

import javax.websocket.server.ServerApplicationConfiguration;
import javax.websocket.server.ServerEndpointConfiguration;
import javax.websocket.server.WebSocketEndpoint;

/**
 * Default mutable implementation of {@link javax.websocket.server.ServerApplicationConfiguration} interface. Allows setting all the configuration
 * properties.
 *
 * @author Martin Matula (martin.matula at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class DefaultServerConfiguration implements ServerApplicationConfiguration {

    private final Set<Class> annotatedClasses = new HashSet<Class>();
    private final Set<Class> annotatedClassesView = Collections.unmodifiableSet(annotatedClasses);
    private final Set<Class<? extends ServerEndpointConfiguration>> programmaticClasses = new HashSet<Class<? extends ServerEndpointConfiguration>>();
    private final Set<Class<? extends ServerEndpointConfiguration>> programmaticCLassesView = Collections.unmodifiableSet(programmaticClasses);


    @Override
    public Set<Class<? extends ServerEndpointConfiguration>> getEndpointConfigurationClasses(Set<Class<? extends ServerEndpointConfiguration>> scanned) {
        return programmaticCLassesView;
    }

    @Override
    public Set<Class> getAnnotatedEndpointClasses(Set<Class> scanned) {
        return annotatedClassesView;
    }

    /**
     * Registers a new annotated or programmatic endpoint.
     *
     * @param endpointClass class annotated with {@link javax.websocket.server.WebSocketEndpoint} annotation or extending {@link ServerEndpointConfiguration}.
     * @return this configuration object.
     */
    public DefaultServerConfiguration endpoint(Class<?> endpointClass) throws IllegalArgumentException {
        if (endpointClass.isAnnotationPresent(WebSocketEndpoint.class)) {
            annotatedClasses.add(endpointClass);
        } else if (ServerEndpointConfiguration.class.isAssignableFrom(endpointClass)) {
            programmaticClasses.add((Class<? extends ServerEndpointConfiguration>) endpointClass);
        } else {
            throw new IllegalArgumentException("Class: " + endpointClass.getName() + " is not annotated with " + WebSocketEndpoint.class.getName());
        }

        return this;
    }

    /**
     * Registers new endpoint annotated or programmatic classes.
     *
     * @param endpointClasses classes annotated with {@link javax.websocket.server.WebSocketEndpoint}
     *                        annotation or extending {@link ServerEndpointConfiguration}.
     * @return this configuration object.
     */
    public DefaultServerConfiguration endpoints(Class<?>... endpointClasses) {
        for (Class<?> endpointClass : endpointClasses) {
            this.endpoint(endpointClass);
        }

        return this;
    }

    /**
     * Registers new endpoint annotated classes.
     *
     * @param endpointClasses classes annotated with {@link javax.websocket.server.WebSocketEndpoint}
     *                        annotation or extending {@link ServerEndpointConfiguration}.
     * @return this configuration object.
     */
    public DefaultServerConfiguration endpoints(Set<Class<?>> endpointClasses) {
        for (Class<?> endpointClass : endpointClasses) {
            this.endpoint(endpointClass);
        }

        return this;
    }
}
