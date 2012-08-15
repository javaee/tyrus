/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 - 2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.websocket.sample.chat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.glassfish.websocket.api.*;
import org.glassfish.websocket.api.annotations.*;
import org.glassfish.websocket.sample.chat.chatdata.*;


@WebSocket(path = "/chat",
remote = org.glassfish.websocket.sample.chat.ChatClientRemote.class,
decoders = {org.glassfish.websocket.sample.chat.chatdata.LoginRequestDecoder.class,
    org.glassfish.websocket.sample.chat.chatdata.ChatUpdateDecoder.class,
    org.glassfish.websocket.sample.chat.chatdata.DisconnectRequestDecoder.class},
encoders = {org.glassfish.websocket.sample.chat.chatdata.DisconnectResponseEncoder.class})
public class ChatServer {

    final static Logger logger = Logger.getLogger("application");
    @WebSocketContext
    public EndpointContext context;
    private List<String> chatTranscript = new ArrayList<String>();
    static int transcriptMaxLines = 20;

    @WebSocketOpen
    public void init(RemoteEndpoint remote) {
        logger.info("############Someone connected...");
    }

    @WebSocketMessage
    public void handleLoginRequest(LoginRequestMessage lrm, ChatClientRemote ccr) {

        String newUsername = this.registerNewUsername(lrm.getUsername(), ccr);
        logger.info("Signing " + newUsername + " into chat.");
        LoginResponseMessage lres = new LoginResponseMessage(newUsername);
        try {
            ccr.sendLoginResponseChanged(lres);
        } catch (IOException ioe) {
            logger.warning("Error signing " + lrm.getUsername() + " into chat : " + ioe.getMessage());
        } catch (EncodeException ce) {
            logger.warning("Error serializing message " + lres + " : " + ce.getMessage());
        }

        this.addToTranscriptAndNotify(newUsername, " has just joined.");
        this.broadcastUserList();
    }

    @WebSocketMessage
    public void handleChatMessage(ChatUpdateMessage cum) {
        logger.info("Receiving chat message from " + cum.getUsername());
        this.addToTranscriptAndNotify(cum.getUsername(), cum.getMessage());
    }

    @WebSocketMessage
    public DisconnectResponseMessage handleDisconnectRequest(DisconnectRequestMessage drm) {
        logger.info(drm.getUsername() + " would like to leave chat");
        DisconnectResponseMessage reply = new DisconnectResponseMessage(drm.getUsername());
        this.addToTranscriptAndNotify(drm.getUsername(), " has just left.");
        this.removeUserAndBroadcast(drm.getUsername());
        return reply;
    }

    @WebSocketClose
    public void handleClientClose(ChatClientRemote ccr) {
        logger.info("The web socket closed");
        String username = (String) ccr.getConversation().XXgetProperties().get("username");
        if (username != null) {
            this.removeUserAndBroadcast(username);
            this.addToTranscriptAndNotify(username, " has just left...rather abruptly !");
        }
    }

    private List<String> getUsernames() {
        List<String> usernames = new ArrayList<String>();
        for (Session nextSession : this.context.getConversations()) {
            String nextUsername = (String) nextSession.XXgetProperties().get("username");
            if (nextUsername != null) {
                usernames.add(nextUsername);
            }
        }
        return usernames;
    }

    private void broadcastUserList() {
        logger.info("Broadcasting updated user list");
        UserListUpdateMessage ulum = new UserListUpdateMessage(this.getUsernames());
        for (Session nextSession : this.context.getConversations()) {
            ChatClientRemote chatClient = (ChatClientRemote) nextSession.getRemote();
            try {
                chatClient.sendUserListUpdate(ulum);
            } catch (IOException ioe) {
                logger.warning("Error updating a client " + chatClient + " : " + ioe.getMessage());
            } catch (EncodeException ce) {
                logger.warning("Error serializing message " + ulum);
            }
        }
    }

    private void removeUserAndBroadcast(String username) {
        logger.info("Removing " + username + " from chat.");
        for (Session nextSession : this.context.getConversations()) {
            if (username.equals(nextSession.XXgetProperties().get("username"))) {
                try {
                    nextSession.close(new CloseReason(CloseReason.Code.NORMAL_CLOSURE, "User logged off"));
                } catch (IOException ioe) {
                    System.out.println("Failed to expire the session: " + ioe.getMessage());
                }

            }
        }
        ChatMessage cm = new UserListUpdateMessage(this.getUsernames());
        this.broadcastUserList();
    }

    private void broadcastUpdatedTranscript() {
        List transcriptEntry = new ArrayList();
        transcriptEntry.add(this.chatTranscript.get(this.chatTranscript.size() - 1).toString());
        logger.info("Broadcasting updated transcript with " + transcriptEntry);

        for (Session nextSession : this.context.getConversations()) {
            ChatClientRemote chatClient = (ChatClientRemote) nextSession.getRemote();
            if (chatClient != null) {
                ChatTranscriptUpdateMessage cm = new ChatTranscriptUpdateMessage(transcriptEntry);
                try {
                    chatClient.sendChatTranscriptUpdate(cm);
                } catch (IOException ioe) {
                    logger.warning("Error updating a client " + chatClient + " : " + ioe.getMessage());
                } catch (EncodeException ce) {
                    logger.warning("Error serializing message " + cm);
                }
            }
        }
    }

    private void addToTranscriptAndNotify(String user, String message) {
        if (chatTranscript.size() > transcriptMaxLines) {
            chatTranscript.remove(0);
        }
        chatTranscript.add(user + "> " + message);
        this.broadcastUpdatedTranscript();
    }

    private String registerNewUsername(String newUsername, ChatClientRemote chatClient) {
        for (Session session : this.context.getConversations()) {
            if (newUsername.equals(session.XXgetProperties().get("username"))) {
                return this.registerNewUsername(newUsername + "1", chatClient);
            }
        }
        chatClient.getConversation().XXgetProperties().put("username", newUsername);
        return newUsername;
    }
}
