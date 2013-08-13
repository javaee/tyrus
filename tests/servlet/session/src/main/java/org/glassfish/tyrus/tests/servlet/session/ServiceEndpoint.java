/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.tests.servlet.session;

import javax.websocket.OnMessage;
import javax.websocket.server.ServerEndpoint;

/**
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
@ServerEndpoint(value = "/service")
public class ServiceEndpoint {
    private static final String POSITIVE = "1";
    private static final String NEGATIVE = "0";

    @OnMessage
    public String onMessage(String message) {

        if (message.equals("server")) {
            final CloseServerEndpoint closeServerEndpoint = SingletonConfigurator.getEndpoint(CloseServerEndpoint.class);

            if (closeServerEndpoint.isAddMessageHandlerExceptionThrown() &&
                    closeServerEndpoint.isRemoveMessageHandlerExceptionThrown() &&
                    closeServerEndpoint.isGetBasicRemoteExceptionThrown() &&
                    closeServerEndpoint.isGetAsyncRemoteExceptionThrown() &&
                    closeServerEndpoint.isInCloseSendTextExceptionThrown() &&
                    !closeServerEndpoint.isInCloseGetTimeoutExceptionThrown()) {
                return POSITIVE;
            } else {
                return NEGATIVE;
            }
        } else if (message.equals("client")) {
            final CloseClientEndpoint closeClientEndpoint = SingletonConfigurator.getEndpoint(CloseClientEndpoint.class);

            return closeClientEndpoint.isInCloseSendTextExceptionThrown() ? POSITIVE : NEGATIVE;
        } else if (message.equals("idleTimeoutReceiving")) {
            final IdleTimeoutReceivingEndpoint idleTimeoutReceivingEndpoint = SingletonConfigurator.getEndpoint(IdleTimeoutReceivingEndpoint.class);

            return idleTimeoutReceivingEndpoint.isOnCloseCalled() ? POSITIVE : NEGATIVE;
        } else if (message.equals("idleTimeoutSending")) {
            final IdleTimeoutSendingEndpoint idleTimeoutSendingEndpoint = SingletonConfigurator.getEndpoint(IdleTimeoutSendingEndpoint.class);

            return idleTimeoutSendingEndpoint.isOnCloseCalled() ? POSITIVE : NEGATIVE;
        } else if (message.equals("idleTimeoutSendingPing")) {
            final IdleTimeoutSendingPingEndpoint idleTimeoutSendingPingEndpoint = SingletonConfigurator.getEndpoint(IdleTimeoutSendingPingEndpoint.class);

            return idleTimeoutSendingPingEndpoint.isOnCloseCalled() ? POSITIVE : NEGATIVE;
        } else if (message.equals("reset")) {
            final IdleTimeoutReceivingEndpoint idleTimeoutReceivingEndpoint = SingletonConfigurator.getEndpoint(IdleTimeoutReceivingEndpoint.class);
            final IdleTimeoutSendingEndpoint idleTimeoutSendingEndpoint = SingletonConfigurator.getEndpoint(IdleTimeoutSendingEndpoint.class);
            final IdleTimeoutSendingPingEndpoint idleTimeoutSendingPingEndpoint = SingletonConfigurator.getEndpoint(IdleTimeoutSendingPingEndpoint.class);

            idleTimeoutReceivingEndpoint.setOnCloseCalled(false);
            idleTimeoutSendingEndpoint.setOnCloseCalled(false);
            idleTimeoutSendingPingEndpoint.setOnCloseCalled(false);
            return "-1";
        } else {
            return "-1";
        }
    }
}