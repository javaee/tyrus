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
package org.glassfish.tyrus.tests.qa.lifecycle.server;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.MessageHandler;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import javax.websocket.WebSocketMessage;
import javax.websocket.WebSocketOpen;
import javax.websocket.server.DefaultServerConfiguration;
import javax.websocket.server.WebSocketEndpoint;
import org.glassfish.tyrus.websockets.HandShake;

public class ProgrammaticServer extends Endpoint {

    private static final Logger logger = Logger.getLogger(ProgrammaticServer.class.getCanonicalName());

    @Override
    public void onOpen(Session s, EndpointConfiguration ec) {
        logger.log(Level.INFO, "Someone connected:{0}", s.getRequestURI().toString());
        final RemoteEndpoint remote = s.getRemote();
        s.addMessageHandler(
                new MessageHandler.Basic<String>() {
                    @Override
                    public void onMessage(String recv) {
                        try {
                            remote.sendString(messageHandler(recv));
                        } catch (IOException ex) {
                            logger.log(Level.SEVERE, null, ex);
                        }
                    }
                });
    }

    public String messageHandler(String message) {
        logger.log(Level.INFO, "message={0}", message);
        return message;
    }

    @Override
    public void onClose(Session s, CloseReason reason) {
        logger.log(Level.INFO, "Clossing the session: {0}", s.toString());
        final RemoteEndpoint remote = s.getRemote();
        try {
            remote.sendString("onClose");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void onError(Session s, Throwable thr) {
        logger.log(Level.SEVERE, "onError: {0}", thr.getLocalizedMessage());
        final RemoteEndpoint remote = s.getRemote();
        try {
            remote.sendString("onError");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
}
