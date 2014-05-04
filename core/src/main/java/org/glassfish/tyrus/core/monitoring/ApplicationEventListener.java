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
package org.glassfish.tyrus.core.monitoring;

import org.glassfish.tyrus.core.Beta;

/**
 * Listens to application-level events that are interesting for monitoring.
 * Only one listener per application can be registered.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
@Beta
public interface ApplicationEventListener {

    public static final String APPLICATION_EVENT_LISTENER = "org.glassfish.tyrus.core.monitoring.ApplicationEventListener";

    /**
     * Called when the application has been initialized.
     *
     * @param applicationName name of the initialized application.
     */
    void onApplicationInitialized(String applicationName);

    /**
     * Called when the application has been destroyed.
     */
    void onApplicationDestroyed();

    /**
     * Called when an endpoint has been registered.
     *
     * @param endpointPath  the path the endpoint has been registered on.
     * @param endpointClass class of the registered endpoint.
     * @return endpoint event listener for registered endpoint.
     */
    EndpointEventListener onEndpointRegistered(String endpointPath, Class<?> endpointClass);

    /**
     * Called when an endpoint has been unregistered.
     *
     * @param endpointPath the path the endpoint has been registered on.
     */
    void onEndpointUnregistered(String endpointPath);

    /**
     * An instance of @ApplicationEventListener that does not do anything.
     */
    public static final ApplicationEventListener NO_OP = new ApplicationEventListener() {

        @Override
        public void onApplicationInitialized(String applicationName) {
            // do nothing
        }

        @Override
        public void onApplicationDestroyed() {
            // do nothing
        }

        @Override
        public EndpointEventListener onEndpointRegistered(String endpointPath, Class<?> endpointClass) {
            return EndpointEventListener.NO_OP;
        }

        @Override
        public void onEndpointUnregistered(String endpointPath) {
            // do nothing
        }
    };
}
