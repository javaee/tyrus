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
package org.glassfish.tyrus.extensions.client.cli;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;

import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.PongMessage;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import jline.TerminalFactory;
import jline.console.ConsoleReader;
import jline.console.completer.StringsCompleter;

/**
 * Simple WebSocket CLI client, handy tool usable for simple endpoint testing.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class ClientCli {

    public static final String CONSOLE_PREFIX = "tyrus-client> ";

    /**
     * Client side endpoint, prints everything into given console.
     */
    @javax.websocket.ClientEndpoint
    public static class ClientEndpoint {
        private final ConsoleReader console;

        public ClientEndpoint(ConsoleReader console) {
            this.console = console;
        }

        @OnMessage
        public void onMessage(String s) throws IOException {
            print("message", s);
        }

        @OnClose
        public void onClose(CloseReason closeReason) throws IOException {
            print("closed", closeReason.toString());
        }

        @OnMessage
        public void onMessage(PongMessage pongMessage) throws IOException {
            print(null, "pong");
        }

        private void print(String prefix, String message) throws IOException {
            ClientCli.print(console, prefix, message);
        }
    }

    public static void main(String[] args) throws IOException, DeploymentException, InterruptedException {


        final WebSocketContainer webSocketContainer = ContainerProvider.getWebSocketContainer();

        try {
            final ConsoleReader console = new ConsoleReader();
            console.addCompleter(new StringsCompleter("open", "close", "send", "ping"));
            console.setPrompt(CONSOLE_PREFIX);
            String line;
            Session session = null;
            while ((line = console.readLine()) != null) {
                if (line.startsWith("open ")) {
                    final String uri = line.substring(5);
                    if (session != null) {
                        session.close();
                    }
                    ClientCli.print(console, null, String.format("Connecting to %s...", uri));
                    session = webSocketContainer.connectToServer(new ClientEndpoint(console), new URI(uri));
                } else if (line.startsWith("close")) {
                    if (session != null) {
                        session.close();
                    }
                    session = null;
                } else if (line.startsWith("send ")) {
                    final String message = line.substring(5);

                    if (session != null) {
                        session.getBasicRemote().sendText(message);
                    }
                } else if (line.startsWith("ping")) {
                    if (session != null) {
                        session.getBasicRemote().sendPing(ByteBuffer.wrap("tyrus-client-ping".getBytes()));
                    }
                } else {
                    ClientCli.print(console, null, "unable to parse given command.", false);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } finally {
            try {
                TerminalFactory.get().restore();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Console printer.
     * <p/>
     * Format: # prefix: message.
     *
     * @param console console to be used for printing.
     * @param prefix  message prefix. Ignored when {@code null} or empty string.
     * @param message message data. Ignored when {@code null} or empty string.
     * @throws IOException when there is some issue with printing to a console.
     */
    private static void print(ConsoleReader console, String prefix, String message) throws IOException {
        print(console, prefix, message, true);
    }

    /**
     * Console printer.
     * <p/>
     * Format: # prefix: message.
     *
     * @param console console to be used for printing.
     * @param prefix  message prefix. Ignored when {@code null} or empty string.
     * @param message message data. Ignored when {@code null} or empty string.
     * @param restore restore command line prefix.
     * @throws IOException when there is some issue with printing to a console.
     */
    private static void print(ConsoleReader console, String prefix, String message, boolean restore) throws IOException {
        console.restoreLine("", 0);
        String m = (message == null ? "" : message);

        if (prefix != null && !prefix.isEmpty()) {
            console.println(String.format("# %s: %s", prefix, m));
        } else {
            console.println(String.format("# %s", m));
        }

        if(restore) {
            console.restoreLine(CONSOLE_PREFIX, 0);
        } else {
            console.resetPromptLine(CONSOLE_PREFIX, "", 0);
        }
    }
}
