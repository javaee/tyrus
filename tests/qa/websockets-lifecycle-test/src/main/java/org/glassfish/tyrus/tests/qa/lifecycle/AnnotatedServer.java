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
package org.glassfish.tyrus.tests.qa.lifecycle;

import org.glassfish.tyrus.tests.qa.lifecycle.config.ServerConfiguration;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.websocket.CloseReason;
import javax.websocket.EndpointConfiguration;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import javax.websocket.WebSocketClose;
import javax.websocket.WebSocketError;
import javax.websocket.WebSocketMessage;
import javax.websocket.WebSocketOpen;
import javax.websocket.server.WebSocketEndpoint;
import org.glassfish.tyrus.tests.qa.lifecycle.config.ServerAnnotatedConfiguration;
import org.glassfish.tyrus.tests.qa.tools.SessionController;

/**
 *
 * @author michal.conos at oracle.com
 */
@WebSocketEndpoint(value=LifeCycleDeployment.LIFECYCLE_ENDPOINT_PATH, configuration=ServerAnnotatedConfiguration.class)
public class AnnotatedServer {

    private static final Logger logger = Logger.getLogger(AnnotatedClient.class.getCanonicalName());
    LifeCycleServer server;
    SessionController sc;

    @WebSocketOpen
    public void onOpen(Session s, EndpointConfiguration config) {
        server = ((ServerAnnotatedConfiguration) config).getServerHandler();
        sc = ((ServerConfiguration) config).getSessionController();
        server.setSessionController(sc);
        server.onOpen(s, config);
    }

    @WebSocketMessage
    public void onMessage(String message, Session session) throws IOException {
       server.onMessage(message, session);
    }

    @WebSocketClose
    public void onClose(Session s, CloseReason reason) {
        server.onClose(s, reason);
    }

    @WebSocketError
    public void onError(Session s, Throwable thr) {
        server.onError(s, thr);
    }
}
