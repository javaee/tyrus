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
package org.glassfish.tyrus.server;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import javax.websocket.DeploymentException;

import org.glassfish.tyrus.spi.ServerContainer;
import org.glassfish.tyrus.spi.ServerContainerFactory;

/**
 * Implementation of the WebSocket Server.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class Server {
    private ServerContainer server;
    private final Map<String, Object> properties;
    private final Set<Class<?>> configuration;
    private final String hostName;
    private final int port;
    private final String rootPath;

    private static final String ENGINE_PROVIDER_CLASSNAME = "org.glassfish.tyrus.container.grizzly.server.GrizzlyServerContainer";
    private static final Logger LOGGER = Logger.getLogger(Server.class.getClass().getName());
    private static final int DEFAULT_PORT = 8025;
    private static final String DEFAULT_HOST_NAME = "localhost";
    private static final String DEFAULT_ROOT_PATH = "/websockets/tests";

    /**
     * Create new server instance.
     *
     * @param configuration to be registered with the server. Classes annotated with
     *                      {@link javax.websocket.server.ServerEndpoint},
     *                      implementing {@link javax.websocket.server.ServerApplicationConfig}
     *                      or extending {@link javax.websocket.server.ServerEndpointConfig} are supported.
     */
    public Server(Class<?>... configuration) {
        this(null, 0, null, null, configuration);
    }

    /**
     * Create new server instance.
     *
     * @param properties    properties used as a parameter to {@link ServerContainerFactory#createServerContainer(java.util.Map)} call.
     * @param configuration to be registered with the server. Classes annotated with
     *                      {@link javax.websocket.server.ServerEndpoint},
     *                      implementing {@link javax.websocket.server.ServerApplicationConfig}
     *                      or extending {@link javax.websocket.server.ServerEndpointConfig} are supported.
     */
    public Server(Map<String, Object> properties, Class<?>... configuration) {
        this(null, 0, null, properties, configuration);
    }

    /**
     * Construct new server.
     *
     * @param hostName      hostName of the server.
     * @param port          port of the server.
     * @param rootPath      root path to the server App.
     * @param properties    properties used as a parameter to {@link ServerContainerFactory#createServerContainer(java.util.Map)} call.
     * @param configuration to be registered with the server. Classes annotated with
     *                      {@link javax.websocket.server.ServerEndpoint},
     *                      implementing {@link javax.websocket.server.ServerApplicationConfig}
     *                      or extending {@link javax.websocket.server.ServerEndpointConfig} are supported.
     */
    public Server(String hostName, int port, String rootPath, Map<String, Object> properties, Class<?>... configuration) {
        this(hostName, port, rootPath, properties, new HashSet<Class<?>>(Arrays.asList(configuration)));
    }

    /**
     * Construct new server.
     *
     * @param hostName      hostName of the server.
     * @param port          port of the server.
     * @param rootPath      root path to the server App.
     * @param properties    properties used as a parameter to {@link ServerContainerFactory#createServerContainer(java.util.Map)} call.
     * @param configuration to be registered with the server. Classes annotated with
     *                      {@link javax.websocket.server.ServerEndpoint},
     *                      implementing {@link javax.websocket.server.ServerApplicationConfig}
     *                      or extending {@link javax.websocket.server.ServerEndpointConfig} are supported.
     */
    public Server(String hostName, int port, String rootPath, Map<String, Object> properties, Set<Class<?>> configuration) {
        this.hostName = hostName == null ? DEFAULT_HOST_NAME : hostName;
        this.port = port == 0 ? DEFAULT_PORT : port;
        this.rootPath = rootPath == null ? DEFAULT_ROOT_PATH : rootPath;
        this.configuration = configuration;
        this.properties = properties == null ? null : new HashMap<String, Object>(properties);
    }

    /**
     * Start the server.
     */
    public synchronized void start() throws DeploymentException {
        try {
            if (server == null) {
                server = ServerContainerFactory.createServerContainer(null);

                for (Class<?> clazz : configuration) {
                    server.addEndpoint(clazz);
                }

                server.start(rootPath, port);
                LOGGER.info("WebSocket Registered apps: URLs all start with ws://" + this.hostName + ":" + this.port);
                LOGGER.info("WebSocket server started.");
            }
        } catch (IOException e) {
            throw new DeploymentException(e.getMessage(), e);
        }
    }

    /**
     * Stop the server.
     */
    public synchronized void stop() {
        if (server != null) {
            server.stop();
            server = null;
            LOGGER.info("Websocket Server stopped.");
        }
    }

    public static void main(String[] args) {
        if (args.length < 4) {
            System.out.println("Please provide: (<hostname>, <port>, <websockets root path>, <;-sep fully qualfied classnames of your bean>) in the command line");
            System.out.println("e.g. localhost 8021 /websockets/myapp myapp.Bean1;myapp.Bean2");
            System.exit(1);
        }
        Set<Class<?>> beanClasses = getClassesFromString(args[3]);
        int port = Integer.parseInt(args[1]);
        String hostname = args[0];
        String wsroot = args[2];

        Server server = new Server(hostname, port, wsroot, null, beanClasses);

        try {
            server.start();
            System.out.println("Press any key to stop the WebSocket server...");
            //noinspection ResultOfMethodCallIgnored
            System.in.read();
        } catch (IOException ioe) {
            System.err.println("IOException during server run");
            ioe.printStackTrace();
        } catch (DeploymentException de) {
            de.printStackTrace();
        } finally {
            server.stop();
        }
    }

    private static Set<Class<?>> getClassesFromString(String rawString) {
        Set<Class<?>> beanClasses = new HashSet<Class<?>>();
        StringTokenizer st = new StringTokenizer(rawString, ";");
        while (st.hasMoreTokens()) {
            String nextClassname = st.nextToken().trim();
            if (!"".equals(nextClassname)) {
                try {
                    beanClasses.add(Class.forName(nextClassname));
                } catch (ClassNotFoundException cnfe) {
                    throw new RuntimeException("Stop: cannot load class: " + nextClassname);
                }
            }
        }
        return beanClasses;
    }
}
