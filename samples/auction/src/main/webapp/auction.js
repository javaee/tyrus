/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2015 Oracle and/or its affiliates. All rights reserved.
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

var output;
var username = "";
var debug = false;
var websocket;
var separator = ":";
var id = 0;

var endpointPath = "/auction";
var wsUri = getRootUri() + endpointPath;

/**
 * Get application root uri with ws/wss protocol.
 *
 * @returns {string}
 */
function getRootUri() {
    var uri = "ws://" + (document.location.hostname == "" ? "localhost" : document.location.hostname) + ":" +
        (document.location.port == "" ? "8080" : document.location.port);

    var pathname = window.location.pathname;

    if (endsWith(pathname, "/auction.html")) {
        uri = uri + pathname.substring(0, pathname.length - 13);
    } else if (endsWith(pathname, "/")) {
        uri = uri + pathname.substring(0, pathname.length - 1);
    }

    return uri;
}

function endsWith(str, suffix) {
    return str.indexOf(suffix, str.length - suffix.length) !== -1;
}

function init() {
    output = document.getElementById("output");
    //writeToScreen("init()");
    //chatTranscriptElt = document.getElementByID("chatTranscriptID");
    id = getParam("id");
    //refreshForUsernameChange();
    do_login();
}


function do_login() {
    username = getParam("name");
    writeToScreen("name: " + username);
    websocket = new WebSocket(wsUri);
    websocket.onopen = function (evt) {
        login();
    };
    websocket.onmessage = function (evt) {
        handleResponse(evt);
    };
    websocket.onerror = function (evt) {
        onError(evt);
    };

    var usr = document.createElement("p");
    usr.style.wordWrap = "break-word";
    usr.innerHTML = "User: " + username;
    document.getElementById("userID").appendChild(usr);
}

function login() {
    writeToScreen("onOpen");
    var myStr = "lreq" + separator + id + separator + username;
    websocket.send(myStr);
    writeToScreen("loginFinished");
}


function handleResponse(evt) {
    var mString = evt.data.toString();
    writeToScreen(evt.data);
    if (mString.search("lres") === 0) {
        var message = mString.substring(4, mString.length);
        var messageList = message.split(':'); // split on colon

        var pre = document.createElement("p");
        pre.style.wordWrap = "break-word";
        pre.innerHTML = messageList[2];
        document.getElementById("nameID").appendChild(pre);

        var pre2 = document.createElement("p");
        pre2.style.wordWrap = "break-word";
        pre2.innerHTML = messageList[3];
        document.getElementById("descriptionID").appendChild(pre2);

        document.getElementById("currentPriceID").value = messageList[4];
        document.getElementById("remainingTimeID").value = "Bidding allowed once the auction is started";
        document.getElementById("startTimeID").value = messageList[6];
    }
    if (mString.search("cres") === 0) {
        document.getElementById("currentPriceID").value = "Auction closed already";
        document.getElementById("remainingTimeID").value = "Auction closed already";
        document.getElementById("startTimeID").value = "Auction closed already";
    }
    if (mString.search("ares") === 0) {
        var message2 = mString.substring(4, mString.length);
        var messageList2 = message2.split(':');
        document.getElementById("startTimeID").value = "Auction Started";
        document.getElementById("remainingTimeID").value = messageList2[2];
    }
    if (mString.search("tres") === 0) {
        var message1 = mString.substring(4, mString.length);
        var messageList1 = message1.split(':');

        text = messageList1[2] + " days " + messageList1[3] + " hours " + messageList1[4] + " minutes " + messageList1[5] + " seconds ";
        document.getElementById("startTimeID").value = text;
    }
    if (mString.search("pres") === 0) {
        message3 = mString.substring(4, mString.length);
        messageList3 = message3.split(':');
        document.getElementById("currentPriceID").value = messageList3[2];
    }
    if (mString.search("rres") === 0) {
        message4 = mString.substring(4, mString.length);
        messageList4 = message4.split(':');
        var res = document.createElement("p");
        res.style.wordWrap = "break-word";
        res.innerHTML = '<span style="color: red;">Auction Result:</span> ' + messageList4[2];
        document.getElementById("currentPriceID").value = "Auction closed already";
        document.getElementById("remainingTimeID").value = "Auction closed already";
        document.getElementById("startTimeID").value = "Auction closed already";
        document.getElementById("resultID").appendChild(res);
    }
    if (mString.search("dres") === 0) {
        writeToScreen("Server confirmed disconnect");
    }
    if (mString.search("ctupd") === 0) {
        var transcriptUpdate = mString.substring(6, mString.length);
        updateTranscript(transcriptUpdate);
    }
    if (mString.search("ulupd") === 0) {
        writeToScreen("Userlistupdate: " + mString);
        var updateString = mString.substring(6, mString.length);
        writeToScreen("var " + updateString);
        refresh_userlist(updateString);
        writeToScreen("dfinished ");
    }
    writeToScreen('<span style="color: blue;">RESPONSE: ' + evt.data + '</span>');
}


function sendBid() {
    var bidString = document.getElementById("bidID").value;
    if (bidString.length > 0) {
        websocket.send("breq" + separator + id + separator + bidString);
        //chatMessageTextID.value = "";
    }
    bidString = document.getElementById("bidID").value = "";
}

function onError(evt) {
    writeToScreen('<span style="color: red;">ERROR:</span> ' + evt.data);
}

function isLoggedIn() {
    return (username !== "");
}

function writeToScreen(message) {
    if (debug) {
        var pre = document.createElement("p");
        pre.style.wordWrap = "break-word";
        pre.innerHTML = message;
        output.appendChild(pre);
    }
}

function goBack() {
    var link = "select.html" + "?id=" + id + "&name=" + username;
    var myStr = "dreq" + separator + id + separator + username;
    websocket.send(myStr);
    websocket.close();
    window.location = link;
}

function getParam(sname) {
    var params = location.search.substr(location.search.indexOf("?") + 1);
    var sval = "";
    params = params.split("&");
    // split param and value into individual pieces
    for (var i = 0; i < params.length; i++) {
        var temp = params[i].split("=");
        if (temp[0] === sname) {
            sval = temp[1];
        }
    }
    return sval;
}

window.addEventListener("load", init, false);


