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
import java.util.HashSet;
import java.util.Set;

import org.glassfish.tyrus.core.WebSocketApplication;

/**
 * @author dannycoward
 */
public class TestBestMatch {
    private URI uri;
    private final Set<WebSocketApplication> eps = new HashSet<WebSocketApplication>();
    private final String title;

    public TestBestMatch(String title) {
        this.title = title;
    }

    public TestBestMatch addEP(TestWebSocketApplication ep) {
        this.eps.add(ep);
        return this;
    }

    public TestBestMatch setUri(URI uri) {
        this.uri = uri;
        return this;
    }


    public void verifyResult(boolean shouldHaveAMatch, String whichPathMatched) {
        System.out.println("RUNNING MATCH TEST: " + this.title + ", eps=" + this.eps);
        Match m = Match.getBestMatch(this.uri.toString(), this.eps);
        System.out.println("  Match for " + this.uri + " calculated is: " + m);
        if (shouldHaveAMatch) {
            if (m == null) {
                throw new RuntimeException("Test Failed: was expecting a match on " + whichPathMatched + ", but didn't get one.");
            } else {
                if (!m.getPath().equals(whichPathMatched)) {
                    throw new RuntimeException("Test Failed: wrong path matched. Expected " + whichPathMatched + ", but got " + m.getPath());
                } else {
                    System.out.println("Test passed: expected match on " + whichPathMatched + ", got " + m.getPath());
                }
            }
        } else {// shouldn't be a match
            if (m != null) {
                System.out.println("Test failed, wasn't expecting a match, got one.");
                throw new RuntimeException("Test failed");
            } else {
                System.out.println("Test passed, expected no match for " + this.uri + ", got no match.");
            }
        }
    }
}
