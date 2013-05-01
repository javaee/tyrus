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

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.ContainerProvider;
import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import org.glassfish.tyrus.server.Server;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class EncoderDecoderLifecycleTest {

    public static class MyType {
        public final String s;

        public MyType(String s) {
            this.s = s;
        }

        public MyType() {
            this.s = "";
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("MyType");
            sb.append("{s='").append(s).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }

    public static class MyEncoder implements Encoder.Text<MyType> {
        public static final Set<MyEncoder> instances = new HashSet<MyEncoder>();

        public boolean initialized = false;
        public boolean destroyed = false;

        @Override
        public String encode(MyType object) throws EncodeException {
            instances.add(this);

            System.out.println("### MyEncoder encode(" + object + ")");
            return object.s;
        }

        @Override
        public void init(EndpointConfig config) {
            if (config != null) {
                initialized = true;
            }
        }

        @Override
        public void destroy() {
            destroyed = true;
            destroyCallLatch.countDown();
        }
    }

    public static class MyDecoder implements Decoder.Text<MyType> {
        public static final Set<MyDecoder> instances = new HashSet<MyDecoder>();

        public boolean initialized = false;
        public boolean destroyed = false;

        @Override
        public boolean willDecode(String s) {
            return true;
        }

        @Override
        public MyType decode(String s) throws DecodeException {
            instances.add(this);

            System.out.println("### MyDecoder decode(" + s + ")");
            return new MyType(s);
        }

        @Override
        public void init(EndpointConfig config) {
            if (config != null) {
                initialized = true;
            }
        }

        @Override
        public void destroy() {
            destroyed = true;
            destroyCallLatch.countDown();
        }
    }

    @ServerEndpoint(value = "/myEndpoint",
            encoders = {EncoderDecoderLifecycleTest.MyEncoder.class},
            decoders = {EncoderDecoderLifecycleTest.MyDecoder.class})
    public static class MyEndpointAnnotated {

        @OnMessage
        public MyType onMessage(MyType message) {
            System.out.println("### MyEndpoint onMessage()");
            return message;
        }

        @OnError
        public void onError(Throwable t) {
            System.out.println("### MyEndpoint onError()");
            t.printStackTrace();
        }
    }

    public static class ClientEndpoint extends Endpoint implements MessageHandler.Whole<String> {
        @Override
        public void onOpen(Session session, EndpointConfig config) {
            session.addMessageHandler(this);

            try {
                System.out.println("### ClientEndpoint onOpen()");
                session.getBasicRemote().sendText("test");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onMessage(String message) {
            System.out.println("### ClientEndpoint onMessage()");
            messageLatch.countDown();
        }

        @Override
        public void onError(Session session, Throwable thr) {
            System.out.println("### ClientEndpoint onError()");
            thr.printStackTrace();
        }
    }

    static CountDownLatch messageLatch;
    static volatile CountDownLatch destroyCallLatch;

    // encoders/decoders per session
    @Test
    public void testAnnotated() {
        cleanup();

        final Server server = new Server(MyEndpointAnnotated.class);

        try {
            server.start();
            messageLatch = new CountDownLatch(1);

            WebSocketContainer client = ContainerProvider.getWebSocketContainer();
            Session session = client.connectToServer(ClientEndpoint.class, ClientEndpointConfig.Builder.create().build(),
                    new URI("ws://localhost:8025/websockets/tests/myEndpoint"));

            messageLatch.await(5, TimeUnit.SECONDS);

            assertEquals(0, messageLatch.getCount());
            assertEquals(1, MyDecoder.instances.size());
            assertEquals(1, MyEncoder.instances.size());

            for (MyDecoder decoder : MyDecoder.instances) {
                assertTrue(decoder.initialized);
                assertFalse(decoder.destroyed);
            }
            for (MyEncoder encoder : MyEncoder.instances) {
                assertTrue(encoder.initialized);
                assertFalse(encoder.destroyed);
            }

            messageLatch = new CountDownLatch(1);
            session.getBasicRemote().sendText("test");
            messageLatch.await(5, TimeUnit.SECONDS);

            assertEquals(1, MyDecoder.instances.size());
            assertEquals(1, MyEncoder.instances.size());

            destroyCallLatch = new CountDownLatch(2);
            session.close();
            destroyCallLatch.await(5, TimeUnit.SECONDS);


            for (MyDecoder decoder : MyDecoder.instances) {
                assertTrue(decoder.initialized);
                assertTrue(decoder.destroyed);
            }
            for (MyEncoder encoder : MyEncoder.instances) {
                assertTrue(encoder.initialized);
                assertTrue(encoder.destroyed);
            }

            messageLatch = new CountDownLatch(1);
            session = client.connectToServer(ClientEndpoint.class, ClientEndpointConfig.Builder.create().build(),
                    new URI("ws://localhost:8025/websockets/tests/myEndpoint"));
            messageLatch.await(5, TimeUnit.SECONDS);

            assertEquals(0, messageLatch.getCount());
            assertEquals(2, MyDecoder.instances.size());
            assertEquals(2, MyEncoder.instances.size());

            destroyCallLatch = new CountDownLatch(4);
            session.close();
            destroyCallLatch.await(4, TimeUnit.SECONDS);


            for (MyDecoder decoder : MyDecoder.instances) {
                assertTrue(decoder.initialized);
                assertTrue(decoder.destroyed);
            }
            for (MyEncoder encoder : MyEncoder.instances) {
                assertTrue(encoder.initialized);
                assertTrue(encoder.destroyed);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            server.stop();
        }
    }

    public static class MyEndpointProgrammatic extends Endpoint implements MessageHandler.Whole<MyType> {

        private Session session;

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            System.out.println("### MyEndpointProgrammatic onOpen()");

            this.session = session;
            session.addMessageHandler(this);
        }

        @Override
        public void onMessage(MyType message) {
            System.out.println("### MyEndpointProgrammatic onMessage() " + session);

            try {
                session.getBasicRemote().sendObject(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onError(Session session, Throwable thr) {
            System.out.println("### MyEndpointProgrammatic onError()");
            thr.printStackTrace();
        }
    }

    public static class MyApplicationConfiguration implements ServerApplicationConfig {
        @Override
        public Set<ServerEndpointConfig> getEndpointConfigs(Set<Class<? extends Endpoint>> scanned) {
            return new HashSet<ServerEndpointConfig>() {{
                add(ServerEndpointConfig.Builder.create(MyEndpointProgrammatic.class, "/myEndpoint").
                        decoders(Arrays.<Class<? extends Decoder>>asList(MyDecoder.class)).
                        encoders(Arrays.<Class<? extends Encoder>>asList(MyEncoder.class)).build());
            }};
        }

        @Override
        public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> scanned) {
            return Collections.emptySet();
        }
    }

    // encoders/decoders per session
    @Test
    public void testProgrammatic() {
        cleanup();

        final Server server = new Server(MyApplicationConfiguration.class);

        try {
            server.start();
            messageLatch = new CountDownLatch(1);

            WebSocketContainer client = ContainerProvider.getWebSocketContainer();
            Session session = client.connectToServer(ClientEndpoint.class, ClientEndpointConfig.Builder.create().build(),
                    new URI("ws://localhost:8025/websockets/tests/myEndpoint"));

            messageLatch.await(5, TimeUnit.SECONDS);

            assertEquals(0, messageLatch.getCount());
            assertEquals(1, MyDecoder.instances.size());
            assertEquals(1, MyEncoder.instances.size());

            for (MyDecoder decoder : MyDecoder.instances) {
                assertTrue(decoder.initialized);
                assertFalse(decoder.destroyed);
            }
            for (MyEncoder encoder : MyEncoder.instances) {
                assertTrue(encoder.initialized);
                assertFalse(encoder.destroyed);
            }

            messageLatch = new CountDownLatch(1);
            session.getBasicRemote().sendText("test");
            messageLatch.await(5, TimeUnit.SECONDS);

            assertEquals(1, MyDecoder.instances.size());
            assertEquals(1, MyEncoder.instances.size());

            destroyCallLatch = new CountDownLatch(2);
            session.close();
            destroyCallLatch.await(5, TimeUnit.SECONDS);


            for (MyDecoder decoder : MyDecoder.instances) {
                assertTrue(decoder.initialized);
                assertTrue(decoder.destroyed);
            }
            for (MyEncoder encoder : MyEncoder.instances) {
                assertTrue(encoder.initialized);
                assertTrue(encoder.destroyed);
            }

            messageLatch = new CountDownLatch(1);
            session = client.connectToServer(ClientEndpoint.class, ClientEndpointConfig.Builder.create().build(),
                    new URI("ws://localhost:8025/websockets/tests/myEndpoint"));
            messageLatch.await(5, TimeUnit.SECONDS);

            assertEquals(0, messageLatch.getCount());
            assertEquals(2, MyDecoder.instances.size());
            assertEquals(2, MyEncoder.instances.size());

            destroyCallLatch = new CountDownLatch(4);
            session.close();
            destroyCallLatch.await(4, TimeUnit.SECONDS);


            for (MyDecoder decoder : MyDecoder.instances) {
                assertTrue(decoder.initialized);
                assertTrue(decoder.destroyed);
            }
            for (MyEncoder encoder : MyEncoder.instances) {
                assertTrue(encoder.initialized);
                assertTrue(encoder.destroyed);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            server.stop();
        }
    }

    private void cleanup() {
        MyDecoder.instances.clear();
        MyEncoder.instances.clear();
        messageLatch = new CountDownLatch(1);
    }
}
