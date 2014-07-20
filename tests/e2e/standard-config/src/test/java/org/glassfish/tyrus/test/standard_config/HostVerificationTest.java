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

import javax.websocket.ClientEndpoint;
import javax.websocket.server.ServerEndpoint;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.client.SslContextConfigurator;
import org.glassfish.tyrus.client.SslEngineConfigurator;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

/**
 * The test assumes that {@code tyrus.test.host.ip} contains IP of the server, which is not in the server certificate.
 * <p/>
 * The test will be run only if systems properties {@code tyrus.test.host.ip} and {@code tyrus.test.port.ssl} are set.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class HostVerificationTest extends TestContainer {

    /**
     * Test that the client will manage to connect to the server using server IP, when host name verification is disabled.
     */
    @Test
    public void disabledHostVerificationTest() {
        try {
            ClientManager client = createClient();
            SslEngineConfigurator sslEngineConfigurator = new SslEngineConfigurator(new SslContextConfigurator());
            sslEngineConfigurator.setHostVerificationEnabled(false);
            client.getProperties().put(ClientProperties.SSL_ENGINE_CONFIGURATOR, sslEngineConfigurator);

            client.connectToServer(AnnotatedClientEndpoint.class, getURI(AnnotatedServerEndpoint.class, "wss"));
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    /**
     * Test that the client will fail to connect to the server using server IP, when host name verification is enabled.
     */
    @Test
    public void enabledHostVerificationTest() {
        try {
            ClientManager client = createClient();

            // Grizzly client logs the exception - giving a hint to the reader of the logs
            System.out.println("=== SSL error may follow in the log ===");
            client.connectToServer(AnnotatedClientEndpoint.class, getURI(AnnotatedServerEndpoint.class, "wss"));

            fail();
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof SSLException);
        }
    }

    /**
     * Test that the client will manage to connect to the server using a custom host verifier.
     */
    @Test
    public void customHostVerifierPassTest() {
        try {
            ClientManager client = createClient();
            SslEngineConfigurator sslEngineConfigurator = new SslEngineConfigurator(new SslContextConfigurator());
            sslEngineConfigurator.setHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String s, SSLSession sslSession) {
                    return true;
                }
            });
            client.getProperties().put(ClientProperties.SSL_ENGINE_CONFIGURATOR, sslEngineConfigurator);

            client.connectToServer(AnnotatedClientEndpoint.class, getURI(AnnotatedServerEndpoint.class, "wss"));
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    /**
     * Test that the client will not manage to connect to the server using a custom host verifier.
     */
    @Test
    public void customHostVerifierFailTest() {
        try {
            ClientManager client = createClient();
            SslEngineConfigurator sslEngineConfigurator = new SslEngineConfigurator(new SslContextConfigurator());
            sslEngineConfigurator.setHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String s, SSLSession sslSession) {
                    return false;
                }
            });
            client.getProperties().put(ClientProperties.SSL_ENGINE_CONFIGURATOR, sslEngineConfigurator);

            // Grizzly client logs the exception - giving a hint to the reader of the logs
            System.out.println("=== SSL error may follow in the log ===");
            client.connectToServer(AnnotatedClientEndpoint.class, getURI(AnnotatedServerEndpoint.class, "wss"));

            fail();
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof SSLException);
        }
    }

    /**
     * The test will be run only if systems properties {@code tyrus.test.host.ip} and {@code tyrus.test.port.ssl} are set.
     */
    @Before
    public void before() {
        assumeTrue(System.getProperty("tyrus.test.host.ip") != null && System.getProperty("tyrus.test.port.ssl") != null);
    }

    @Override
    protected String getHost() {
        return System.getProperty("tyrus.test.host.ip");
    }

    @Override
    protected int getPort() {
        String sslPort = System.getProperty("tyrus.test.port.ssl");

        try {
            return Integer.parseInt(sslPort);
        } catch (NumberFormatException nfe) {
            fail();
        }

        // just to make javac happy - won't be executed.
        return 0;
    }

    @ClientEndpoint
    public static class AnnotatedClientEndpoint {
    }

    @ServerEndpoint("/hostVerificationEndpoint")
    public static class AnnotatedServerEndpoint {
    }
}