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
<!DOCTYPE html>

<meta charset="utf-8" />

<title id="titleID">Web Sockets Chat Client</title>

<script language="javascript" type="text/javascript" src="chat.js">
</script>

<h2 style="text-align: center;">Web Sockets Chat Client</h2>
<div style="text-align: center;"><img style=" width: 64px; height: 64px;" alt=""src="HTML5_Logo_512.png"></div>
        <br></br>


<table style=" text-align: center; " cellpadding="5"
cellspacing="5">
<tbody>
<tr>
<td style=" vertical-align: top;">
    <textarea id ="chatTranscriptID" rows="10" cols="50" readonly="readonly"></textarea>
    <br>
</td>
<td style="vertical-align: top;">
    <textarea id ="userListID" rows="10" cols="15" readonly="readonly"></textarea>
    <br>
</td>
</tr>
</tbody>
</table>

<table style=" text-align: center; " cellpadding="5"
cellspacing="5">
<tbody>
<tr>
<td style=" vertical-align: top;">
    <form action=""> 
        <input id="chatMessageTextID" size="70" name="chattext" value="Hello everyone !" type="text"><br>
    </form>
    
    <br>
</td>
<td style="vertical-align: top;">
    
    
    <table style=" text-align: left; " cellpadding="0"
           cellspacing="2">
        <tr>
            <td style=" vertical-align: top;">
                <form action=""><input type="button" id ="SendChatButtonID" onclick="send_chatmessage()" value="Send" ></form>
            </td>
            <td style=" vertical-align: top;">
                <form action=""><input type="button" id ="LoginButtonID" onclick="handleLoginLogout()" value="Login" ></form>

            </td>
            
        </tr>
        
        
    </table>
    
    
    <br>
</td>
</tr>
</tbody>
</table>


<div id="output"></div>

</html> 
