<%--

    DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.

    Copyright (c) 2011 - 2012 Oracle and/or its affiliates. All rights reserved.

    The contents of this file are subject to the terms of either the GNU
    General Public License Version 2 only ("GPL") or the Common Development
    and Distribution License("CDDL") (collectively, the "License").  You
    may not use this file except in compliance with the License.  You can
    obtain a copy of the License at
    http://glassfish.java.net/public/CDDL+GPL_1_1.html
    or packager/legal/LICENSE.txt.  See the License for the specific
    language governing permissions and limitations under the License.

    When distributing the software, include this License Header Notice in each
    file and include the License file at packager/legal/LICENSE.txt.

    GPL Classpath Exception:
    Oracle designates this particular file as subject to the "Classpath"
    exception as provided by Oracle in the GPL Version 2 section of the License
    file that accompanied this code.

    Modifications:
    If applicable, add the following below the License Header, with the fields
    enclosed by brackets [] replaced by your own identifying information:
    "Portions Copyright [year] [name of copyright owner]"

    Contributor(s):
    If you wish your version of this file to be governed by only the CDDL or
    only the GPL Version 2, indicate your decision by adding "[Contributor]
    elects to include this software in this distribution under the [CDDL or GPL
    Version 2] license."  If you don't indicate a single choice of license, a
    recipient has the option to distribute your version of this file under
    either the CDDL, the GPL Version 2 or to extend the choice of license to
    its licensees as provided above.  However, if you add GPL Version 2 code
    and therefore, elected the GPL Version 2 license, then the option applies
    only if the new code is made subject to such option by the copyright
    holder.

--%>
<%--
    Document   : index
    Created on : Oct 12, 2011, 5:52:25 PM
    Author     : Danny Coward (danny.coward at oracle.com)
--%>

<%@page contentType="text/html" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html>
<script language="javascript" type="text/javascript">
    var thisWsUri = "ws://localhost:8080/websockets/tests/this";
    var thatWsUri = "ws://localhost:8080/websockets/tests/that";

    function init() {
        output = document.getElementById("output");
    }

    function do_this() {
        websocket = new WebSocket(thisWsUri);
        websocket.onopen = function (evt) {
            onOpen("THIS", evt)
        };
        websocket.onmessage = function (evt) {
            onMessage("THIS", evt)
        };
        websocket.onerror = function (evt) {
            onError(evt)
        };

    }

    function do_that() {

        websocket = new WebSocket(thatWsUri);
        websocket.onopen = function (evt) {
            onOpen("THAT", evt)
        };
        websocket.onmessage = function (evt) {
            onMessage("THAT", evt)
        };
        websocket.onerror = function (evt) {
            onError(evt)
        };

    }

    function onOpen(who, evt) {
        writeToScreen(who + " connected");
        doSend(who, "hello from JavaScript (" + who + ")");

    }

    function onMessage(who, evt) {
        writeToScreen(who + " got: " + evt.data);
    }

    function onError(evt) {
        writeToScreen('<span style="color: red;">ERROR:</span> ' + evt.data);
    }

    function doSend(who, message) {
        writeToScreen(who + " sent: " + message);
        websocket.send(message);
    }

    function writeToScreen(message) {
        var pre = document.createElement("p");
        pre.style.wordWrap = "break-word";
        pre.innerHTML = message;
        //alert(output);
        output.appendChild(pre);
    }

    window.addEventListener("load", init, false);

</script>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>JSP Page</title>
</head>
<body>
This page tests the creation of two web sockets mapped to
distinct URLs.
<div style="text-align: center;">
    <form action="">
        <input onclick="do_this()" value="Do This" type="button">
        <input onclick="do_that()" value="Do That" type="button">
    </form>
</div>
<div id="output"></div>
</body>
</html>
