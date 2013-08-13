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
package org.glassfish.tyrus.tests.servlet.remote;

import java.util.HashMap;
import java.util.Map;

import javax.websocket.server.ServerEndpointConfig;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class SingletonConfigurator extends ServerEndpointConfig.Configurator {

    private static final NoTimeoutEndpointResultByFuture NO_TIMEOUT_ENDPOINT_RESULT_BY_FUTURE = new NoTimeoutEndpointResultByFuture();
    private static final NoTimeoutEndpointResultByHandler NO_TIMEOUT_ENDPOINT_RESULT_BY_HANDLER = new NoTimeoutEndpointResultByHandler();
    private static final TimeoutEndpointResultByFuture TIMEOUT_ENDPOINT_RESULT_BY_FUTURE = new TimeoutEndpointResultByFuture();
    private static final TimeoutEndpointResultByHandler TIMEOUT_ENDPOINT_RESULT_BY_HANDLER = new TimeoutEndpointResultByHandler();


    private static final Map<Class<?>, Object> instanceMap = new HashMap<Class<?>, Object>() {{

        put(NoTimeoutEndpointResultByFuture.class, NO_TIMEOUT_ENDPOINT_RESULT_BY_FUTURE);
        put(NoTimeoutEndpointResultByHandler.class, NO_TIMEOUT_ENDPOINT_RESULT_BY_HANDLER);
        put(TimeoutEndpointResultByFuture.class, TIMEOUT_ENDPOINT_RESULT_BY_FUTURE);
        put(TimeoutEndpointResultByHandler.class, TIMEOUT_ENDPOINT_RESULT_BY_HANDLER);
    }};

    @Override
    public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
        return SingletonConfigurator.getEndpoint(endpointClass);
    }

    public static <T> T getEndpoint(Class<T> endpointClass) {
        return (T) instanceMap.get(endpointClass);
    }
}
