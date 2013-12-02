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

package org.glassfish.tyrus.core;

import java.util.List;

import javax.websocket.CloseReason;
import javax.websocket.Extension;

import org.glassfish.tyrus.spi.UpgradeRequest;
import org.glassfish.tyrus.spi.UpgradeResponse;

/**
 * Abstract server-side {@link WebSocket} application, which will handle
 * application {@link WebSocket}s events.
 *
 * @author Alexey Stashok
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public abstract class WebSocketApplication implements WebSocketListener {

    /**
     * Factory method to create new {@link WebSocket} instances.  Developers may
     * wish to override this to return customized {@link WebSocket} implementations.
     *
     * @param handler  the {@link ProtocolHandler} to use with the newly created
     *                 {@link WebSocket}.
     * @param listener the {@link WebSocketListener} to associate with the new
     *                 {@link WebSocket}.
     * @return TODO
     */
    public abstract WebSocket createSocket(final ProtocolHandler handler,
                                           final WebSocketListener listener);

    /**
     * When a {@link WebSocket#onClose(org.glassfish.tyrus.core.frame.CloseFrame)} is invoked, the {@link WebSocket}
     * will be unassociated with this application and closed.
     *
     * @param socket      the {@link WebSocket} being closed.
     * @param closeReason the {@link CloseReason}.
     */
    @Override
    public abstract void onClose(WebSocket socket, CloseReason closeReason);

    /**
     * When a new {@link WebSocket} connection is made to this application, the
     * {@link WebSocket} will be associated with this application.
     *
     * @param socket         the new {@link WebSocket} connection.
     * @param upgradeRequest request associated with connection.
     */
    @Override
    public abstract void onConnect(WebSocket socket, UpgradeRequest upgradeRequest);

    /**
     * Checks protocol specific information can and should be upgraded.
     * <p/>
     * The default implementation will check for the presence of the
     * <code>Upgrade</code> header with a value of <code>WebSocket</code>.
     * If present, {@link #isApplicationRequest(org.glassfish.tyrus.spi.UpgradeRequest)}
     * will be invoked to determine if the request is a valid websocket request.
     *
     * @param request TODO
     * @return <code>true</code> if the request should be upgraded to a
     * WebSocket connection
     */
    public final boolean upgrade(UpgradeRequest request) {
        final String upgradeHeader = request.getHeader(UpgradeRequest.UPGRADE);
        return request.getHeaders().get(UpgradeRequest.UPGRADE) != null &&
                // RFC 6455, paragraph 4.2.1.3
                UpgradeRequest.WEBSOCKET.equalsIgnoreCase(upgradeHeader) && isApplicationRequest(request);
    }

    /**
     * This method will be invoked if an unexpected exception is caught by
     * the WebSocket runtime.
     *
     * @param webSocket the websocket being processed at the time the
     *                  exception occurred.
     * @param t         the unexpected exception.
     * @return {@code true} if the WebSocket should be closed otherwise
     * {@code false}.
     */
    public boolean onError(final WebSocket webSocket,
                           final Throwable t) {
        return true;
    }

    /**
     * Invoked when server side handshake is ready to send response.
     * <p/>
     * Changes in response parameter will be reflected in data sent back to client.
     *
     * @param request  original request which caused this handshake.
     * @param response response to be send.
     */
    public abstract void onHandShakeResponse(UpgradeRequest request, UpgradeResponse response);

    /**
     * Checks application specific criteria to determine if this application can
     * process the request as a WebSocket connection.
     *
     * @param request the incoming HTTP request.
     * @return <code>true</code> if this application can service this request
     */
    protected abstract boolean isApplicationRequest(UpgradeRequest request);

    /**
     * Return path for which is current {@link WebSocketApplication} registered.
     *
     * @return path. {@code null} will be returned when called on client {@link WebSocketApplication}.
     */
    public abstract String getPath();

    /**
     * Return the websocket extensions supported by this <code>WebSocketApplication</code>.
     * The {@link Extension}s added to this {@link List} should not include
     * any {@link Extension.Parameter}s as they will be ignored.  This is used
     * exclusively for matching the requested extensions.
     *
     * @return the websocket extensions supported by this
     * <code>WebSocketApplication</code>.
     */
    public abstract List<Extension> getSupportedExtensions();

    /**
     * @param subProtocol TODO
     * @return TODO
     */
    public abstract List<String> getSupportedProtocols(List<String> subProtocol);
}
