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
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.websocket.ClientContainer;
import javax.websocket.CloseReason;
import javax.websocket.MessageHandler;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;

import org.glassfish.tyrus.spi.SPIRemoteEndpoint;

/**
 * Implementation of the WebSocketConversation.
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Martin Matula (martin.matula at oracle.com)
 */
public class SessionImpl implements Session {

    private final ClientContainer container;
    private final EndpointWrapper endpoint;
    private final RemoteEndpointWrapper remote;
    private final String negotiatedSubprotocol;
    private final List<String> negotiatedExtensions;
    private final boolean isSecure;
    private final URI uri;
    private final String queryString;
    private final Map<String, String> pathParameters;
    private Map<MessageHandler, MessageHandler> messageHandlerToInvokableMessageHandlers = new HashMap<MessageHandler, MessageHandler>();
    private final Map<String, Object> properties = new HashMap<String, Object>();
    private long timeout;
    private long maximumMessageSize = 8192;

    private static final Logger LOGGER = Logger.getLogger(SessionImpl.class.getName());

    private static final Map<String,Object> userProperties = new HashMap<String, Object>();

    /**
     * Timestamp of the last send/receive message activity.
     */
    private long lastConnectionActivity;

    SessionImpl(ClientContainer container, SPIRemoteEndpoint remoteEndpoint, EndpointWrapper endpointWrapper,
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
        return "13"; // TODO
    }

    @Override
    public String getNegotiatedSubprotocol() {
        return negotiatedSubprotocol;
    }

    @Override
    public RemoteEndpoint getRemote() {
        return remote;
    }

    @Override
    public boolean isOpen() {
        return endpoint.isOpen(this);
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
        return "Session(" + hashCode() + ", " + this.isOpen() + ")";
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
    public void addMessageHandler(MessageHandler listener) {
        this.messageHandlerToInvokableMessageHandlers.put(listener, listener);
    }


    @Override
    public Set<MessageHandler> getMessageHandlers() {
        return Collections.unmodifiableSet(this.messageHandlerToInvokableMessageHandlers.keySet());
    }

    @Override
    public void removeMessageHandler(MessageHandler listener) {
        this.messageHandlerToInvokableMessageHandlers.remove(listener);
    }

    @Override
    public URI getRequestURI() {
        return uri;
    }

    // TODO: this method should be deleted?
    @Override
    public Map<String, String[]> getRequestParameterMap() {
        return Collections.emptyMap();
    }

    @Override
    public Map<String, String> getPathParameters() {
        return pathParameters;
    }

    @Override
    public Map<String, Object> getUserProperties() {
        return userProperties;
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

    void notifyMessageHandlers(Object message, List<DecoderWrapper> availableDecoders) {
        updateLastConnectionActivity();

        boolean decoded = false;

        if (availableDecoders.isEmpty()) {
            LOGGER.severe("No decoder found");
        }

        for (DecoderWrapper decoder : availableDecoders) {
            for (MessageHandler mh : this.getOrderedMessageHandlers()) {
                Class<?> type;
                if ((mh instanceof MessageHandler.Basic)
                        && (type = getHandlerType(mh)).isAssignableFrom(decoder.getType())) {
                    Object object = endpoint.decodeCompleteMessage(message, type);
                    if (object != null) {
                        //noinspection unchecked
                        ((MessageHandler.Basic) mh).onMessage(object);
                        decoded = true;
                        break;
                    }
                }
            }
            if (decoded) {
                break;
            }
        }
    }

    void notifyMessageHandlers(Object message, boolean last) {
        boolean handled = false;

        for (MessageHandler handler : this.getInvokableMessageHandlers()) {
            if ((handler instanceof MessageHandler.Async) &&
                    getHandlerType(handler).isAssignableFrom(message.getClass())) {
                //noinspection unchecked
                ((MessageHandler.Async) handler).onMessage(message, last);
                handled = true;
                break;
            }
        }

        if (!handled) {
            LOGGER.severe("Unhandled text message in EndpointWrapper");
        }
    }

    Set<MessageHandler> getInvokableMessageHandlers() {
        Set<MessageHandler> s = new HashSet<MessageHandler>();
        for (MessageHandler mh : this.messageHandlerToInvokableMessageHandlers.values()) {
            s.add(mh);
        }
        return s;
    }

    private List<MessageHandler> getOrderedMessageHandlers() {
        Set<MessageHandler> handlers = this.getMessageHandlers();
        ArrayList<MessageHandler> result = new ArrayList<MessageHandler>();

        result.addAll(handlers);
        Collections.sort(result, new MessageHandlerComparator());

        return result;
    }

    private Class<?> getHandlerType(MessageHandler handler) {
        Class<?> root;
        if (handler instanceof AsyncMessageHandler) {
            return ((AsyncMessageHandler) handler).getType();
        } else if (handler instanceof BasicMessageHandler) {
            return ((BasicMessageHandler) handler).getType();
        } else if (handler instanceof MessageHandler.Async) {
            root = MessageHandler.Async.class;
        } else if (handler instanceof MessageHandler.Basic) {
            root = MessageHandler.Basic.class;
        } else {
            throw new IllegalArgumentException(handler.getClass().getName()); // should never happen
        }
        Class<?> result = ReflectionHelper.getClassType(handler.getClass(), root);
        return result == null ? Object.class : result;
    }

    private class MessageHandlerComparator implements Comparator<MessageHandler> {

        @Override
        public int compare(MessageHandler o1, MessageHandler o2) {
            if (o1 instanceof MessageHandler.Basic) {
                if (o2 instanceof MessageHandler.Basic) {
                    Class<?> type1 = getHandlerType(o1);
                    Class<?> type2 = getHandlerType(o2);

                    if (type1.isAssignableFrom(type2)) {
                        return 1;
                    } else if (type2.isAssignableFrom(type1)) {
                        return -1;
                    } else {
                        return 0;
                    }
                } else {
                    return 1;
                }
            } else if (o2 instanceof MessageHandler.Basic) {
                return 1;
            }
            return 0;
        }
    }
}
