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

package org.glassfish.tyrus.sample.echo.auth;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.client.auth.AuthConfig;
import org.glassfish.tyrus.client.auth.AuthenticationException;
import org.glassfish.tyrus.client.auth.Credentials;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Ondrej Kosatka (ondrej.kosatka at oracle.com)
 */
public class SampleBasicAuthTest extends TestContainer {

    public SampleBasicAuthTest() {
        setContextPath("/sample-echo-basic-auth");
    }

    @Test
    public void testDisabledBasicAuth() throws InterruptedException, IOException, AuthenticationException {
        AuthConfig authConfig = AuthConfig.Builder.create().
                disableProvidedBasicAuth().
                build();
        Credentials credentials = new Credentials("ws_user", "password");
        try {
            testEcho(authConfig, credentials);
        } catch (DeploymentException e) {
            assertTrue("Invalid auth config should throw an AuthenticationException", e.getCause() instanceof AuthenticationException);
        }
    }

    @Test
    public void testSimplestBasicAuth() throws DeploymentException, InterruptedException, IOException, AuthenticationException {
        Credentials credentials = new Credentials("ws_user", "password");
        testEcho(null, credentials);
    }

    @Test
    public void testNullCredentials() throws InterruptedException, IOException, AuthenticationException {
        AuthConfig authConfig = AuthConfig.Builder.create().build();
        Credentials credentials = null;
        try {
            testEcho(authConfig, credentials);
        } catch (DeploymentException e) {
            assertTrue("Invalid auth config should throw an AuthenticationException", e.getCause() instanceof AuthenticationException);
        }
    }

    @Test
    public void testNulls() throws InterruptedException, IOException, AuthenticationException {
        AuthConfig authConfig = null;
        Credentials credentials = null;
        try {
            testEcho(authConfig, credentials);
        } catch (DeploymentException e) {
            assertTrue("Invalid auth config should throw an AuthenticationException", e.getCause() instanceof AuthenticationException);
        }
    }

    public void testEcho(AuthConfig authConfig, Credentials credentials) throws DeploymentException, InterruptedException, IOException {
        if (System.getProperty("tyrus.test.host") == null) {
            return;
        }

        final CountDownLatch messageLatch = new CountDownLatch(1);
        final CountDownLatch onOpenLatch = new CountDownLatch(1);

        final ClientManager client = createClient();

        client.getProperties().put(ClientProperties.AUTH_CONFIG, authConfig);
        client.getProperties().put(ClientProperties.CREDENTIALS, credentials);

        client.connectToServer(new Endpoint() {
            @Override
            public void onOpen(Session session, EndpointConfig EndpointConfig) {
                try {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            System.out.println("### Received: " + message);

                            if (message.equals("Do or do not, there is no try. (from your server)")) {
                                messageLatch.countDown();
                            } else if (message.equals("onOpen")) {
                                onOpenLatch.countDown();
                            }
                        }
                    });

                    session.getBasicRemote().sendText("Do or do not, there is no try.");
                } catch (IOException e) {
                    // do nothing
                }
            }
        }, ClientEndpointConfig.Builder.create().build(), getURI(BasicAuthEchoEndpoint.class, "wss"));

        messageLatch.await(1, TimeUnit.SECONDS);
        if (messageLatch.getCount() != 0 || onOpenLatch.getCount() != 0) {
            fail();
        }
    }

}
