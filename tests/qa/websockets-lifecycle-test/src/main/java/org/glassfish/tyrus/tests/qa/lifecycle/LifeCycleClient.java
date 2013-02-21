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

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.websocket.CloseReason;
import javax.websocket.EndpointConfiguration;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import org.glassfish.tyrus.tests.qa.lifecycle.config.ClientConfiguration;
import org.glassfish.tyrus.tests.qa.regression.Issue;
import org.glassfish.tyrus.tests.qa.tools.SessionController;

/**
 *
 * @author michal.conos at oracle.com
 */
abstract public class LifeCycleClient<T> {

    private  SessionController sc;
    protected static final Logger logger = Logger.getLogger(ProgrammaticClient.class.getCanonicalName());

    public void setSessionController(SessionController sc) {
        this.sc = sc;
    }

    public void onOpen(Session s, EndpointConfiguration config) {
        sc=((ClientConfiguration)config).getSessionController();
        if (!Issue.checkTyrus93(s)) {
            sc.setState("TYRUS_93_FAIL");
        }
        sc.clientOnOpen();

        try {
            startTalk(s);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    public void onClose(Session s, CloseReason reason) {
        logger.log(Level.INFO, "client: Closing the session: {0}", s.toString());
        //sc.clientOnClose();
        final RemoteEndpoint remote = s.getRemote();
        try {
            s.getRemote().sendString("client:onClose");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    public void onError(Session s, Throwable thr) {
        logger.log(Level.SEVERE, "client: onError: {0}", thr.getMessage());
    }
    
    abstract public void handleMessage(T message, Session session) throws IOException;
    abstract public void startTalk(Session s) throws IOException;
    

    public void onMessage(T message, Session session) {
        sc.onMessage();
        logger.log(Level.INFO, "client:message={0}", message);
        try {

            handleMessage(message, session);
        } catch (IOException ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, null, ex);
        }
    }
}
