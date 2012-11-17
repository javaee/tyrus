/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.Set;
import javax.websocket.Endpoint;

/**
 * Represents server configuration. Can be used to provide the list of registered endpoints,
 * extensions, buffer limits, and timeouts to the server during its initialization. Once the server is initialized with
 * this configuration, any modifications to the configuration may not be reflected in the running server.
 *
 * @author Martin Matula (martin.matula at oracle.com)
 */
public interface ServerConfiguration {
    /**
     * Returns a set of endpoint classes annotated with {@link javax.websocket.WebSocketEndpoint}
     * annotation that should be published by the container initialized with this configuration object.
     *
     * @return Set of annotated endpoint classes to be exposed by the server.
     */
    Set<Class<?>> getEndpointClasses();

    /**
     * Returns a set of {@link Endpoint} instances that should be exposed by the server in addition to the
     * class-based endpoints returned from {@link #getEndpointClasses()}.
     *
     * @return Set of {@link Endpoint endpoint objects}.
     */
    Set<Endpoint> getEndpointInstances();

    /**
     * Return the maximum time in seconds that a web socket session may be idle before
     * the container may close it.
     * @return the number of seconds idle wed socket sessions are active.
     */
    long getMaxSessionIdleTimeout();

    /**
     * Returns the maximum size of binary message that this container
     * will buffer.
     * @return the maximum size of binary message in number of bytes.
     */
    long getMaxBinaryMessageBufferSize();

    /**
     * Gets the maximum size of text message that this container
     * will buffer.
     * @return the maximum size of text message in number of bytes.
     */
    long getMaxTextMessageBufferSize();

    /**
     * Return a mutable list of extension names supported by the container.
     * @return the mutable set of extension names.
     */
    List<String> getExtensions();

}
