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
package org.glassfish.tyrus.sample.auction.message;

import org.glassfish.tyrus.sample.auction.Auction;
import org.glassfish.tyrus.sample.auction.Auction;

/**
 *
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public abstract class AuctionMessage<T> {

    public static String LOGIN_REQUEST = "lreq";
    public static String LOGIN_RESPONSE = "lres";
    public static String BID_REQUEST = "breq";
    public static String PRICE_UPDATE_RESPONSE = "pres";
    public static String LOGOUT_REQUEST = "dreq";
    public static String LOGOUT_RESPONSE = "dres";
    public static String PRE_AUCTION_TIME_RESPONSE = "tres";
    public static String AUCTION_TIME_RESPONSE = "ares";
    public static String RESULT_RESPONSE = "rres";
    public static String AUCTION_LIST_REQUEST = "xreq";
    public static String AUCTION_LIST_RESPONSE = "xres";
    public static String SEP = ":";

    /*
     * Message type
     */
    private String type;

    /*
     * Message data
     */
    private T data;

    /*
     * ID used for communication purposes
     */
    private String communicationId;

    public AuctionMessage(String type, String communicationId, T data) {
        this.type = type;
        this.communicationId = communicationId;
        this.data = data;
    }

    public String asString() {
        return type + Auction.SEPARATOR+communicationId+ Auction.SEPARATOR +data.toString();
    }

    public T getData() {
        return data;
    }

    public String getCommunicationId(){
        return communicationId;
    }
}
