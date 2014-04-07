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
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.websocket.ClientEndpoint;
import javax.websocket.DeploymentException;
import javax.websocket.OnMessage;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.core.AnnotatedEndpoint;
import org.glassfish.tyrus.core.TyrusWebSocketEngine;
import org.glassfish.tyrus.core.l10n.LocalizationMessages;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.spi.ClientContainer;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests warnings logged when max message size given in {@link javax.websocket.OnMessage} is larger than max message
 * size specified in a container.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class MaxMessageSizeDeploymentTest extends TestContainer {

    private final Logger logger = Logger.getLogger(AnnotatedEndpoint.class.getName());

    @ServerEndpoint("/largeMaxMessageSizeServerEndpoint")
    public static class LargeMaxMessageSizeServerEndpoint {

        @OnMessage(maxMessageSize = 2)
        public void onTooBigMessage(String message) {
        }
    }

    @Test
    public void serverMaxMessageSizeTooLargeTest() throws DeploymentException, InterruptedException, IOException {
        Map<String, Object> serverProperties = getServerProperties();
        serverProperties.put(TyrusWebSocketEngine.INCOMING_BUFFER_SIZE, 1);
        final AtomicBoolean warningLogged = new AtomicBoolean(false);
        LoggerHandler handler = new LoggerHandler() {
            @Override
            public void publish(LogRecord record) {
                String expectedWarningMessage = LocalizationMessages.ENDPOINT_MAX_MESSAGE_SIZE_TOO_LONG(2, LargeMaxMessageSizeServerEndpoint.class.getMethods()[0].getName(), LargeMaxMessageSizeServerEndpoint.class.getName(), 1);
                System.out.println("Expected message: " + expectedWarningMessage);
                System.out.println("Logged message: " + record.getMessage());
                if (expectedWarningMessage.equals(record.getMessage())) {
                    warningLogged.set(true);
                }
            }
        };
        logger.setLevel(Level.CONFIG);
        logger.addHandler(handler);
        Server server = null;
        try {
            server = startServer(LargeMaxMessageSizeServerEndpoint.class);
        } finally {
            stopServer(server);
        }
        assertTrue(warningLogged.get());
        logger.removeHandler(handler);
    }

    /**
     * Tests that no warning is given during server endpoint deployment.
     * It does not look for a specific message, but checks that no warning is given, therefore it
     * might fail, when other warnings than max message size check are introduced.
     */
    @Test
    public void serverMaxMessageSizeOkTest() throws DeploymentException, InterruptedException, IOException {
        Map<String, Object> serverProperties = getServerProperties();
        serverProperties.put(TyrusWebSocketEngine.INCOMING_BUFFER_SIZE, 3);
        final AtomicBoolean warningLogged = new AtomicBoolean(false);
        LoggerHandler handler = new LoggerHandler() {
            @Override
            public void publish(LogRecord record) {
                System.out.println("Logged message: " + record.getMessage());
                warningLogged.set(true);
            }
        };
        logger.setLevel(Level.CONFIG);
        logger.addHandler(handler);
        Server server = null;
        try {
            server = startServer(LargeMaxMessageSizeServerEndpoint.class);
        } finally {
            stopServer(server);
        }
        assertFalse(warningLogged.get());
        logger.removeHandler(handler);
    }

    @ClientEndpoint
    public static class LargeMaxMessageSizeClientEndpoint {

        @OnMessage(maxMessageSize = 2)
        public void onTooBigMessage(String message) {
        }

    }

    @ServerEndpoint("/dummyServerEndpoint")
    public static class DummyServerEndpoint {

    }

    @Test
    public void clientMaxMessageSizeTooLargeTest() throws DeploymentException {
        Server server = startServer(DummyServerEndpoint.class);
        try {
            ClientManager client = createClient();
            Map<String, Object> properties = client.getProperties();
            properties.put(ClientContainer.INCOMING_BUFFER_SIZE, 1);
            final AtomicBoolean warningLogged = new AtomicBoolean(false);
            LoggerHandler handler = new LoggerHandler() {
                @Override
                public void publish(LogRecord record) {
                    String expectedWarningMessage = LocalizationMessages.ENDPOINT_MAX_MESSAGE_SIZE_TOO_LONG(2, LargeMaxMessageSizeClientEndpoint.class.getMethods()[0].getName(), LargeMaxMessageSizeClientEndpoint.class.getName(), 1);
                    System.out.println("Expected message: " + expectedWarningMessage);
                    System.out.println("Logged message: " + record.getMessage());
                    if (expectedWarningMessage.equals(record.getMessage())) {
                        warningLogged.set(true);
                    }
                }
            };
            logger.setLevel(Level.CONFIG);
            logger.addHandler(handler);
            client.connectToServer(LargeMaxMessageSizeClientEndpoint.class, getURI(DummyServerEndpoint.class, "ws"));
            assertTrue(warningLogged.get());
            logger.removeHandler(handler);

        } catch (IOException e) {

        } finally {
            stopServer(server);
        }
    }

    /**
     * Tests that no warning is given during client endpoint deployment.
     * It does not look for a specific message, but checks that no warning is given, therefore it
     * might fail, when other warnings than max message size check are introduced.
     */
    @Test
    public void clientMaxMessageSizeOkTest() throws DeploymentException {
        Server server = startServer(DummyServerEndpoint.class);
        try {
            ClientManager client = createClient();
            Map<String, Object> properties = client.getProperties();
            properties.put(ClientContainer.INCOMING_BUFFER_SIZE, 3);
            final AtomicBoolean warningLogged = new AtomicBoolean(false);
            LoggerHandler handler = new LoggerHandler() {
                @Override
                public void publish(LogRecord record) {
                    System.out.println("Logged message: " + record.getMessage());
                    warningLogged.set(true);
                }
            };
            logger.setLevel(Level.CONFIG);
            logger.addHandler(handler);
            client.connectToServer(LargeMaxMessageSizeClientEndpoint.class, getURI(DummyServerEndpoint.class, "ws"));
            assertFalse(warningLogged.get());
            logger.removeHandler(handler);

        } catch (IOException e) {

        } finally {
            stopServer(server);
        }
    }

    private static abstract class LoggerHandler extends Handler {

        @Override
        public void flush() {

        }

        @Override
        public void close() throws SecurityException {

        }

        @Override
        public synchronized Level getLevel() {
            return Level.CONFIG;
        }
    }

}
