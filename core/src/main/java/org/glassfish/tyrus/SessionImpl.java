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
package org.glassfish.tyrus;


import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.net.websocket.ClientContainer;
import javax.net.websocket.CloseReason;
import javax.net.websocket.Encoder;
import javax.net.websocket.MessageHandler;
import javax.net.websocket.RemoteEndpoint;
import javax.net.websocket.Session;

/**
 * Implementation of the WebSocketConversation.
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Martin Matula (martin.matula at oracle.com)
 */
public class SessionImpl<T> implements Session<T> {

    private final ClientContainer container;
    private final EndpointWrapper endpoint;
    private final RemoteEndpointWrapper remote;
    private final String negotiatedSubprotocol;
    private final List<String> negotiatedExtensions;
    private final boolean isSecure;
    private final URI uri;
    private final String queryString;
    private final Map<String, String> pathParameters;

    private Set<MessageHandler> messageHandlers = new HashSet<MessageHandler>();
    private final Map<String, Object> properties = new HashMap<String, Object>();
    private long timeout;
    private long maximumMessageSize = 8192;

    /**
     * Timestamp of the last send/receive message activity.
     */
    private long lastConnectionActivity;

    SessionImpl(ClientContainer container, RemoteEndpoint remoteEndpoint, EndpointWrapper endpointWrapper,
                String subprotocol, List<String> extensions, boolean isSecure,
                URI uri, String queryString, Map<String, String> pathParameters) {
        this.container = container;
        this.endpoint = endpointWrapper;
        this.negotiatedSubprotocol = subprotocol;
        this.negotiatedExtensions = extensions == null ? Collections.<String>emptyList() :
                Collections.unmodifiableList(extensions);
        this.isSecure = isSecure;
        this.uri = uri;
        this.queryString = queryString;
        this.pathParameters = Collections.unmodifiableMap(pathParameters);
        this.remote = new RemoteEndpointWrapper(this, remoteEndpoint, endpointWrapper);
    }

    /**
     * Web Socket protocol version used.
     *
     * @return protocol version
     */
    @Override
    public String getProtocolVersion() {
        return "13";
    }

    @Override
    public String getNegotiatedSubprotocol() {
        return negotiatedSubprotocol;
    }

    @Override
    public RemoteEndpoint getRemote() {
        return remote;
    }

    // TODO: should be removed from the API - does not provide much value
    // TODO: also the <T> from RemoteEndpoint should be removed
    @Override
    public RemoteEndpoint<T> getRemoteL(Class<T> aClass) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isActive() {
        return endpoint.isActive(this);
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
        remote.close(closeReason);
    }

    @Override
    public String toString() {
        return "Session(" + hashCode() + ", " + this.isActive() + ")";
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
    public List<String> getNegotiatedExtensions() {
        return this.negotiatedExtensions;
    }

    @Override
    public boolean isSecure() {
        return isSecure;
    }

    @Override
    public long getInactiveTime() {
        return (System.currentTimeMillis() - this.lastConnectionActivity) / 1000;
    }

    @Override
    public ClientContainer getContainer() {
        return this.container;
    }

    @Override
    public void setEncoders(List<Encoder> encoders) {
        // TODO: implement
        throw new UnsupportedOperationException();
    }

    @Override
    public void addMessageHandler(MessageHandler listener) {
        this.messageHandlers.add(listener);
    }

    @Override
    public Set<MessageHandler> getMessageHandlers() {
        return Collections.unmodifiableSet(this.messageHandlers);
    }

    @Override
    public void removeMessageHandler(MessageHandler listener) {
        this.messageHandlers.remove(listener);
    }

    @Override
    public URI getRequestURI() {
        return uri;
    }

    // TODO: this method should be deleted?
    @Override
    public Map<String, String[]> getParameterMap() {
        return Collections.emptyMap();
    }

    public Map<String, String> getPathParameters() {
        return pathParameters;
    }

    @Override
    public String getQueryString() {
        return queryString;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    void updateLastConnectionActivity() {
        this.lastConnectionActivity = System.currentTimeMillis();
    }

    void notifyMessageHandlers(String message) {
        updateLastConnectionActivity();
        // TODO: messageHandlers should be ordered!
        // TODO: should we order based on the most specific type?
        for (MessageHandler mh : this.messageHandlers) {
            if (mh instanceof MessageHandler.Text) {
                ((MessageHandler.Text) mh).onMessage(message);
                break;
            } else if (mh instanceof MessageHandler.CharacterStream) {
                ((MessageHandler.CharacterStream) mh).onMessage(new StringReader(message));
                break;
            } else if (mh instanceof DecodedObjectMessageHandler) {
                DecodedObjectMessageHandler domh = (DecodedObjectMessageHandler) mh;
                Object object = endpoint.decodeMessage(message, domh.getType(), true);
                if (object != null) {
                    domh.onMessage(object);
                    break;
                }
            } else if (mh instanceof MessageHandler.DecodedObject) {
                Class<?> type = Object.class; // TODO: extract the real type from the type parameter
                Object object = endpoint.decodeMessage(message, type, true);
                if (object != null) {
                    ((MessageHandler.DecodedObject) mh).onMessage(object);
                    break;
                }
            }
        }
    }

    void notifyMessageHandlers(ByteBuffer message) {
        updateLastConnectionActivity();
        // TODO: messageHandlers should be ordered!
        // TODO: should we order based on the most specific type?
        for (MessageHandler mh : this.messageHandlers) {
            if (mh instanceof MessageHandler.Binary) {
                ((MessageHandler.Binary) mh).onMessage(message);
                break;
            } else if (mh instanceof MessageHandler.BinaryStream) {
                // TODO: convert ByteBuffer to stream
//                ((MessageHandler.BinaryStream) mh).onMessage(message.);
//                break;
            } else if (mh instanceof DecodedObjectMessageHandler) {
                DecodedObjectMessageHandler domh = (DecodedObjectMessageHandler) mh;
                Object object = endpoint.decodeMessage(message, domh.getType(), false);
                if (object != null) {
                    domh.onMessage(object);
                    break;
                }
            } else if (mh instanceof MessageHandler.DecodedObject) {
                Class<?> type = Object.class; // TODO: extract the real type from the type parameter
                Object object = endpoint.decodeMessage(message, type, false);
                if (object != null) {
                    ((MessageHandler.DecodedObject) mh).onMessage(object);
                    break;
                }
            }
        }
    }

    Set<MessageHandler> getInvokableMessageHandlers() {
        Set<MessageHandler> imh = new HashSet<MessageHandler>();
        for (MessageHandler mh : this.getMessageHandlers()) {
            if (mh instanceof MessageHandler.CharacterStream) {
                imh.add(new AsyncTextToCharStreamAdapter((MessageHandler.CharacterStream) mh));
            } else if (mh instanceof MessageHandler.BinaryStream) {
                imh.add(new AsyncBinaryToOutputStreamAdapter((MessageHandler.BinaryStream) mh));
            } else {
                imh.add(mh);
            }
        }
        return imh;
    }
}
