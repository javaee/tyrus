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

package org.glassfish.tyrus.test.standard_config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.spi.UpgradeRequest;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests a situation when a handshake request contains the same header twice with different values.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class SameHeadersOnServerTest extends TestContainer {

    private static final String HEADER_KEY = "my-header";
    private static final String HEADER_VALUE_1 = "my-header-value-1";
    private static final String HEADER_VALUE_2 = "my-header-value-2";

    @Test
    public void testSameHeaderNamesInHandshakeRequest() {
        Server server = null;
        try {
            server = startServer(AnnotatedServerEndpoint.class);
            final CountDownLatch responseLatch = new CountDownLatch(1);

            StringBuilder handshakeRequest = new StringBuilder();
            URI serverEndpointUri = getURI(AnnotatedServerEndpoint.class);
            handshakeRequest.append("GET " + serverEndpointUri.getPath() + " HTTP/1.1\r\n");
            appendHeader(handshakeRequest, "Host", getHost() + ":" + getPort());
            appendHeader(handshakeRequest, UpgradeRequest.CONNECTION, UpgradeRequest.UPGRADE);
            appendHeader(handshakeRequest, UpgradeRequest.UPGRADE, UpgradeRequest.WEBSOCKET);
            appendHeader(handshakeRequest, HandshakeRequest.SEC_WEBSOCKET_KEY, "MX3DK3cbUu5DHEWW6dyzJQ==");
            appendHeader(handshakeRequest, HandshakeRequest.SEC_WEBSOCKET_VERSION, "13");

            appendHeader(handshakeRequest, HEADER_KEY, HEADER_VALUE_1);
            appendHeader(handshakeRequest, HEADER_KEY, HEADER_VALUE_2);

            handshakeRequest.append("\r\n");

            String requestStr = handshakeRequest.toString();
            byte[] requestBytes = requestStr.getBytes(Charset.forName("ISO-8859-1"));

            final Socket socket = new Socket(getHost(), getPort());
            OutputStream out = socket.getOutputStream();
            out.write(requestBytes);
            out.flush();

            Executor executor = Executors.newSingleThreadExecutor();
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    BufferedReader in = null;
                    try {
                        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                        while (true) {
                            String responseLine = in.readLine();
                            if (responseLine == null) {
                                break;
                            }

                            if (responseLine.contains("101")) {
                                responseLatch.countDown();
                                break;
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        fail();
                    } finally {
                        if (in != null) {
                            try {
                                in.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            });

            try {
                assertTrue(responseLatch.await(5, TimeUnit.SECONDS));
            } finally {
                out.close();
                socket.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
        }
    }

    private void appendHeader(StringBuilder request, String key, String value) {
        request.append(key);
        request.append(":");
        request.append(value);
        request.append("\r\n");
    }

    @ServerEndpoint(value = "/sameHeadersEndpoint", configurator = HeaderCheckingConfigurator.class)
    public static class AnnotatedServerEndpoint {
    }

    public static class HeaderCheckingConfigurator extends ServerEndpointConfig.Configurator {

        @Override
        public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
            List<String> headerValues = request.getHeaders().get(HEADER_KEY);
            System.out.println("RECEIVED HEADERS: " + headerValues);

            if (!headerValues.contains(HEADER_VALUE_1) || !headerValues.contains(HEADER_VALUE_2)) {
                throw new RuntimeException("Request does not contain both headers " + headerValues);
            }
        }
    }
}
