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
package org.glassfish.tyrus.tests.qa.tools;

import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONException;

/**
 *
 * @author michal.conos at oracle.com
 */
public class SessionController {
    public static final Logger logger = Logger.getLogger(SessionController.class.getCanonicalName());

    public enum SessionState {

        START("null"),
        OPEN_SERVER("server.onOpen"),
        OPEN_CLIENT("client.onOpen"),
        MESSAGE("onMessage"),
        CLOSE_SERVER("server.onClose"),
        CLOSE_CLIENT("client.onClose"),
        ERROR_SERVER("server.onError"),
        ERROR_CLIENT("client.onError"),
        FINISHED_SERVER("server.finished");
        private String msg;

        SessionState(String msg) {
            this.msg = msg;
        }

        public String getMessage() {
            return msg;
        }
        
        @Override
        public String toString() {
            return msg;
        }
    }
    private final CommChannel.Client channel;
    private final String sessionName;

    public SessionController(String sessionName, CommChannel.Client channel) {
        this.channel = channel;
        this.sessionName = sessionName;
    }

    public CommChannel.Client getChannel() {
        return channel;
    }

    public String getSessionName() {
        return sessionName;
    }

    private synchronized void changeState(SessionState expect, SessionState newState) {
        try {
            logger.log(Level.INFO, "changeState: {0} ---> {1}", new Object[] {expect, newState});
            String currentState = channel.getSessionStatus(sessionName);
            logger.log(Level.INFO, "changeState: currState {0}", currentState);
            if (currentState.equals(expect.getMessage())) {
                logger.log(Level.INFO, "changeState: Switching to {0}", newState);
                channel.setSessionStatus(sessionName, newState.getMessage());
            }
        } catch (JSONException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        } catch (URISyntaxException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }
    
    public void setState(String customState) {
        channel.setSessionStatus(sessionName, customState);
    }
    
    public String getState() {
        try {
            return channel.getSessionStatus(sessionName);
        } catch (URISyntaxException ex) {
            logger.log(Level.SEVERE, null, ex);
             throw new RuntimeException(ex);
        } catch (JSONException ex) {
            logger.log(Level.SEVERE, null, ex);
             throw new RuntimeException(ex);
        }
    }

    public void serverOnOpen() {
        changeState(SessionState.START, SessionState.OPEN_SERVER);
    }

    public void clientOnOpen() {
        changeState(SessionState.OPEN_SERVER, SessionState.OPEN_CLIENT);
    }

    public void onMessage() {
        changeState(SessionState.OPEN_CLIENT, SessionState.MESSAGE);
    }

    public void serverOnClose() {
        changeState(SessionState.MESSAGE, SessionState.CLOSE_SERVER);
    }

    public void serverOnError(Throwable t) {
        t.printStackTrace();
        changeState(SessionState.CLOSE_SERVER, SessionState.ERROR_SERVER);
    }

    public void serverOnFinish() {
        changeState(SessionState.ERROR_SERVER, SessionState.FINISHED_SERVER);
    }

    public boolean isFinished() {
        try {
            return channel.getSessionStatus(sessionName).equals(SessionState.FINISHED_SERVER.getMessage());
        } catch (URISyntaxException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        } catch (JSONException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }
}
