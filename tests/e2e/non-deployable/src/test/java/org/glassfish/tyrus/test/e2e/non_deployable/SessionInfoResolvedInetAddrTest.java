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

package org.glassfish.tyrus.test.e2e.non_deployable;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.EncodeException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.core.TyrusSession;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * Cannot be moved to standard tests due the need of running on Grizzly server.
 *
 * @author Ondrej Kosatka (ondrej.kosatka at oracle.com)
 */
public class SessionInfoResolvedInetAddrTest extends TestContainer {

    @ServerEndpoint(value = "/session-info-inetaddress-check")
    public static class SessionInfoInetAddressCheckEndpoint {
        @OnOpen
        public void onOpen(Session session) throws IOException, EncodeException {
            TyrusSession tyrusSession = (TyrusSession) session;
            printSessionInfo(tyrusSession, "SERVER");
        }

        @OnMessage
        public String onMessage(Session session, String message) {
            TyrusSession tyrusSession = (TyrusSession) session;
            if (message.equals("local")) {
                return String.valueOf(tyrusSession.getLocalInetAddress().toString().startsWith("/"));
            } else {
                return String.valueOf(tyrusSession.getRemoteInetAddress().toString().startsWith("/"));
            }
        }
    }

    @Test
    public void testPropagatedInetAddressOnServer() throws DeploymentException {
        Server server = startServer(SessionInfoInetAddressCheckEndpoint.class);

        final CountDownLatch latch = new CountDownLatch(2);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            createClient().connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    final TyrusSession tyrusSession = (TyrusSession) session;

                    printSessionInfo(tyrusSession, "CLIENT");

                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String result) {
                            if (result.equals("true")) {
                                latch.countDown();
                            }
                        }
                    });
                    try {
                        session.getBasicRemote().sendText("local");
                        session.getBasicRemote().sendText("remote");
                        assertTrue("Server session obtains unresolved InetAddress", latch.await(1, TimeUnit.SECONDS));
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }, cec, getURI(SessionInfoInetAddressCheckEndpoint.class));

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    private static void printSessionInfo(TyrusSession tyrusSession, String prefix) {
        InetAddress remoteInetAddress = tyrusSession.getRemoteInetAddress();
        System.out.println(prefix + " remoteInetAddress.toString(): " + remoteInetAddress.toString());
        System.out.println(prefix + " remoteAddr: " + tyrusSession.getRemoteAddr());
        System.out.println(prefix + " remoteHost: " + tyrusSession.getRemoteHostName());
        System.out.println(prefix + " remotePort: " + tyrusSession.getRemotePort());
        InetAddress localInetAddress = tyrusSession.getLocalInetAddress();
        System.out.println(prefix + " localInetAddress.toString(): " + localInetAddress.toString());
        System.out.println(prefix + " localAddr: " + tyrusSession.getLocalAddr());
        System.out.println(prefix + " localName: " + tyrusSession.getLocalHostName());
        System.out.println(prefix + " localPort: " + tyrusSession.getLocalPort());
    }

}
