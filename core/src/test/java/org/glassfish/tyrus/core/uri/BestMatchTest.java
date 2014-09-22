/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2014 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;

import org.glassfish.tyrus.core.ComponentProviderService;
import org.glassfish.tyrus.core.DebugContext;
import org.glassfish.tyrus.core.TyrusEndpointWrapper;

import org.junit.Test;
import static org.junit.Assert.assertNull;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.fail;

/**
 * @author dannycoward
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class BestMatchTest {

    @Test
    public void testBasicExactMatch() {
        try {
            List<TestWebSocketEndpoint> endpoints = Arrays.asList(new TestWebSocketEndpoint("/a"), new TestWebSocketEndpoint("/a/b"), new TestWebSocketEndpoint("/a/b/c"));

            verifyResult(endpoints, "/a", "/a");
            verifyResult(endpoints, "/a/b", "/a/b");
            verifyResult(endpoints, "/a/b/c", "/a/b/c");
            verifyResult(endpoints, "/d", null);
            verifyResult(endpoints, "/", null);
            verifyResult(endpoints, "/a/b/c/d", null);
            verifyResult(endpoints, "/d/d/d", null);

        } catch (DeploymentException e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testSingleVariableTemplates() {
        try {
            List<TestWebSocketEndpoint> endpoints = Collections.singletonList(new TestWebSocketEndpoint("/a/{var}"));

            verifyResult(endpoints, "/a", null);
            verifyResult(endpoints, "/a/b/c", null);
            verifyResult(endpoints, "/a/b", "/a/{var}");

        } catch (DeploymentException e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testMultipleVariableTemplates() {
        try {
            List<TestWebSocketEndpoint> endpoints = Arrays.asList(new TestWebSocketEndpoint("/{var1}"), new TestWebSocketEndpoint("/{var1}/{var2}"), new TestWebSocketEndpoint("/{var1}/{var2}/{var3}"));

            verifyResult(endpoints, "/a", "/{var1}");
            verifyResult(endpoints, "/a/b", "/{var1}/{var2}");
            verifyResult(endpoints, "/a/b/c", "/{var1}/{var2}/{var3}");
            verifyResult(endpoints, "/a/b/c/d", null);

        } catch (DeploymentException e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testExactMatchWinsOverVariableMatch() {
        try {
            List<TestWebSocketEndpoint> endpoints = Arrays.asList(new TestWebSocketEndpoint("/a/b/c"), new TestWebSocketEndpoint("/a/{var2}/{var3}"), new TestWebSocketEndpoint("/a/{var2}/c"));

            verifyResult(endpoints, "/a/b/c", "/a/b/c");
            verifyResult(endpoints, "/a/d/c", "/a/{var2}/c");
            verifyResult(endpoints, "/a/x/y", "/a/{var2}/{var3}");

        } catch (DeploymentException e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testLeftRightMatchPrecedence() {
        try {
            List<TestWebSocketEndpoint> endpoints = Arrays.asList(new TestWebSocketEndpoint("/{var1}/d"), new TestWebSocketEndpoint("/b/{var2}"));

            verifyResult(endpoints, "/b/d", "/b/{var2}");

        } catch (DeploymentException e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testMoreLeftRightPrecedenceMatch() {
        try {
            List<TestWebSocketEndpoint> endpoints = Arrays.asList(new TestWebSocketEndpoint("/a"), new TestWebSocketEndpoint("/{var1}"), new TestWebSocketEndpoint("/a/b"), new TestWebSocketEndpoint("/{var1}/b"), new TestWebSocketEndpoint("/a/{var2}"));

            verifyResult(endpoints, "/a", "/a");
            verifyResult(endpoints, "/x", "/{var1}");
            verifyResult(endpoints, "/a/b", "/a/b");
            verifyResult(endpoints, "/x/y", null);
            verifyResult(endpoints, "/x/b", "/{var1}/b");
            verifyResult(endpoints, "/a/y", "/a/{var2}");

        } catch (DeploymentException e) {
            e.printStackTrace();
            fail();
        }
    }


    private void verifyResult(List<TestWebSocketEndpoint> endpoints, String testedUri, String expectedMatchedPath) {
        Match m = getBestMatch(testedUri, new HashSet<TyrusEndpointWrapper>(endpoints));
        System.out.println("  Match for " + testedUri + " calculated is: " + m);

        if (expectedMatchedPath != null) {
            assertNotNull("Was expecting a match on " + expectedMatchedPath + ", but didn't get one.", m);
            assertEquals("Wrong path matched.", expectedMatchedPath, m.getEndpointWrapper().getEndpointPath());
        } else {// shouldn't be a match
            assertNull("Wasn't expecting a match, but got one.", m);
        }
    }

    private Match getBestMatch(String incoming, Set<TyrusEndpointWrapper> thingsWithPath) {
        List<Match> sortedMatches = Match.getAllMatches(incoming, thingsWithPath, new DebugContext());
        if (sortedMatches.isEmpty()) {
            return null;
        } else {
            return sortedMatches.get(0);
        }
    }

    private static class TestWebSocketEndpoint extends TyrusEndpointWrapper {

        private final String path;

        private TestWebSocketEndpoint(String path) throws DeploymentException {
            super(TestEndpoint.class, null, ComponentProviderService.createClient(), null, null, null, null, null, null, null);
            this.path = path;
        }

        @Override
        public String getEndpointPath() {
            return path;
        }

        public static class TestEndpoint extends Endpoint {
            @Override
            public void onOpen(Session session, EndpointConfig config) {

            }
        }
    }
}
