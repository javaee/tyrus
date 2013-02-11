var wsUri = getRootUri() + "/sample-auction/auction";
var output;
var username = "";
var debug = false;
var websocket;
var separator = ":";
var id = 0;

function getRootUri() {
    return "ws://" + (document.location.hostname == "" ? "localhost" : document.location.hostname) + ":" +
        (document.location.port == "" ? "8080" : document.location.port);
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
        handleResponse(evt)
    };
    websocket.onerror = function (evt) {
        onError(evt)
    };

    var usr = document.createElement("p");
    usr.style.wordWrap = "break-word";
    usr.innerHTML = username;
    document.getElementById("userID").appendChild("User: " + username);
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
    if (mString.search("lres") == 0) {
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
    if (mString.search("ares") == 0) {
        var message2 = mString.substring(4, mString.length);
        var messageList2 = message2.split(':');
        document.getElementById("startTimeID").value = "Auction Started";
        document.getElementById("remainingTimeID").value = messageList2[2];
    }
    if (mString.search("tres") == 0) {
        var message1 = mString.substring(4, mString.length);
        var messageList1 = message1.split(':');

        text = messageList1[2] + " days " + messageList1[3] + " hours " + messageList1[4] + " minutes " + messageList1[5] + " seconds ";
        document.getElementById("startTimeID").value = text;
    }
    if (mString.search("pres") == 0) {
        message3 = mString.substring(4, mString.length);
        messageList3 = message3.split(':');
        document.getElementById("currentPriceID").value = messageList3[2];
    }
    if (mString.search("rres") == 0) {
        message4 = mString.substring(4, mString.length);
        messageList4 = message4.split(':');
        var res = document.createElement("p");
        res.style.wordWrap = "break-word";
        res.innerHTML = '<span style="color: red;">Auction Result:</span> ' + messageList4[2];
        document.getElementById("resultID").appendChild(res);
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


function sendBid() {
    var bidString = document.getElementById("bidID").value;
    if (bidString.length > 0) {
        websocket.send("breq" + separator + id + separator + bidString);
        chatMessageTextID.value = "";
    }
    bidString = document.getElementById("bidID").value = "";
}

function onError(evt) {
    writeToScreen('<span style="color: red;">ERROR:</span> ' + evt.data);
}

function isLoggedIn() {
    return (username != "");
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
        temp = params[i].split("=");
        if ([temp[0]] == sname) {
            sval = temp[1];
        }
    }
    return sval;
}

window.addEventListener("load", init, false);


