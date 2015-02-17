/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
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

window.removeEvent = function (id) {
//    $("#" + id).hide('slow', function() {$(this).remove();});
    $("#" + id).remove();

    var start = $("#event_container").height() - $(".event").height();
    var counter = 0;

    $(".event").each(function () {
        if (counter < 7) {
            $(this).stop(true);
            $(this).animate({top: start - (counter * ($(".event").height() + 5))}, {
                duration: 1000,
                easing: 'easeOutBounce'
            });
            $(this).show();
        } else {
            $(this).hide();
        }
        counter++;
    })
};

window.accept = function (eventId) {

    var data = JSON.stringify({"id": eventId});
    window.websocket.send(data);
};

window.wsUrl = function (s) {
    var l = window.location;
    return ((l.protocol === "https:") ? "wss://" : "ws://") + l.hostname + (((l.port != 80) && (l.port != 443)) ? ":" + l.port : "") + s;
};

Date.prototype.timeNow = function () {
    return ((this.getHours() < 10) ? "0" : "") + this.getHours() + ":" + ((this.getMinutes() < 10) ? "0" : "") + this.getMinutes() + ":" + ((this.getSeconds() < 10) ? "0" : "") + this.getSeconds();
};

function init() {

    window.graphColor = 'black';

    // WebSocket
    window.websocket = new WebSocket(window.wsUrl("/sample-btc-xchange/market"));
    var websocket = window.websocket;
    websocket.onopen = function () {
        console.log("ws onopen");
    };
    websocket.onmessage = function (evt) {

        console.log("ws onmessage: " + evt.data);

        var message = JSON.parse(evt.data);

        if (message.type === "invalidate") {
            window.removeEvent(message.id);
        } else if (message.type === "balance") {

            var log = $("#console");
            var balanceUSD = $("#balanceUSD");
            var balanceBTC = $("#balanceBTC");

            log.prepend(
                new Date().timeNow() +
                " Transaction recorded: " +
                (message.usdDelta).toFixed(2) + " USD, " +
                (message.btcDelta / 1000).toFixed(3) + " BTC<br />");

            balanceUSD.html((message.usd).toFixed(2));
            balanceBTC.html((message.btc / 1000).toFixed(3));

        } else if (message.type === "name") {
            //$("#marketName").html(message.name);
            //window.graphColor = message.color;
            initGraph();
        } else if (message.type === "price-update") {
            $("#price").html(message.price);
            window.throughput.series.addData({one: message.price});
            window.throughput.render();
        } else {
            var container = $("#event_container");
            var event = $(".event");

            container.append(
                "<div id='" + message.id + "' class='event'><div>" +

                    // BUY X BTC
                message.type + " " + (message.amount / 1000).toFixed(3) + " BTC<br />" +
                    // for Y USD
                    // "for " + ((message.amount / 1000) * message.price).toFixed(2) + "USD<br />" +
                    // X USD per BTC
                "<small class='badge'>" + message.price + " USD/BTC</small>" +
                    // [buy/sell]
                "</div><button class='btn btn-default' onclick='window.accept(" + message.id + ")'>" + message.type + "</button>" +
                "</div>");

            if (message.type === "sell") {
                $("#" + message.id).addClass("eventSell");
            } else {
                $("#" + message.id).addClass("eventBuy");
            }

            if (event.length > 7) {
                $("#" + message.id).hide();
            } else {
                var start = container.height() - $(".event").height();

                $("#" + message.id).animate({top: start - (event.length * (event.height() + 5))}, {
                    duration: 1000,
                    easing: 'easeOutBounce'
                });
            }
        }
    };
    websocket.onclose = function () {
        console.log("ws onclose");
    };
    websocket.onerror = function () {
        console.log("ws onerror");
    };
}

function initGraph() {
    var tv = 1000;

    window.throughput = new Rickshaw.Graph({
        element: document.querySelector("#throughput_chart"),
        width: 700,
        height: 400,
        renderer: 'line',
        interpolation: 'step-after',
        stroke: true,
        preserve: true,
        max: 282,
        min: 218,
        series: new Rickshaw.Series.FixedDuration([{
            name: 'one', color: window.graphColor
        }], undefined, {
            timeInterval: tv,
            maxDataPoints: 100,
            timeBase: new Date().getTime() / 1000
        })
    });

    var ticksTreatment = 'glow';

    var xAxis = new Rickshaw.Graph.Axis.Time({
        graph: window.throughput,
        ticksTreatment: ticksTreatment,
        timeFixture: new Rickshaw.Fixtures.Time.Local()
    });

    xAxis.render();

    var yAxis = new Rickshaw.Graph.Axis.Y({
        graph: window.throughput,
        tickFormat: Rickshaw.Fixtures.Number.formatKMBT,
        ticksTreatment: ticksTreatment
    });

    yAxis.render();

    for (var i = 0; i < 100; i++) {
        window.throughput.series.addData({one: 250});
    }

    window.throughput.render();
}


$(function () {

    init();

});
