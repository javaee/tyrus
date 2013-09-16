/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.core.uri;

import java.net.URI;

import org.glassfish.tyrus.core.uri.TestBestMatch;
import org.glassfish.tyrus.core.uri.TestEquivalentPaths;
import org.glassfish.tyrus.core.uri.TestWebSocketApplication;

import org.junit.Test;


public class UriMatchingTest {

    @Test
    public void test() throws Exception {
        runStaticPathTests(); // just a few static checks for equivalent paths
        runMatchingTests(); // best matching cases
    }

    public static void runStaticPathTests() {
        TestEquivalentPaths basicTest = new TestEquivalentPaths("Basic test");
        basicTest
                .addPath("/a/b")
                .addPath("/a/b")
                .addPath("/a/b/c");

        basicTest.runTest(true);

        TestEquivalentPaths testWithTemplates = new TestEquivalentPaths("Templates test");
        testWithTemplates
                .addPath("/a/{var2}")
                .addPath("/a/b")
                .addPath("/b/{var29}");
        testWithTemplates.runTest(false);

        TestEquivalentPaths morePathsTest = new TestEquivalentPaths("More paths test");
        morePathsTest
                .addPath("/a/{var2}/c")
                .addPath("/a/{var2}")
                .addPath("/b/{var2}/c")
                .addPath("/b/{var2}/{var3}")
                .addPath("/a/{m}");

        morePathsTest.runTest(true);
    }

    public static void runMatchingTests() throws Exception {
        TestBestMatch basicExactMatching = new TestBestMatch("Basic exact match testing");
        basicExactMatching
                .addEP(new TestWebSocketApplication("/a"))
                .addEP(new TestWebSocketApplication("/a/b"))
                .addEP(new TestWebSocketApplication("/a/b/c"));

        basicExactMatching.setUri(new URI("/a")).verifyResult(true, "/a");
        basicExactMatching.setUri(new URI("/a/b")).verifyResult(true, "/a/b");
        basicExactMatching.setUri(new URI("/a/b/c")).verifyResult(true, "/a/b/c");
        basicExactMatching.setUri(new URI("/d")).verifyResult(false, null);
        basicExactMatching.setUri(new URI("/")).verifyResult(false, null);
        basicExactMatching.setUri(new URI("/a/b/c/d")).verifyResult(false, null);
        basicExactMatching.setUri(new URI("/d/d/d")).verifyResult(false, null);


        TestBestMatch basicSingleVariable = new TestBestMatch("Basic single variable templates");
        basicSingleVariable
                .addEP(new TestWebSocketApplication("/a/{var}"));

        basicSingleVariable.setUri(new URI("/a")).verifyResult(false, null);
        basicSingleVariable.setUri(new URI("/a/b/c")).verifyResult(false, null);
        basicSingleVariable.setUri(new URI("/a/b")).verifyResult(true, "/a/{var}");


        TestBestMatch multiVariableMatch = new TestBestMatch("Multivariable Match");
        multiVariableMatch
                .addEP(new TestWebSocketApplication("/{var1}"))
                .addEP(new TestWebSocketApplication("/{var1}/{var2}"))
                .addEP(new TestWebSocketApplication("/{var1}/{var2}/{var3}"));

        multiVariableMatch.setUri(new URI("/a")).verifyResult(true, "/{var1}");
        multiVariableMatch.setUri(new URI("/a/b")).verifyResult(true, "/{var1}/{var2}");
        multiVariableMatch.setUri(new URI("/a/b/c")).verifyResult(true, "/{var1}/{var2}/{var3}");
        multiVariableMatch.setUri(new URI("/a/b/c/d")).verifyResult(false, null);

        TestBestMatch exactVsVariableMatchPrecedence = new TestBestMatch("Exact Match wins over variable match");
        exactVsVariableMatchPrecedence
                .addEP(new TestWebSocketApplication("/a/b/c"))
                .addEP(new TestWebSocketApplication("/a/{var2}/{var3}"))
                .addEP(new TestWebSocketApplication("/a/{var2}/c"));

        exactVsVariableMatchPrecedence.setUri(new URI("/a/b/c")).verifyResult(true, "/a/b/c");
        exactVsVariableMatchPrecedence.setUri(new URI("/a/d/c")).verifyResult(true, "/a/{var2}/c");
        exactVsVariableMatchPrecedence.setUri(new URI("/a/x/y")).verifyResult(true, "/a/{var2}/{var3}");


        TestBestMatch leftRightPrecedence = new TestBestMatch("Left Right precedence Match");
        leftRightPrecedence
                .addEP((new TestWebSocketApplication("/{var1}/d")))
                .addEP((new TestWebSocketApplication("/b/{var2}")));

        leftRightPrecedence.setUri(new URI("/b/d")).verifyResult(true, "/b/{var2}");


        TestBestMatch moreLeftRightPrecedence = new TestBestMatch("More Left Right precedence Match");
        moreLeftRightPrecedence
                .addEP((new TestWebSocketApplication("/a")))
                .addEP((new TestWebSocketApplication("/{var1}")))
                .addEP((new TestWebSocketApplication("/a/b")))
                .addEP((new TestWebSocketApplication("/{var1}/b")))
                .addEP((new TestWebSocketApplication("/a/{var2}")));
        moreLeftRightPrecedence.setUri(new URI("/a")).verifyResult(true, "/a");
        moreLeftRightPrecedence.setUri(new URI("/x")).verifyResult(true, "/{var1}");
        moreLeftRightPrecedence.setUri(new URI("/a/b")).verifyResult(true, "/a/b");
        moreLeftRightPrecedence.setUri(new URI("/x/y")).verifyResult(false, null);
        moreLeftRightPrecedence.setUri(new URI("/x/b")).verifyResult(true, "/{var1}/b");
        moreLeftRightPrecedence.setUri(new URI("/a/y")).verifyResult(true, "/a/{var2}");
    }
}
