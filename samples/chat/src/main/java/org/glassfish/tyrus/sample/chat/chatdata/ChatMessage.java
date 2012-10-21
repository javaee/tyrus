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

package org.glassfish.tyrus.sample.chat.chatdata;

    /**
    *   loginRequest: username (no commas)
    *   loginResponse: username / blank
    *   userList: comma sep: list of usernames
    *   chat transcript update: list of data
    *   disconnect request
    *   disconnect response
    */

import java.util.*;

public abstract class ChatMessage {
     static String LOGIN_REQUEST = "lreq";
     static String LOGIN_RESPONSE = "lres";
     static String USERLIST_UPDATE = "ulupd";
     static String CHAT_MESSAGE = "ctmsg";
     static String CHATTRANSCRIPT_UPDATE = "ctupd";
     static String DISCONNECT_REQUEST = "dreq";
     static String DISCONNECT_RESPONSE = "dres";
     static String SEP = ":";

     String type;

    public static void main(String args[]) {
        ChatMessage login = new LoginRequestMessage("Danny");

        String loginData = (String) login.asString();
        System.out.println(loginData);
        LoginRequestMessage parsedLogin = new LoginRequestMessage();
        parsedLogin.fromString(loginData);
        String parsedLoginData = (String) parsedLogin.getData();
        System.out.println(loginData + " : " + parsedLoginData);

        List users = new ArrayList();
        users.add("Danny");
        users.add("Jared");
        users.add("Tyrus");

        UserListUpdateMessage userListUpdate = new UserListUpdateMessage(users);
        System.out.println(userListUpdate.asString());
        UserListUpdateMessage parsedUserListUpdate = new UserListUpdateMessage();
        parsedUserListUpdate.fromString(userListUpdate.asString());
        System.out.println(parsedUserListUpdate.asString());

        List chatNameValue = new ArrayList();
        chatNameValue.add("Danny");
        chatNameValue.add("hi there");

        ChatUpdateMessage cm = new ChatUpdateMessage("Danny", "Hi There");
        System.out.println(cm.asString());
        ChatUpdateMessage parsedCM = new ChatUpdateMessage();
        parsedCM.fromString(cm.asString());
        System.out.println(parsedCM.asString());
    }


    private static ChatMessage parseMessage(String s) {
        System.out.println("Parse: " + s);
        ChatMessage chatMessage;

        if (s.startsWith(LOGIN_REQUEST)) {
            chatMessage = new LoginRequestMessage();
        } else if (s.startsWith(LOGIN_RESPONSE)) {
            chatMessage = new LoginResponseMessage();
        }  else if (s.startsWith(DISCONNECT_REQUEST)) {
            chatMessage = new DisconnectRequestMessage();
        }  else if (s.startsWith(DISCONNECT_RESPONSE)) {
            chatMessage = new DisconnectResponseMessage();
        }  else if (s.startsWith(CHAT_MESSAGE)) {
            chatMessage = new ChatUpdateMessage();
        }  else if (s.startsWith(USERLIST_UPDATE)) {
            chatMessage = new UserListUpdateMessage();
        }  else if (s.startsWith(CHATTRANSCRIPT_UPDATE)) {
            chatMessage = new ChatTranscriptUpdateMessage();
        } else {
            throw new RuntimeException("Unknown message: " + s);
        }
        chatMessage.fromString(s);
        return chatMessage;
    }


    public abstract String asString();
    public abstract void fromString(String s);


    ChatMessage(String type) {
        this.type = type;
    }

    abstract Object getData();


}






