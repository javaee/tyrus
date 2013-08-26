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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class OnCloseTest extends TestContainer {

    @ServerEndpoint(value = "/close2")
    public static class OnCloseEndpoint {
        public static Session session;

        @OnMessage
        public String message(String message, Session session) {
            try {
                session.close();
                return null;
            } catch (IOException e) {
                // do nothing.
            }
            return "message";
        }
    }

    @Test
    public void testOnClose() throws DeploymentException {
        Server server = startServer(OnCloseEndpoint.class);

        final CountDownLatch messageLatch = new CountDownLatch(1);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager.createClient().connectToServer(new TestEndpointAdapter() {
                @Override
                public EndpointConfig getEndpointConfig() {
                    return cec;
                }

                @Override
                public void onOpen(Session session) {
                    session.addMessageHandler(new TestTextMessageHandler(this));
                    try {
                        session.getBasicRemote().sendText("message");
                    } catch (IOException e) {
                        // do nothing.
                    }
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    if (closeReason != null && closeReason.getCloseCode().getCode() == 1000) {
                        messageLatch.countDown();
                    }
                }

                @Override
                public void onMessage(String message) {
                    // do nothing
                }
            }, cec, getURI(OnCloseEndpoint.class));

            messageLatch.await(1, TimeUnit.SECONDS);

            assertEquals(0L, messageLatch.getCount());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    static final String CUSTOM_REASON = "When nine hundred years old you reach, look as good, you will not, hmmm?";

    @ServerEndpoint(value = "/close3")
    public static class OnCloseWithCustomReasonEndpoint {
        public static Session session;
        public static volatile CloseReason closeReason;

        @OnMessage
        public String message(String message, Session session) {
            try {
                session.close(new CloseReason(new CloseReason.CloseCode() {
                    @Override
                    public int getCode() {
                        // custom close codes (4000-4999)
                        return 4000;
                    }
                }, CUSTOM_REASON));
                return null;
            } catch (IOException e) {
                // do nothing.
            }
            return "message";
        }

        @OnClose
        public void onClose(Session s, CloseReason c) {
            closeReason = c;
        }

        @OnError
        public void onError(Throwable t) {
            t.printStackTrace();
        }
    }

    @Test
    public void testOnCloseCustomCloseReasonServerInitiated() throws DeploymentException {
        Server server = startServer(OnCloseWithCustomReasonEndpoint.class);

        final CountDownLatch messageLatch = new CountDownLatch(1);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager.createClient().connectToServer(new TestEndpointAdapter() {
                @Override
                public EndpointConfig getEndpointConfig() {
                    return cec;
                }

                @Override
                public void onOpen(Session session) {
                    session.addMessageHandler(new TestTextMessageHandler(this));
                    try {
                        session.getBasicRemote().sendText("message");
                    } catch (IOException e) {
                        // do nothing.
                    }
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    if (closeReason != null &&
                            closeReason.getCloseCode().getCode() == 4000 &&
                            closeReason.getReasonPhrase().equals(CUSTOM_REASON)) {
                        messageLatch.countDown();
                    }
                }

                @Override
                public void onMessage(String message) {
                    // do nothing
                }
            }, cec, getURI(OnCloseWithCustomReasonEndpoint.class));

            messageLatch.await(5, TimeUnit.SECONDS);

            assertEquals(0L, messageLatch.getCount());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testOnCloseCustomCloseReasonClientInitiated() throws DeploymentException {
        Server server = startServer(OnCloseWithCustomReasonEndpoint.class, ServiceEndpoint.class);

        final CountDownLatch messageLatch = new CountDownLatch(1);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager client = ClientManager.createClient();
            client.connectToServer(new TestEndpointAdapter() {
                @Override
                public EndpointConfig getEndpointConfig() {
                    return cec;
                }

                @Override
                public void onOpen(Session session) {
                    session.addMessageHandler(new TestTextMessageHandler(this));
                    try {
                        session.close(new CloseReason(new CloseReason.CloseCode() {
                            @Override
                            public int getCode() {
                                // custom close codes (4000-4999)
                                return 4000;
                            }
                        }, CUSTOM_REASON));
                    } catch (IOException e) {
                        // do nothing.
                    } finally {
                        messageLatch.countDown();
                    }
                }

                @Override
                public void onMessage(String message) {
                    // do nothing
                }
            }, cec, getURI(OnCloseWithCustomReasonEndpoint.class));

            messageLatch.await(5, TimeUnit.SECONDS);
            Thread.sleep(1000);

            Session serviceSession = client.connectToServer(MyServiceClientEndpoint.class, getURI(ServiceEndpoint.class));
            MyServiceClientEndpoint.latch = new CountDownLatch(1);
            MyServiceClientEndpoint.receivedMessage = null;
            serviceSession.getBasicRemote().sendText("OnCloseWithCustomReasonEndpoint");
            MyServiceClientEndpoint.latch.await(1, TimeUnit.SECONDS);
            assertEquals(0, MyServiceClientEndpoint.latch.getCount());
            assertEquals(POSITIVE, MyServiceClientEndpoint.receivedMessage);


        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    static int[] supportedCloseReasons = {
            1000, 1001, 1002, 1003, 1007, 1008, 1009, 1010, 1011
    };

    @ServerEndpoint(value = "/close5")
    public static class OnCloseAllSupportedReasonsEndpoint {

        @OnMessage
        public String message(final Integer message, Session session) {
            try {
                session.close(new CloseReason(new CloseReason.CloseCode() {
                    @Override
                    public int getCode() {
                        // custom close codes (4000-4999)
                        return message;
                    }
                }, null));
                return null;
            } catch (IOException e) {
                // do nothing.
            }
            return "message";
        }

        @OnError
        public void onError(Throwable t) {
            t.printStackTrace();
        }
    }

    @Test
    public void testOnCloseServerInitiatedAll() throws DeploymentException {
        for (int i : supportedCloseReasons) {
            testOnCloseServerInitiated(i);
        }
    }

    public void testOnCloseServerInitiated(int supportedCode) throws DeploymentException {
        Server server = startServer(OnCloseAllSupportedReasonsEndpoint.class);

        // close codes 1000 - 1015

        final CountDownLatch messageLatch = new CountDownLatch(1);

        final int closeCode = supportedCode;
        System.out.println("### Testing CloseCode " + supportedCode);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            ClientManager.createClient().connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig endpointConfig) {
                    try {
                        session.getBasicRemote().sendObject(closeCode);
                    } catch (Exception e) {
                        // do nothing.
                    }
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    System.out.println("#### received: " + closeReason);
                    if (closeReason != null &&
                            closeReason.getCloseCode().getCode() == closeCode) {
                        messageLatch.countDown();
                    }
                }
            }, cec, getURI(OnCloseAllSupportedReasonsEndpoint.class));

            messageLatch.await(1, TimeUnit.SECONDS);

            assertEquals(0L, messageLatch.getCount());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint(value = "/close4")
    public static class OnCloseAllSupportedReasonsClientInitEndpoint {

        public static volatile CloseReason closeReason;

        @OnMessage
        public String message(final Integer message, Session session) {
            return "message";
        }

        @OnClose
        public void onClose(CloseReason closeReason, Session session) {
            OnCloseAllSupportedReasonsClientInitEndpoint.closeReason = closeReason;
        }

        @OnError
        public void onError(Throwable t) {
            t.printStackTrace();
        }
    }

    @Test
    public void testOnCloseClientInitiated() throws DeploymentException {

        // close codes 1000 - 1015
        for (int i : supportedCloseReasons) {
            Server server = startServer(OnCloseAllSupportedReasonsClientInitEndpoint.class, ServiceEndpoint.class);

            final int closeCode = i;
            System.out.println("### Testing CloseCode " + i);

            try {
                final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

                ClientManager client = ClientManager.createClient();
                client.connectToServer(new Endpoint() {
                    @Override
                    public void onOpen(Session session, EndpointConfig endpointConfig) {
                        try {
                            session.close(new CloseReason(new CloseReason.CloseCode() {
                                @Override
                                public int getCode() {
                                    // custom close codes (4000-4999)
                                    return closeCode;
                                }
                            }, null));
                        } catch (Exception e) {
                            // do nothing.
                        }
                    }
                }, cec, getURI(OnCloseAllSupportedReasonsClientInitEndpoint.class));

                Session serviceSession = client.connectToServer(MyServiceClientEndpoint.class, getURI(ServiceEndpoint.class));
                MyServiceClientEndpoint.latch = new CountDownLatch(1);
                MyServiceClientEndpoint.receivedMessage = null;
                serviceSession.getBasicRemote().sendText(String.valueOf(i));
                MyServiceClientEndpoint.latch.await(1, TimeUnit.SECONDS);
                assertEquals(0, MyServiceClientEndpoint.latch.getCount());
                assertEquals(POSITIVE, MyServiceClientEndpoint.receivedMessage);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e.getMessage(), e);
            } finally {
                stopServer(server);
            }
        }
    }

    @ServerEndpoint(value = "/serviceonclosetest")
    public static class ServiceEndpoint {

        @OnMessage
        public String message(String message, Session session) {
            if(message.equals("OnCloseWithCustomReasonEndpoint")){
                if (OnCloseWithCustomReasonEndpoint.closeReason != null &&
                        OnCloseWithCustomReasonEndpoint.closeReason.getCloseCode().getCode() == 4000 &&
                        OnCloseWithCustomReasonEndpoint.closeReason.getReasonPhrase().equals(CUSTOM_REASON)) {
                    return POSITIVE;
                }
            } else if(message.equals("DoubleCloseEndpoint")){
                if(DoubleCloseEndpoint.exceptionThrown){
                    return POSITIVE;
                }
            } else if(message.equals("SessionTestAllMethodsAfterCloseEndpoint")){
                if(SessionTestAllMethodsAfterCloseEndpoint.exceptionsThrown){
                    return POSITIVE;
                }
            } else { //testOnCloseClientInitiated, different test codes sent as a message
                if(OnCloseAllSupportedReasonsClientInitEndpoint.closeReason.getCloseCode().getCode() == Integer.parseInt(message)){
                    return POSITIVE;
                }
            }

            return NEGATIVE;
        }
    }

    @ServerEndpoint(value = "/close1")
    public static class DoubleCloseEndpoint {
        public static boolean exceptionThrown = false;

        @OnMessage
        public String message(String message, Session session) {
            try {
                session.close();
                session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "Normal closure."));
            } catch (IllegalStateException e) {
                exceptionThrown = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
            return "message";
        }
    }

    @Test
    public void testDoubleClose() throws DeploymentException {
        Server server = startServer(DoubleCloseEndpoint.class, ServiceEndpoint.class);

        final CountDownLatch messageLatch = new CountDownLatch(1);
        ClientManager client = ClientManager.createClient();

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    try {
                        session.getBasicRemote().sendText("message");
                    } catch (IOException e) {
                        // do nothing.
                    }
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    messageLatch.countDown();
                }
            }, cec, getURI(DoubleCloseEndpoint.class));

            messageLatch.await(2, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());

            testViaServiceEndpoint(client, ServiceEndpoint.class, POSITIVE, "DoubleCloseEndpoint");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint(value = "/close6")
    public static class SessionTestAllMethodsAfterCloseEndpoint {
        public static boolean exceptionsThrown = false;

        @OnMessage
        public String message(String message, Session session) {
            boolean checker = true;
            boolean thrown = false;

            try {
                session.close();
                session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "Normal closure."));
            } catch (IllegalStateException e) {
                exceptionsThrown = true;
                thrown = true;
            } catch (IOException e) {
                e.printStackTrace();
            }

            checker &= thrown;
            thrown = false;
            try{
                session.getAsyncRemote();
            }catch (IllegalStateException e){
                thrown = true;
            }

            checker &= thrown;
            thrown = false;
            try{
                session.getBasicRemote();
            }catch (IllegalStateException e){
                thrown = true;
            }

            checker &= thrown;
            thrown = false;
            try{
                session.addMessageHandler(null);
            }catch (IllegalStateException e){
                thrown = true;
            }

            checker &= thrown;
            thrown = false;
            try{
                session.getContainer();
            }catch (IllegalStateException e){
                thrown = true;
            }

            checker &= thrown;
            thrown = false;
            try{
                session.getId();
            }catch (IllegalStateException e){
                thrown = true;
            }

            checker &= thrown;
            thrown = false;
            try{
                session.getMaxBinaryMessageBufferSize();
            }catch (IllegalStateException e){
                thrown = true;
            }

            checker &= thrown;
            thrown = false;
            try{
                session.getMaxIdleTimeout();
            }catch (IllegalStateException e){
                thrown = true;
            }

            checker &= thrown;
            thrown = false;
            try{
                session.getNegotiatedExtensions();
            }catch (IllegalStateException e){
                thrown = true;
            }

            checker &= thrown;
            thrown = false;
            try{
                session.getMessageHandlers();
            }catch (IllegalStateException e){
                thrown = true;
            }

            checker &= thrown;
            thrown = false;
            try{
                session.getMaxTextMessageBufferSize();
            }catch (IllegalStateException e){
                thrown = true;
            }

            checker &= thrown;
            thrown = false;
            try{
                session.getNegotiatedSubprotocol();
            }catch (IllegalStateException e){
                thrown = true;
            }

            checker &= thrown;
            thrown = false;
            try{
                session.getOpenSessions();
            }catch (IllegalStateException e){
                thrown = true;
            }

            checker &= thrown;
            thrown = false;
            try{
                session.getPathParameters();
            }catch (IllegalStateException e){
                thrown = true;
            }

            checker &= thrown;
            thrown = false;
            try{
                session.getProtocolVersion();
            }catch (IllegalStateException e){
                thrown = true;
            }

            checker &= thrown;
            thrown = false;
            try{
                session.getQueryString();
            }catch (IllegalStateException e){
                thrown = true;
            }

            checker &= thrown;
            thrown = false;
            try{
                session.getRequestParameterMap();
            }catch (IllegalStateException e){
                thrown = true;
            }

            checker &= thrown;
            thrown = false;
            try{
                session.getRequestURI();
            }catch (IllegalStateException e){
                thrown = true;
            }

            checker &= thrown;
            thrown = false;
            try{
                session.getUserPrincipal();
            }catch (IllegalStateException e){
                thrown = true;
            }

            checker &= thrown;
            thrown = false;
            try{
                session.getUserProperties();
            }catch (IllegalStateException e){
                thrown = true;
            }

            checker &= thrown;
            thrown = false;
            try{
                session.isSecure();
            }catch (IllegalStateException e){
                thrown = true;
            }

            checker &= thrown;
            thrown = false;
            try{
                session.removeMessageHandler(null);
            }catch (IllegalStateException e){
                thrown = true;
            }

            checker &= thrown;
            thrown = false;
            try{
                session.setMaxBinaryMessageBufferSize(10);
            }catch (IllegalStateException e){
                thrown = true;
            }

            checker &= thrown;
            thrown = false;
            try{
                session.setMaxIdleTimeout(1);
            }catch (IllegalStateException e){
                thrown = true;
            }

            if(checker &= thrown){
                exceptionsThrown = true;
            }

            return "message";
        }
    }

    @Test
    public void testSessionAllMethodsAfterClose() throws DeploymentException {
        Server server = startServer(SessionTestAllMethodsAfterCloseEndpoint.class, ServiceEndpoint.class);

        final CountDownLatch messageLatch = new CountDownLatch(1);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();
            ClientManager client = ClientManager.createClient();

            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    try {
                        session.getBasicRemote().sendText("message");
                    } catch (IOException e) {
                        // do nothing.
                    }
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    messageLatch.countDown();
                }
            }, cec, getURI(SessionTestAllMethodsAfterCloseEndpoint.class));

            messageLatch.await(2, TimeUnit.SECONDS);
            assertEquals(0L, messageLatch.getCount());

            testViaServiceEndpoint(client, ServiceEndpoint.class, POSITIVE, "SessionTestAllMethodsAfterCloseEndpoint");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }
}
