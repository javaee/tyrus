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


import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import javax.websocket.Session;

import javax.servlet.http.HttpSession;

import org.glassfish.tyrus.oldservlet.web.WebSocketServerWebIntegration;

@ServerEndpoint(
        path = "/buddies", Xremote = org.glassfish.tyrus.sample.trading.wsbeans.BuddiesRemote.class
)
/**
 *
 * @author Danny Coward (danny.coward at oracle.com)
 */
public class Buddies {
    public static String BUDDIES = "buddies";
//   @XWebSocketContext
//   public XEndpointContext myContext;

    @OnMessage
    public void register(String thiz, BuddiesRemote remote) {
        System.out.println("Here");
//        remote.getContext().getContainerContext().XgetProperties().put(BUDDIES, this);
        if (thiz.equals("register")) {

        } else {
            this.logout(remote.getSession().getHttpSession());
        }
        this.broadcast();
    }

    public void logout(HttpSession httpSession) {
        ThreadManager.stopThreads();
        httpSession.invalidate(); // this should invalidate the remotes....
    }

    public void broadcastActivity(HttpSession httpSession, String action, String symbol) {
        String username = this.getUsername(httpSession);
        Activity activity = new Activity(username, action, symbol);
        List<Activity> activities = new ArrayList<Activity>();
        activities.add(activity);
        for (Session wsSession : myContext.getConversations()) {
            BuddiesRemote remote = (BuddiesRemote) wsSession.getRemote();
            if (remote.getSession().getHttpSession() != httpSession) {
            }
            try {
                remote.sendActivityUpdate(activities);
            } catch (Exception e) {
                System.out.println("Error notifying client of activity update " + e.getMessage());
            }
        }
    }

    private String getUsername(HttpSession httpSession) {
        Principal principal = (Principal) httpSession.getAttribute(WebSocketServerWebIntegration.PRINCIPAL);
        String username;
        if (principal == null) {
            username = "guest" + Math.random();
        } else {
            username = principal.getName();
        }
        if (httpSession.getAttribute(ApplicationPreferences.APP_PREFERENCES) == null) {
            httpSession.setAttribute(ApplicationPreferences.APP_PREFERENCES, new ApplicationPreferences(principal));
        }
        return username;
    }

    public void broadcast() {
        List<String> buddies = new ArrayList<String>();

        for (Session wsSession : myContext.getConversations()) {
            HttpSession httpSession = wsSession.getHttpSession();
            String nextUsername = this.getUsername(httpSession);
            buddies.add(nextUsername);

        }
        for (Session wsSession : myContext.getConversations()) {
            try {
                ((BuddiesRemote) wsSession.getRemote()).sendBuddyList(buddies);
            } catch (Exception e) {
                System.out.println("Error notifying client " + e.getMessage());
            }
        }


    }

}
