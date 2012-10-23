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
package org.glassfish.tyrus.sample.auction;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.websocket.Session;
import javax.net.websocket.annotations.WebSocketClose;
import javax.net.websocket.annotations.WebSocketEndpoint;
import javax.net.websocket.annotations.WebSocketMessage;
import javax.net.websocket.annotations.WebSocketOpen;
import org.glassfish.tyrus.sample.auction.message.AuctionListRequestMessage;
import org.glassfish.tyrus.sample.auction.message.AuctionListResponseMessage;
import org.glassfish.tyrus.sample.auction.message.BidRequestMessage;
import org.glassfish.tyrus.sample.auction.message.LoginRequestMessage;
import org.glassfish.tyrus.sample.auction.message.LogoutRequestMessage;

/**
 * Runs multiple auctions.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
@WebSocketEndpoint(value = "/auction",
        decoders = {org.glassfish.tyrus.sample.auction.decoders.LoginRequestDecoder.class,
                org.glassfish.tyrus.sample.auction.decoders.BidRequestDecoder.class,
                org.glassfish.tyrus.sample.auction.decoders.LogoutRequestDecoder.class,
                org.glassfish.tyrus.sample.auction.decoders.AuctionListRequestDecoder.class,
                org.glassfish.tyrus.sample.auction.decoders.LogoutRequestDecoder.class},
        encoders = {org.glassfish.tyrus.sample.auction.encoders.LogoutResponseEncoder.class})
public class AuctionServer {

    /*
     * Set of auctions (finished, running, to be started auctions).
     */
    private Set<Auction> auctions = new HashSet<Auction>();
    private final static Logger logger = Logger.getLogger("application");

    /*
     * Used just to generate test data
     */
    public AuctionServer() {
        Auction auction = new Auction(this, new AuctionItem("Swatch", "Nice Swatch watches, hand made", 100, System.currentTimeMillis() + 60000, 30));
        auctions.add(auction);

        Auction auction1 = new Auction(this, new AuctionItem("Rolex", "Nice Rolex watches, hand made", 200, System.currentTimeMillis() + 120000, 30));
        auctions.add(auction1);

        Auction auction2 = new Auction(this, new AuctionItem("Omega", "Nice Omega watches, hand made", 300, System.currentTimeMillis() + 180000, 30));
        auctions.add(auction2);
    }

    @WebSocketOpen
    public void init(Session remote) {

    }

    @WebSocketClose
    public void handleClosedConnection(Session arc) {
        for (Auction auction : auctions) {
            auction.removeArc(arc);
        }
    }

    @WebSocketMessage
    public void handleLogoutRequest(LogoutRequestMessage alrm, Session arc) {
        handleClosedConnection(arc);
    }

    @WebSocketMessage
    public void handleAuctionListRequest(AuctionListRequestMessage alrm, Session arc) {
        StringBuilder sb = new StringBuilder("-");
        DateFormat formatter = new SimpleDateFormat("hh:mm:ss");

        for (Auction auction : auctions) {
            Date d = new Date(auction.getItem().getAuctionStartTime());
//            sb.append(auction.getId()).append("-").append(auction.getItem().getName())//.append(" starts at ").append(formatter.format(d)).append("-");
            sb.append(auction.getId()).append("-").append(auction.getItem().getName()).append("-");
        }

        try {
            arc.getRemote().sendString((new AuctionListResponseMessage("0", sb.toString())).asString());
        } catch (IOException ex) {
            Logger.getLogger(AuctionServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @WebSocketMessage
    public void handleLoginRequest(LoginRequestMessage lrm, Session arc) {
        String communicationId = lrm.getCommunicationId();
        for (Auction auction : auctions) {
            if (communicationId.equals(auction.getId())) {
                auction.handleLoginRequest(lrm, arc);
            }
        }
    }

    @WebSocketMessage
    public void handleBidRequest(BidRequestMessage brm, Session arc) {
        String communicationId = brm.getCommunicationId();
        for (Auction auction : auctions) {
            if (communicationId.equals(auction.getId())) {
                auction.handleBidRequest(brm, arc);
                break;
            }
        }
    }
}
