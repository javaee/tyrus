/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2015 Oracle and/or its affiliates. All rights reserved.
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

var wsUri = getRootUri() + "/sample-chat/chat";
var output;
var username = "";
var debug = false;
var chatTranscriptElt;
var messageTextElt;
var websocket;

function getRootUri() {
    return "ws://" + (document.location.hostname == "" ? "localhost" : document.location.hostname) + ":" +
        (document.location.port == "" ? "8080" : document.location.port);
}

function init() {
    output = document.getElementById("output");
    refreshForUsernameChange();
}

function test() {
    writeToScreen(chatMessageTextID.value);

}

function do_login() {
    var retVal = prompt("Enter your name : ", "guest-user", "hello");
    if (retVal != "") {
        username = retVal;
        websocket = new WebSocket(wsUri);
        websocket.onopen = function (evt) {
            login();
        };
        websocket.onmessage = function (evt) {
            handleResponse(evt)
        };
        websocket.onerror = function (evt) {
            onError(evt)
        };
    }
}

function login() {
    writeToScreen("onOpen");
    websocket.send("lreq" + username);
}

function handleResponse(evt) {
    var mString = evt.data.toString();
    if (mString.search("lres") == 0) {
        writeToScreen(evt.data);
        username = mString.substring(4, mString.length);
        refreshForUsernameChange();
    }
    if (mString.search("dres") == 0) {
        writeToScreen("Server confirmed disconnect");
    }
    if (mString.search("ctupd") == 0) {
        var transcriptUpdate = mString.substring(6, mString.length);
        updateTranscript(transcriptUpdate);
    }
    if (mString.search("ulupd") == 0) {
        writeToScreen("Userlistupdate: " + mString);
        var updateString = mString.substring(6, mString.length);
        writeToScreen("var " + updateString);
        refresh_userlist(updateString);
        writeToScreen("dfinished ");
    }
    writeToScreen('<span style="color: blue;">RESPONSE: ' + evt.data + '</span>');
}


function do_logout() {
    websocket.send("dreq" + username);
    username = "";
    refreshForUsernameChange();
    writeToScreen("ClosinG!!!");
    websocket.close();
}

function send_chatmessage() {
    var chatString = chatMessageTextID.value;
    if (chatString.length > 0) {
        websocket.send("ctmsg" + username + ":" + chatString);
        chatMessageTextID.value = "";
    }
}

function onError(evt) {
    writeToScreen('<span style="color: red;">ERROR:</span> ' + evt.data);
}

function isLoggedIn() {
    return (username != "");
}

function handleLoginLogout() {
    if (isLoggedIn()) {
        do_logout();
    } else {
        do_login();
    }
}

function writeToScreen(message) {
    if (debug) {
        var pre = document.createElement("p");
        pre.style.wordWrap = "break-word";
        pre.innerHTML = message;
        output.appendChild(pre);
    }
}

function refreshForUsernameChange() {
    writeToScreen("Refresh for " + username);
    var newTitle = "WSBean Chat Client";
    if (isLoggedIn()) {
        newTitle = newTitle + ":" + username;
        SendChatButtonID.disabled = false;
        chatMessageTextID.disabled = false;
        LoginButtonID.value = "Logout";
    } else {
        writeToScreen("blank user");
        SendChatButtonID.disabled = true;
        chatMessageTextID.disabled = true;
        LoginButtonID.value = "Login";
        chatTranscriptID.textContent = "";
        userListID.textContent = "";
    }
    var titleNode = document.getElementById("titleID");
    titleNode.textContent = newTitle;
}

function updateTranscript(str) {
    chatTranscriptID.textContent = chatTranscriptID.textContent + "\n" + str;

}

function refresh_userlist(rawStr) {
    var indexOfNext = -1;
    var stringLeft = rawStr;
    var usernames = new Array();
    while (stringLeft.search(":") != -1) {
        var index = stringLeft.search(":");
        var nextPiece = stringLeft.substring(0, index);
        usernames.push(nextPiece);
        //writeToScreen("Next piece " + nextPiece);
        stringLeft = stringLeft.substring(index + 1, stringLeft.length);
        //writeToScreen("String left " + stringLeft);
    }
    usernames.push(stringLeft);
    userListID.textContent = "";
    var i = 0;
    for (i = 0; i < usernames.length; i++) {
        userListID.textContent = userListID.textContent + usernames[i];
        if (i < (usernames.length - 1)) {
            userListID.textContent = userListID.textContent + "\n";
        }
    }
}


window.addEventListener("load", init, false);


