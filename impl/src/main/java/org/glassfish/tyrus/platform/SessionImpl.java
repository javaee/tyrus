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
package org.glassfish.tyrus.platform;


import javax.net.websocket.ClientContainer;
import javax.net.websocket.CloseReason;
import javax.net.websocket.Encoder;
import javax.net.websocket.MessageHandler;
import javax.net.websocket.RemoteEndpoint;
import javax.net.websocket.Session;
import javax.net.websocket.extensions.Extension;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation of the WebSocketConversation.
 *
 * @author Danny Coward
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class SessionImpl<T> implements Session<T> {

    /**
     * The container for this session.
     */
    private final ClientContainer container;

    /**
     * Session properties.
     */
    private Map<String, Object> properties = new ConcurrentHashMap<String, Object>();

    /**
     * ID.
     */
    private final long id;

    /**
     * Owner of this session.
     */
    private RemoteEndpointWrapper peer;

    /**
     * Reason for closure.
     */
    private CloseReason closeReason = null;

    /**
     * Session timeout.
     */
    private long timeout;

    /**
     * Max. size of message.
     */
    private long maximumMessageSize = 8192;

    /**
     * Registered message handlers.
     */
    private Set<MessageHandler> messageHandlers = new HashSet<MessageHandler>();

    /**
     * Registered encoders.
     */
    private Set<Encoder> encoders = new HashSet<Encoder>();

    /**
     * Used for ID computation.
     */
    private static final AtomicLong count = new AtomicLong();

    /**
     * Sub-protocol negotiated during the handshake phase.
     */
    private String negotiatedSubprotocol;

    /**
     * Extensions used for this Session.
     */
    private List<Extension> negotiatedExtensions;

    /**
     * This Session is secure.
     */
    private boolean isSecure;

    /**
     * Timestamp of the last send/receive message activity.
     */
    private long lastConnectionActivity;

    SessionImpl(ClientContainer container) {
        this.container = container;
        this.id = count.getAndIncrement();
    }

    /**
     * Web Socket protocol version used.
     *
     * @return protocol version
     */
    public String getProtocolVersion() {
        return "13";
    }

    @Override
    public String getNegotiatedSubprotocol() {
        return negotiatedSubprotocol;
    }

    @Override
    public RemoteEndpoint getRemote() {
        return peer;
    }

    @Override
    public RemoteEndpoint<T> getRemoteL(Class<T> aClass) {
        return null;
    }

    /**
     * Return a unique ID for this session.
     *
     * @return id
     */
    public Long getId() {
        return count.get();
    }

    @Override
    public boolean isActive() {
        return peer.isConnected();
    }

    @Override
    public long getTimeout() {
        return timeout;
    }

    @Override
    public void close() throws IOException {
        this.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "no reason given"));
    }

    /**
     * Closes the underlying connection this session is based upon.
     */
    @Override
    public void close(CloseReason closeReason) throws IOException {
        this.closeReason = closeReason;
        peer.close(closeReason);
    }

    @Override
    public String toString() {
        return "Session(" + id + ", " + this.isActive() + ")";
    }

    void setPeer(RemoteEndpointWrapper peer) {
        this.peer = peer;
    }

    public void setTimeout(long seconds) {
        this.timeout = seconds;

    }

    @Override
    public void setMaximumMessageSize(long maximumMessageSize) {
        this.maximumMessageSize = maximumMessageSize;
    }

    @Override
    public long getMaximumMessageSize() {
        return this.maximumMessageSize;
    }

    @Override
    public List<Extension> getNegotiatedExtensions() {
        return this.negotiatedExtensions;
    }

    @Override
    public boolean isSecure() {
        return isSecure;
    }

    @Override
    public long getInactiveTime() {
        return (System.currentTimeMillis() - this.lastConnectionActivity)/1000;
    }

    @Override
    public ClientContainer getContainer() {
        return this.container;
    }

    @Override
    public void setEncoders(List<Encoder> encoders) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void addMessageHandler(MessageHandler listener) {
        this.messageHandlers.add(listener);
    }

    @Override
    public Set getMessageHandlers() {
        return Collections.unmodifiableSet(this.messageHandlers);
    }

    @Override
    public void removeMessageHandler(MessageHandler listener) {
        this.messageHandlers.remove(listener);
    }

    @Override
    public URI getRequestURI() {
        return URI.create(peer.getAddress());
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return null;
    }

    @Override
    public String getQueryString() {
        return null;
    }

    protected void updateLastConnectionActivity(){
        this.lastConnectionActivity = System.currentTimeMillis();
    }

    void notifyMessageHandlers(String message) {
        for (MessageHandler mh : this.messageHandlers) {
            if (mh instanceof MessageHandler.Text) {
                ((MessageHandler.Text) mh).onMessage(message);
            } else {
                throw new UnsupportedOperationException("don't handle types other than MessageHandler.Text so far.");
            }
        }
    }

    void notifyMessageHandlers(ByteBuffer message) {
        for (MessageHandler mh : this.messageHandlers) {
            if (mh instanceof MessageHandler.Binary) {
                ((MessageHandler.Binary) mh).onMessage(message);
            } else {
                throw new UnsupportedOperationException("don't handle types other than MessageHandler.Text so far.");
            }
        }
    }
}
