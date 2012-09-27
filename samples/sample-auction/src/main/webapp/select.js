var wsUri = "ws://localhost:8025/sample-auction/auction";
var output;
var debug = false;
var websocket;
var separator = ":";
var id = 0;
var name = "";


function init()
{
    output = document.getElementById("output");
    name = getParam("name");

    writeToScreen("init name: "+name);
    websocket = new WebSocket(wsUri);
    websocket.onopen = function(evt) {
        getData();
    };
    websocket.onmessage = function(evt) {
        handleResponse(evt)
    };
    websocket.onerror = function(evt) {
        onError(evt)
    };
}

function getData(){
    var myStr = "xreq"+separator+id+separator+"selectList";
    websocket.send(myStr);
}

function handleResponse(evt) {
    var mString = evt.data.toString();
    writeToScreen(evt.data);
    if (mString.search("xres") == 0) {
        var message = mString.substring(4, mString.length);
        var messageList = message.split('-'); // split on hyphen
        var i=0;

        for (i=1; i<messageList.length-1; i+=2)
        {
            var val = messageList[i];
            var text = messageList[i+1];
            document.getElementById( "comboID" ).add(new Option(text,val),null);
        }
    }

    writeToScreen('<span style="color: blue;">RESPONSE: ' + evt.data+'</span>');
}

function selected(){
    writeToScreen("Select");
    var myselect = document.getElementById("comboID");
    for (var i=0; i<myselect.options.length; i++){
        if (myselect.options[i].selected==true){
            var link = "auction.jsp"+"?id="+myselect.options[i].value+"&name="+name;
            break
        }
    }
        window.location = link;
}

function onError(evt)
{
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

function getParam ( sname )
{
  var params = location.search.substr(location.search.indexOf("?")+1);
  var sval = "";
  params = params.split("&");
    // split param and value into individual pieces
    for (var i=0; i<params.length; i++)
       {
         temp = params[i].split("=");
         if ( [temp[0]] == sname ) { sval = temp[1]; }
       }
  return sval;
}

window.addEventListener("load", init, false);