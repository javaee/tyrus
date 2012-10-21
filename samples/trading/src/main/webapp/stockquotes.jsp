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
    Document   : stockquotes
    Created on : Dec 7, 2011, 10:48:58 AM
    Author     : dannycoward
--%>

<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    
    <script type="text/javascript" src="http://www.google.com/jsapi"></script>
    <script type="text/javascript">

    // Load the latest version of the Google Data JavaScript Client
    google.load('gdata', '2.x');

    function onGoogleDataLoad() {
        alert("no");
        var quote = new google.finance.Quote();
        alert(quote);
        quote.enableDomUpdates( { 'GOOG' : '_IG_SYM1', 'AAPL' : '_IG_SYM2', 'INTC' : '_IG_SYM3' } );
        quote.getQuotes(["GOOG", "AAPL", "INTC"]);
    }

  // Call function once the client has loaded
    google.setOnLoadCallback(onGoogleDataLoad);

    </script>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>JSP Page</title>
    </head>
    <body>
        <h1>Hello World!</h1>
        Hello world!
        Here is your portfolio:<br/>
        GOOG: <span id=_IG_SYM1_l></span> (<span id=_IG_SYM1_c></span>)<br/>
        AAPL: <span id=_IG_SYM2_l></span> (<span id=_IG_SYM2_c></span>)<br/>
        INTC: <span id=_IG_SYM3_l></span> (<span id=_IG_SYM3_c></span>)<br/>
    </body>
</html>
