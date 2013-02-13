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
package org.glassfish.tyrus.test.e2e;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.Extension;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.WebSocketMessage;
import javax.websocket.WebSocketOpen;
import javax.websocket.server.WebSocketEndpoint;

import org.glassfish.tyrus.TyrusClientEndpointConfiguration;
import org.glassfish.tyrus.TyrusExtension;
import org.glassfish.tyrus.TyrusServerEndpointConfiguration;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class ExtensionsTest {

    private static final CountDownLatch messageLatch = new CountDownLatch(4);

    private String receivedMessage;

    private static final String SENT_MESSAGE = "Always pass on what you have learned.";

    @WebSocketEndpoint(value = "/echo", configuration = MyServerConfiguration.class)
    public static class TestEndpoint {
        @WebSocketOpen
        public void onOpen(Session s) {
            for (Extension extension : s.getNegotiatedExtensions()) {
                if (extension.getName().equals("ext1") || extension.getName().equals("ext2")) {
                    messageLatch.countDown();
                }
            }
        }

        @WebSocketMessage
        public String onMessage(String message) {
            return message;
        }
    }

    public static class MyServerConfiguration extends TyrusServerEndpointConfiguration {
        public MyServerConfiguration(Class<? extends Endpoint> endpointClass, String path) {
            super(endpointClass, path);
        }

        @Override
        public List<Extension> getNegotiatedExtensions(List<Extension> requestedExtensions) {
            // all extensions are accepted.
            return requestedExtensions;
        }
    }

    public static class MyClientConfiguration extends TyrusClientEndpointConfiguration {

    }

    @Test
    public void testExtensions() {
        Server server = new Server(TestEndpoint.class);

        try {
            server.start();

            final List<Extension.Parameter> list1 = new ArrayList<Extension.Parameter>() {{
                add(new TyrusExtension.TyrusParameter("prop1", "val1"));
                add(new TyrusExtension.TyrusParameter("prop2", "val2"));
                add(new TyrusExtension.TyrusParameter("prop3", "val3"));
            }};

            final List<Extension.Parameter> list2 = new ArrayList<Extension.Parameter>() {{
                add(new TyrusExtension.TyrusParameter("prop1", "val1"));
                add(new TyrusExtension.TyrusParameter("prop2", "val2"));
                add(new TyrusExtension.TyrusParameter("prop3", "val3"));
            }};

            ArrayList<Extension> extensions = new ArrayList<Extension>();
            extensions.add(new TyrusExtension("ext1", list1));
            extensions.add(new TyrusExtension("ext2", list2));

            final MyClientConfiguration clientConfiguration = new MyClientConfiguration();
            clientConfiguration.setExtensions(extensions);

            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(final Session session, EndpointConfiguration endpointConfiguration) {
                    try {
                        System.out.println("client conf: " + endpointConfiguration);

                        session.addMessageHandler(new MessageHandler.Basic<String>() {
                            @Override
                            public void onMessage(String message) {
                                for (Extension extension : session.getNegotiatedExtensions()) {
                                    if (extension.getName().equals("ext1") || extension.getName().equals("ext2")) {
                                        messageLatch.countDown();
                                    }
                                }

                                receivedMessage = message;
                            }
                        });

                        session.getRemote().sendString(SENT_MESSAGE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, clientConfiguration, new URI("ws://localhost:8025/websockets/tests/echo"));

            messageLatch.await(1, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());
            assertEquals(SENT_MESSAGE, receivedMessage);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }
}
