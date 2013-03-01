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
package org.glassfish.tyrus.tests.qa.lifecycle.handlers;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Objects;
import java.util.logging.Level;
import javax.websocket.Session;
import org.glassfish.tyrus.tests.qa.lifecycle.SessionConversation;
import org.glassfish.tyrus.tests.qa.lifecycle.SessionLifeCycle;

/**
 *
 * @author michal.conos at oracle.com
 */
public class ObjectInputStreamSessionImpl implements SessionConversation {

    @Override
    public SessionLifeCycle getSessionConversation() {
        return new SessionLifeCycle<InputStream>() {
            ObjectInputStreamSessionImpl.SendMeSomething original;

            @Override
            public void startTalk(Session s) throws IOException {
                this.original = new ObjectInputStreamSessionImpl.SendMeSomething("message", "over network", "now");
                logger.log(Level.INFO, "startTalk: Sending:{0}", this.original);
                ObjectOutputStream oos = new ObjectOutputStream(s.getBasicRemote().getSendStream());
                oos.writeObject(original);
                oos.close();
            }

            @Override
            public void onServerMessageHandler(InputStream is, Session session) throws IOException {
                logger.log(Level.INFO, "onServerMessageHandler:is:{0}", is.toString());
                try {
                    ObjectOutputStream oos = new ObjectOutputStream(session.getBasicRemote().getSendStream());
                    ObjectInputStream ois = new ObjectInputStream(is);
                    logger.log(Level.INFO, "onServerMessageHandler:ois:{0}", ois.toString());
                    Object objToBounce = ois.readObject();
                    oos.writeObject(objToBounce);
                    oos.close();
                } catch (ClassNotFoundException ex) {
                    logger.log(Level.SEVERE, null, ex);
                    ex.printStackTrace();
                    throw new RuntimeException(ex);
                }
            }

            @Override
            public void onClientMessageHandler(InputStream message, Session session) throws IOException {
                try {
                    SendMeSomething what = (SendMeSomething) new ObjectInputStream(message).readObject();
                    if (what.equals(original)) {
                        closeTheSessionFromClient(session);
                    }
                } catch (ClassNotFoundException ex) {
                    logger.log(Level.SEVERE, null, ex);
                    ex.printStackTrace();
                    throw new RuntimeException(ex);
                }
            }
        };
    }

    static class SendMeSomething implements Serializable {

        private String what;
        private String how;
        private String when;
        private boolean nice;

        public SendMeSomething(String what, String how, String when) {
            this.what = what;
            this.how = how;
            this.when = when;
            this.nice = false;
        }

        public String getWhat() {
            return what;
        }

        public String getHow() {
            return how;
        }

        public String getWhen() {
            return when;
        }

        public boolean isNice() {
            return nice;
        }

        public void setNice(boolean nice) {
            this.nice = nice;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof SendMeSomething) {
                SendMeSomething dst = (SendMeSomething) obj;
                if (dst.getHow().equals(how) && dst.getWhat().equals(what) && dst.getWhen().equals(when) && dst.isNice() == nice) {
                    return true;
                } else {
                    return false;
                }
            }
            return false;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 41 * hash + Objects.hashCode(this.what);
            hash = 41 * hash + Objects.hashCode(this.how);
            hash = 41 * hash + Objects.hashCode(this.when);
            hash = 41 * hash + (this.nice ? 1 : 0);
            return hash;
        }
    }
}
