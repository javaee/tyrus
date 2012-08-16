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
package org.glassfish.websocket.platform;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicLong;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpSession;

import org.glassfish.websocket.api.*;
import org.glassfish.websocket.api.extension.Extension;

/**
 *
 * @author Danny Coward
 */
public class WebSocketConversationImpl implements Session {
     private Map<String, Object> properties = new ConcurrentHashMap<String, Object>();
    private final long conversationID;
    private RemoteEndpoint peer;
    private HttpSession httpSession;
    private CloseReason closeReason = null;
    private long timeout = 60*1000;
    private long maximumMessageSize = 8192;
    private Set<MessageHandler> messageHandlers = new HashSet<MessageHandler>();

    private static final AtomicLong count = new AtomicLong();

    WebSocketConversationImpl() {
        this.conversationID = count.getAndIncrement();
    }
    
    public Map<String, Object> XXgetProperties() {
        return this.properties;
    }
    
    public String getProtocolVersion() {
        return "13";
    }

    void setHttpSession(HttpSession httpSession) {
        this.httpSession = httpSession;
    }

    @Override
    public HttpSession getHttpSession() {
        return this.httpSession;
    }

    @Override
    public String getNegotiatedSubprotocol() {
        return "not implemented";
    }

    @Override
    public RemoteEndpoint getRemote() {
        return peer;
    }
    
    @Override
    public RemoteEndpoint getRemote(Class c) {
        return peer;
    }

     /** Return a unique ID for this session.*/
    public Long getConversationID() {
        return count.get();
    }

    public boolean isActive() {
        return getWebSocketWrapper().isConnected();
    }

    public Date getActivationTime() {
        return getWebSocketWrapper().getActivationTime();
    }
    
    @Override
    public void close() throws IOException {
        this.close(new CloseReason(CloseReason.Code.NORMAL_CLOSURE, "no reason given"));
    }

    /** Closes the underlying connection this session is based upon.*/
    @Override
    public void close(CloseReason closeReason) throws IOException {
        this.closeReason = closeReason;
        getWebSocketWrapper().close(closeReason);
        
        
    }

    @Override
    public String toString() {
        return "Session("+conversationID+", "+this.isActive()+")";
    }

    private WebSocketWrapper getWebSocketWrapper() {
        return WebSocketWrapper.getWebSocketWrapper(peer);
    }

    void setPeer(RemoteEndpoint peer) {
        this.peer = peer;
    }
    
    @Override
    public CloseReason getCloseStatus() {
        return this.closeReason;
    }
    
    public long getTimeout() {
        return this.timeout;
    }
    public void setTimeout(long seconds) {
        this.timeout = timeout;
    }
    
    public void setMaximumMessageSize(long maximumMessageSize) {
        this.maximumMessageSize = maximumMessageSize;
    }
    public long getMaximumMessageSize() {
        return this.maximumMessageSize;
    }
    
    public List<Extension> getNegotiatedExtensions() {
        if (true) throw new UnsupportedOperationException();
        return null;
    }
    public boolean isSecure() {
        if (true) throw new UnsupportedOperationException();
        return false;
    }
    
    public long getInactiveTime() {
        if (true) throw new UnsupportedOperationException();
        return -1;
    }
    
    public void addEncoder(Encoder tme) {
        if (true) throw new UnsupportedOperationException();
        
    }
    public void addMessageHandler(MessageHandler listener) {
        this.messageHandlers.add(listener);
    }
    public Set getMessageHandlers() {
        return Collections.unmodifiableSet(this.messageHandlers);
    }
    
    public void removeMessageHandler(MessageHandler listener) {
        this.messageHandlers.remove(listener);
    }
    
    public URI getRequestURI() {
        if (true) throw new UnsupportedOperationException();
        return null;
    }
    
    void notifyMessageHandlers(String message) {
        for (MessageHandler mh : this.messageHandlers) {
            if (mh instanceof MessageHandler.Text) {
                System.out.println("Notify " + mh + " of " + message);
                ((MessageHandler.Text) mh).onMessage(message);
            } else {
                throw new UnsupportedOperationException("don't handle types other than MessageHandler.Text so far.");
            }
        }
    }
    
    
}
