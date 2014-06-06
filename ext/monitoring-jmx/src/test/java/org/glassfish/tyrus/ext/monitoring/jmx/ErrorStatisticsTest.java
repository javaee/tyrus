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
package org.glassfish.tyrus.ext.monitoring.jmx;

import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.core.monitoring.ApplicationEventListener;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

/**
 * Tests that errors are counted correctly.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class ErrorStatisticsTest extends TestContainer {

    @Test
    public void monitoringOnEndpointLevelTest() {
        test(false);
    }

    @Test
    public void monitoringOnSessionLevelTest() {
        test(true);
    }

    private void test(boolean monitorOnSessionLevel) {
        Server server = null;
        try {
            setContextPath("/errorTestApp");

            ApplicationMonitor applicationMonitor;
            if (monitorOnSessionLevel) {
                applicationMonitor = new SessionAwareApplicationMonitor();
            } else {
                applicationMonitor = new SessionlessApplicationMonitor();
            }

            CountDownLatch errorCountDownLatch = new CountDownLatch(8);

            ApplicationEventListener applicationEventListener = new TestApplicationEventListener(applicationMonitor, null, null, null, null, errorCountDownLatch);
            getServerProperties().put(ApplicationEventListener.APPLICATION_EVENT_LISTENER, applicationEventListener);
            server = startServer(ApplicationConfig.class);

            ClientManager client = createClient();

            Session session = client.connectToServer(AnnotatedClientEndpoint.class, getURI(AnnotatedServerEndpoint.class));
            session.getBasicRemote().sendText("Hello");
            session.getBasicRemote().sendBinary(ByteBuffer.wrap("Hello".getBytes()));
            session.close();

            Session session2 = client.connectToServer(AnnotatedClientEndpoint.class, getURI("/programmaticEndpoint"));
            session2.getBasicRemote().sendText("Hello");
            session2.getBasicRemote().sendBinary(ByteBuffer.wrap("Hello".getBytes()));
            session2.close();

            assertTrue(errorCountDownLatch.await(1, TimeUnit.SECONDS));

            String applicationMXBeanName = "org.glassfish.tyrus:type=/errorTestApp";
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            ApplicationMXBean applicationMXBean = JMX.newMXBeanProxy(mBeanServer, new ObjectName(applicationMXBeanName), ApplicationMXBean.class);

            Map<String, Long> errorMap = new HashMap<String, Long>();
            for (ErrorCount error : applicationMXBean.getErrorCounts()) {
                errorMap.put(error.getThrowableClassName(), error.getCount());
            }

            long onOpenCount = errorMap.get(OnOpenException.class.getName());
            assertEquals(2, onOpenCount);

            long onTextCount = errorMap.get(OnTextException.class.getName());
            assertEquals(2, onTextCount);

            long onBinaryCount = errorMap.get(OnBinaryException.class.getName());
            assertEquals(2, onBinaryCount);

            long onCloseCount = errorMap.get(OnCloseException.class.getName());
            assertEquals(2, onCloseCount);

        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
        }
    }

    public static class ApplicationConfig implements ServerApplicationConfig {

        @Override
        public Set<ServerEndpointConfig> getEndpointConfigs(Set<Class<? extends Endpoint>> endpointClasses) {
            return new HashSet<ServerEndpointConfig>() {{
                add(ServerEndpointConfig.Builder.create(ProgrammaticServerEndpoint.class, "/programmaticEndpoint").build());
            }};
        }

        @Override
        public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> scanned) {
            return new HashSet<Class<?>>(Collections.singleton(AnnotatedServerEndpoint.class));
        }

        public static class ProgrammaticServerEndpoint extends Endpoint {

            @Override
            public void onOpen(final Session session, final EndpointConfig EndpointConfig) {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(String message) {
                        throw new OnTextException();
                    }
                });
                session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {

                    @Override
                    public void onMessage(ByteBuffer message) {
                        throw new OnBinaryException();
                    }
                });
                throw new OnOpenException();
            }

            @Override
            public void onClose(Session session, CloseReason closeReason) {
                throw new OnCloseException();
            }

            @Override
            public void onError(Session session, Throwable thr) {
                // do nothing - keep so that exceptions will not be logged.
            }
        }
    }

    public static class OnTextException extends RuntimeException {
    }

    public static class OnBinaryException extends RuntimeException {
    }

    public static class OnOpenException extends RuntimeException {
    }

    public static class OnCloseException extends RuntimeException {
    }

    @ServerEndpoint("/annotatedEndpoint")
    public static class AnnotatedServerEndpoint {

        @OnOpen
        public void onOpen(Session session) {
            throw new OnOpenException();
        }

        @OnMessage
        public void onTextMessage(String message, Session session) {
            throw new OnTextException();
        }

        @OnMessage
        public void onBinaryMessage(ByteBuffer message, Session session) {
            throw new OnBinaryException();
        }

        @OnClose
        public void onClose() {
            throw new OnCloseException();
        }

        @OnError
        public void onError(Throwable t) {
            // do nothing - keep so that exceptions will not be logged.
        }
    }

    @ClientEndpoint
    public static class AnnotatedClientEndpoint {
    }
}
