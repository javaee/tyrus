/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2014 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.ext.client.cli;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;

import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.PongMessage;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.glassfish.tyrus.client.ClientManager;

import jline.TerminalFactory;
import jline.console.ConsoleReader;
import jline.console.CursorBuffer;
import jline.console.completer.StringsCompleter;

/**
 * Simple WebSocket CLI client, handy tool usable for simple endpoint testing.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 * @author Gerard Davison (gerard.davison at oracle.com)
 */
public class ClientCli {

    private static final String NAME = "tyrus-client";
    private static final String CONSOLE_PREFIX_NO_CONN = NAME + "> ";
    private static final String CONSOLE_PREFIX_CONN = "session %s> ";

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
            print("text-message", s.replace("\n", "\n# "));
        }

        @OnMessage
        public void onMessage(byte[] buffer) throws IOException {

            // Covert into octets, must be a better way to do this
            //
            //
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < buffer.length && i < 1024; i++) {
                sb.append("0x");
                sb.append(Integer.toHexString(buffer[i]));
                sb.append(' ');
            }

            if (buffer.length >= 1024) {
                sb.append(" ...");
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

        final ClientManager clientManager = new ClientManager();

        try {
            final ConsoleReader console = new ConsoleReader(
                    NAME,
                    new FileInputStream(FileDescriptor.in), System.out, null);

            console.addCompleter(new StringsCompleter("open", "close", "send", "ping", "exit", "quit", "help"));
            console.setPrompt(getPrompt());

            if (args.length > 0) {

                int i = 0;
                String arg;

                while (i < args.length && args[i].startsWith("--")) {
                    arg = args[i++];

                    if (arg.equals("--proxy")) {
                        if (i < args.length) {
                            final String proxyUrl = args[i++];
                            clientManager.getProperties().put(ClientManager.PROXY_URI, proxyUrl);
                        } else {
                            ClientCli.print(console, null, String.format("--proxy requires an argument (url)"), false);
                        }
                    }

                    if (arg.equals("--help")) {

                        String help = "\n"
                                + "\nUsage: cmd [--proxy proxyUrl] [ws uri]"
                                + "\n"
                                + "\nruntime commands:"
                                + "\n\topen uri : open a connection to the web socket uri"
                                + "\n\tclose : close a currently open web socket session"
                                + "\n\tsend message : send a text message"
                                + "\n\tsend : send a multiline text message teminated with a ."
                                + "\n\tping : send a ping message"
                                + "\n\tquit | exit : exit this tool"
                                + "\n\thelp : display this message";

                        ClientCli.print(console, null, help, false);
                        return;
                    }
                }

                if (i == (args.length - 1)) {
                    connectToURI(console, args[i], clientManager);
                    console.getHistory().add("open " + args[i]);
                    console.setPrompt(getPrompt());
                    i++;
                }

                if (i != args.length) {
                    ClientCli.print(console, null, String.format("Invalid argument count, usage cmd [--proxy proxyUrl] [ws uri]"), false);
                    return;
                }
            }

            String line;
            mainLoop:
            while ((line = console.readLine()) != null) {

                try {
                    // Get rid of extranious white space
                    line = line.trim();

                    if (line.length() == 0) {
                        // Do nothing
                    } else if (line.startsWith("open ")) {
                        final String uri = line.substring(5).trim();
                        connectToURI(console, uri, clientManager);
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

                        ClientCli.print(console, null, String.format("End multiline message with . on own line"), false);

                        String temporaryPrompt = "send...> ";
                        console.setPrompt(temporaryPrompt);

                        StringBuilder sb = new StringBuilder();
                        String subLine;
                        while (!".".equals((subLine = console.readLine()))) {
                            sb.append(subLine);
                            sb.append('\n');
                        }

                        // Send message
                        if (session != null) {
                            session.getBasicRemote().sendText(sb.toString());
                        }

                    } else if (line.startsWith("ping")) {
                        if (session != null) {
                            session.getBasicRemote().sendPing(ByteBuffer.wrap("tyrus-client-ping".getBytes("UTF-8")));
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

                // Restore prompt
                console.setPrompt(getPrompt());
            }

        } finally {
            try {
                TerminalFactory.get().restore();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    private static void connectToURI(final ConsoleReader console, final String uri, final WebSocketContainer webSocketContainer) throws IOException {

        // Use a local copy so that we don't get odd race conditions
        //
        Session localCopy = session;
        if (localCopy != null) {
            ClientCli.print(console, null, String.format("Closing session %s", localCopy.getId()), false);
            localCopy.close();
        }
        ClientCli.print(console, null, String.format("Connecting to %s...", uri), false);
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
    private static synchronized void print(ConsoleReader console, String prefix, String message, boolean restore) throws IOException {

        // Store the buffer information so we can restore it 
        // after the message has been sent
        CursorBuffer cursorBuffer = console.getCursorBuffer().copy();
        String buffer = cursorBuffer.buffer.toString();
        int cursor = cursorBuffer.cursor;
        String prompt = console.getPrompt();

        console.restoreLine("", 0);

        String m = (message == null ? "" : message);

        if (prefix != null && !prefix.isEmpty()) {
            console.println(String.format("# %s: %s", prefix, m));
        } else {
            console.println(String.format("# %s", m));
        }

        if (restore) {
            console.resetPromptLine(prompt, buffer, cursor);
        } else {
            console.restoreLine("", 0);
        }
    }
}
