/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 - 2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.sample.trading.wsbeans;


import java.io.IOException;
import java.util.List;
import javax.net.websocket.CloseReason;
import javax.net.websocket.ServerContainer;
import javax.net.websocket.Session;
import javax.net.websocket.annotations.WebSocketEndpoint;
import javax.net.websocket.annotations.WebSocketMessage;
import javax.servlet.http.HttpSession;

@WebSocketEndpoint(
        path = "/quotes", Xremote = org.glassfish.tyrus.sample.trading.wsbeans.QuoteRemote.class
)
/**
 *
 * @author Danny Coward (danny.coward at oracle.com)
 */

public class Quotes implements Broadcaster {
    UpdateThread updateThread = null;
    //private List<QuoteRemote> remotes = new ArrayList<QuoteRemote>();
    @XWebSocketContext
    public XEndpointContext myContext;


    public void initThread() {
        if (this.updateThread == null) {
            this.updateThread = new UpdateThread("quotes", 3, this);
            ThreadManager.get().registerThread(updateThread);
            updateThread.start();
        }
    }


    @WebSocketMessage
    public void registerForQuotes(String message, QuoteRemote remote) {
        ServerContainer context = remote.getContext().getContainerContext();
        if (message.equals("register")) {
            this.initThread();
        } else if (message.startsWith("add:")) {
            String symbol = message.substring(4, message.trim().length());
            HttpSession httpSession = remote.getSession().getHttpSession();
            ApplicationPreferences preferences = (ApplicationPreferences) httpSession.getAttribute(ApplicationPreferences.APP_PREFERENCES);
            preferences.addTicker(symbol);
            Buddies buddies = (Buddies) context.XgetProperties().get(Buddies.BUDDIES);
            buddies.broadcastActivity(httpSession, Activity.ADDED, symbol);
        } else if (message.startsWith("remove:")) {
            String symbol = message.substring(7, message.trim().length());
            HttpSession httpSession = remote.getSession().getHttpSession();
            ApplicationPreferences preferences = (ApplicationPreferences) httpSession.getAttribute(ApplicationPreferences.APP_PREFERENCES);
            preferences.removeTicker(symbol);
            Buddies buddies = (Buddies) context.XgetProperties().get(Buddies.BUDDIES);
            buddies.broadcastActivity(httpSession, Activity.REMOVED, symbol);
        } else {
            try {
                remote.getSession().close(new CloseReason(CloseReason.Code.NORMAL_CLOSURE, "User logged off"));
            } catch (IOException ioe) {
            }
            if (myContext.getConversations().size() == 0 && this.updateThread != null) {
                this.updateThread.halt();
                this.updateThread = null;
            }
        }
    }

    public void broadcast() {
        List<String> symbols = Util.getDefaultStockTickers();
        for (Session session : myContext.getConversations()) {
            try {
                HttpSession httpSession = session.getHttpSession();
                ApplicationPreferences preferences = (ApplicationPreferences) httpSession.getAttribute(ApplicationPreferences.APP_PREFERENCES);
                if (preferences != null) {
                    List<Quote> quotes = Quote.getRandomQuotes(preferences.getTickers());
                    ((QuoteRemote) session.getRemote()).sendQuotes(quotes);
                } else {
                    System.out.println("Failed to get preferences in broadcasting quotes update");
                }
            } catch (Exception e) {
                System.out.println("Error notifying client " + e.getMessage());
            }
        }
    }

}
