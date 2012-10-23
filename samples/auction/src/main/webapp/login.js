var wsUri = "ws://" + document.location.host + "/sample-auction/auction";
var output;
var debug = false;
var websocket;
var separator = ":";
var id = 0;


function init() {
    output = document.getElementById("output");
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

function login() {
}

function doLogin() {
    var myStr = "lreq" + separator + id + separator + document.getElementById("loginID").value;
    websocket.send(myStr);
    window.setTimeout('to_select()', 10);
}

function to_select() {
    var link = "select.jsp?name=" + document.getElementById("loginID").value;
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

window.addEventListener("load", init, false);
