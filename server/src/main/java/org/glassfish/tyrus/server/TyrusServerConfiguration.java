/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.websocket.DeploymentException;
import javax.websocket.server.ServerApplicationConfiguration;
import javax.websocket.server.ServerEndpointConfiguration;
import javax.websocket.server.WebSocketEndpoint;

import org.glassfish.tyrus.AnnotatedEndpoint;
import org.glassfish.tyrus.ErrorCollector;
import org.glassfish.tyrus.ReflectionHelper;

/**
 * Container for either deployed {@link ServerApplicationConfiguration}s, if any, or deployed classes.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class TyrusServerConfiguration implements ServerApplicationConfiguration {

    private final Set<Class<? extends ServerEndpointConfiguration>> programmaticClasses = new HashSet<Class<? extends ServerEndpointConfiguration>>();
    private final Set<Class> annotatedClasses = new HashSet<Class>();


    public TyrusServerConfiguration(Set<Class<?>> classes) {
        ErrorCollector errorCollector = new ErrorCollector();
        Set<ServerApplicationConfiguration> configurations = new HashSet<ServerApplicationConfiguration>();

        for (Iterator<Class<?>> it = classes.iterator(); it.hasNext(); ) {
            Class<?> cls = it.next();

            if (cls.isInterface() || Modifier.isAbstract(cls.getModifiers()) || AnnotatedEndpoint.class.isAssignableFrom(cls)) {
                errorCollector.addException(new DeploymentException(cls.getName() + ": Deployed Classes can't be abstract nor interface. The class will not be deployed."));
                it.remove();
            }
            if (ServerApplicationConfiguration.class.isAssignableFrom(cls)) {
                ServerApplicationConfiguration config = (ServerApplicationConfiguration) ReflectionHelper.getInstance(cls, errorCollector);
                configurations.add(config);
            }
        }

        final Set<Class<? extends ServerEndpointConfiguration>> scannedProgramatics = new HashSet<Class<? extends ServerEndpointConfiguration>>();
        final Set<Class> scannedAnnotateds = new HashSet<Class>();

        for (Class<?> cls : classes) {
            if (ServerEndpointConfiguration.class.isAssignableFrom(cls)) {
                scannedProgramatics.add((Class<? extends ServerEndpointConfiguration>) cls);
            } else if (cls.isAnnotationPresent(WebSocketEndpoint.class)) {
                scannedAnnotateds.add(cls);
            }
        }

        if (!configurations.isEmpty()) {
            for (ServerApplicationConfiguration configuration : configurations) {
                Set<Class<? extends ServerEndpointConfiguration>> programmatic = configuration.getEndpointConfigurationClasses(scannedProgramatics);
                programmatic = programmatic == null ? new HashSet<Class<? extends ServerEndpointConfiguration>>() : programmatic;
                programmaticClasses.addAll(programmatic);

                Set<Class> annotated = configuration.getAnnotatedEndpointClasses(scannedAnnotateds);
                annotated = annotated == null ? new HashSet<Class>() : annotated;
                annotatedClasses.addAll(annotated);
            }
        } else {
            programmaticClasses.addAll(scannedProgramatics);
            annotatedClasses.addAll(scannedAnnotateds);
        }
    }

    /**
     * Gets all the {@link ServerEndpointConfiguration} classes which should be deployed.
     *
     * @param scanned is unused.
     * @return all the {@link ServerEndpointConfiguration} classes which should be deployed.
     */
    @Override
    public Set<Class<? extends ServerEndpointConfiguration>> getEndpointConfigurationClasses(Set<Class<? extends ServerEndpointConfiguration>> scanned) {
        return Collections.unmodifiableSet(programmaticClasses);
    }

    /**
     * Gets all the classes annotated with {@link WebSocketEndpoint} annotation which should be deployed.
     *
     * @param scanned is unused.
     * @return all the classes annotated with {@link WebSocketEndpoint} annotation which should be deployed.
     */
    @Override
    public Set<Class> getAnnotatedEndpointClasses(Set<Class> scanned) {
        return Collections.unmodifiableSet(annotatedClasses);
    }
}