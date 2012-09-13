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

package org.glassfish.tyrus.platform;

import javax.net.websocket.ClientConfiguration;
import javax.net.websocket.Endpoint;
import javax.net.websocket.ServerConfiguration;
import javax.net.websocket.ServerContainer;
import javax.net.websocket.Session;

import java.util.Set;

/**
 *
 * @author dannycoward
 */
public class ServerContainerImpl extends WithProperties implements ServerContainer {
    static ServerContainerImpl instance;
    static boolean WEB_MODE = true;
    private String wsPath;
    private int port;
    private BeanServer beanServer;
    private ClassLoader applicationLevelClassLoader;

    public ServerContainerImpl(BeanServer beanServer, String wsPath, int port) {
        this.wsPath = wsPath;
        this.port = port;
        this.beanServer = beanServer;
        instance = this;
    }

    public void setApplicationLevelClassLoader(ClassLoader applicationLevelClassLoader) {
        this.applicationLevelClassLoader = applicationLevelClassLoader;
    }

    public ClassLoader getApplicationLevelClassLoader() {
        return this.applicationLevelClassLoader;
    }

    public String getPath() {
        return this.wsPath;
    }

    public int getPort() {
        return this.port;
    }

//    @Override
//    public void Xdeploy(Endpoint endpoint, String path) {
//        WebSocketEndpointAdapter adapter = new WebSocketEndpointAdapter(this, endpoint, path);
//        adapter.init();
//        this.beanServer.deploy(adapter);
//    }

    public static void setWebMode(boolean b) {
        WEB_MODE = b;
    }

    @Override
    public String toString() {
        return "ContainerContext("+this.wsPath+")";
    }

    /** Publish the given endpoint with the provided configuration
     * information.
     * @param endpoint
     * @param ilc
     */
    public void publishServer(Endpoint endpoint, ServerConfiguration ilc) {
        throw new UnsupportedOperationException("");
    }

    public void connectToServer(Endpoint endpoint, ClientConfiguration olc) {
         throw new UnsupportedOperationException("");
    }
    /** Return an unordered collection of the currently active web socket sessions.
     * @return
     */
    public Set<Session> getActiveSessions() {
         throw new UnsupportedOperationException("");
    }

    /** Return the maximum time in seconds that a web socket session may be idle before
     * the container may close it.
     * @return
     */
    public long getMaxSessionIdleTimeout() {
         throw new UnsupportedOperationException("");
    }
    /** Sets the maximum time in seconds that a web socket session may be idle before
     * the container may close it.
     * @return
     */
    public void setMaxSessionIdleTimeout(long timeout) {
         throw new UnsupportedOperationException("");
    }
     /** Returns the maximum size of binary message in number of bytes that this container
      * will buffer.
      * @return
      */
    public long getMaxBinaryMessageBufferSize() {
         throw new UnsupportedOperationException("");
    }
    /** Sets the maximum size of binary message in number of bytes that this container
      * will buffer.
      * @return
      */
    public void setMaxBinaryMessageBufferSize(long max) {
         throw new UnsupportedOperationException("");
    }
    /** Sets the maximum size of text message in number of bytes that this container
     * will buffer.
     * @return
     */
    public long getMaxTextMessageBufferSize() {
         throw new UnsupportedOperationException("");
    }
     /** Returns the maximum size of text message in number of bytes that this container
      * will buffer.
      * @return
      */
    public void setMaxTextMessageBufferSize(long max) {
         throw new UnsupportedOperationException("");
    }
}
