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

    private void simpleClientHandShake(SeleniumToolkit.Browser browser) throws InterruptedException {
        logger.log(Level.INFO, "===============testSimpleClientHandshake===============");
        TestScenarios ts = new TestScenarios(new SeleniumToolkit(browser));
        ts.testSimpleHandshake();
        logger.log(Level.INFO, "========================================================");
    }

    private void clientChat(SeleniumToolkit.Browser browser) throws InterruptedException, Exception {
        logger.log(Level.INFO, "============testClientChatApp=================");
        TestScenarios ts = new TestScenarios(new SeleniumToolkit(browser));
        ts.testChatSample();
        logger.log(Level.INFO, "==================================================");
    }

    private void twoClientsChat(SeleniumToolkit.Browser alice, SeleniumToolkit.Browser bob) throws InterruptedException, Exception {
        logger.log(Level.INFO, "============testClientChatWithTwoUsers=================");
        SeleniumToolkit aliceBrowser = new SeleniumToolkit(alice);
        SeleniumToolkit bobBrowser = new SeleniumToolkit(bob);
        TestScenarios ts = new TestScenarios(aliceBrowser, bobBrowser);
        ts.testChatSampleWithTwoUsers();
        logger.log(Level.INFO, "==============================================================");
    }

    private void chatScalabitlity(SeleniumToolkit.Browser browser) throws InterruptedException, Exception {
        logger.log(Level.INFO, "=============testScalabilityWith" + TestScenarios.MAX_CHAT_CLIENTS + "Users===============================");
        List<SeleniumToolkit> toolkits = new ArrayList<SeleniumToolkit>();
        // Launch 100 browsers
        for (int idx = 0; idx < TestScenarios.MAX_CHAT_CLIENTS; idx++) {
            toolkits.add(new SeleniumToolkit(browser));
        }
        TestScenarios ts = new TestScenarios(toolkits.toArray(new SeleniumToolkit[toolkits.size()]));
        ts.testChatSampleWith100Users();
        logger.log(Level.INFO, "==============================================================");
    }

    private void auctionTest(SeleniumToolkit.Browser browser) throws InterruptedException, Exception {
        logger.log(Level.INFO, "============testClientAuction=================");
        TestScenarios ts = new TestScenarios(new SeleniumToolkit(SeleniumToolkit.Browser.FIREFOX));
        ts.testAuctionSample();
        logger.log(Level.INFO, "=====================================================");
    }

    @Test
    public void testFirefoxClientSimpleHandshake() throws InterruptedException {
        simpleClientHandShake(SeleniumToolkit.Browser.FIREFOX);
    }

    @Test
    public void testFirefoxClientChat() throws InterruptedException, Exception {
        clientChat(SeleniumToolkit.Browser.FIREFOX);
    }

    @Test
    public void testFirefoxClientChatWithTwoUsers() throws InterruptedException, Exception {
        twoClientsChat(SeleniumToolkit.Browser.FIREFOX, SeleniumToolkit.Browser.FIREFOX);
    }

    @Test
    public void testFirefoxClientChatWith100Users() throws InterruptedException, Exception {
        chatScalabitlity(SeleniumToolkit.Browser.FIREFOX);
    }

    @Test
    public void testFirefoxClientAuction() throws InterruptedException, Exception {
        auctionTest(SeleniumToolkit.Browser.FIREFOX);
    }

    @Test
    public void testChromeClient() throws InterruptedException {
        simpleClientHandShake(SeleniumToolkit.Browser.CHROME);
    }

    @Test
    public void testChromeClientChat() throws InterruptedException, Exception {
        clientChat(SeleniumToolkit.Browser.CHROME);
    }

    @Test
    public void testChromefoxClientChatWithTwoUsers() throws InterruptedException, Exception {
        twoClientsChat(SeleniumToolkit.Browser.CHROME, SeleniumToolkit.Browser.CHROME);
    }

    @Test
    public void testChromeClientChatWith100Users() throws InterruptedException, Exception {
        chatScalabitlity(SeleniumToolkit.Browser.CHROME);
    }

    @Test
    public void testChromeClientAuction() throws InterruptedException, Exception {
        auctionTest(SeleniumToolkit.Browser.CHROME);
    }

    //
    // Visit http://code.google.com/p/selenium/wiki/SafariDriver to know more about Safari Driver
    // installation.
    //
    @Test
    public void testSafariClient() throws InterruptedException {
        Assume.assumeTrue(SeleniumToolkit.safariPlatform());
        simpleClientHandShake(SeleniumToolkit.Browser.SAFARI);
    }

    @Test
    public void testSafariClientChat() throws InterruptedException, Exception {
        Assume.assumeTrue(SeleniumToolkit.safariPlatform());
        clientChat(SeleniumToolkit.Browser.SAFARI);
    }

    @Test
    public void testSafariClientChatWithTwoUsers() throws InterruptedException, Exception {
        Assume.assumeTrue(SeleniumToolkit.safariPlatform());
        twoClientsChat(SeleniumToolkit.Browser.SAFARI, SeleniumToolkit.Browser.SAFARI);
    }

    @Test
    public void testSafariClientChatWith100Users() throws InterruptedException, Exception {
        Assume.assumeTrue(SeleniumToolkit.safariPlatform());
        chatScalabitlity(SeleniumToolkit.Browser.SAFARI);
    }

    @Test
    public void testSafariClientAuction() throws InterruptedException, Exception {
        Assume.assumeTrue(SeleniumToolkit.safariPlatform());
        auctionTest(SeleniumToolkit.Browser.SAFARI);
    }

    @Test
    public void testInternetExplorerClientSimpleHandshake() throws InterruptedException {
        Assume.assumeTrue(SeleniumToolkit.onWindows()); // skip this test on non-Windows platforms
        simpleClientHandShake(SeleniumToolkit.Browser.IE);
    }

    @Test
    public void testInternetExplorerClientChat() throws InterruptedException, Exception {
        Assume.assumeTrue(SeleniumToolkit.onWindows()); // skip this test on non-Windows platforms
        clientChat(SeleniumToolkit.Browser.IE);
    }

    @Test
    public void testInternetExplorerClientChatWithTwoUsers() throws InterruptedException, Exception {
        Assume.assumeTrue(SeleniumToolkit.onWindows()); // skip this test on non-Windows platforms
        twoClientsChat(SeleniumToolkit.Browser.IE, SeleniumToolkit.Browser.IE);
    }

    @Test
    public void testInterentExplorerClientChatWith100Users() throws InterruptedException, Exception {
        Assume.assumeTrue(SeleniumToolkit.onWindows()); // skip this test on non-Windows platforms
        chatScalabitlity(SeleniumToolkit.Browser.IE);
    }

    @Test
    public void testInternetExplorerClientAuction() throws InterruptedException, Exception {
        Assume.assumeTrue(SeleniumToolkit.onWindows()); // skip this test on non-Windows platforms
        auctionTest(SeleniumToolkit.Browser.IE);
    }
}
