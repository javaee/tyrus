/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.sample.btc.xchange;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import javax.json.Json;
import javax.json.JsonObject;

/**
 * Transactions endpoint.
 * <p/>
 * One instance is created per user (page view), representing one trader. Each trader has resources, but offers are
 * global (shared), same as graph data.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
@ServerEndpoint("/market")
public class MarketEndpoint implements Market.MarketListener {

    private final Market market = Market.getInstance();

    private volatile long lastId = 0;
    private volatile Session session = null;
    private volatile ScheduledFuture<?> scheduledFuture;

    // 30000 = 30.000 BTC
    private volatile long btc;
    private volatile long usd;

    @OnOpen
    public void onOpen(final Session session) throws IOException {
        session.getBasicRemote().sendText(Json.createObjectBuilder()
                                              .add("type", "name")
                                              .build().toString()
        );

        this.session = session;
        market.registerMarketListener(this);

        scheduledFuture = Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    session.getBasicRemote().sendText(Json.createObjectBuilder()
                                                          .add("type", "price-update")
                                                          .add("price", market.getCurrentPrice())
                                                          .build().toString());
                } catch (IOException ignored) {
                }
            }
        }, 0, 1, TimeUnit.SECONDS);

        // send initial balance update.
        onBalanceChange(30000L, 3000L);
    }

    @OnMessage
    public void onMessage(Session session, Reader reader) throws IOException {
        JsonObject jsonObject = Json.createReader(reader).readObject();

        market.realize(this, jsonObject.getInt("id"));
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        System.out.println("onClose: " + closeReason);
        market.unregisterMarketListener(this);
        scheduledFuture.cancel(true);
    }

    @OnError
    public void onError(Session session, Throwable t) {
        System.out.println("onError: " + t);
    }


    @Override
    public void onNewOffer() {
        List<Market.Offer> offers = market.getOffers(lastId);
        for (Market.Offer offer : offers) {

            try {
                session.getBasicRemote().sendText(Json.createObjectBuilder()
                                                      .add("id", offer.id)
                                                      .add("type", offer.type)
                                                      .add("amount", offer.amount)
                                                      .add("price", offer.price)
                                                      .build().toString());
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (offer.id > lastId) {
                lastId = offer.id;
            }
        }
    }

    @Override
    public void onRemoved(Long id) {
        try {
            session.getBasicRemote().sendText(Json.createObjectBuilder()
                                                  .add("id", id)
                                                  .add("type", "invalidate")
                                                  .build().toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onBalanceChange(Long btc, Long usd) {

        System.out.println("onBalanceChange: " + btc + " " + usd);

        this.btc += btc;
        this.usd += usd;

        try {
            session.getBasicRemote().sendText(Json.createObjectBuilder()
                                                  .add("type", "balance")
                                                  .add("btc", this.btc)
                                                  .add("usd", this.usd)
                                                  .add("btcDelta", btc)
                                                  .add("usdDelta", usd)
                                                  .build().toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
