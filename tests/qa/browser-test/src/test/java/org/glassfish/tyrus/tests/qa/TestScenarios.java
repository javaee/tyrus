/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.tests.qa;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Logger;
import org.junit.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/**
 *
 * @author michal.conos at oracle.com
 */
public class TestScenarios {

    private final String DEFAULT_HOST = "localhost";
    private final int DEFAULT_INSTANCE_PORT = 8080;
    private final String CONTEXT_PATH = "/browser-test";
    private static final Logger logger = Logger.getLogger(TestScenarios.class.getCanonicalName());
    SeleniumToolkit toolkit = null;

    public TestScenarios(SeleniumToolkit toolkit) {
        this.toolkit = toolkit;
    }

    private String getHost() {
        final String host = System.getProperty("tyrus.test.host");
        if (host != null) {
            return host;
        }
        return DEFAULT_HOST;
    }

    private int getPort() {
        final String port = System.getProperty("tyrus.test.port");
        if (port != null) {
            try {
                return Integer.parseInt(port);
            } catch (NumberFormatException nfe) {
                // do nothing
            }
        }
        return DEFAULT_INSTANCE_PORT;
    }

    private URI getURI() {
        try {
            return new URI("http", null, getHost(), getPort(), CONTEXT_PATH, null, null);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void testSimpleHandshake() throws InterruptedException {
        toolkit.get(getURI().toString() + "/reset.jsp");
        toolkit.get(getURI().toString() + "/status.jsp");
        Assert.assertEquals("Status NONE after deployment/reset", "Status: NONE", toolkit.bodyText());
        toolkit.get(getURI().toString());
        toolkit.get(getURI().toString() + "/status.jsp");
        Assert.assertEquals("Status BROWSER_OKAY after test", "Status: BROWSER_OKAY", toolkit.bodyText());
    }
}
