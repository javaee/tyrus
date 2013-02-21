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
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ContainerProvider;
import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.DefaultClientConfiguration;
import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.Extension;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import javax.websocket.WebSocketError;
import javax.websocket.WebSocketMessage;
import javax.websocket.server.DefaultServerConfiguration;
import javax.websocket.server.ServerApplicationConfiguration;
import javax.websocket.server.ServerEndpointConfiguration;
import javax.websocket.server.WebSocketEndpoint;

import org.glassfish.tyrus.server.Server;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

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
            return "MyType{" +
                    "s='" + s + '\'' +
                    '}';
        }
    }

    public static class MyEncoder implements Encoder.Text<MyType> {
        public static final Set<MyEncoder> instances = new HashSet<>();

        @Override
        public String encode(MyType object) throws EncodeException {
            instances.add(this);

            System.out.println("### MyEncoder encode(" + object + ")");
            return object.s;
        }
    }

    public static class MyDecoder implements Decoder.Text<MyType> {
        public static final Set<MyDecoder> instances = new HashSet<>();

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
    }

    @WebSocketEndpoint(value = "myEndpoint",
            encoders = {EncoderDecoderLifecycleTest.MyEncoder.class},
            decoders = {EncoderDecoderLifecycleTest.MyDecoder.class})
    public static class MyEndpointAnnotated {

        @WebSocketMessage
        public MyType onMessage(MyType message) {
            System.out.println("### MyEndpoint onMessage()");
            return message;
        }

        @WebSocketError
        public void onError(Throwable t) {
            System.out.println("### MyEndpoint onError()");
            t.printStackTrace();
        }
    }

    public static class ClientEndpoint extends Endpoint implements MessageHandler.Basic<String> {
        @Override
        public void onOpen(Session session, EndpointConfiguration config) {
            session.addMessageHandler(this);

            try {
                System.out.println("### ClientEndpoint onOpen()");
                session.getRemote().sendString("test");
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

    // encoders/decoders per session
    @Test
    public void testAnnotated() {
        cleanup();

        final Server server = new Server(MyEndpointAnnotated.class);

        try {
            server.start();
            messageLatch = new CountDownLatch(1);

            WebSocketContainer client = ContainerProvider.getWebSocketContainer();
            Session session = client.connectToServer(ClientEndpoint.class, new DefaultClientConfiguration(), new URI("ws://localhost:8025/websockets/tests/myEndpoint"));

            messageLatch.await(5, TimeUnit.SECONDS);

            assertEquals(0, messageLatch.getCount());
            assertEquals(1, MyDecoder.instances.size());
            assertEquals(1, MyEncoder.instances.size());

            messageLatch = new CountDownLatch(1);
            session.getRemote().sendString("test");
            messageLatch.await(5, TimeUnit.SECONDS);

            assertEquals(1, MyDecoder.instances.size());
            assertEquals(1, MyEncoder.instances.size());

            messageLatch = new CountDownLatch(1);
            client.connectToServer(ClientEndpoint.class, new DefaultClientConfiguration(), new URI("ws://localhost:8025/websockets/tests/myEndpoint"));
            messageLatch.await(5, TimeUnit.SECONDS);

            assertEquals(0, messageLatch.getCount());
            assertEquals(2, MyDecoder.instances.size());
            assertEquals(2, MyEncoder.instances.size());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            server.stop();
        }
    }

    public static class MyEndpointProgrammatic extends Endpoint implements MessageHandler.Basic<MyType> {

        private Session session;

        @Override
        public void onOpen(Session session, EndpointConfiguration config) {
            System.out.println("### MyEndpointProgrammatic onOpen()");

            this.session = session;
            session.addMessageHandler(this);
        }

        @Override
        public void onMessage(MyType message) {
            System.out.println("### MyEndpointProgrammatic onMessage() " + session);

            try {
                session.getRemote().sendObject(message);
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

    public static class MyEndpointProgrammaticConfiguration extends DefaultServerConfiguration {
        public MyEndpointProgrammaticConfiguration() {
            super(MyEndpointProgrammatic.class, "myEndpoint");
        }

        @Override
        public List<Encoder> getEncoders() {
            return Arrays.<Encoder>asList(new MyEncoder());
        }

        @Override
        public List<Decoder> getDecoders() {
            return Arrays.<Decoder>asList(new MyDecoder());
        }

        @Override
        public String getNegotiatedSubprotocol(List<String> requestedSubprotocols) {
            return "";
        }

        @Override
        public List<Extension> getNegotiatedExtensions(List<Extension> requestedExtensions) {
            return Collections.emptyList();
        }

        @Override
        public boolean checkOrigin(String originHeaderValue) {
            return true;
        }
    }

    public static class MyApplicationConfiguration implements ServerApplicationConfiguration {
        @Override
        public Set<Class<? extends ServerEndpointConfiguration>> getEndpointConfigurationClasses(Set<Class<? extends ServerEndpointConfiguration>> scanned) {
            return new HashSet<Class<? extends ServerEndpointConfiguration>>() {{
                add(MyEndpointProgrammaticConfiguration.class);
            }};
        }

        @Override
        public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> scanned) {
            return Collections.emptySet();
        }
    }

    // encoders/decoders singletons
    @Test
    public void testProgrammatic() {
        cleanup();

        final Server server = new Server(MyApplicationConfiguration.class);

        try {
            server.start();
            messageLatch = new CountDownLatch(1);

            WebSocketContainer client = ContainerProvider.getWebSocketContainer();
            Session session = client.connectToServer(ClientEndpoint.class, new DefaultClientConfiguration(), new URI("ws://localhost:8025/websockets/tests/myEndpoint"));

            messageLatch.await(5, TimeUnit.SECONDS);

            assertEquals(0, messageLatch.getCount());
            assertEquals(1, MyDecoder.instances.size());
            assertEquals(1, MyEncoder.instances.size());

            messageLatch = new CountDownLatch(1);
            session.getRemote().sendString("test");
            messageLatch.await(5, TimeUnit.SECONDS);

            assertEquals(1, MyDecoder.instances.size());
            assertEquals(1, MyEncoder.instances.size());

            messageLatch = new CountDownLatch(1);
            client.connectToServer(ClientEndpoint.class, new DefaultClientConfiguration(), new URI("ws://localhost:8025/websockets/tests/myEndpoint"));
            messageLatch.await(5, TimeUnit.SECONDS);

            assertEquals(0, messageLatch.getCount());
            assertEquals(1, MyDecoder.instances.size());
            assertEquals(1, MyEncoder.instances.size());
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
