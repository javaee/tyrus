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


<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>

    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title >Group Shapes Demo</title>
    </head>
    <body>
        <h1 style="text-align:center">Group Drawing</h1>
        <p style="text-align:center">Click to draw below (use SHIFT key too) <p>

        <form style="text-align:center">
            <select id="shapeID">
                <option value="bigcircle">Big Circle</option>
                <option value="smallcircle">Small Circle</option>
                <option value="bigsquare">Big Square</option>
                <option value="smallsquare">Small Square</option>
            </select>
            <select id="colorID">
                <option value="red">Red</option>
                <option value="green">Green</option>
                <option value="blue">Blue</option>
                <option value="yellow">Yellow</option>
            </select>
            <input onclick="clear_canvas()" value="Clear" type="button">
            <input onclick="clone()" value="Clone" type="button">

        </form>


        <table style="text-align: left; width: 500px; margin-left: auto;
        margin-right: auto;" border="1" cellpadding="2" cellspacing="2">
            <tbody>
                <tr>
                    <td style=" text-align: center; vertical-align: top;">
                        <canvas id="myDrawing" width="500" height="500"></canvas>
                    </td>
                </tr>
            </tbody>
        </table>
        <div id="output"></div>
    </body>
         <script language="javascript" type="text/javascript">
            var drawingCanvas = document.getElementById('myDrawing');
            var websocket;
            var output;

            //alert(drawingCanvas);
            drawingCanvas.addEventListener("mousedown", mouseDown, false);
            drawingCanvas.addEventListener("mousemove", mouseMove, false);
            // Check the element is in the DOM and the browser supports canvas
            if(drawingCanvas.getContext) {
                // Initaliase a 2-dimensional drawing context
            } else {
                alter("oops");
            }

             function mouseMove(event) {
                 if (event.shiftKey) {
                    drawCircle(event.layerX, event.layerY);
                 }

            }

            function mouseDown(event) {

                drawCircle(event.layerX, event.layerY);
            }

            function drawCircleX(x, y, color, shape, notify) {
                var context = drawingCanvas.getContext('2d');
                var radius = 8;



                //Canvas commands go here
                //context.strokeStyle = "#000000";
                context.fillStyle = color;
                if (shape == 'smallcircle') {
                    context.beginPath();
                    context.arc(x,y,radius,0,Math.PI*2,true);
                    context.closePath();
                    context.fill();
                } else if (shape == 'bigcircle') {
                    context.beginPath();
                    context.arc(x,y,2*radius,0,Math.PI*2,true);
                    context.closePath();
                    context.fill();
                } else if (shape == 'bigsquare') {
                    //context.fillRect(0,1,10,10);
                    context.fillRect( (x-(2*radius)), (y-(2*radius)), (4*radius), (4*radius));
                    //context.fill();
                } else if (shape == 'smallsquare') {
                    context.fillRect( (x-(radius)), (y-(radius)), (2*radius), (2*radius));
                } else {
                    alert(shape);
                }

                if (notify) {
                    message = "{'"+shape+"': '" + x + "," + y + "," + radius+"'}";
                    websocket.send(message);
                }
            }

            function drawCircle(x, y) {
                var shapeElt = document.getElementById('shapeID');
                var colElt = document.getElementById('colorID');
                drawCircleX(x, y, colElt.value, shapeElt.value, true);
            }

            function updateShape(shape) {
                //var shape = '{"Circle":"173,137,10"}';

                var startDataIndex = shape.search(":");
                var shapeName =  shape.substring(2, startDataIndex-1);

                var definition = shape.substring(startDataIndex+2, shape.length-1);
                var nextIndex = definition.search(",");
                var x = definition.substring(0, nextIndex);
                definition = definition.substring(nextIndex+1, definition.length);
                nextIndex = definition.search(",");
                var y = definition.substring(0, nextIndex);
                var radius = definition.substring(nextIndex+1, definition.length-1);
                //alert(shapeName);
                drawCircleX(x, y, "blue", shapeName, false);
                drawCircleX(x, y, "blue", shapeName, false);
                drawCircleX(x, y, "blue", shapeName, false);
            }

            function init() {
                output = document.getElementById("output");
                websocket = new WebSocket("ws://localhost:8025/sample-draw/draw");
                websocket.onopen = function(evt) { onOpen(evt) };
                websocket.onmessage = function(evt) { onMessage(evt) };
                websocket.onerror = function(evt) { onError(evt) };
            }

            function clone() {
                window.open("drawingexample.jsp", "clone");
            }

            function testit() {
                var elt = document.getElementById('shapeID');
                alert(elt.value)
            }

            function clear_canvas() {
                var context = drawingCanvas.getContext('2d');
                context.fillStyle = "white";
                context.fillRect(0,0,500,500);
                context.fill();
            }

            function onMessage(evt) {
                updateShape(evt.data);
            }

            function onOpen(evt) {}
            function onError(evt) {}

            window.addEventListener("load", init, false);
        </script>
</html>
