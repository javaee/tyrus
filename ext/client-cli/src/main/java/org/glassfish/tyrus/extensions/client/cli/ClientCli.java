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

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;

import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.OnClose;
import javax.websocket.OnError;
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
 * @author Gerard Davison (gerard.davison at oracle.com)
 */
public class ClientCli {

    public static final String NAME = "tyrus-client";
    public static final String CONSOLE_PREFIX_NO_CONN = NAME + "> ";
    public static final String CONSOLE_PREFIX_CONN = "session %s> ";

    private static volatile Session session = null;

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
            print("text-message", s);
        }

        @OnMessage
        public void onMessage(byte[] buffer) throws IOException {

            // Covert into octets, must be a better way to do this
            //
            //
            StringBuilder sb = new StringBuilder();
            for (byte b : buffer) {
                sb.append("0x");
                sb.append(Integer.toHexString(b));
                sb.append(' ');
            }

            print("binary-message", sb.toString());
        }

        @OnError
        public void onError(Throwable th) throws IOException {
            print("error", th.getMessage());
        }

        @OnClose
        public void onClose(CloseReason closeReason) throws IOException {
            session = null;
            print("closed", closeReason.toString());
        }

        @OnMessage
        public void onMessage(PongMessage pongMessage) throws IOException {
            print(null, "pong-message");
        }

        private void print(String prefix, String message) throws IOException {
            ClientCli.print(console, prefix, message);
        }
    }

    public static void main(String[] args) throws IOException, DeploymentException, InterruptedException {


        final WebSocketContainer webSocketContainer = ContainerProvider.getWebSocketContainer();

        try {
            final ConsoleReader console = new ConsoleReader(
                    NAME,
                    new FileInputStream(FileDescriptor.in), System.out, null);

            //

            console.addCompleter(new StringsCompleter("open", "close", "send", "ping", "exit", "quit", "help"));
            console.setPrompt(getPrompt());

            // If we have one parameter assume it to be a URI
            //

            if (args.length == 1) {
                connectToURI(console, args[0], webSocketContainer);
                console.getHistory().add("open " + args[0]);
            } else if (args.length > 1) {
                ClientCli.printSynchronous(console, null, String.format("Invalid argument count, usage cmd [ws uri]"));
                return;
            }

            String line;
            mainLoop:
            while ((line = console.readLine()) != null) {

                try {
                    // Get ride of extranious white space
                    //
                    line = line.trim();

                    if (line.length() == 0) {
                        // Do nothing
                    } else if (line.startsWith("open ")) {
                        final String uri = line.substring(5).trim();
                        connectToURI(console, uri, webSocketContainer);
                    } else if (line.startsWith("close")) {
                        if (session != null) {
                            session.close();
                        }
                        session = null;

                        ClientCli.print(console, null, String.format("Session closed"), false);
                    } else if (line.startsWith("send ")) {
                        final String message = line.substring(5);

                        if (session != null) {
                            session.getBasicRemote().sendText(message);
                        }
                        // Multiline send, complets on the full stop
                    } else if (line.startsWith("send")) {

                        ClientCli.printSynchronous(console, null, String.format("End multiline message with . on own line"));
                        console.restoreLine("", 0);

                        StringBuilder sb = new StringBuilder();
                        String subLine;
                        while (!".".equals((subLine = console.readLine()))) {
                            sb.append(subLine);
                            sb.append('\n');
                        }

                        if (session != null) {
                            session.getBasicRemote().sendText(sb.toString());
                        }

                        // Put the prompt back
                        console.resetPromptLine(getPrompt(), "", 0);
                    } else if (line.startsWith("ping")) {
                        if (session != null) {
                            session.getBasicRemote().sendPing(ByteBuffer.wrap("tyrus-client-ping".getBytes()));
                        }
                    } else if (line.startsWith("exit") || line.startsWith("quit")) {
                        break mainLoop;
                    } else if (line.startsWith("help")) {

                        String help = ""
                                + "\n\topen uri : open a connection to the web socket uri"
                                + "\n\tclose : close a currently open web socket session"
                                + "\n\tsend message : send a text message"
                                + "\n\tsend : send a multiline text message teminated with a ."
                                + "\n\tping : send a ping message"
                                + "\n\tquit | exit : exit this tool"
                                + "\n\thelp : display this message"
                                + "\n\t";

                        ClientCli.print(console, null, help, false);

                    } else {
                        ClientCli.print(console, null, "Unable to parse given command.", false);
                    }

                } catch (IOException e) {
                    ClientCli.print(console, null, String.format("IOException: %s", e.getMessage()), false);
                }
            }

        } finally {
            try {
                TerminalFactory.get().restore();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    protected static void connectToURI(final ConsoleReader console, final String uri, final WebSocketContainer webSocketContainer) throws IOException {

        // Use a local copy so that we don't get odd race conditions
        //
        Session localCopy = session;
        if (localCopy != null) {
            ClientCli.printSynchronous(console, null, String.format("Closing session %s", localCopy.getId()));
            localCopy.close();
        }
        ClientCli.printSynchronous(console, null, String.format("Connecting to %s...", uri));
        try {
            // TODO support sub protocols
            localCopy = webSocketContainer.connectToServer(new ClientEndpoint(console), new URI(uri));
            session = localCopy;

            ClientCli.print(console, null, String.format("Connected in session %s", localCopy.getId()), false);
        } catch (URISyntaxException ex) {
            ClientCli.print(console, null, String.format("Problem parsing uri %s beause of %s", uri, ex.getMessage()), false);
        } catch (DeploymentException ex) {
            ClientCli.print(console, null, String.format("Failed to connect to %s due to %s", uri, ex.getMessage()), false);
        }
    }


    /**
     * Derive the prompt from the session id.
     *
     * @return prompt string.
     */
    private static String getPrompt() {
        // Hendge against current updates
        Session currentSession = session;
        if (session != null) {
            String id = currentSession.getId();
            String shortId = id.substring(0, 4) + "..."
                    + id.substring(id.length() - 4);
            return String.format(CONSOLE_PREFIX_CONN, shortId);
        } else {
            return CONSOLE_PREFIX_NO_CONN;
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
        printSynchronous(console, prefix, message);

        if (restore) {
            console.restoreLine(getPrompt(), 0);
        } else {
            console.resetPromptLine(getPrompt(), "", 0);
        }
    }

    /**
     * Console printer for when the console doesn't have control.
     * <p/>
     * Format: # prefix: message.
     *
     * @param console console to be used for printing.
     * @param prefix  message prefix. Ignored when {@code null} or empty string.
     * @param message message data. Ignored when {@code null} or empty string.
     * @throws IOException when there is some issue with printing to a console.
     */
    private static void printSynchronous(ConsoleReader console, String prefix, String message) throws IOException {
        console.restoreLine("", 0);

        String m = (message == null ? "" : message);

        if (prefix != null && !prefix.isEmpty()) {
            console.println(String.format("# %s: %s", prefix, m));
        } else {
            console.println(String.format("# %s", m));
        }

        console.restoreLine("", 0);
    }
}
