/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2016 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.spi;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Factory used to get instances of {@link ServerContainer}.
 */
public abstract class ServerContainerFactory {

    private static final String CONTAINTER_CLASS =
            "org.glassfish.tyrus.container.grizzly.server.GrizzlyServerContainer";

    /**
     * Create new {@link org.glassfish.tyrus.spi.ServerContainer} with default configuration.
     *
     * @return new {@link org.glassfish.tyrus.spi.ServerContainer}.
     */
    public static ServerContainer createServerContainer() {
        return createServerContainer(Collections.<String, Object>emptyMap());
    }

    /**
     * Create new {@link org.glassfish.tyrus.spi.ServerContainer} with configuration.
     *
     * @param properties configuration passed to created server container.
     * @return new {@link org.glassfish.tyrus.spi.ServerContainer}.
     */
    public static ServerContainer createServerContainer(final Map<String, Object> properties) {
        ServerContainerFactory factory = null;

        Iterator<ServerContainerFactory> it = ServiceLoader.load(ServerContainerFactory.class).iterator();
        if (it.hasNext()) {
            factory = it.next();
        }
        if (factory == null) {
            try {
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                Class factoryClass = (classLoader == null)
                        ? Class.forName(CONTAINTER_CLASS)
                        : classLoader.loadClass(CONTAINTER_CLASS);
                factory = (ServerContainerFactory) factoryClass.newInstance();
            } catch (ClassNotFoundException ce) {
                throw new RuntimeException(ce);
            } catch (InstantiationException ie) {
                throw new RuntimeException(ie);
            } catch (IllegalAccessException ie) {
                throw new RuntimeException(ie);
            }
        }
        return factory.createContainer(properties);
    }

    /**
     * Create container delegate method.
     * <p>
     * Has to be implemented by {@link org.glassfish.tyrus.spi.ServerContainerFactory} implementations.
     *
     * @param properties configuration passed to created server container.
     * @return new {@link org.glassfish.tyrus.spi.ServerContainer}.
     */
    public abstract ServerContainer createContainer(Map<String, Object> properties);

}
