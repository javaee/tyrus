/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2015 Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;

import org.glassfish.tyrus.tests.qa.regression.Issue;
import org.glassfish.tyrus.tests.qa.regression.IssueTests;
import org.glassfish.tyrus.tests.qa.tools.IssueManipulator;
import org.glassfish.tyrus.tests.qa.tools.SessionController;

/**
 * @author Michal Čonos (michal.conos at oracle.com)
 */
abstract public class SessionLifeCycle<T> {

    public SessionLifeCycle(boolean partial) {
        this.partialMessageHandler = partial;
    }

    private boolean partialMessageHandler;
    private SessionController sc;
    protected static final Logger logger = Logger.getLogger(SessionLifeCycle.class.getCanonicalName());

    abstract public void onServerMessageHandler(T message, Session session) throws IOException;

    abstract public void onServerMessageHandler(T message, Session session, boolean last) throws IOException;

    abstract public void onClientMessageHandler(T message, Session session) throws IOException;

    abstract public void onClientMessageHandler(T message, Session session, boolean last) throws IOException;

    abstract public void startTalk(Session s) throws IOException;

    abstract public void startTalkPartial(Session s) throws IOException;

    public void setSessionController(SessionController sc) {
        this.sc = sc;
    }

    public void setPartialMessageHandler(boolean partial) {
        partialMessageHandler = partial;
    }

    public void onServerOpen(Session s, EndpointConfig config) {
        logger.log(Level.INFO, "Someone connected:{0}", s.getRequestURI().toString());
        sc.serverOnOpen();
    }

    public void onServerClose(Session s, CloseReason reason) {
        logger.log(Level.INFO, "Closing the session: {0}", s.toString());
        logger.log(Level.INFO, "Closing the session with reason: {0}", reason);

        if (!IssueTests.checkTyrus101(reason)) {
            sc.setState("server.TYRUS101");
        }

        if (!IssueTests.checkTyrus104(s)) {
            sc.setState("server.TYRUS104");
        }

        if (reason != null && reason.getCloseCode().equals(CloseReason.CloseCodes.GOING_AWAY) &&
                reason.getReasonPhrase() != null && reason.getReasonPhrase().equals("Going away")) {
            sc.serverOnClose();
        }
        throw new MyException("going onError");
    }

    private boolean checkError(Throwable thr) {
        // Programmatic Case
        if (thr instanceof RuntimeException && thr.getMessage() != null && "going onError".equals(thr.getMessage())) {
            return true;
        }
        // Annotated case - see TYRUS-94
        if (thr instanceof InvocationTargetException) {
            logger.log(Level.INFO, "TYRUS-94: should be runtime exception!");
            Throwable cause = thr.getCause();
            boolean res = cause instanceof RuntimeException && cause.getMessage() != null &&
                    "going onError".equals(cause.getMessage());
            logger.log(Level.INFO, "At least RuntimeException", thr);
            logger.log(Level.INFO, "RuntimeException.getMessage()=={0}", cause.getMessage());
            return res;
        }
        return false;
    }

    public void onServerError(Session s, Throwable thr) {
        logger.log(Level.INFO, "onServerError:", thr);

        if (checkError(thr)) {
            sc.serverOnError(thr);
            if (!IssueTests.checkTyrus94(thr)) {
                sc.setState("server.TYRUS_94");
            }
            sc.serverOnFinish();
        }
    }

    public void onServerMessage(T message, Session session) {
        logger.log(Level.INFO, "server:message={0}", message);
        sc.onMessage();
        try {
            //throw new RuntimeException();
            onServerMessageHandler(message, session);
        } catch (Exception ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, null, ex);
            sc.setState("on.server.message.exception");
        }
    }

    public void onServerMessage(T message, Session session, boolean last) {
        logger.log(Level.INFO, "server:message={0}", message);
        sc.onMessage();
        try {
            //throw new RuntimeException();
            onServerMessageHandler(message, session, last);
        } catch (Exception ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, null, ex);
            sc.setState("on.server.message.exception");
        }
    }

    public void onClientOpen(Session s, EndpointConfig config) {

        if (!IssueTests.checkTyrus93(s)) {
            sc.setState("TYRUS_93_FAIL");
        }
        sc.clientOnOpen();

        try {
            if (partialMessageHandler) {
                startTalkPartial(s);
            } else {
                startTalk(s);
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    public void onClientClose(Session s, CloseReason reason) {
        logger.log(Level.INFO, "client: Closing the session: {0}", s.toString());
        //sc.clientOnClose();
        final RemoteEndpoint remote = s.getBasicRemote();
        try {
            s.getBasicRemote().sendText("client:onClose");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
            sc.setState("on.client.close.exception");
        }
    }

    public void onClientError(Session s, Throwable thr) {
        logger.log(Level.SEVERE, "client: onError: {0}", thr.getMessage());
    }

    public void onClientMessage(T message, Session session) {
        sc.onMessage();
        logger.log(Level.INFO, "client:message={0}", message);
        try {
            onClientMessageHandler(message, session);
        } catch (IOException ex) {
            sc.setState("on.client.message.exception");
            ex.printStackTrace();
            logger.log(Level.SEVERE, null, ex);
        }
    }

    public void onClientMessage(T message, Session session, boolean last) {
        sc.onMessage();
        logger.log(Level.INFO, "client:message={0}", message);
        try {
            onClientMessageHandler(message, session, last);
        } catch (IOException ex) {
            sc.setState("on.client.partial.message.exception");
            ex.printStackTrace();
            logger.log(Level.SEVERE, null, ex);
        }
    }

    protected void closeTheSessionFromClient(Session session) throws IOException {
        logger.log(Level.INFO, "closing the session from the client");
        session.close(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "Going away"));
    }
}
