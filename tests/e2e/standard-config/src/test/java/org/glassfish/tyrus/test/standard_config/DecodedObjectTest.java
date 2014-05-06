/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2014 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.test.standard_config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.core.coder.CoderAdapter;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.standard_config.bean.TestEndpoint;
import org.glassfish.tyrus.test.standard_config.message.StringContainer;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests the decoding and message handling of custom object.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class DecodedObjectTest extends TestContainer {

    private CountDownLatch messageLatch;

    private static String receivedMessage;
    private final static String receivedTextMessage = null;
    private static final String SENT_MESSAGE = "hello";

    @Test
    public void testSimpleDecoder() throws DeploymentException {

        Server server = startServer(TestEndpoint.class);

        try {
            messageLatch = new CountDownLatch(1);
            ArrayList<Class<? extends Decoder>> decoders = new ArrayList<Class<? extends Decoder>>();
            decoders.add(CustomDecoder.class);
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().decoders(decoders).build();

            ClientManager client = createClient();
            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new DecodedMessageHandler());
                        session.getBasicRemote().sendText(SENT_MESSAGE);
                        System.out.println("Sent message: " + SENT_MESSAGE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, cec, getURI(TestEndpoint.class));

            assertTrue(messageLatch.await(5, TimeUnit.SECONDS));
            assertTrue("The received message is the same as the sent one", receivedMessage.equals(SENT_MESSAGE));
            assertNull("The message was not received via the TextMessageHandler", receivedTextMessage);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testDecodeException() throws DeploymentException {

        Server server = startServer(TestEndpoint.class);

        try {
            messageLatch = new CountDownLatch(1);
            ArrayList<Class<? extends Decoder>> decoders = new ArrayList<Class<? extends Decoder>>();
            decoders.add(CustomDecoderThrowingDecodeException.class);
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().decoders(decoders).build();

            ClientManager client = createClient();
            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        session.addMessageHandler(new DecodedMessageHandler());
                        session.getBasicRemote().sendText(SENT_MESSAGE);
                        System.out.println("Sent message: " + SENT_MESSAGE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onError(Session session, Throwable thr) {
                    if (thr instanceof DecodeException) {
                        messageLatch.countDown();
                    }
                }
            }, cec, getURI(TestEndpoint.class));

            assertTrue(messageLatch.await(5, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testExtendedDecoded() throws DeploymentException {
        Server server = startServer(TestEndpoint.class);

        try {
            messageLatch = new CountDownLatch(1);
            ArrayList<Class<? extends Decoder>> decoders = new ArrayList<Class<? extends Decoder>>();
            decoders.add(ExtendedDecoder.class);
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().decoders(decoders).build();

            ClientManager client = createClient();
            client.connectToServer(new Endpoint() {

                @Override
                public void onOpen(Session session, EndpointConfig EndpointConfig) {
                    try {
                        System.out.println("#### onOpen Client side ####");
                        // session.addMessageHandler(new ObjectMessageHandler());
                        session.addMessageHandler(new DecodedMessageHandler());
                        session.getBasicRemote().sendText(SENT_MESSAGE);
                        System.out.println("Sent message: " + SENT_MESSAGE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, cec, getURI(TestEndpoint.class));

            assertTrue(messageLatch.await(5, TimeUnit.SECONDS));
            assertTrue("The received message is the same as the sent one", receivedMessage.equals("Extended " + SENT_MESSAGE));
            assertNull("The message was not received via the TextMessageHandler", receivedTextMessage);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    public static class CustomDecoder extends CoderAdapter implements Decoder.Text<StringContainer> {

        @Override
        public StringContainer decode(String s) throws DecodeException {
            return new StringContainer(s);
        }

        @Override
        public boolean willDecode(String s) {
            return true;
        }
    }

    public static class CustomDecoderThrowingDecodeException extends CoderAdapter implements Decoder.Text<StringContainer> {

        @Override
        public StringContainer decode(String s) throws DecodeException {
            System.out.println(CustomDecoderThrowingDecodeException.class.getName());
            throw new DecodeException(s, s);
        }

        @Override
        public boolean willDecode(String s) {
            return true;
        }
    }

    public static class ExtendedDecoder extends CoderAdapter implements Decoder.Text<ExtendedStringContainer> {

        @Override
        public ExtendedStringContainer decode(String s) throws DecodeException {
            return new ExtendedStringContainer(s);
        }

        @Override
        public boolean willDecode(String s) {
            return true;
        }
    }

    public static class ExtendedStringContainer extends StringContainer {
        public ExtendedStringContainer(String string) {
            super("Extended " + string);
        }
    }

    class DecodedMessageHandler implements MessageHandler.Whole<StringContainer> {

        @Override
        public void onMessage(StringContainer customObject) {
            System.out.println("### DecodedMessageHandler ### " + customObject.getString());
            DecodedObjectTest.receivedMessage = customObject.getString();
            messageLatch.countDown();
        }
    }
}
