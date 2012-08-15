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

package org.glassfish.websocket.api;

import java.io.*;

/**
 * An object that represents the 'other end' of the web socket conversation
 * with a web socket end point.
 * @author dannycoward
 */

public interface RemoteEndpoint {
    /** Return the context of the end point that this remote is a peer of
     * in a web socket conversation.
     * @return
     */
    public EndpointContext getContext();
    /** Return the conversation to which this remote peer is part of.*/
    public Session getConversation();
    /** Send a message to the remote peer this object represents.*/
    public void sendMessage(String data) throws IOException;
    /** Send a message to the remote peer this object represents.*/
    public void sendMessage(byte[] data) throws IOException;
    /** Close this remote object. i.e. end the conversation, close the underlying
     * web socket connection.*/
    public void doClose(int code, String reason) throws IOException;
    /** Test whether this remote object is active, i.e. the underlying web
     * socket connection which connects the remote peer is open or not.
     * @return
     */
    public boolean isConnected();
    /** Obtain the remote address of the remote peer. This is obtained from
     * the initial web socket handshake ( the 'Origin' header ).
     * @return
     */
    public String getAddress();
}
