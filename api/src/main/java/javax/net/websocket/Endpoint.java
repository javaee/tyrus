/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package javax.net.websocket;

import javax.net.websocket.Session;

/**
 * The Web Socket Endpoint represents and object that can handle web socket conversations. If
 * deployed as a server, that is to say, the endpoint is registered to a URL, the endpoint may
 * handle one or more web socket conversations, one for each client that establishes a connection. If
 * deployed as a client, the endpoint will participate in only one conversation: that with the server
 * to which it connects.
 * If the endpoint is a server which will cater to multiple clients, the endpoint may be called by multiple
 * threads, no more than one per client, at any one time. This means that when implementing/overriding the methods
 * of Endpoint, the developer should be aware that any state management must be carefully synchronized with this in
 * mine.
 *
 * @since DRAFT 001
 * @author dannycoward
 */
public abstract class Endpoint {
    /** Developers may implement this method to be notified when a new conversation has
     * just begun.
     * @param session
     */
    public abstract void onOpen(Session session);
    /** Developers may implement this method to be notified when an active conversation
     * has just been terminated.
     * @param session
     */
    public void onClose(Session session) {}

    /** Developers may implement this method when a web socket connection, represented by the session,
     * creates some kind of error that is not modeled in the web socket protocol. This may for example
     * be a notification that an incoming message is too big to handle, or that the incoming message could not be encoded.<br><br>
     * There are a number of categories of exception that this method is (currently) defined to handle:-<br>
     * - connection problems, for example, a socket failure that occurs before the web socket connection can be formally closed.<br>
     * - errors thrown by developer create message handlers calls.<br>
     * - conversion errors encoding incoming messages before any message handler has been called.<br>
     * TBD We may come up with less of a 'catch-all' mechanism for handling exceptions, especially given the varying nature
     * of these categories of exception.
     */

    public void onError(Throwable thr, Session s) {}



}
