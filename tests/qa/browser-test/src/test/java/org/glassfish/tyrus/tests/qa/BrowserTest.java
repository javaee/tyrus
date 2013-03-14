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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Assert;

import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/**
 *
 * @author michal.conos at oracle.com
 */
public class BrowserTest {

    private static final Logger logger = Logger.getLogger(BrowserTest.class.getCanonicalName());

    @After
    public void cleanup() {
        SeleniumToolkit.quit();
    }

    
    @Test
    public void testFirefoxClientSimpleHandshake() throws InterruptedException {
        logger.log(Level.INFO, "===============testFirefoxClientHandshake===============");
        TestScenarios ts = new TestScenarios(new SeleniumToolkit(SeleniumToolkit.Browser.FIREFOX));
        ts.testSimpleHandshake();
        logger.log(Level.INFO, "========================================================");
    }
    
    
    @Test
    public void testFirefoxClientChat() throws InterruptedException, Exception {
        logger.log(Level.INFO, "============testFirefoxClientChat=================");
        TestScenarios ts = new TestScenarios(new SeleniumToolkit(SeleniumToolkit.Browser.FIREFOX));
        ts.testChatSample();
        logger.log(Level.INFO, "==================================================");
    }
        
    @Test
    public void testFirefoxClientChatWithTwoUsers() throws InterruptedException, Exception {
        logger.log(Level.INFO, "============testFirefoxClientChatWithTwoUsers=================");
        SeleniumToolkit aliceBrowser = new SeleniumToolkit(SeleniumToolkit.Browser.FIREFOX);
        SeleniumToolkit bobBrowser = new SeleniumToolkit(SeleniumToolkit.Browser.FIREFOX);
        TestScenarios ts = new TestScenarios(aliceBrowser, bobBrowser);
        ts.testChatSampleWithTwoUsers();
        logger.log(Level.INFO, "==============================================================");
    }
    
    @Test
    public void testFirefoxClientChatWith100Users() throws InterruptedException, Exception {
        List<SeleniumToolkit> toolkits = new ArrayList<SeleniumToolkit>();
        // Launch 100 browsers
        for(int idx=0; idx<TestScenarios.MAX_CHAT_CLIENTS; idx++) {
            toolkits.add(new SeleniumToolkit(SeleniumToolkit.Browser.FIREFOX));
        }
        TestScenarios ts = new TestScenarios(toolkits.toArray(new SeleniumToolkit[toolkits.size()]));
        ts.testChatSampleWith100Users();
    }
    
    @Test
    public void testFirefoxClientAuction() throws InterruptedException, Exception {
        logger.log(Level.INFO, "============testFirefoxClientAuction=================");
        TestScenarios ts = new TestScenarios(new SeleniumToolkit(SeleniumToolkit.Browser.FIREFOX));
        ts.testAuctionSample();
        logger.log(Level.INFO, "=====================================================");
    }

    @Test
    public void testChromeClient() throws InterruptedException {
        logger.log(Level.INFO, "===============testChromeClientHandshake===============");
        TestScenarios ts = new TestScenarios(new SeleniumToolkit(SeleniumToolkit.Browser.CHROME));
        ts.testSimpleHandshake();
        logger.log(Level.INFO, "=======================================================");
    }
    
     @Test
    public void testChromeClientChat() throws InterruptedException, Exception {
        logger.log(Level.INFO, "============testChromeClientChat=================");
        TestScenarios ts = new TestScenarios(new SeleniumToolkit(SeleniumToolkit.Browser.CHROME));
        ts.testChatSample();
        logger.log(Level.INFO, "==================================================");
    }
        
    @Test
    public void testChromefoxClientChatWithTwoUsers() throws InterruptedException, Exception {
        logger.log(Level.INFO, "============testChromeClientChatWithTwoUsers=================");
        SeleniumToolkit aliceBrowser = new SeleniumToolkit(SeleniumToolkit.Browser.CHROME);
        SeleniumToolkit bobBrowser = new SeleniumToolkit(SeleniumToolkit.Browser.CHROME);
        TestScenarios ts = new TestScenarios(aliceBrowser, bobBrowser);
        ts.testChatSampleWithTwoUsers();
        logger.log(Level.INFO, "==============================================================");
    }
    
    @Test
    public void testChromeClientChatWith100Users() throws InterruptedException, Exception {
        List<SeleniumToolkit> toolkits = new ArrayList<SeleniumToolkit>();
        // Launch 100 browsers
        for(int idx=0; idx<TestScenarios.MAX_CHAT_CLIENTS; idx++) {
            toolkits.add(new SeleniumToolkit(SeleniumToolkit.Browser.CHROME));
        }
        TestScenarios ts = new TestScenarios(toolkits.toArray(new SeleniumToolkit[toolkits.size()]));
        ts.testChatSampleWith100Users();
    }
    
    @Test
    public void testChromeClientAuction() throws InterruptedException, Exception {
        logger.log(Level.INFO, "============testFirefoxClientAuction=================");
        TestScenarios ts = new TestScenarios(new SeleniumToolkit(SeleniumToolkit.Browser.CHROME));
        ts.testAuctionSample();
        logger.log(Level.INFO, "=====================================================");
    }
    
    //
    // Visit http://code.google.com/p/selenium/wiki/SafariDriver to know more about Safari Driver
    // installation.
    //
    @Test
    public void testSafariClient() throws InterruptedException {
        Assume.assumeTrue(SeleniumToolkit.safariPlatform());
        logger.log(Level.INFO, "===============testChromeClientHandshake===============");
        TestScenarios ts = new TestScenarios(new SeleniumToolkit(SeleniumToolkit.Browser.SAFARI));
        ts.testSimpleHandshake();
        logger.log(Level.INFO, "=======================================================");
    }

    @Test
    public void testInternetExplorerClientSimpleHandshake() throws InterruptedException {
        Assume.assumeTrue(SeleniumToolkit.onWindows()); // skip this test on non-Windows platforms
        logger.log(Level.INFO, "==========testInternetExplorerClientHandshake==========");
        TestScenarios ts = new TestScenarios(new SeleniumToolkit(SeleniumToolkit.Browser.IE));
        ts.testSimpleHandshake();
        logger.log(Level.INFO, "=======================================================");
    }
    
}
