/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.sample.shared.collection;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import javax.json.Json;
import javax.json.JsonObject;

import org.glassfish.tyrus.client.ClientManager;

/**
 * Basic java representation for shared map.
 * <p/>
 * Backed up by {@link SharedCollectionEndpoint}. Does not handle failed connection loss or connection loss.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
class SharedMap implements Map<String, String> {

    private static final UpdateListener NOOP_UPDATE_LISTENER = new UpdateListener() {
        @Override
        public void onUpdate() {

        }
    };

    private final Map<String, String> map = new ConcurrentHashMap<String, String>();
    private final UpdateListener updateListener;
    private final SharedMapEndpoint sharedMapEndpoint;

    /**
     * Constructor.
     *
     * @param clientManager               Tyrus client used to send/receive messages.
     * @param sharedCollectionEndpointUri uri where the server endpoint is deployed.
     * @param updateListener              invoked when any change is done to the map (including local modifications).
     *                                    Initialization is treated as single update event - after that, every singe
     *                                    change will trigger {@link UpdateListener#onUpdate()} invocation.
     * @throws DeploymentException when there is an issue with underlying WebSocket connection.
     * @throws IOException         when there is an issue with underlying WebSocket connection.
     */
    public SharedMap(ClientManager clientManager, URI sharedCollectionEndpointUri, UpdateListener updateListener) throws
            DeploymentException, IOException {
        sharedMapEndpoint = new SharedMapEndpoint();
        clientManager.connectToServer(sharedMapEndpoint, ClientEndpointConfig.Builder.create().build(),
                                      sharedCollectionEndpointUri);
        this.updateListener = updateListener == null ? NOOP_UPDATE_LISTENER : updateListener;
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    @Override
    public String get(Object key) {
        return map.get(key);
    }

    @Override
    public String put(String key, String value) {
        final String put = map.put(key, value);
        sharedMapEndpoint.send(Json.createObjectBuilder()
                                   .add("event", "put")
                                   .add("key", key)
                                   .add("value", value).build()
        );
        updateListener.onUpdate();
        return put;
    }

    @Override
    public String remove(Object key) {
        final String remove = map.remove(key);
        sharedMapEndpoint.send(Json.createObjectBuilder()
                                   .add("event", "remove")
                                   .add("key", key.toString()).build()
        );
        updateListener.onUpdate();
        return remove;
    }

    @Override
    public void putAll(Map<? extends String, ? extends String> m) {
        for (Entry<? extends String, ? extends String> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    @Override
    public void clear() {
        map.clear();
        sharedMapEndpoint.send(Json.createObjectBuilder()
                                   .add("event", "clear").build()
        );
        updateListener.onUpdate();
    }

    /**
     * Returned value is unmodifiable.
     * <p/>
     * {@inheritDoc}
     */
    @Override
    public Set<String> keySet() {
        return Collections.unmodifiableSet(map.keySet());
    }

    /**
     * Returned value is unmodifiable.
     * <p/>
     * {@inheritDoc}
     */
    @Override
    public Collection<String> values() {
        return Collections.unmodifiableCollection(map.values());
    }

    /**
     * Returned value is unmodifiable.
     * <p/>
     * {@inheritDoc}
     */
    @Override
    public Set<Entry<String, String>> entrySet() {
        return Collections.unmodifiableSet(map.entrySet());
    }

    /**
     * Map collection update.
     */
    public static interface UpdateListener {

        /**
         * Invoked when map is updated.
         */
        public void onUpdate();
    }

    private class SharedMapEndpoint extends Endpoint implements MessageHandler.Whole<Reader> {

        private volatile Session session;
        private volatile boolean online;

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            session.addMessageHandler(Reader.class, this);
            this.session = session;
            online = true;
        }

        @Override
        public void onClose(Session session, CloseReason closeReason) {
            online = false;
        }

        @Override
        public void onMessage(Reader message) {
            final JsonObject jsonObject = Json.createReader(message).readObject();

            switch (jsonObject.getString("event")) {
                case "init":
                    final JsonObject map = jsonObject.getJsonObject("map");
                    for (String key : map.keySet()) {
                        SharedMap.this.map.put(key, map.getString(key));
                    }
                    break;
                case "put":
                    SharedMap.this.map.put(jsonObject.getString("key"), jsonObject.getString("value"));
                    break;
                case "remove":
                    SharedMap.this.map.remove(jsonObject.getString("key"));
                    break;
                case "clear":
                    SharedMap.this.map.clear();
                    break;
            }

            updateListener.onUpdate();
        }

        public void send(JsonObject object) {
            if (online) {
                try {
                    session.getBasicRemote().sendText(object.toString());
                } catch (IOException e) {
                    // ignored.
                }
            } else {
                throw new IllegalStateException("Not connected to server endpoint.");
            }
        }
    }
}
