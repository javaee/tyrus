
  var wsUri = "ws://localhost:8080/sample-chat/chat";
  var output;
  var username = "";
  var debug = false;
  var chatTranscriptElt;
  var messageTextElt;
  var websocket;


  function init()
  {
    output = document.getElementById("output");
    //chatTranscriptElt = document.getElementByID("chatTranscriptID");
    refreshForUsernameChange();
  }
  
  function test() {
      writeToScreen(chatMessageTextID.value);
 
  }
  
  function clone() {
      alert("agh");
      //window.open("mychat.jsp", "chat2");
  }

  function do_login()
  {
    var retVal = prompt("Enter your name : ", "guest-user", "hello");
    if (retVal != "") {
        username = retVal;
        websocket = new WebSocket(wsUri);
        websocket.onopen = function(evt) { login(); };
        websocket.onmessage = function(evt) { handleResponse(evt) };
        websocket.onerror = function(evt) { onError(evt) };
    }
  }
  
  function login()
  {
    writeToScreen("onOpen");
    websocket.send("lreq"+username);
  }
  
  function handleResponse(evt) {
    var mString = evt.data.toString();
    if (mString.search("lres") == 0) {
      writeToScreen(evt.data);
      username = mString.substring(4, mString.length);
      refreshForUsernameChange();
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
        refresh_userlist(updateString)
        writeToScreen("dfinished ");
    }
    writeToScreen('<span style="color: blue;">RESPONSE: ' + evt.data+'</span>');
  }
 
  
  function do_logout() {
      websocket.send("dreq" + username);
      username = "";
      refreshForUsernameChange();
      websocket.close();
  }
  
  function send_chatmessage() {
      var chatString = chatMessageTextID.value;
      if (chatString.length > 0) {
        websocket.send("ctmsg" + username + ":" + chatString);
        chatMessageTextID.value = "";
      }
  }

  function onError(evt)
  {
    writeToScreen('<span style="color: red;">ERROR:</span> ' + evt.data);
  }
  
  function isLoggedIn() {
      return (username != "");
  }
  
  function handleLoginLogout() {
      if (isLoggedIn()) {
          do_logout();
      } else {
          do_login();
      }
  }
  
  function writeToScreen(message) {
      if (debug) {
        var pre = document.createElement("p");
        pre.style.wordWrap = "break-word";
        pre.innerHTML = message;
        output.appendChild(pre);
    }
  }
  
  function refreshForUsernameChange() {
      writeToScreen("Refresh for " + username);
      var newTitle = "WSBean Chat Client"
      if (isLoggedIn()) {
          newTitle = newTitle + ":" + username;
          SendChatButtonID.disabled = false;
          chatMessageTextID.disabled = false;
          LoginButtonID.value = "Logout";
      } else {
          writeToScreen("blank user");
          SendChatButtonID.disabled = true;
          chatMessageTextID.disabled = true;
          LoginButtonID.value = "Login";
          chatTranscriptID.textContent = "";
          userListID.textContent = "";
      }
      var titleNode = document.getElementById("titleID");
      titleNode.textContent = newTitle;
  }
  
  function updateTranscript(str) {
      chatTranscriptID.textContent = chatTranscriptID.textContent + "\n" + str;

  }
  
  function refresh_userlist(rawStr) {
      var indexOfNext = -1;
      var stringLeft = rawStr;
      var usernames = new Array();
      while (stringLeft.search(":") != -1) {
          var index = stringLeft.search(":");
          var nextPiece = stringLeft.substring(0, index);
          usernames.push(nextPiece);
          //writeToScreen("Next piece " + nextPiece);
          stringLeft = stringLeft.substring(index + 1, stringLeft.length);
          //writeToScreen("String left " + stringLeft);
      }
      usernames.push(stringLeft);
      userListID.textContent = "";
      var i=0;
      for (i=0; i < usernames.length; i++) {
          userListID.textContent = userListID.textContent + usernames[i];
          if ( i < (usernames.length - 1)) {
              userListID.textContent = userListID.textContent + "\n";
          }
      }
  }
  


  window.addEventListener("load", init, false);
 

