/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2013 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.testing.TestUtilities;

import org.junit.Assert;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class PathParamTest extends TestUtilities {

    private CountDownLatch messageLatch;

    private String receivedMessage;

    private static final String SENT_MESSAGE = "Hello World";

    @ServerEndpoint(value = "/pathparam1/{first}/{second}/{third}")
    public static class PathParamTestEndpoint {

        @OnMessage
        public String doThat1(@PathParam("first") String first,
                             @PathParam("second") String second,
                             @PathParam("third") String third,
                             @PathParam("fourth") String fourth,
                             String message, Session peer) {

            if (first != null && second != null && third != null && fourth == null && message != null && peer != null) {
                return message + first + second + third;
            } else {
                return "Error";
            }
        }
    }

    @Test
    public void testPathParam() throws DeploymentException {
        Server server = startServer(PathParamTestEndpoint.class);

        try {
            messageLatch = new CountDownLatch(1);

            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint(){
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                receivedMessage = message;
                                messageLatch.countDown();
                            }
                        });
                        session.getBasicRemote().sendText(SENT_MESSAGE);
                        System.out.println("Hello message sent.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, cec, getURI("/pathparam1/first/second/third"));
            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertEquals(SENT_MESSAGE + "first" + "second" + "third", receivedMessage);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint(value = "/servicepathparam")
    public static class ServiceEndpoint {

        @OnMessage
        public String onMessage(String message) {
            if (message.equals("PathParamTestBeanError")){
                if(PathParamTestBeanError.onErrorCalled && PathParamTestBeanError.onErrorThrowable != null){
                    return POSITIVE;
                }
            }

            return NEGATIVE;
        }
    }

    @ServerEndpoint(value = "/pathparam2/{one}/{two}/")
    public static class PathParamTestBeanError {

        public static boolean onErrorCalled = false;
        public static Throwable onErrorThrowable = null;

        @OnMessage
        public String doThat2(@PathParam("one") String one,
                             @PathParam("two") Integer two,
                             String message, Session peer) {

            assertNotNull(one);
            assertNotNull(two);
            assertNotNull(message);
            assertNotNull(peer);

            return message + one + two;
        }

        @OnError
        public void onError(Throwable t) {
            onErrorCalled = true;
            onErrorThrowable = t;
        }
    }

    @Test
    public void testPathParamError() throws DeploymentException {
        Server server = startServer(PathParamTestBeanError.class, ServiceEndpoint.class);

        try {
            messageLatch = new CountDownLatch(1);

            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                receivedMessage = message;
                                messageLatch.countDown();
                            }
                        });
                        session.getBasicRemote().sendText(SENT_MESSAGE);
                        System.out.println("Hello message sent.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }, cec, getURI("/pathparam2/first/second/"));
            messageLatch.await(1, TimeUnit.SECONDS);
            testViaServiceEndpoint(client, ServiceEndpoint.class, POSITIVE, "PathParamTestBeanError");

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint(value = "/pathparam3/{first}/{second}/")
    public static class PathParamTestBeanErrorNotPrimitive {

        @OnMessage
        public String doThat3(@PathParam("first") String first,
                             @PathParam("second") PathParamTest second,
                             String message, Session peer) {

            return message + first + second;
        }
    }

    @Test
    public void testPathParamErrorNotPrimitive() throws DeploymentException {
        boolean exceptionThrown = false;
        Server server = null;

        try {
            server = startServer(PathParamTestBeanErrorNotPrimitive.class);

        } catch (Exception e) {
            exceptionThrown = true;
        } finally {
            stopServer(server);
            assertEquals(true, exceptionThrown);
        }
    }


    @ServerEndpoint(value = "/pathparam4/{one}/{second}/{third}/{fourth}/{fifth}/{sixth}/{seventh}/{eighth}")
    public static class PathParamTestEndpointPrimitiveBoxing {

        @OnMessage
        public String doThat4(@PathParam("one") String one,
                             @PathParam("second") Integer second,
                             @PathParam("third") Boolean third,
                             @PathParam("fourth") Long fourth,
                             @PathParam("fifth") Float fifth,
                             @PathParam("sixth") Double sixth,
                             @PathParam("seventh") Character seventh,
                             @PathParam("eighth") Byte eighth,
                             String message, Session peer) {

            if (one != null && second != null && third != null && fourth != null && fifth != null && sixth != null && seventh != null && eighth != null && message != null && peer != null) {
                return message + one + second + third + fourth + fifth + sixth + seventh + eighth;
            } else {
                return "Error";
            }
        }
    }

    @ServerEndpoint(value = "/pathparam5/{first}/{second}/{third}/{fourth}/{fifth}/{sixth}/{seventh}/{eighth}")
    public static class PathParamTestEndpointPrimitives {

        @OnMessage
        public String doThat5(@PathParam("first") String first,
                             @PathParam("second") int second,
                             @PathParam("third") boolean third,
                             @PathParam("fourth") long fourth,
                             @PathParam("fifth") float fifth,
                             @PathParam("sixth") double sixth,
                             @PathParam("seventh") char seventh,
                             @PathParam("eighth") byte eighth,
                             String message, Session peer) {
            if(message != null && peer != null){
                return message + first + second + third + fourth + fifth + sixth + seventh + eighth;
            }else{
                return "Error";
            }
        }

        @OnError
        public void onError(Throwable t) {
            t.printStackTrace();
        }
    }

    @Test
    public void testPathParamPrimitives() throws DeploymentException {
        testPathParamPrimitive(PathParamTestEndpointPrimitives.class, getURI("/pathparam5/first/2/true/4/5/6/c/0"));
    }

    @Test
    public void testPathParamPrimitivesBoxing() throws DeploymentException {
        testPathParamPrimitive(PathParamTestEndpointPrimitiveBoxing.class, getURI("/pathparam4/first/2/true/4/5/6/c/0"));
    }

    public void testPathParamPrimitive(Class<?> testedClass, URI uri) throws DeploymentException {
        Server server = startServer(testedClass);

        try {
            messageLatch = new CountDownLatch(1);

            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager client = ClientManager.createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                receivedMessage = message;
                                messageLatch.countDown();

                            }
                        });
                        session.getBasicRemote().sendText(SENT_MESSAGE);
                        System.out.println("Hello message sent.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, cec, uri);
            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertEquals(SENT_MESSAGE + "first" + "2" + "true" + "4" + "5.0" + "6.0" + "c" + "0", receivedMessage);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }


}
