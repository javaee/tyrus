/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.spi;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;

import javax.websocket.CloseReason;
import javax.websocket.EndpointConfiguration;
import javax.websocket.Extension;
import javax.websocket.Session;

/**
 * The WebSocket SDK implements SPIEndpoint with its representation of
 * a websocket endpoint mapped to a base URI that wishes to handle incoming
 * messages.
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public abstract class SPIEndpoint {

    /**
     * This method must be called by the provider during
     * its check for a successful websocket handshake. The
     * provider must turn down the handshake if the method returns false.
     * If the web socket handshake does complete, as determined by the provider,
     * the endpoint must establish a connection and route all websocket
     * events to this SDK provided component as appropriate.
     *
     * @param hr <code>SPIHandshakeRequest</code> that is going to be checked.
     * @return <code>true</code> if handshake is successful <code>false</code> otherwise.
     */
    public abstract boolean checkHandshake(SPIHandshakeRequest hr);

    /**
     * Called by the provider when the web socket connection
     * is established.
     *
     * @param gs SPIRemoteEndpoint who has just connected to this web socket endpoint.
     */
    public abstract void onConnect(SPIRemoteEndpoint gs, String subprotocol, List<Extension> extensions);

    /**
     * Called by the provider when the web socket connection
     * has an incoming text message from the given remote endpoint.
     *
     * @param gs            <code>SPIRemoteEndpoint</code> who sent the message.
     * @param messageString the String message.
     */
    public abstract void onMessage(SPIRemoteEndpoint gs, String messageString);


    /**
     * Called by the provider when the web socket connection
     * has an incoming partial text message from the given remote endpoint. Partial
     * text messages are passed in sequential order, one piece at a time. If an implementation
     * does not support streaming, it will need to reconstruct the message here and pass the whole
     * thing along.
     *
     * @param gs            <code>SPIRemoteEndpoint</code> who sent the message.
     * @param partialString the String message.
     * @param last          to indicate if this is the last partial string in the sequence
     */
    public abstract void onPartialMessage(SPIRemoteEndpoint gs, String partialString, boolean last);

    /**
     * Called by the provider when the web socket connection
     * has an incoming partial binary message from the given remote endpoint. Partial
     * binary messages are passed in sequential order, one piece at a time. If an implementation
     * does not support streaming, it will need to reconstruct the message here and pass the whole
     * thing along.
     *
     * @param gs            <code>SPIRemoteEndpoint</code> who sent the message.
     * @param partialBytes  the piece of the binary message.
     * @param last          to indicate if this is the last partial byte buffer in the sequence
     */
    public abstract void onPartialMessage(SPIRemoteEndpoint gs, ByteBuffer partialBytes, boolean last);

    /**
     * Called by the provider when the web socket connection
     * has an incoming binary message from the given remote endpoint.
     *
     * @param gs    <code>SPIRemoteEndpoint</code> who sent the message.
     * @param bytes the message.
     */
    public abstract void onMessage(SPIRemoteEndpoint gs, ByteBuffer bytes);

    /**
     * Called by the provider when the web socket connection
     * has an incoming pong message from the given remote endpoint.
     *
     * @param gs    <code>SPIRemoteEndpoint</code> who sent the message.
     * @param bytes the message.
     */
    public abstract void onPong(SPIRemoteEndpoint gs, ByteBuffer bytes);

    /**
     * Called by the provider when the web socket connection
     * has an incoming ping message from the given remote endpoint.
     *
     * @param gs    <code>SPIRemoteEndpoint</code> who sent the message.
     * @param bytes the message.
     */
    public abstract void onPing(SPIRemoteEndpoint gs, ByteBuffer bytes);

    /**
     * Called by the provider when the web socket connection
     * to the given remote endpoint has just closed.
     *
     * @param gs SPIRemoteEndpoint who has just closed the connection.
     */
    public abstract void onClose(SPIRemoteEndpoint gs, CloseReason closeReason);

    /**
     * Called by the provider after all connections have been closed to
     * this endpoint, and after the endpoint has been removed from service.
     */
    public abstract void remove();

    /**
     * Get the negotiated extensions' names based on the extensions supported by client.
     *
     * @param clientExtensions names of the extensions' supported by client.
     * @return names of extensions supported by both client and class that implements this one.
     */
    public abstract List<Extension> getNegotiatedExtensions(List<Extension> clientExtensions);

    /**
     * Compute the sub - protocol which will be used.
     *
     * @param clientProtocols sub - protocols supported by client.
     * @return negotiated sub - protocol, {@code null} if none found.
     */
    public abstract String getNegotiatedProtocol(List<String> clientProtocols);

    /**
     * Get the endpoint's open {@link Session}s.
     *
     * @return open sessions.
     */
    public abstract Set<Session> getOpenSessions();

    /**
     * Creates a Session based on the {@link SPIRemoteEndpoint}, subprotocols and extensions.
     *
     * @param re the other end of the connection.
     * @param subprotocol used.
     * @param extensions extensions used.
     * @return {@link Session} representing the connection.
     */
    public abstract Session createSessionForRemoteEndpoint(SPIRemoteEndpoint re, String subprotocol, List<Extension> extensions);

    /**
     * Get Endpoint configuration.
     *
     * @return configuration.
     */
    public abstract EndpointConfiguration getEndpointConfiguration();
}
