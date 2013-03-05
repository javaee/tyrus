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

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import org.glassfish.tyrus.server.TyrusServerContainer;
import org.glassfish.tyrus.tests.qa.handlers.BasicMessageHandler;
import org.glassfish.tyrus.tests.qa.tools.CommChannel;
import org.glassfish.tyrus.tests.qa.tools.SessionController;

abstract public class ProgrammaticWholeMessageEndpoint<T> extends Endpoint implements MessageHandler.Whole<T> {

    private static final Logger logger = Logger.getLogger(ProgrammaticWholeMessageEndpoint.class.getCanonicalName());
    protected SessionLifeCycle lifeCycle;
    protected MessageHandler messageHandler;
    protected SessionController sc;
    private Session session = null;

    public abstract void createLifeCycle();

    boolean isServerContainer(Session session) {
        logger.log(Level.INFO, "websocket.container:{0}", session.getContainer().toString());
        return session.getContainer() instanceof TyrusServerContainer;
    }

    @Override
    public void onMessage(T message) {
        
        logger.log(Level.INFO, "Programmatic.onMessage:{0}", message.toString());
        if (isServerContainer(session)) {
            logger.log(Level.INFO, "PRGEND:server:onMessage:{0}", message.toString());
            lifeCycle.onServerMessage(message, session);
        } else {
            logger.log(Level.INFO, "PRGEND:client:onMessage:{0}", message.toString());
            lifeCycle.onClientMessage(message, session);
        }
    }
    
    @Override
    public void onOpen(Session session, EndpointConfig ec) {
        if (this.session == null) {
            this.session = session;
        }
        logger.log(Level.INFO, "ProgrammaticEndpoint: onOpen");
        this.sc = new SessionController(session);
        createLifeCycle();
        lifeCycle.setSessionController(sc);
        session.addMessageHandler(this);
        if (isServerContainer(session)) {
            lifeCycle.onServerOpen(session, ec);
        } else {
            lifeCycle.onClientOpen(session, ec);
        }
    }

    @Override
    public void onClose(Session s, CloseReason reason) {
        if (isServerContainer(s)) {
            lifeCycle.onServerClose(s, reason);
        } else {
            lifeCycle.onClientClose(s, reason);
        }
    }

    @Override
    public void onError(Session s, Throwable thr) {
        if (isServerContainer(s)) {
            lifeCycle.onServerError(s, thr);
        } else {
            lifeCycle.onClientError(s, thr);
        }
    }
   
}
