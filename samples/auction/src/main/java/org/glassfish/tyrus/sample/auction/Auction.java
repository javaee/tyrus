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
package org.glassfish.tyrus.sample.auction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.EncodeException;
import javax.websocket.Session;

import org.glassfish.tyrus.sample.auction.message.AuctionMessage;

/**
 * Implements the auction protocol
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class Auction {

    /*
     * Current state of the auction
     */
    private AuctionState state;

    /*
     * ID of the auction used for communication
     */
    private final String id;

    /*
     * Assigns id to newly created Auction object
     */
    private static int idCounter = 0;

    /*
     * Auction Item
     */
    private final AuctionItem item;

    /*
     * List of remote clients (Peers)
     */
    private final List<Session> arcList = new ArrayList<>();

    /*
     * Timer that sends pre-auction time broadcasts
     */
    private Timer preAuctionTimer;

    /*
     * Timer that sends pre-auction time broadcasts
     */
    private Timer auctionRunningTimer;

    /*
     * Bidder of the heighest bid
     */
    private Session bestBidder;

    /*
     * Value of the heighest bid
     */
    private double bestBid;

    /*
     * Separator used to separate different fields in the communication
     * datastring
     */
    public static final String SEPARATOR = ":";

    public enum AuctionState {
        PRE_AUCTION, AUCTION_RUNNING, AUCTION_FINISHED
    }


    public Auction(AuctionItem item) {
        this.item = item;

        this.state = AuctionState.PRE_AUCTION;
        this.id = Integer.toString(Auction.idCounter);
        bestBid = item.getStartingPrice();
        idCounter++;
    }

    synchronized void addArc(Session arc) {
        arcList.add(arc);
    }

    public synchronized void removeArc(Session arc) {
        arcList.remove(arc);
    }

    /*
     * New user logs into the auction
     */
    public void handleLoginRequest(AuctionMessage.LoginRequestMessage lrm, Session arc) {
        this.addArc(arc);
        AuctionMessage.LoginResponseMessage response = new AuctionMessage.LoginResponseMessage(id, item);
        try {
            arc.getBasicRemote().sendObject(response);
        } catch (IOException | EncodeException e) {
            Logger.getLogger(Auction.class.getName()).log(Level.SEVERE, null, e);
        }

        //first client connected
        if (arcList.size() == 1) {
            startPreAuctionTimeBroadcast();
        }
    }

    public void handleBidRequest(AuctionMessage.BidRequestMessage brm, Session arc) {
        if (state == AuctionState.AUCTION_RUNNING) {
            Double bid = Double.parseDouble(brm.getData());
            if (bid > bestBid) {
                bestBid = bid;
                bestBidder = arc;
                sendPriceUpdateMessage();
                stopAuctionTimeBroadcast();
                startAuctionTimeBroadcast();
            }
        }
    }

    private void sendPriceUpdateMessage() {
        AuctionMessage.PriceUpdateResponseMessage purm = new AuctionMessage.PriceUpdateResponseMessage(id, "" + bestBid);
        for (Session arc : getRemoteClients()) {
            try {
                arc.getBasicRemote().sendObject(purm);
            } catch (IOException | EncodeException e) {
                Logger.getLogger(Auction.class.getName()).log(Level.SEVERE, null, e);
            }
        }
    }

    private void startPreAuctionTimeBroadcast() {
        preAuctionTimer = new Timer();
        preAuctionTimer.schedule(new PreAuctionTimeBroadcasterTask(this, item.getAuctionStartTime()), 0, 1000);
    }

    private void stopPreAuctionTimeBroadcast() {
        preAuctionTimer.cancel();
    }

    public void switchStateToAuctionRunning() {
        state = AuctionState.AUCTION_RUNNING;
        stopPreAuctionTimeBroadcast();
        startAuctionTimeBroadcast();
    }

    public void switchStateToAuctionFinished() {
        state = AuctionState.AUCTION_FINISHED;
        stopAuctionTimeBroadcast();
        sendAuctionResults();
    }

    private void sendAuctionResults() {
        if (bestBidder != null) {
            AuctionMessage.ResultMessage winnerMessage = new AuctionMessage.ResultMessage(id, "Congratulations, You have won the auction.");
            try {
                bestBidder.getBasicRemote().sendObject(winnerMessage);
            } catch (IOException | EncodeException e) {
                Logger.getLogger(Auction.class.getName()).log(Level.SEVERE, null, e);
            }
        }

        AuctionMessage.ResultMessage loserMessage = new AuctionMessage.ResultMessage(id, "User " + "");
        for (Session arc : arcList) {
            if (arc != bestBidder) {
                try {
                    arc.getBasicRemote().sendObject(loserMessage);
                } catch (IOException | EncodeException e) {
                    Logger.getLogger(Auction.class.getName()).log(Level.SEVERE, null, e);
                }
            }
        }
    }

    private void startAuctionTimeBroadcast() {
        auctionRunningTimer = new Timer();
        auctionRunningTimer.schedule(new AuctionTimeBroadcasterTask(this, item.getBidTimeoutS()), 0, 1000);
    }

    private void stopAuctionTimeBroadcast() {
        auctionRunningTimer.cancel();
    }

    public AuctionState getState() {
        return state;
    }

    public void setState(AuctionState state) {
        this.state = state;
    }

    public String getId() {
        return id;
    }

    public List<Session> getRemoteClients() {
        return Collections.unmodifiableList(arcList);
    }

    public AuctionItem getItem() {
        return item;
    }
}
