/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2015 Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class Tyrus109Test extends TestContainer {

    @ServerEndpoint("/open109")
    public static class OnOpenErrorTestEndpoint {
        public static volatile Throwable throwable;
        public static volatile Session session;
        public static CountDownLatch errorLatch = new CountDownLatch(1);

        @OnOpen
        public void open() {
            throw new RuntimeException("testException");
        }

        @OnMessage
        public String message(String message, Session session) {
            // won't be called.
            return message;
        }

        @OnError
        public void handleError(Throwable throwable, Session session) {
            OnOpenErrorTestEndpoint.throwable = throwable;
            OnOpenErrorTestEndpoint.session = session;
            errorLatch.countDown();
            throw new RuntimeException(throwable);
        }
    }

    @ServerEndpoint("/tyrus-109-test-service-endpoint")
    public static class ServiceEndpoint {

        @OnMessage
        public String onMessage(String message) throws InterruptedException {
            if ("throwable".equals(message)) {
                if (OnOpenErrorTestEndpoint.throwable != null) {
                    return POSITIVE;
                }
            }
            if ("session".equals(message)) {
                if (OnOpenErrorTestEndpoint.session != null) {
                    return POSITIVE;
                }
            }
            if ("errorLatch".equals(message)) {
                if (OnOpenErrorTestEndpoint.errorLatch.await(1, TimeUnit.SECONDS)) {
                    return POSITIVE;
                }
            }
            if ("exceptionMessage".equals(message)) {
                if (OnOpenErrorTestEndpoint.throwable.getMessage().equals("testException")) {
                    return POSITIVE;
                }
            }
            return NEGATIVE;
        }
    }

    @Test
    public void testErrorOnOpen() throws DeploymentException {
        Server server = startServer(OnOpenErrorTestEndpoint.class, ServiceEndpoint.class);

        try {
            final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

            final CountDownLatch closeLatch = new CountDownLatch(1);
            ClientManager client = createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig configuration) {
                    // do nothing
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    closeLatch.countDown();
                }
            }, cec, getURI(OnOpenErrorTestEndpoint.class));

            closeLatch.await(1, TimeUnit.SECONDS);

            testViaServiceEndpoint(client, ServiceEndpoint.class, POSITIVE, "errorLatch");
            testViaServiceEndpoint(client, ServiceEndpoint.class, POSITIVE, "session");
            testViaServiceEndpoint(client, ServiceEndpoint.class, POSITIVE, "throwable");
            testViaServiceEndpoint(client, ServiceEndpoint.class, POSITIVE, "exceptionMessage");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }
}
