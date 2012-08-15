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

<title id="titleID">Web Sockets Auction Client</title>

<script language="javascript" type="text/javascript" src="auction.js">
</script>

<div id="userID" style="text-align: right;"></div>

<h2 style="text-align: center;">Web Sockets Auction Client</h2>
<div style="text-align: center;">
    <img style=" width: 64px; height: 64px;" alt=""src="HTML5_Logo_512.png">
    <img style=" width: 64px; height: 64px;" alt=""src="websocket-sdk.png">
</div>
<br></br>

<table style=" text-align: center; " cellpadding="5" align="center"
       cellspacing="5">
    <tbody>
        <tr>
            <td>

                <table style=" text-align: center; " cellpadding="5" align="center"
                       cellspacing="5">
                    <tbody>
                        <tr>
                            <td style="vertical-align: top;" align="left">
                                <p><font color="blue">Item name</font></p>
                            </td>
                            <td style=" vertical-align: center; background-color: #E8E8E8;" align="left">
                                <div id="nameID"></div>
                            </td>
                        </tr>
                        <tr>
                            <td style="vertical-align: top;" align="left">
                                <p><font color="blue">Item description</font></p>
                            </td>
                            <td style=" vertical-align: center; background-color: #E8E8E8;" align="left">
                                <div id="descriptionID"></div>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </td>
            <td>
                <table style=" text-align: center; " cellpadding="5" align="center"
                       cellspacing="5">
                    <tbody>
                        <tr>
                            <td style="vertical-align: top;" align="left">
                                <p><font color="blue">Auction starts in:</font></p>
                            </td>
                            <td style=" vertical-align: center; background-color: #E8E8E8;">
                                <textarea id ="startTimeID" rows="1" cols="30" readonly="readonly"></textarea>
                            </td>
                        </tr>
                        <tr>
                            <td style="vertical-align: top; " align="left">
                                <p><font color="blue">Current price is:</font></p>
                            </td>
                            <td style=" vertical-align: center; background-color: #E8E8E8;">
                                <textarea id ="currentPriceID" rows="1" cols="30" readonly="readonly"></textarea>
                            </td>
                        </tr>
                        <tr>
                            <td style="vertical-align: top;" align="left">
                                <p><font color="blue">Remaining bid time:</font></p>
                            </td>
                            <td style=" vertical-align: center; background-color: #E8E8E8;">
                                <textarea id ="remainingTimeID" rows="1" cols="30" readonly="readonly"></textarea>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </td>
        </tr>
    </tbody>
</table>
<br></br>
<div id="resultID" style="text-align: center;"></div>
<br></br>
<div style="text-align: center;">
    <h3>Your New Bid:</h3>
    <form action="">
        <input id="bidID" size="20" name="bidtext" value="" type="text"></input><br>
    </form>
    <br>
    <form action="">
        <input type="button" id ="sendBidID" onclick="sendBid()" value="Bid" >
        <input type="button" id ="backButtonID" onclick="goBack()" value="Exit the auction" >
    </form>
</div>

<div id="output"></div>

</html>
