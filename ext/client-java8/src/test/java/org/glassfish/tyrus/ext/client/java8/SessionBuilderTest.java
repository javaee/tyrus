/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.ext.client.java8;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.DeploymentException;
import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.core.coder.CoderAdapter;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class SessionBuilderTest extends TestContainer {

    @ServerEndpoint("/sessionBuilderTest")
    public static class SessionBuilderTestEndpoint {

        @OnMessage
        public String onMessage(Session session, String message) {
            return message;
        }

        @OnMessage
        public byte[] onMessage(byte[] message) {
            return message;
        }
    }

    private static final String MESSAGE = "I find your lack of faith disturbing";

    @Test
    public void testEcho() throws IOException, DeploymentException, InterruptedException {
        Server server = startServer(SessionBuilderTestEndpoint.class);

        CountDownLatch messageLatch = new CountDownLatch(1);

        try {
            Session session = new SessionBuilder()
                    .uri(getURI(SessionBuilderTestEndpoint.class))
                    .messageHandler(String.class,
                                    message -> {
                                        if (MESSAGE.equals(message)) {
                                            messageLatch.countDown();
                                        }
                                    })
                    .connect();

            session.getBasicRemote().sendText(MESSAGE);

            assertTrue(messageLatch.await(3, TimeUnit.SECONDS));
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testEchoPartial() throws IOException, DeploymentException, InterruptedException {
        Server server = startServer(SessionBuilderTestEndpoint.class);

        CountDownLatch messageLatch = new CountDownLatch(1);

        try {
            Session session = new SessionBuilder()
                    .uri(getURI(SessionBuilderTestEndpoint.class))
                    .messageHandlerPartial(String.class,
                                           (message, complete) -> {
                                               System.out.println("partial: " + message + " " + complete);

                                               if (MESSAGE.equals(message) && complete) {
                                                   messageLatch.countDown();
                                               }
                                           })
                    .connect();

            session.getBasicRemote().sendText(MESSAGE);

            assertTrue(messageLatch.await(30000000, TimeUnit.SECONDS));
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testEchoBinary() throws IOException, DeploymentException, InterruptedException {
        Server server = startServer(SessionBuilderTestEndpoint.class);

        CountDownLatch messageLatch = new CountDownLatch(1);

        try {
            Session session = new SessionBuilder()
                    .uri(getURI(SessionBuilderTestEndpoint.class))
                    .messageHandler(byte[].class,
                                    message -> {
                                        if (MESSAGE.equals(new String(message))) {
                                            messageLatch.countDown();
                                        }
                                    })
                    .connect();

            session.getBasicRemote().sendBinary(ByteBuffer.wrap(MESSAGE.getBytes()));

            assertTrue(messageLatch.await(3, TimeUnit.SECONDS));
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testEchoAsync() throws IOException, DeploymentException, InterruptedException {
        Server server = startServer(SessionBuilderTestEndpoint.class);

        CountDownLatch messageLatch = new CountDownLatch(1);

        try {
            CompletableFuture<Session> sessionCompletableFuture = new SessionBuilder()
                    .uri(getURI(SessionBuilderTestEndpoint.class))
                    .messageHandler(String.class,
                                    message -> {
                                        if (MESSAGE.equals(message)) {
                                            messageLatch.countDown();
                                        }
                                    })
                    .connectAsync();

            sessionCompletableFuture.thenApply(new Function<Session, Session>() {
                @Override
                public Session apply(Session session) {
                    try {
                        session.getBasicRemote().sendText(MESSAGE);
                    } catch (IOException ignored) {
                    }
                    return session;
                }
            });


            assertTrue(messageLatch.await(3, TimeUnit.SECONDS));
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testEchoAsyncCustomES() throws IOException, DeploymentException, InterruptedException {
        Server server = startServer(SessionBuilderTestEndpoint.class);

        CountDownLatch messageLatch = new CountDownLatch(1);

        try {
            CompletableFuture<Session> sessionCompletableFuture = new SessionBuilder()
                    .uri(getURI(SessionBuilderTestEndpoint.class))
                    .messageHandler(String.class,
                                    message -> {
                                        if (MESSAGE.equals(message)) {
                                            messageLatch.countDown();
                                        }
                                    })
                    .connectAsync(Executors.newCachedThreadPool());

            sessionCompletableFuture.thenApply(new Function<Session, Session>() {
                @Override
                public Session apply(Session session) {
                    try {
                        session.getBasicRemote().sendText(MESSAGE);
                    } catch (IOException ignored) {
                    }
                    return session;
                }
            });


            assertTrue(messageLatch.await(3, TimeUnit.SECONDS));
        } finally {
            stopServer(server);
        }
    }

    public static class AClass {
        @Override
        public String toString() {
            return MESSAGE;
        }
    }

    public static class AClassCoder extends CoderAdapter implements Encoder.Text<AClass>, Decoder.Text<AClass> {

        @Override
        public String encode(AClass aClass) throws EncodeException {
            return aClass.toString();
        }

        @Override
        public AClass decode(String s) throws DecodeException {
            return new AClass();
        }

        @Override
        public boolean willDecode(String s) {
            return true;
        }
    }

    @ServerEndpoint("/sessionBuilderEncDecTest")
    public static class SessionBuilderEncDecTestEndpoint {

        @OnMessage
        public String onMessage(String message) {
            return message;
        }
    }

    @Test
    public void testEncoderDecoder() throws IOException, DeploymentException, InterruptedException, EncodeException {
        Server server = startServer(SessionBuilderEncDecTestEndpoint.class);

        CountDownLatch messageLatch = new CountDownLatch(1);

        final ClientEndpointConfig clientEndpointConfig =
                ClientEndpointConfig.Builder.create()
                                            .encoders(Collections.singletonList(AClassCoder.class))
                                            .decoders(Collections.singletonList(AClassCoder.class))
                                            .build();

        try {
            Session session = new SessionBuilder()
                    .uri(getURI(SessionBuilderEncDecTestEndpoint.class))
                    .clientEndpointConfig(clientEndpointConfig)
                    .messageHandler(AClass.class,
                                    aClass -> {
                                        if (MESSAGE.equals(aClass.toString())) {
                                            messageLatch.countDown();
                                        }
                                    })
                    .connect();

            session.getBasicRemote().sendObject(new AClass());

            assertTrue(messageLatch.await(3, TimeUnit.SECONDS));
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testAllMethods() throws IOException, DeploymentException, InterruptedException, EncodeException {
        Server server = startServer(SessionBuilderEncDecTestEndpoint.class);

        CountDownLatch messageLatch = new CountDownLatch(1);
        CountDownLatch onOpenLatch = new CountDownLatch(1);
        CountDownLatch onCloseLatch = new CountDownLatch(1);
        CountDownLatch onErrorLatch = new CountDownLatch(1);

        final ClientEndpointConfig clientEndpointConfig =
                ClientEndpointConfig.Builder.create()
                                            .encoders(Collections.singletonList(AClassCoder.class))
                                            .decoders(Collections.singletonList(AClassCoder.class))
                                            .build();

        try {
            Session session = new SessionBuilder()
                    .uri(getURI(SessionBuilderEncDecTestEndpoint.class))
                    .clientEndpointConfig(clientEndpointConfig)
                    .messageHandler(AClass.class,
                                    aClass -> {
                                        if (MESSAGE.equals(aClass.toString())) {
                                            messageLatch.countDown();
                                        }
                                    })
                    .onOpen((session1, endpointConfig) -> onOpenLatch.countDown())
                    .onError((session1, throwable) -> onErrorLatch.countDown())
                    .onClose((session1, closeReason) -> {
                        onCloseLatch.countDown();
                        throw new RuntimeException("onErrorTrigger");
                    })
                    .connect();

            session.getBasicRemote().sendObject(new AClass());

            assertTrue(onOpenLatch.await(3, TimeUnit.SECONDS));
            assertTrue(messageLatch.await(3, TimeUnit.SECONDS));

            session.close();

            assertTrue(onCloseLatch.await(3, TimeUnit.SECONDS));
            assertTrue(onErrorLatch.await(3, TimeUnit.SECONDS));

        } finally {
            stopServer(server);
        }
    }
}
