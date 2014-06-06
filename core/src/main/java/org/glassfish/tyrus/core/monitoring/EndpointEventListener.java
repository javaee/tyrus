/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.core.monitoring;

import javax.websocket.Session;

import org.glassfish.tyrus.core.Beta;

/**
 * Listens to endpoint-level events that are interesting for monitoring.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
@Beta
public interface EndpointEventListener {

    /**
     * Called when a session has been opened.
     *
     * @param sessionId an ID of the newly opened session.
     * @return listener that listens for message-level events.
     */
    MessageEventListener onSessionOpened(String sessionId);

    /**
     * Called when a session has been closed.
     *
     * @param sessionId an ID of the closed session.
     */
    void onSessionClosed(String sessionId);

    /**
     * Called when an error has occurred.
     * <p/>
     * Errors that occur either during {@link javax.websocket.Endpoint#onOpen(javax.websocket.Session, javax.websocket.EndpointConfig)},
     * {@link javax.websocket.Endpoint#onClose(javax.websocket.Session, javax.websocket.CloseReason)} and their annotated equivalent
     * or when handling an incoming message, cause this listener to be called. It corresponds to the event of invocation of
     * {@link javax.websocket.Endpoint#onError(javax.websocket.Session, Throwable)} and its annotated equivalent.
     *
     * @param session session
     * @param t       throwable that has been thrown.
     */
    void onError(Session session, Throwable t);

    /**
     * An instance of @EndpointEventListener that does not do anything.
     */
    public static final EndpointEventListener NO_OP = new EndpointEventListener() {
        @Override
        public MessageEventListener onSessionOpened(String sessionId) {
            return MessageEventListener.NO_OP;
        }

        @Override
        public void onSessionClosed(String sessionId) {
            // do nothing
        }

        @Override
        public void onError(Session session, Throwable t) {
            // do nothing
        }
    };
}
