/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 - 2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.oldservlet;

import org.glassfish.tyrus.server.BeanServer;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.util.Set;

/**
 * Web application lifecycle listener.
 *
 * @author dannycoward
 */
@WebListener()
public class WebSocketServerWebIntegration implements ServletContextListener {
    static final String ENDPOINT_CLASS_SET = "org.glassfish.websockets.platform.web.endpoint.class.set";
    // TODO: move somewhere sensible
    private static final String defaultProviderClassname = "org.glassfish.tyrus.spi.grizzlyprovider.GrizzlyEngine";
    private static final String PROVIDER_CLASSNAME_KEY = "org.glassfish.websocket.provider.class";
    private static final String websocketroot = "/websockets";
    public static final String PRINCIPAL = "ws_principal";
    private static final int informational_fixed_port = 8080;

    private BeanServer bs = null;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        Set<Class<?>> endpointClassSet = (Set<Class<?>>) sce.getServletContext().getAttribute(ENDPOINT_CLASS_SET);
        if (endpointClassSet == null || endpointClassSet.isEmpty()) {
            return;
        }
        String engineProviderClassname = defaultProviderClassname;
        if (sce.getServletContext().getInitParameter(PROVIDER_CLASSNAME_KEY) != null) {
            engineProviderClassname = (String) sce.getServletContext().getInitParameter(PROVIDER_CLASSNAME_KEY);
        }

        bs = new BeanServer(engineProviderClassname);
        String contextRoot = sce.getServletContext().getContextPath();
        try {
            // bs.initWebSocketServer(this.websocketroot, informational_fixed_port, endpointClassSet);
            bs.initWebSocketServer(contextRoot, informational_fixed_port, endpointClassSet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void contextDestroyed(ServletContextEvent sce) {
        bs.closeWebSocketServer();
    }

}
