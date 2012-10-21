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
import javax.net.websocket.Session;
import java.util.ArrayList;
import java.util.List;
import javax.net.websocket.CloseReason;
import javax.servlet.http.HttpSession;
import javax.net.websocket.annotations.WebSocketEndpoint;
import javax.net.websocket.annotations.WebSocketMessage;
import org.json.JSONArray;
import org.json.JSONObject;

    @WebSocketEndpoint(
        path="/twitter",
        Xremote= org.glassfish.tyrus.sample.trading.wsbeans.TwitterRemote.class
    )
    // http://search.twitter.com/search.json?q=%40ORCL
    // https://dev.twitter.com/docs/api/1/get/search

/**
 *
 * @author dannycoward
 */
public class Twitter implements Broadcaster {
    UpdateThread updateThread = null;
    @XWebSocketContext
    public XEndpointContext myContext;
    String maxResults = "8";
    //List<TwitterRemote> remotes = new ArrayList<TwitterRemote>();
    //String searchTerm = "%40ORCL%20OR%20%40MSFT";

   public void initThread() {
       if (this.updateThread == null) {
            this.updateThread = new UpdateThread("twitter", 4, this);
            ThreadManager.get().registerThread(updateThread);
            updateThread.start();
       }
   }

    @WebSocketMessage
    public void startSession(String message, TwitterRemote tr) {
        if (message.equals("register")) {
            this.initThread();
        } else {
            try {
                tr.getSession().close(new CloseReason(CloseReason.Code.NORMAL_CLOSURE, "User logged off"));
            } catch (IOException ioe ) {}

            if (myContext.getConversations().size() == 0 && this.updateThread != null) {
                this.updateThread.halt();
                this.updateThread = null;
            }
        }
    }

    public void broadcast() {
        for (Session session : myContext.getConversations()) {


            try {
                HttpSession httpSession = session.getHttpSession();
                ApplicationPreferences preferences = (ApplicationPreferences) httpSession.getAttribute(ApplicationPreferences.APP_PREFERENCES);
                if (preferences != null) {
                    ((TwitterRemote) session.getRemote()).sendSearchResults(this.doSearch(preferences.getTickers()));
                } else {
                    System.out.println("Failed to get preferences in broadcasting twitter update");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }



     public List<TwitterSearchResult> doSearch(List<String> symbols) throws Exception {
        List<TwitterSearchResult> resultList = new ArrayList<TwitterSearchResult>();
        String searchTerm = "";
        for (String symbol : symbols) {
            if (searchTerm.equals("")) {
                searchTerm = "%23" + symbol;
            } else {
                searchTerm = searchTerm + "%20OR%20%23" + symbol;
            }
        }
        String result = Util.getData("http://search.twitter.com/search.json?q="+searchTerm + "&rpp="+maxResults + "&result_type=recent");
        JSONObject json = new JSONObject(result);
        JSONArray results = (JSONArray) json.get("results");
        for (int i = 0; i < results.length(); i++) {
            JSONObject eachResult = (JSONObject) results.get(i);
            //System.out.println(eachResult);
            String username = eachResult.getString("from_user_name");
            String text = eachResult.getString("text");
            String picURL = eachResult.getString("profile_image_url");
            String usercode = eachResult.getString("from_user");
            TwitterSearchResult tsr = new TwitterSearchResult(username, text, picURL, usercode);
            resultList.add(tsr);
        }
        return resultList;
    }

}
