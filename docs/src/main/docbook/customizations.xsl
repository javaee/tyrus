<?xml version="1.0" encoding="utf-8"?>
<!--

    DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.

    Copyright (c) 2013-2014 Oracle and/or its affiliates. All rights reserved.

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

-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:d="http://docbook.org/ns/docbook"
                exclude-result-prefixes="d"
                version="1.0">

    <xsl:import href="urn:docbkx:stylesheet"/>
    <!--xsl:import href="urn:docbkx:stylesheet/highlight.xsl"/-->

    <xsl:template name="user.footer.content">
        <xsl:element name="link">
            <xsl:attribute name="href">http://tyrus.java.net/sh/shCore.css</xsl:attribute>
            <xsl:attribute name="rel">stylesheet</xsl:attribute>
            <xsl:attribute name="type">text/css</xsl:attribute>
        </xsl:element>
        <xsl:element name="link">
            <xsl:attribute name="href">http://tyrus.java.net/sh/shThemeDefault.css</xsl:attribute>
            <xsl:attribute name="rel">stylesheet</xsl:attribute>
            <xsl:attribute name="type">text/css</xsl:attribute>
        </xsl:element>
        <xsl:element name="script">
            <xsl:attribute name="src">http://tyrus.java.net/sh/shCore.js</xsl:attribute>
            <xsl:attribute name="type">text/javascript</xsl:attribute>
        </xsl:element>
        <xsl:element name="script">
            <xsl:attribute name="src">http://tyrus.java.net/sh/shAutoloader.js</xsl:attribute>
            <xsl:attribute name="type">text/javascript</xsl:attribute>
        </xsl:element>
        <xsl:element name="script">
            <xsl:attribute name="type">text/javascript</xsl:attribute>
            function path() {
              var args = arguments,
                  result = []
                  ;

              for(var i = 0; i &lt; args.length; i++)
                  result.push(args[i].replace('@', 'http://tyrus.java.net/sh/'));

              return result
            };

            SyntaxHighlighter.autoloader.apply(null, path(
              'applescript            @shBrushAppleScript.js',
              'actionscript3 as3      @shBrushAS3.js',
              'bash shell             @shBrushBash.js',
              'coldfusion cf          @shBrushColdFusion.js',
              'cpp c                  @shBrushCpp.js',
              'c# c-sharp csharp      @shBrushCSharp.js',
              'css                    @shBrushCss.js',
              'delphi pascal          @shBrushDelphi.js',
              'diff patch pas         @shBrushDiff.js',
              'erl erlang             @shBrushErlang.js',
              'groovy                 @shBrushGroovy.js',
              'java                   @shBrushJava.js',
              'jfx javafx             @shBrushJavaFX.js',
              'js jscript javascript  @shBrushJScript.js',
              'perl pl                @shBrushPerl.js',
              'php                    @shBrushPhp.js',
              'text plain             @shBrushPlain.js',
              'py python              @shBrushPython.js',
              'ruby rails ror rb      @shBrushRuby.js',
              'sass scss              @shBrushSass.js',
              'scala                  @shBrushScala.js',
              'sql                    @shBrushSql.js',
              'vb vbnet               @shBrushVb.js',
              'xml xhtml xslt html    @shBrushXml.js'
            ));

            SyntaxHighlighter.all();
        </xsl:element>
        <xsl:element name="script">
              (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
              (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
              m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
              })(window,document,'script','//www.google-analytics.com/analytics.js','ga');

              ga('create', 'UA-41373632-1', 'java.net');
              ga('send', 'pageview');
        </xsl:element>
    </xsl:template>

    <xsl:template name="user.head.content">
        <xsl:element name="script">
            <xsl:attribute name="type">text/javascript</xsl:attribute>
              (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
              (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
              m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
              })(window,document,'script','//www.google-analytics.com/analytics.js','ga');

              ga('create', 'UA-41373632-1', 'java.net');
              ga('send', 'pageview');

        </xsl:element>
    </xsl:template>

    <xsl:template match="d:programlisting">
<xsl:element name="pre"><xsl:attribute name="class">
    toolbar: false;
    brush: <xsl:value-of select="@language"/>;
    <xsl:if test="@linenumbering = 'unnumbered'">gutter: false;</xsl:if>
    <xsl:if test="@startinglinenumber &gt; 1">first-line: <xsl:value-of select="@startinglinenumber"/>;</xsl:if>
</xsl:attribute><xsl:apply-templates/></xsl:element>
    </xsl:template>
</xsl:stylesheet>
