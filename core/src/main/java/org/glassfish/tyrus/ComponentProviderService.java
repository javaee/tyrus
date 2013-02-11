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
package org.glassfish.tyrus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.websocket.DeploymentException;
import javax.websocket.Session;

import org.glassfish.tyrus.spi.ComponentProvider;

/**
 * Provides an instance of component. Searches for registered {@link ComponentProvider}s which are used to provide instances.
 *
 * @author Martin Matula (martin.matula at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class ComponentProviderService {

    private final Map<Session, Object> sessionToObject;
    private final List<ComponentProvider> providers;


    /**
     * Creates new instance of {@link ComponentProviderService}.
     * </p>
     * Searches for registered {@link ComponentProvider}s and registeres them with this service.
     * </p>
     * {@link DefaultComponentProvider} is always added to found providers.
     * @param errorCollector error collector.
     * @return initialized {@link ComponentProviderService}.
     */
    public static ComponentProviderService create(ErrorCollector errorCollector){
        final List<ComponentProvider> foundProviders = new ArrayList<ComponentProvider>();
        ServiceFinder<ComponentProvider> finder = ServiceFinder.find(ComponentProvider.class);

        for (ComponentProvider componentProvider : finder) {
            foundProviders.add(componentProvider);
        }

        foundProviders.add(new DefaultComponentProvider());
        return new ComponentProviderService(Collections.unmodifiableList(foundProviders));
    }

    private ComponentProviderService(List<ComponentProvider> providers) {
        this.providers = providers;
        this.sessionToObject = new ConcurrentHashMap<Session, Object>();
    }

    /**
     * Provides an instance of class which is coupled to {@link Session}. Currently these are endpoints only.
     * </p>
     * The first time the method is called the provider creates an instance and caches it.
     * Next time the method is called the cached instance is returned.
     *
     * @param c         {@link Class} whose instance will be provided.
     * @param collector error collector.
     * @param <T>       type of the provided instance.
     * @return instance
     */
    public <T> T getInstance(Class<T> c, Session session, ErrorCollector collector) {
        T loaded = null;

        if (sessionToObject.containsKey(session)) {
            Object fromMap = sessionToObject.get(session);
            loaded = c.isAssignableFrom(fromMap.getClass()) ? (T) fromMap : null;
        } else {
            for (ComponentProvider componentProvider : providers) {
                if (componentProvider.isApplicable(c)) {
                    try {
                        loaded = componentProvider.provideInstance(c);
                        sessionToObject.put(session, loaded);
                        break;
                    } catch (Exception e) {
                        collector.addException(new DeploymentException(String.format("Component provider %s threw exception when providing instance of class %s",
                                componentProvider.getClass().getName(), c.getName()), e));
                    }
                }
            }
        }

        return loaded;
    }

    /**
     * Removes {@link Session} from cache.
     *
     * @param session to be removed.
     */
    public void removeSession(Session session) {
        sessionToObject.remove(session);
    }
}