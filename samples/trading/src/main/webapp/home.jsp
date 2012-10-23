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
    Document   : home.jsp
    Created on : Dec 6, 2011, 4:43:11 PM
    Author     : Danny Coward (danny.coward at oracle.com)
--%>

<%@page contentType="text/html" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html>
<script language="javascript" type="text/javascript">
var buddiesWebSocket;
var quotesWebSocket;
var twitterWebSocket;
var deleteQuoteForm;
var quoteElement;

function init() {
    window.setTimeout('init_buddies_ws()', 0);
    window.setTimeout('init_quotes_ws()', 5);
    window.setTimeout('init_twitter_ws()', 10);
}

function logout() {
    alert("logout");
}

function close_up() {
    buddiesWebSocket.send("deregister");
}

function init_buddies_ws() {
    buddiesWebSocket = new WebSocket("ws://localhost:8080/web/buddies");
    buddiesWebSocket.onopen = function (evt) {
        onBuddiesWSOpen(evt)
    };
    buddiesWebSocket.onmessage = function (evt) {
        onBuddiesWSMessage(evt)
    };
    buddiesWebSocket.onerror = function (evt) {
        onBuddiesWSError(evt)
    };
    show(buddies, "initializing....");
}

function onBuddiesWSOpen(evt) {
    show(buddies, "connected.");
    buddiesWebSocket.send("register");
}

function onBuddiesWSMessage(evt) {
    if (evt.data.search("usernames:") != -1) {
        var usernames = evt.data.substring(10, evt.data.length);
        refresh_buddies(usernames);
    }
    if (evt.data.search("activities:") != -1) {
        var activities = evt.data.substring(11, evt.data.length);
        display_activities(activities);

    }
}

function display_activities(activities) {
    var activitiesArray = parseArray(activities, ",");
    var activity = activitiesArray[0];
    var activityArray = parseArray(activity, "@");
    var message = "<p style = 'align middle'>Investor Acticity !<br>" + activityArray[0] + " " + activityArray[1] + " " + activityArray[2] + "</p>";
    doPopup(3, 't1', message);
}


function onBuddiesWSError(evt) {
    show(buddies, '<span style="color: red;">ERROR:</span> ' + evt.data);
}

function parseArray(data, token) {
    var indexOfNext = -1;
    var stringLeft = data;
    var arr = new Array();

    while (stringLeft.search(token) != -1) {
        var index = stringLeft.search(token);
        var nextPiece = stringLeft.substring(0, index);
        arr.push(nextPiece);
        stringLeft = stringLeft.substring(index + 1, stringLeft.length);
    }
    arr.push(stringLeft);
    return arr;
}

function refresh_buddies(rawStr) {
    var buddiesArray = parseArray(rawStr, ";");
    var i = 0;
    clear(buddies);
    var table = document.createElement("table");
    for (i = 0; i < buddiesArray.length; i++) {
        var row = document.createElement("tr");
        table.appendChild(row);
        var col = document.createElement("tr");
        row.appendChild(col);
        var buddy = buddiesArray[i];
        col.innerHTML = buddy;
    }
    buddies.appendChild(table);
}

function addstock() {
    addstockform.submit();
}

function refresh_quotes(rawStr) {
    var quoteStringsArray = parseArray(rawStr, ",");
    var i = 0;
    for (i = 0; i < quoteStringsArray.length; i++) {
        var pre = document.createElement("p");
        var qt = parseArray(quoteStringsArray[i], ";");
        quotes.appendChild(quoteNode(qt));
    }
}

function deleteQuote(symbol) {
    alert(symbol);
}

function addQuote() {
    var quote = prompt("Add a stock symbol : ", "KBR", "Input");
    if (quote != null) {
        quotesWebSocket.send("add:" + quote);
    }
}

function removeQuote(quote) {
    quotesWebSocket.send("remove:" + quote);
}


function quoteNode(quoteArray) {
    var table = document.createElement("table");
    table.align = "left";
    var row = document.createElement("tr");
    var col1 = document.createElement("td");
    col1.align = "left";
    col1.width = "66";
    col1.innerHTML = "<a href='http://finance.yahoo.com/q?s=" + quoteArray[0] + "&ql=1' target=\"_blank\">" + quoteArray[0] + "</a>";
    var col2 = document.createElement("td");
    col2.align = "left";
    col2.width = "66";
    col2.innerHTML = quoteArray[1];
    var col3 = document.createElement("td");
    col3.innerHTML = quoteArray[2];
    col3.align = "left";
    col3.width = "66px";
    col3.title = "share volume: " + quoteArray[3];
    var col4 = document.createElement("td");
    var symbol = "";//quoteArray[0];
    var onclick = "removeQuote(\"" + quoteArray[0] + "\")";
    col4.innerHTML = "<input type='button' value = '-' onclick='" + onclick + "' title='remove stock symbol'></input>";
    col4.align = "right";
    row.appendChild(col1);
    row.appendChild(col2);
    row.appendChild(col3);
    row.appendChild(col4);
    table.appendChild(row);
    return table;
}

function refresh_twitter(rawStr) {
    var twtr = parseArray(rawStr, ",");
    var i = 0;
    var table = document.createElement("table");
    table.style = "text-align: left";
    for (i = 0; i < twtr.length; i++) {
        var tweetArray = parseArray(twtr[i], ";");
        table.appendChild(tweetNode(tweetArray));
    }
    twitter.appendChild(table);
}

function tweetNode(tweetArray) {
    var row = document.createElement("tr");
    var col1 = document.createElement("td");
    var img = document.createElement("img");
    img.src = tweetArray[2];
    col1.appendChild(img);
    var col2 = document.createElement("td");
    col2.innerHTML = tweetArray[0];
    col2.innerHTML = "<a href='http://twitter.com/#!/" + tweetArray[3] + "' target=\"_blank\" >" + tweetArray[0] + "</a>";
    var col3 = document.createElement("td");
    col3.innerHTML = tweetArray[1];
    col3.innerHTML.valign = "top";
    row.appendChild(col1);
    row.appendChild(col2);
    row.appendChild(col3);
    col1.width = "100px";
    col2.width = "100px";
    col1.align = "center";
    col1.valign = "middle";
    col2.valign = "middle";
    col3.valign = "top";
    return row;
}

function clear_all() {
    clear(buddies);
    clear(quotes);
    clear(output);
    clear(twitter);
    buddiesWebSocket = null;
    quotesWebSocket = null;
    twitterWebSocket = null;
}

function clear(node) {
    if (node.hasChildNodes()) {
        while (node.childNodes.length >= 1) {
            node.removeChild(node.firstChild);
        }
    }
}

function init_quotes_ws() {
    quotesWebSocket = new WebSocket("ws://localhost:8080/web/quotes");
    quotesWebSocket.onopen = function (evt) {
        onQuotesWSOpen(evt)
    };
    quotesWebSocket.onmessage = function (evt) {
        onQuotesWSMessage(evt)
    };
    quotesWebSocket.onerror = function (evt) {
        onQuotesWSError(evt)
    };
    show(quotes, "initializing quotes....");
}

function onQuotesWSOpen(evt) {
    show(quotes, "connected.");
    quotesWebSocket.send("register");
}

function onQuotesWSMessage(evt) {
    clear(quotes);
    refresh_quotes(evt.data);
}

function onQuotesWSError(evt) {
    show(quotes, '<span style="color: red;">ERROR:</span> ' + evt.data);
}

function init_twitter_ws() {
    twitterWebSocket = new WebSocket("ws://localhost:8080/web/twitter");
    twitterWebSocket.onopen = function (evt) {
        onTwitterWSOpen(evt)
    };
    twitterWebSocket.onmessage = function (evt) {
        onTwitterWSMessage(evt)
    };
    twitterWebSocket.onerror = function (evt) {
        onTwitterWSError(evt)
    };
    show(twitter, "initializing twitter feed....");
}

function onTwitterWSOpen(evt) {
    show(twitter, "Connected.");
    twitterWebSocket.send("register");
}

function onTwitterWSMessage(evt) {
    clear(twitter);
    refresh_twitter(evt.data);
}

function onTwitterWSError(evt) {
    show(twitter, '<span style="color: red;">ERROR:</span> ' + evt.data);
}

function show(node, message) {
    node.innerHTML = node.innerHTML + "<br>" + message;
}

function debug(message) {
    var pre = document.createElement("p");
    pre.style.wordWrap = "break-word";
    pre.innerHTML = message;
    output.appendChild(pre);
}

function pw() {
    return window.innerWidth ||
            document.documentElement.clientWidth ||
            document.body.clientWidth
}
;

function mouseX(evt) {
    return evt.clientX ? evt.clientX + (document.documentElement.scrollLeft || document.body.scrollLeft) : evt.pageX;
}

function mouseY(evt) {
    return evt.clientY ? evt.clientY + (document.documentElement.scrollTop || document.body.scrollTop) : evt.pageY
}

function popUp(evt, oi) {
    if (document.getElementById) {
        var wp = pw();
        dm = document.getElementById(oi);
        ds = dm.style;
        st = ds.visibility;
        if (dm.offsetWidth)
            ew = dm.offsetWidth;
        else if (dm.clip.width)
            ew = dm.clip.width;
        if (st == "visible" || st == "show") {
            ds.visibility = "hidden";
        } else {
            tv = mouseY(evt) + 20;
            lv = mouseX(evt) - (ew / 4);
            if (lv < 2) lv = 2;
            else if (lv + ew > wp) lv -= ew / 2;
            lv += 'px';
            tv += 'px';
            ds.left = lv;
            ds.top = tv;
            ds.visibility = "visible";
        }
    }
}

function popXUp(x, y, oi) {
    if (document.getElementById) {
        var wp = pw();
        dm = document.getElementById(oi);
        ds = dm.style;
        st = ds.visibility;
        if (dm.offsetWidth)
            ew = dm.offsetWidth;
        else if (dm.clip.width)
            ew = dm.clip.width;
        if (st == "visible" || st == "show") {
            ds.visibility = "hidden";
        } else {
            tv = x + 20;
            lv = y - (ew / 4);
            if (lv < 2) lv = 2;
            else if (lv + ew > wp) lv -= ew / 2;
            lv += 'px';
            tv += 'px';
            ds.left = lv;
            ds.top = tv;
            ds.visibility = "visible";
        }
    }
}

function popup() {
    var seconds = 3;
    doPopup(seconds, 't1', "hello");
}

function doPopup(seconds, eltName, message) {
    var elt = document.getElementById(eltName);
    elt.innerHTML = message;
    var flash = 0;
    for (flash = 0; flash < 3; flash++) {
        window.setTimeout("popXUp(100,100,'t1')", flash * 1400);
        window.setTimeout("popXUp(100,100,'t1')", (flash * 1400) + 1750);
    }
}

window.addEventListener("load", init, false);
window.addEventListener("unload", close_up, false);


</script>
<link rel="stylesheet" href="tooltip.css" type="text/css"/>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>Stock Console</title>
</head>
<body>
<p align="center">
    <img src="banner.jpg"></img>
</p>

<div id="t1" class="tip">This is a <br>Javascript Tooltip</div>
<table style="background-color: white; text-align: center; width: 100%;" border="1" cellpadding="0" cellspacing="2">
    <tr>
        <td>
            <table style=" text-align: center; width: 100%; height: 500px" border="0" cellpadding="5" cellspacing="1">
                <tbody>
                <tr>
                    <th style="font:24px Bank Gothic,sans-serif;background-color: #FFFFCC">Investors</th>
                    <th style="font:24px Bank Gothic,sans-serif;background-color: #CCFF99">Quotes</th>
                    <th style="font:24px Bank Gothic,sans-serif;background-color: #99ccff">Twitter Buzz</th>
                </tr>
                <tr>
                    <td style="align: center; vertical-align: top;width: 120px"
                    ">
                <div id="buddies" style="font:20px Bank Gothic,sans-serif;"></div>
                </td>
                <td style="align: center; vertical-align: top;width: 230px">
                    <div id="quotes" style="font:16px Damascus,sans-serif;"></div>
                    <form align="right">
                        <input type="button" value="+" onclick="addQuote()" title="add stock symbol"> </input>
                    </form>
                </td>
                <td style="align: center; vertical-align: top;">
                    <div id="twitter" style="font:16px Damascus,sans-serif;"></div>
                </td>
                </tbody>
            </table>
        </td>
    </tr>
</table>
<div id="output"></div>
<table style="font:24px Bank Gothic,sans-serif; text-align: center; width: 100%;" border="0" cellpadding="2"
       cellspacing="1">
    <tr>
        <td>
            <a href="nowhere.com">Terms and Conditions</a>
        </td>
        <td>
            <a href="nowhere.com">Policy</a>
        </td>
        <td>
            <a href="nowhere.com">Account</a>
        </td>
        <td>
            <a href="/web/LogoutServlet">Logout</a>
        </td>
    </tr>
</table>
<input type="button" value="list" onclick="list_buddies('danny')"></input>
<input type="button" value="connect" onclick="init_buddies_ws()"></input>
<input type="button" value="quotes" onclick="init_quotes_ws()"></input>
<input type="button" value="twitter" onclick="init_twitter_ws()"></input>
<input type="button" value="close" onclick="close_up()"></input>
<input type="button" value="clear" onclick="clear_all()"></input>
<input type="button" value="initall" onclick="init()"></input>
<input type="button" value="popup" onclick="popup()"></input>
</body>
</html>
