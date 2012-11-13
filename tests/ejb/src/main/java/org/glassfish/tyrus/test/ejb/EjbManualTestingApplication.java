/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.test.ejb;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.DefaultClientEndpointConfiguration;

import javax.net.websocket.Session;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class EjbManualTestingApplication {

    private static CountDownLatch messageLatch;

    private static String receivedMessage1;

    private static String receivedMessage2;

    private static final String SENT_MESSAGE = "Hello.";

//    TODO create automated test
    public static void main(String[] args) {
        try {
            messageLatch = new CountDownLatch(2);

            DefaultClientEndpointConfiguration.Builder builder = new DefaultClientEndpointConfiguration.Builder("ws://localhost:8080/ejb-test/singleton");
            final DefaultClientEndpointConfiguration configSingleton = builder.build();

            builder = new DefaultClientEndpointConfiguration.Builder("ws://localhost:8080/ejb-test/stateless");
            final DefaultClientEndpointConfiguration configStateless = builder.build();

            ClientManager client = ClientManager.createClient();
            client.connectToServer(new TestEndpointAdapter() {
                @Override
                public void onOpen(Session session) {
                    try {
                        session.addMessageHandler(new TestTextMessageHandler(this));
                        session.getRemote().sendString(SENT_MESSAGE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onMessage(String message) {
                    receivedMessage1 = message;
                    messageLatch.countDown();
                    System.out.println("Received Message1: "+receivedMessage1);
                }
            }, configSingleton);

            client.connectToServer(new TestEndpointAdapter() {
                @Override
                public void onOpen(Session session) {
                    try {
                        session.addMessageHandler(new TestTextMessageHandler(this));
                        session.getRemote().sendString(SENT_MESSAGE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onMessage(String message) {
                    receivedMessage2 = message;
                    messageLatch.countDown();
                    System.out.println("Received Message2: "+receivedMessage2);
                }
            }, configSingleton);
            messageLatch.await(5, TimeUnit.SECONDS);

            messageLatch = new CountDownLatch(2);

            client.connectToServer(new TestEndpointAdapter() {
                @Override
                public void onOpen(Session session) {
                    try {
                        session.addMessageHandler(new TestTextMessageHandler(this));
                        session.getRemote().sendString(SENT_MESSAGE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onMessage(String message) {
                    receivedMessage1 = message;
                    messageLatch.countDown();
                    System.out.println("Received Message3: "+receivedMessage1);
                }
            }, configStateless);

            client.connectToServer(new TestEndpointAdapter() {
                @Override
                public void onOpen(Session session) {
                    try {
                        session.addMessageHandler(new TestTextMessageHandler(this));
                        session.getRemote().sendString(SENT_MESSAGE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onMessage(String message) {
                    receivedMessage2 = message;
                    messageLatch.countDown();
                    System.out.println("Received Message4: "+receivedMessage2);
                }
            }, configStateless);

            messageLatch.await(5, TimeUnit.SECONDS);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
