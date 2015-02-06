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

import java.lang.Override;
import java.lang.Runnable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Market singleton.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class Market {

    private static final Market instance = new Market();

    public static Market getInstance() {
        return instance;
    }

    private final Map<Long, Offer> map = new ConcurrentHashMap<Long, Offer>();
    private final Map<MarketListener, Boolean> endpoints = new ConcurrentHashMap<MarketListener, Boolean>();
    private final AtomicLong idCounter = new AtomicLong(0);

    private volatile int lastPrice = 250;


    private Market() {
        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

        // producer
        scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                int random = (int) (Math.random() * 5000);
                int price = ((int) (Math.random() * 40)) + 230;
                int currentPrice = getCurrentPrice();

                long id = idCounter.incrementAndGet();

                if (currentPrice > price) {
                    map.put(id, new Offer(id, random, price, "buy"));
                } else {
                    map.put(id, new Offer(id, random, price, "sell"));
                }

                for (MarketListener listener : endpoints.keySet()) {
                    listener.onNewOffer();
                }

            }
        }, 0, 3, TimeUnit.SECONDS);

        // consumer
        scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (Math.random() < 0.3) {
                    return;
                }

                do {
                    try {
                        int size = map.size();
                        int toRemove = (int) (Math.random() * size);

                        Long id = (Long) map.keySet().toArray()[toRemove];

                        realize(null, id);
                    } catch (Exception ignored) {
                    }
                } while (map.size() > 10);
            }
        }, 5, 4, TimeUnit.SECONDS);
    }

    public void registerMarketListener(MarketListener transactionsEndpoint) {
        endpoints.put(transactionsEndpoint, true);
    }

    public void unregisterMarketListener(MarketListener transactionsEndpoint) {
        endpoints.remove(transactionsEndpoint);
    }

    public int getCurrentPrice() {
        return lastPrice;
    }

    public List<Offer> getOffers(long fromId) {

        ArrayList<Offer> offers = new ArrayList<Offer>();

        for (Offer offer : map.values()) {
            if (offer.id > fromId) {
                offers.add(offer);
            }
        }

        return offers;
    }

    /**
     * @return {@code true} when the transaction was realized.
     */
    public boolean realize(MarketListener trader, long id) {

        Offer remove = map.remove(id);

        if (remove == null) {
            return false;
        }

        // update last price
        lastPrice = remove.price;

        for (MarketListener listener : endpoints.keySet()) {
            listener.onRemoved(id);
        }

        // AI trader
        if (trader == null) {
            return true;
        }

        if (remove.type.equals("buy")) {
            trader.onBalanceChange((long) remove.amount, -(long) (remove.price * (remove.amount / 1000.0)));
        } else if (remove.type.equals("sell")) {
            trader.onBalanceChange(-(long) remove.amount, (long) (remove.price * (remove.amount / 1000.0)));
        }

        return true;
    }

    /**
     * Single offer.
     */
    public static class Offer {
        public final long id;
        public final int amount;
        public final String type;
        public final int price;

        public Offer(long id, int amount, int price, String type) {
            this.id = id;
            this.amount = amount;
            this.type = type;
            this.price = price;
        }
    }

    /**
     * Each trader (Transaction endpoint instance) implements this resource.
     */
    public static interface MarketListener {

        public void onNewOffer();

        public void onRemoved(Long id);

        public void onBalanceChange(Long btc, Long usd);
    }
}
