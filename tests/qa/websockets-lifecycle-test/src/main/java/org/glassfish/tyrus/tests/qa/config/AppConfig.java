/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.tests.qa.config;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author michal.conos at oracle.com
 */
public class AppConfig {

    public enum AppServer {
        TYRUS,
        GLASSFISH
    };
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 8025;
    private String contextPath;
    private String endpointPath;
    private int port;
    private String host;
    private String installRoot;
    private int commPort;
    private String commHost;
    private String commScheme;

    public AppConfig(String contextPath, String endpointPath, String commScheme, String commHost, int commPort, String installRoot) {
        setContextPath(contextPath);
        setEndpointPath(endpointPath);
        setCommHost(commHost);
        setCommPort(commPort);
        setCommScheme(commScheme);
        setInstallRoot(installRoot);
    }

    public final void setInstallRoot(String installRoot) {
        this.installRoot = installRoot;
    }

    public String getInstallRoot() {
        return installRoot;
    }

    public int getCommPort() {
        return commPort;
    }

    public String getCommHost() {
        return commHost;
    }

    public String getCommScheme() {
        return commScheme;
    }

    public final void setCommPort(int commPort) {
        this.commPort = commPort;
    }

    public final void setCommHost(String commHost) {
        this.commHost = commHost;
    }

    public final void setCommScheme(String commScheme) {
        this.commScheme = commScheme;
    }

    public String getContextPath() {
        return contextPath;
    }

    public final void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public String getEndpointPath() {
        return endpointPath;
    }

    public final void setEndpointPath(String endpointPath) {
        this.endpointPath = endpointPath;
    }

    public URI getURI() {
        try {
            return new URI("ws", null, getHost(), getPort(), getContextPath() + getEndpointPath(), null, null);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }

    public AppServer getWebSocketContainer() {
        final String container = System.getProperty("websocket.container");
        if (container != null && container.equals("glassfish")) {
            return AppServer.GLASSFISH;
        }
        return AppServer.TYRUS;
    }

    public String getHost() {
        final String host = System.getProperty("tyrus.test.host");
        if (host != null) {
            return host;
        }
        return DEFAULT_HOST;
    }

    public int getPort() {
        final String port = System.getProperty("tyrus.test.port");
        if (port != null) {
            try {
                return Integer.parseInt(port);
            } catch (NumberFormatException nfe) {
                // do nothing
            }
        }
        return DEFAULT_PORT;
    }
}
