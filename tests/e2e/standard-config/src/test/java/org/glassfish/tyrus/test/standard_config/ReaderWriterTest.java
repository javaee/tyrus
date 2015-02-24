/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2015 Oracle and/or its affiliates. All rights reserved.
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
import java.io.Reader;
import java.io.Writer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.standard_config.bean.JAXBBean;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class ReaderWriterTest extends TestContainer {

    @ServerEndpoint("/readerWriter-reader")
    public static class ReaderEndpoint {
        @OnMessage
        public String onMessage(Reader reader) throws IOException, JAXBException {
            JAXBBean jaxbBean =
                    (JAXBBean) JAXBContext.newInstance(JAXBBean.class).createUnmarshaller().unmarshal(reader);
            reader.close();

            if (jaxbBean.string1.equals("test") && jaxbBean.string2.equals("bean")) {
                return "ok";
            }

            return null;
        }
    }

    @Test
    public void testClientWriterServerReader() throws DeploymentException {

        final CountDownLatch messageLatch = new CountDownLatch(1);
        Server server = startServer(ReaderEndpoint.class);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            final ClientManager client = createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                if (message.equals("ok")) {
                                    messageLatch.countDown();
                                }
                            }
                        });

                        Writer sendWriter = session.getBasicRemote().getSendWriter();
                        JAXBContext.newInstance(JAXBBean.class).createMarshaller()
                                   .marshal(new JAXBBean("test", "bean"), sendWriter);
                        sendWriter.close();

                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (JAXBException e) {
                        e.printStackTrace();
                    }
                }
            }, cec, getURI(ReaderEndpoint.class));

            assertTrue(messageLatch.await(3, TimeUnit.SECONDS));

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    @ServerEndpoint("/readerWriter-writer")
    public static class WriterEndpoint {

        @OnOpen
        public void onOpen(Session session) throws JAXBException, IOException {
            Writer writer = session.getBasicRemote().getSendWriter();
            JAXBContext.newInstance(JAXBBean.class).createMarshaller().marshal(new JAXBBean("test", "bean"), writer);
            writer.close();
        }
    }

    @Test
    public void testClientReaderServerWriter() throws DeploymentException {

        final CountDownLatch messageLatch = new CountDownLatch(1);
        Server server = startServer(WriterEndpoint.class);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            final ClientManager client = createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    session.addMessageHandler(new MessageHandler.Whole<Reader>() {
                        @Override
                        public void onMessage(Reader reader) {
                            try {
                                JAXBBean jaxbBean =
                                        (JAXBBean) JAXBContext.newInstance(JAXBBean.class).createUnmarshaller()
                                                              .unmarshal(reader);
                                if (jaxbBean.string1.equals("test") && jaxbBean.string2.equals("bean")) {
                                    messageLatch.countDown();
                                }
                                reader.close();
                            } catch (JAXBException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                        }
                    });
                }
            }, cec, getURI(WriterEndpoint.class));

            assertTrue(messageLatch.await(3, TimeUnit.SECONDS));

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }
}
