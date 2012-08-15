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
    Author     : dannycoward
--%>

<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <script language="javascript" type="text/javascript">
            var baseUri = 'ws://' + document.location.host + '/basic-tests'
           

            function init() {
                output = document.getElementById("output");
            }
            
            function clearoutput() {
               if ( output.hasChildNodes() ) {
                    while ( output.childNodes.length >= 1 )
                    {
                        output.removeChild( output.firstChild );       
                    } 
                }
            }
            
            function run_new_window_test(uri) {
                window.open(uri, uri);
            }
            
            function run_input_tests() {
                do_this(baseUri + "/standardInputTypes/String", "StringIn Test", "String");
                window.setTimeout('do_this(baseUri + "/standardInputTypes/boolean", "boolean Test", "true")', 1000);
                window.setTimeout('do_this(baseUri + "/standardInputTypes/int", "int Test", "42")', 1500);
                window.setTimeout('do_this(baseUri + "/standardInputTypes/short", "short Test", "42")', 2000);
                window.setTimeout('do_this(baseUri + "/standardInputTypes/long", "long Test", "42")', 2500);
                window.setTimeout('do_this(baseUri + "/standardInputTypes/char", "char Test", "c")', 3000);
                window.setTimeout('do_this(baseUri + "/standardInputTypes/float", "float Test", "42.0")', 3500);
                window.setTimeout('do_this(baseUri + "/standardInputTypes/double", "double Test", "42.0")', 4000);
                
            } 
            
            function run_output_tests() {
                do_this(baseUri + "/standardOutputTypes/byte", "byte out Test", "String");
                window.setTimeout('do_this(baseUri + "/standardOutputTypes/short", "short Test", "x")', 1000);
                window.setTimeout('do_this(baseUri + "/standardOutputTypes/int", "int Test", "x")', 1500);
                window.setTimeout('do_this(baseUri + "/standardOutputTypes/long", "long Test", "x")', 2000);
                window.setTimeout('do_this(baseUri + "/standardOutputTypes/float", "float Test", "x")', 2500);
                window.setTimeout('do_this(baseUri + "/standardOutputTypes/double", "double Test", "x")', 3000);
                window.setTimeout('do_this(baseUri + "/standardOutputTypes/boolean", "boolean Test", "x")', 3500);
                window.setTimeout('do_this(baseUri + "/standardOutputTypes/char", "char Test", "x")', 4000);
            }
            
            function run_context_tests() {
                do_this(baseUri + "/getcontext", "context injection", "x");
                
            }
            
            function run_custom_client_tests() {
                do_this(baseUri + "/customremote/hello", "hellocustomclient", "hello");
                window.setTimeout('do_this(baseUri + "/customremote/encoded", "encodedcustomclient", "rawstring")', 1000);
                window.setTimeout('do_this(baseUri + "/customremote/int", "intcustomclient", "42")', 1500);
                window.setTimeout('do_this(baseUri + "/customremote/byte", "bytecustomclient", "1")', 2000);
                window.setTimeout('do_this(baseUri + "/customremote/short", "shortcustomclient", "1")', 2500);
                window.setTimeout('do_this(baseUri + "/customremote/long", "longcustomclient", "100")', 3000);
                window.setTimeout('do_this(baseUri + "/customremote/float", "floatcustomclient", "1.5")', 3500);
                window.setTimeout('do_this(baseUri + "/customremote/double", "doublecustomclient", "1.50")', 4000);
                window.setTimeout('do_this(baseUri + "/customremote/boolean", "booleancustomclient", "true")', 4500);
                window.setTimeout('do_this(baseUri + "/customremote/char", "charcustomclient", "c")', 5000);

            }

            function do_this(uri, testname, testmessage) {
                //writeToScreen("attempting " + uri);
                websocket = new WebSocket(uri);
                websocket.onopen = function(evt) { onOpen(websocket, testmessage, testname, evt) };
                websocket.onmessage = function(evt) { onMessage(websocket, testname, evt) };
                websocket.onerror = function(evt) { onError(testname, evt) };
                writeToScreen("Connected to " + uri);
            }
            

            function onOpen(websocket, testmessage, who, evt) {
                
                //writeToScreen(who + " connected");
                doSend(websocket, who, testmessage);
                
                
            }

            function onMessage(websocket, who, evt) {
                writeToScreen(who + " : " + evt.data);
                websocket.close();
            }

            function onError(who, evt) {
                writeToScreen('<span style="color: red;">ERROR:</span> ' + evt.data);
            }

            function doSend(websocket, who, message) {
                //writeToScreen(who + " sending: " + message);
                websocket.send(message);
                //writeToScreen("message sent to " + who);
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
        This page does some automated testing.
        <div style="text-align: center;">
            <form action=""> 
                <input onclick="run_input_tests()" value="Run Input Tests" type="button"> 
                <input onclick="run_output_tests()" value="Run Output Tests" type="button"> 
                <input onclick="run_custom_client_tests()" value="Custom Remote Tests" type="button"> 
                <input onclick="run_context_tests()" value="Context Tests" type="button"> 
                <input onclick="run_new_window_test('twoMethodRemote.jsp')" value="2 meth remote" type="button">
                <input onclick="run_new_window_test('sessions.jsp')" value="Sessions" type="button">
                <input onclick="run_new_window_test('jsonhello.jsp')" value="JSON" type="button">
                <input onclick="run_new_window_test('errortest.jsp')" value="Error" type="button">
                <input onclick="run_new_window_test('decodermultiplexer.jsp')" value="DecoderMultiplexer" type="button">
                <input onclick="run_new_window_test('sessionremoteobjects.jsp')" value="Number of sessions" type="button">
                <input onclick="run_new_window_test('session2websockets.jsp')" value="2 sessions one context" type="button">
                <input onclick="run_new_window_test('subprotocols.jsp')" value="Subprotocols" type="button">
                <input onclick="run_new_window_test('hellodeployhello.jsp')" value="dynamic deploy" type="button">
                <input onclick="run_new_window_test('dynamicpathstest.jsp')" value="dynamic paths" type="button">
                <input onclick="window.open('/basic/CDIServlet')" value="CDI Monitoring" type="button">

                <input onclick="clearoutput()" value="Clear" type="button">
            </form>
        </div>
        <div id="output"></div>
    </body>
</html>

