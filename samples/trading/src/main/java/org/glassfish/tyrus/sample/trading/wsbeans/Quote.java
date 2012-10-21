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

import java.util.*;
/**
 *
 * @author dannycoward
 */
//http://download.finance.yahoo.com/d/quotes.csv?s=ORCL&f=sl1d1t1c1ohgv&e=.csv

    // http://download.finance.yahoo.com/d/quotes.csv?s=GE&e=.csv

    // "GE",16.59,"12/7/2011","2:14pm",-0.13,16.68,16.75,16.53,40773384
public class Quote {
    private String symbol;
    private Double quote;
    private Double delta;
    private Long volume;
    public static long HIGH_VOLUME = 30000000;
                                   //40773384

    public static List<Quote> getQuotes(List<String> symbols) throws Exception {
        List<Quote> quotes = new ArrayList<Quote>();
        for (String symbol : symbols) {
            Quote q = Quote.getQuote(symbol);
            quotes.add(q);
        }
        return quotes;
    }

    public static List<Quote> getRandomQuotes(List<String> symbols) throws Exception {
        List<Quote> list = new ArrayList<Quote>();
        for (String symbol : symbols) {
            Quote q = getRandomQuote(symbol);
            list.add(q);
        }
        return list;
    }

    public static Quote getRandomQuote(String symbol) {
        Quote q = new Quote();
        q.symbol = symbol;
        double d = Math.random() * 100;
        q.quote = new Double(d);
        q.delta = new Double ( (Math.random() * 4) -2);
        double dd = (Math.random()/2)   * 40773384;
        q.volume = new Long((long) dd);
        return q;
    }

    public static Quote getQuote(String symbol) throws Exception {
        String incomingMessage = Util.getData("http://download.finance.yahoo.com/d/quotes.csv?s="+symbol+"&f=sl1d1t1c1ohgv&e=.csv");
        return new Quote(incomingMessage);


    }

    public Quote() {

    }

    public Quote(String csv) {
        List<String> attribs = new ArrayList<String>();
        StringTokenizer st = new StringTokenizer(csv, ",");
        while (st.hasMoreTokens()) {
            attribs.add(st.nextToken());
        }
        this.symbol = attribs.get(0);
        this.quote = new Double(attribs.get(1));
        this.delta = new Double(attribs.get(4));
        this.volume = new Long(attribs.get(8));
    }

    public String getSymbol() {
        return this.symbol;
    }

    public Double getQuote() {
        return this.quote;
    }

    public Double getDelta() {
        return this.delta;
    }

    public Long getVolume() {
        return this.volume;
    }

    public String toString() {
        return "Quote(" + this.symbol + " " + this.quote + " " + this.delta + " " + this.volume + ")";
    }
}
