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
package org.glassfish.websocket.sample.auction;

import org.glassfish.websocket.sample.auction.message.ResultMessage;
import org.glassfish.websocket.sample.auction.message.BidRequestMessage;
import org.glassfish.websocket.sample.auction.message.LoginResponseMessage;
import org.glassfish.websocket.sample.auction.message.PriceUpdateResponseMessage;
import org.glassfish.websocket.sample.auction.message.LoginRequestMessage;

import javax.net.websocket.EncodeException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implements the auction protocol
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class Auction {

    /*
     * Owner of the auction
     */
    private AuctionServer owner;

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
    private AuctionItem item;

    /*
     * List of remote clients (Peers)
     */
    private List<AuctionRemoteClient> arcList = new ArrayList<AuctionRemoteClient>();

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
    public AuctionRemoteClient bestBidder;

    /*
     * Value of the heighest bid
     */
    public double bestBid;

    /*
     * Separator used to separate different fields in the communication
     * datastring
     */
    public static final String SEPARATOR = ":";

    public Auction(AuctionServer owner, AuctionItem item) {
        this.owner = owner;
        this.item = item;

        this.state = AuctionState.PRE_AUCTION;
        this.id = new Integer(Auction.idCounter).toString();
        bestBid = item.getStartingPrice();
        idCounter++;
    }

    public synchronized void addArc(AuctionRemoteClient arc) {
        arcList.add(arc);
    }

    public synchronized void removeArc(AuctionRemoteClient arc){
        arcList.remove(arc);
    }

    /*
     * New user logs into the auction
     */
    public void handleLoginRequest(LoginRequestMessage lrm, AuctionRemoteClient arc) {
        this.addArc(arc);
            LoginResponseMessage response = new LoginResponseMessage(id, item);
            try {
                arc.sendLoginResponse(response);
            } catch (IOException ex) {
                Logger.getLogger(Auction.class.getName()).log(Level.SEVERE, null, ex);
            } catch (EncodeException ex) {
                Logger.getLogger(Auction.class.getName()).log(Level.SEVERE, null, ex);
            }

        //first client connected
        if (arcList.size() == 1) {
            startPreAuctionTimeBroadcast();
        }
    }

    public void handleBidRequest(BidRequestMessage brm, AuctionRemoteClient arc) {
        if (state == AuctionState.AUCTION_RUNNING) {
            Double bid = Double.parseDouble((String) brm.getData());
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
        PriceUpdateResponseMessage purm = new PriceUpdateResponseMessage(id, "" + bestBid);
        for (AuctionRemoteClient arc : getRemoteClients()) {
            try {
                arc.sendPriceUpdate(purm);
            } catch (IOException ex) {
                Logger.getLogger(Auction.class.getName()).log(Level.SEVERE, null, ex);
            } catch (EncodeException ex) {
                Logger.getLogger(Auction.class.getName()).log(Level.SEVERE, null, ex);
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
            ResultMessage winnerMessage = new ResultMessage(id, "Congratulations, You have won the auction.");
            try {
                bestBidder.sendResultMessage(winnerMessage);
            } catch (IOException ex) {
                Logger.getLogger(Auction.class.getName()).log(Level.SEVERE, null, ex);
            } catch (EncodeException ex) {
                Logger.getLogger(Auction.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        ResultMessage loserMessage = new ResultMessage(id, "User "+"");
        for (AuctionRemoteClient arc : arcList) {
            if (arc != bestBidder) {
                try {
                    arc.sendResultMessage(loserMessage);
                } catch (IOException ex) {
                    Logger.getLogger(Auction.class.getName()).log(Level.SEVERE, null, ex);
                } catch (EncodeException ex) {
                    Logger.getLogger(Auction.class.getName()).log(Level.SEVERE, null, ex);
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

    public List<AuctionRemoteClient> getRemoteClients() {
        return Collections.unmodifiableList(arcList);
    }

    public AuctionItem getItem() {
        return item;
    }
}
