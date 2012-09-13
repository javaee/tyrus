/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

/**
 * The WebSocket SDK implements SPIEndpoint with its representation of
 * a websocket endpoint mapped to a base URI that wishes to handle incoming
 * messages.
 *
 * @author dannycoward
 */
public interface SPIEndpoint {

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
    public boolean checkHandshake(SPIHandshakeRequest hr);

    /**
     * Called by the provider when the web socket connection
     * is established.
     *
     * @param gs SPIRemoteEndpoint who has just connected to this web socket endpoint.
     */
    public void onConnect(SPIRemoteEndpoint gs);

    /**
     * Called by the provider when the web socket connection
     * has an incoming text message from the given remote endpoint.
     *
     * @param gs            <code>SPIRemoteEndpoint</code> who sent the message.
     * @param messageString the String message.
     */
    public void onMessage(SPIRemoteEndpoint gs, String messageString);

    /**
     * Called by the provider when the web socket connection
     * has an incoming binary message from the given remote endpoint.
     *
     * @param gs           <code>SPIRemoteEndpoint</code> who sent the message.
     * @param messageBytes the <code>byte[]</code> message.
     */
    public void onMessage(SPIRemoteEndpoint gs, byte[] messageBytes);

    /**
     * Called by the provider when the web socket connection
     * to the given remote endpoint has just closed.
     *
     * @param gs SPIRemoteEndpoint who has just closed the connection.
     */
    public void onClose(SPIRemoteEndpoint gs);

    /**
     * Called by the provider during the handshake to determine
     * a list of subprotocols this endpoint will support of those
     * passed in.
     *
     * @param subProtocols the subprotocols to be checked.
     * @return List of supported subprotocols.
     */
    public List<String> getSupportedProtocols(List<String> subProtocols);

    /**
     * Called by the provider after all connections have been closed to
     * this endpoint, and after the endpoint has been removed from service.
     */
    public void remove();
}