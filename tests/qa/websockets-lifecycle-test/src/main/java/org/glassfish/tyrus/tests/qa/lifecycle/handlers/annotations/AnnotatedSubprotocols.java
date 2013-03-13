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
package org.glassfish.tyrus.tests.qa.lifecycle.handlers.annotations;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import javax.websocket.ClientEndpoint;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;
import org.glassfish.tyrus.tests.qa.lifecycle.AnnotatedEndpoint;
import org.glassfish.tyrus.tests.qa.lifecycle.LifeCycleDeployment;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.StringSessionImpl;
import org.glassfish.tyrus.tests.qa.tools.SessionController;

/**
 *
 * @author michal.conos at oracle.com
 */
public class AnnotatedSubprotocols {

    @ServerEndpoint(value = LifeCycleDeployment.LIFECYCLE_ENDPOINT_PATH, subprotocols=
             {
        "mikc21", "mikc22", "mikc23", "mikc24.111", "mikc25/0", "mikc26-0", "mikc27+0", "mikc28*9", "mikc291", "mikc210",
        "mikc21", "mikc22", "mikc23", "mikc24.111", "mikc25/0", "mikc26-0", "mikc27+0", "mikc28*9", "mikc291", "mikc210",
        "mikc21", "mikc22", "mikc23", "mikc24.111", "mikc25/0", "mikc26-0", "mikc27+0", "mikc28*9", "mikc291", "mikc210",
        "mikc21", "mikc22", "mikc23", "mikc24.111", "mikc25/0", "mikc26-0", "mikc27+0", "mikc28*9", "mikc291", "mikc210",
        "mikc21", "mikc22", "mikc23", "mikc24.111", "mikc25/0", "mikc26-0", "mikc27+0", "mikc28*9", "mikc291", "mikc10", "mikc10"
    })
    static public class Server extends AnnotatedEndpoint {

        @Override
        public void createLifeCycle() {
            lifeCycle = new StringSessionImpl(false);
        }

        private void checkSubProtocols(Session s) {
            logger.log(Level.INFO, "checkSubProtocols:{0}",s.getNegotiatedSubprotocol());

            if (!s.getNegotiatedSubprotocol().equals("mikc10")) {
                throw new RuntimeException("checkSubProtocols: bad subprotocol! Got:" + s.getNegotiatedSubprotocol());
            }


        }

        @OnOpen
        @Override
        public void onOpen(Session session, EndpointConfig ec) {
            super.onOpen(session, ec);
            lifeCycle.onServerOpen(session, ec);
            logger.log(Level.INFO, "lifeCycle={0}", lifeCycle.toString());
            checkSubProtocols(session);
        }

        @OnMessage(maxMessageSize = -1)
        public void onMessage(String message, Session session) throws IOException {
            checkSubProtocols(session);
            lifeCycle.onServerMessage(message, session);
        }

        @OnClose
        public void onClose(Session s, CloseReason reason) {
            checkSubProtocols(s);
            lifeCycle.onServerClose(s, reason);
        }

        @OnError
        public void onError(Session s, Throwable thr) {
            checkSubProtocols(s);
            lifeCycle.onServerError(s, thr);
        }
    }

    @ClientEndpoint(subprotocols=
            { 
        "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
        "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
        "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
        "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
        "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
        "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
        "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
        "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
        "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
        "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
        "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
        "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
        "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
        "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
        "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
        "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
        "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
        "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
        "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
        "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
        "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
        "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
        "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
        "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
        "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10"
    }
            )
    static public class Client extends AnnotatedEndpoint {

        @Override
        public void createLifeCycle() {
            lifeCycle = new StringSessionImpl(false);
        }

        @OnOpen
        public void onOpen(Session session, EndpointConfig ec) {
            if (this.session == null) {
                this.session = session;
            }
            logger.log(Level.INFO, "AnnotatedEndpoint.Client: onOpen");
            logger.log(Level.INFO, "Client can do:{0}", ((ClientEndpointConfig)ec).getPreferredSubprotocols());
            this.sc = new SessionController(session);
            createLifeCycle();
            lifeCycle.setSessionController(sc);
            lifeCycle.onClientOpen(session, ec);
            
        }

        @OnMessage
        public void onMessage(String message, Session session) throws IOException {
            lifeCycle.onClientMessage(message, session);
        }

        @OnClose
        public void onClose(Session s, CloseReason reason) {
            lifeCycle.onClientClose(s, reason);
        }

        @OnError
        public void onError(Session s, Throwable thr) {
            lifeCycle.onClientError(s, thr);
        }
    }
}
