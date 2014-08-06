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

package org.glassfish.tyrus.test.e2e.non_deployable;

import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.HandshakeResponse;
import javax.websocket.Session;
import javax.websocket.server.HandshakeRequest;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.core.Base64Utils;
import org.glassfish.tyrus.spi.UpgradeRequest;
import org.glassfish.tyrus.spi.UpgradeResponse;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests a situation when a handshake response contains the same header twice with different values.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class SameHeadersOnClientTest extends TestContainer {

    private static final String HEADER_KEY = "my-header";
    private static final String HEADER_VALUE_1 = "my-header-value-1";
    private static final String HEADER_VALUE_2 = "my-header-value-2";

    @Test
    public void testSameHeaderNamesInHandshakeResponse() {
        HttpServer server = null;
        try {
            server = getHandshakeServer();

            final CountDownLatch responseLatch = new CountDownLatch(1);

            ClientManager client = createClient();
            client.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig endpointConfig) {
                    // do nothing
                }
            }, ClientEndpointConfig.Builder.create().configurator(new ClientEndpointConfig.Configurator() {
                @Override
                public void afterResponse(HandshakeResponse hr) {
                    List<String> headers = hr.getHeaders().get(HEADER_KEY);
                    System.out.println("Received headers: " + headers);

                    if (headers.contains(HEADER_VALUE_1) && headers.contains(HEADER_VALUE_2)) {
                        responseLatch.countDown();
                    }
                }
            }).build(), URI.create("ws://localhost:8025/testSameHeader"));

            assertTrue(responseLatch.await(5, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            if (server != null) {
                server.shutdown();
            }
        }
    }

    private HttpServer getHandshakeServer() throws IOException {
        HttpServer server = HttpServer.createSimpleServer("/testSameHeader", getHost(), getPort());
        server.getServerConfiguration().addHttpHandler(
                new HttpHandler() {
                    public void service(Request request, Response response) throws Exception {
                        response.setStatus(101);

                        response.addHeader(UpgradeRequest.CONNECTION, UpgradeRequest.UPGRADE);
                        response.addHeader(UpgradeRequest.UPGRADE, UpgradeRequest.WEBSOCKET);

                        String secKey = request.getHeader(HandshakeRequest.SEC_WEBSOCKET_KEY);
                        String key = secKey + UpgradeRequest.SERVER_KEY_HASH;

                        MessageDigest instance;
                        try {
                            instance = MessageDigest.getInstance("SHA-1");
                            instance.update(key.getBytes("UTF-8"));
                            final byte[] digest = instance.digest();
                            String responseKey = Base64Utils.encodeToString(digest, false);

                            response.addHeader(UpgradeResponse.SEC_WEBSOCKET_ACCEPT, responseKey);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        response.addHeader(HEADER_KEY, HEADER_VALUE_1);
                        response.addHeader(HEADER_KEY, HEADER_VALUE_2);
                    }
                }
        );

        server.start();
        return server;
    }
}
