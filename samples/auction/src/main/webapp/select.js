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
var debug = false;
var websocket;
var separator = ":";
var id = 0;
var name = "";

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

    if (endsWith(pathname, "/select.html")) {
        uri = uri + pathname.substring(0, pathname.length - 12);
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
    name = getParam("name");

    writeToScreen("init name: " + name);
    websocket = new WebSocket(wsUri);
    websocket.onopen = function (evt) {
        getData();
    };
    websocket.onmessage = function (evt) {
        handleResponse(evt)
    };
    websocket.onerror = function (evt) {
        onError(evt)
    };
}

function getData() {
    var myStr = "xreq" + separator + id + separator + "selectList";
    websocket.send(myStr);
}

function handleResponse(evt) {
    var mString = evt.data.toString();
    writeToScreen(evt.data);
    if (mString.search("xres") == 0) {
        var message = mString.substring(4, mString.length);
        var messageList = message.split('-'); // split on hyphen
        var i = 0;

        for (i = 1; i < messageList.length - 1; i += 2) {
            var val = messageList[i];
            var text = messageList[i + 1];
            document.getElementById("comboID").add(new Option(text, val), null);
        }
    }

    writeToScreen('<span style="color: blue;">RESPONSE: ' + evt.data + '</span>');
}

function selected() {
    writeToScreen("Select");
    var myselect = document.getElementById("comboID");
    for (var i = 0; i < myselect.options.length; i++) {
        if (myselect.options[i].selected == true) {
            var link = "auction.html" + "?id=" + myselect.options[i].value + "&name=" + name;
            break
        }
    }
    window.location = link;
}

function onError(evt) {
    writeToScreen('<span style="color: red;">ERROR:</span> ' + evt.data);
}

function writeToScreen(message) {
    if (debug) {
        var pre = document.createElement("p");
        pre.style.wordWrap = "break-word";
        pre.innerHTML = message;
        output.appendChild(pre);
    }
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
