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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import org.junit.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

/**
 *
 * @author michal.conos at oracle.com
 */
public class TestScenarios {

    class ChatSample {
        private final String CHAT_CONTEXT_PATH = "/sample-chat";

        SeleniumToolkit session;

        public ChatSample(SeleniumToolkit session) {
            this.session = session;
        }

        void login(String user) throws InterruptedException, Exception {
            // Get the URI of sample-chat application
            session.get(getURI(CHAT_CONTEXT_PATH).toString());
            // Login as user mikc
            session.click(By.id("LoginButtonID"));
            session.getDriver().switchTo().alert().sendKeys(user);
            Thread.sleep(2000);
            session.getDriver().switchTo().alert().accept();
        }
        
        void logout() throws Exception {
            session.click(By.id("LoginButtonID"));
        }

        void sendMessage(String message) throws Exception {
            session.sendKeys(By.id("chatMessageTextID"), message);
            // Send the message
            session.click(By.id("SendChatButtonID"));
        }
        
        String getChatWindowText() {
            return session.getTextById("chatTranscriptID");
        }
        
        String getChatUsersWindowText() {
            return session.getTextById("userListID");
        }
    }
    
    class AuctionSample {
        private static final String AUCTION_CONTEXT_PATH = "/sample-auction";
        public static final int AUCTION_TIMEOUT=35000;
        
        SeleniumToolkit session;
        JavascriptExecutor js;
        
        public AuctionSample(SeleniumToolkit session) {
            this.session = session;
            js = (JavascriptExecutor) session.getDriver();
        }
        
        void login(String user) throws InterruptedException, Exception {
            session.get(getURI(AUCTION_CONTEXT_PATH).toString());
            session.sendKeys(By.id("loginID"), user);
            session.click(By.id("loginButtonID"));
        }
        
        void chooseAuction(String item) throws Exception {
            session.click(By.xpath("//option[text()='"+item+"']"));
            session.click(By.id("selectButtonID"));
        }
        
        private String jsValue(String id) {
            return String.valueOf(js.executeScript("return document.getElementById('"+id+"').value"));
        }
        
        String getAuctionStatus() {
            return jsValue("startTimeID");
            
        }
        
        Float getItemCurrentPrice() {
            return new Float(jsValue("currentPriceID"));
        }
        
        String getAuctionRemainingTime() {
            return jsValue("remainingTimeID");
        }
        
        void bidOnItem(Float howMuch) throws InterruptedException, Exception {
            session.sendKeys(By.id("bidID"), String.valueOf(howMuch.floatValue()));
            session.click(By.id("sendBidID"));
        }
        
        void exit() throws Exception {
            session.click(By.id("backButtonID"));
        }
        
        
    }
    
    private final String DEFAULT_HOST = "localhost";
    private final int DEFAULT_INSTANCE_PORT = 8080;
    private final String HANDSHAKE_CONTEXT_PATH = "/browser-test";
    static final int MAX_CHAT_CLIENTS=100;
    
    private static final Logger logger = Logger.getLogger(TestScenarios.class.getCanonicalName());
    SeleniumToolkit toolkit = null;
    List<SeleniumToolkit> toolkits = new CopyOnWriteArrayList<SeleniumToolkit>();

    public TestScenarios(SeleniumToolkit toolkit) {
        this.toolkit = toolkit;
        toolkits.add(toolkit);
    }

    public TestScenarios(SeleniumToolkit ... toolKits) {
        if (toolKits.length <= 0) {
            throw new IllegalArgumentException("toolkits array has zero length!");
        }
        for (SeleniumToolkit tool : toolKits) {
            toolkits.add(tool);
        }
        this.toolkit = toolkits.get(0);
    }
    
    public boolean numOfClientsGreaterThan(int n) {
        return toolkits.size()>=n;
    }

    public boolean hasOneClient() {
        return toolkits.size() == 1;
    }

    public boolean hasAtLeastTwoClients() {
        return numOfClientsGreaterThan(2);
    }

    public boolean hasAtLeastOneClient() {
        return numOfClientsGreaterThan(1);
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

    private URI getURI(String ctx) {
        try {
            return new URI("http", null, getHost(), getPort(), ctx, null, null);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void testSimpleHandshake() throws InterruptedException {
        Assert.assertTrue("we need at least one client connecting", hasAtLeastOneClient());
        toolkit.get(getURI(HANDSHAKE_CONTEXT_PATH).toString() + "/reset.jsp");
        toolkit.get(getURI(HANDSHAKE_CONTEXT_PATH).toString() + "/status.jsp");
        Assert.assertEquals("Status NONE after deployment/reset", "Status: NONE", toolkit.bodyText());
        toolkit.get(getURI(HANDSHAKE_CONTEXT_PATH).toString());
        toolkit.get(getURI(HANDSHAKE_CONTEXT_PATH).toString() + "/status.jsp");
        Assert.assertEquals("Status BROWSER_OKAY after test", "Status: BROWSER_OKAY", toolkit.bodyText());
    }

    public void testChatSample() throws InterruptedException, Exception {
        Assert.assertTrue("we need at least one client connecting", hasAtLeastOneClient());
        ChatSample session = new ChatSample(toolkit);
        session.login("Sam");
        session.sendMessage("Hello guys");
        // check we get what we wanted
        Assert.assertTrue("We got the message from the client", session.getChatWindowText().contains("Hello guys"));
        Assert.assertTrue("User shown in the user-list window", session.getChatUsersWindowText().contains("Sam"));
        session.logout();
        Thread.sleep(2000);
    }

    public void testChatSampleWithTwoUsers() throws InterruptedException, Exception {
        Assert.assertTrue("we need at least two clients connecting", hasAtLeastTwoClients());
        ChatSample aliceSession = new ChatSample(toolkits.get(0));
        ChatSample bobSession   = new ChatSample(toolkits.get(1));
        aliceSession.login("Alice");
        aliceSession.sendMessage("Hello guys");
        bobSession.login("Bob");
        bobSession.sendMessage("Hi Alice");
        // now both users must see ``Hi from Alice''
        Assert.assertTrue("Alice sees Hi from Alice", aliceSession.getChatWindowText().contains("Hi Alice"));
        Assert.assertTrue("Bob sees Hi from Alice", bobSession.getChatWindowText().contains("Hi Alice"));
        // both users must see themselves
        Assert.assertTrue("Alice sees Bob", aliceSession.getChatUsersWindowText().contains("Bob"));
        Assert.assertTrue("Alice sees herself", aliceSession.getChatUsersWindowText().contains("Alice"));
        Assert.assertTrue("Bob sees Alice", bobSession.getChatUsersWindowText().contains("Alice"));
        Assert.assertTrue("Bob sees himself", bobSession.getChatUsersWindowText().contains("Bob"));
        bobSession.logout();
        Assert.assertFalse("Alice doesn't see Bob anymore", aliceSession.getChatUsersWindowText().contains("Bob"));
        aliceSession.logout();
    }
    
    public void testChatSampleWith100Users() throws InterruptedException, Exception {
        Assert.assertTrue("Need 100 clients", numOfClientsGreaterThan(MAX_CHAT_CLIENTS));
        List<ChatSample> sessions = new ArrayList<ChatSample>(MAX_CHAT_CLIENTS);
        // Login and send some text
        for(int idx=0; idx<MAX_CHAT_CLIENTS; idx++) {
            sessions.add(new ChatSample(toolkits.get(idx)));
            sessions.get(idx).login("User"+idx);
            sessions.get(idx).sendMessage("Hi from User"+idx);
        }
        // Verify All users in user window by some user
        for(int idx=0; idx<MAX_CHAT_CLIENTS; idx++) {
            Assert.assertTrue("User"+idx+" seen by User50", sessions.get(MAX_CHAT_CLIENTS/2).getChatUsersWindowText().contains("User"+idx));
            
        }
        for(int idx=MAX_CHAT_CLIENTS/4; idx<MAX_CHAT_CLIENTS; idx++) {
            Assert.assertTrue("Hi from User"+idx+" seen by User25", sessions.get(MAX_CHAT_CLIENTS/4).getChatWindowText().contains("Hi from User"+idx));
        }
        //Logout
        for(int idx=0; idx<MAX_CHAT_CLIENTS; idx++) {
            sessions.get(idx).logout();
        }
    }
    
    public void testAuctionSample() throws InterruptedException, Exception {
        Assert.assertTrue("we need at least one client connecting", hasAtLeastOneClient());
        AuctionSample session = new AuctionSample(toolkit);
        session.login("Richard");
        session.chooseAuction("Omega");
        Thread.sleep(AuctionSample.AUCTION_TIMEOUT);
        Assert.assertEquals("Auction has started", "Auction Started", session.getAuctionStatus());
        Float bid = 10.0f  + session.getItemCurrentPrice();
        session.bidOnItem(bid);
        Float newPrice = session.getItemCurrentPrice();
        Assert.assertEquals("New bid accepted", bid, newPrice);
        session.exit();
    }

}
