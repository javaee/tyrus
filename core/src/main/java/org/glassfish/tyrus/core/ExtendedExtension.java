/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Map;

import javax.websocket.Extension;

/**
 * WebSocket {@link Extension}.
 * <p/>
 * Capable of parameters negotiation, incoming and outgoing frames processing.
 * <pre>TODO:
 * - naming.
 * - param negotiation.
 * - param validation.
 * - general validation - two extensions using same rsv bit cannot be "negotiated" for same session/connection.
 * - exception handling.
 * - extension ordering
 *   - current state
 *     - server: incoming in headers order, outgoing in reversed order.
 *     - client: incoming in reversed order, outgoing in headers order.
 * </pre>
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public interface ExtendedExtension extends Extension {

    /**
     * Process incoming frame.
     * <p/>
     * Passed frame is unmasked in case it was masked when received (server to client communication).
     *
     * @param context per-connection/session context.
     * @param frame   websocket frame representation.
     * @return processed frame. Can be new instance.
     */
    Frame processIncoming(ExtensionContext context, Frame frame);

    /**
     * Process outgoing frame.
     * <p/>
     * Passed frame is unmasked. Frame payload will be masked when required (server to client communication).
     *
     * @param context per-connection/session context.
     * @param frame   websocket frame representation.
     * @return processed frame. Can be new instance.
     */
    Frame processOutgoing(ExtensionContext context, Frame frame);

    /**
     * Parameter negotiation. Executed before handshake response is sent to the client (server only). Returned
     * list of parameters will be present in handshake response headers.
     *
     * @param context             extension context.
     * @param requestedParameters requested parameters (from handshake request).
     * @return parameters to be present in handshake response.
     */
    List<Parameter> onExtensionNegotiation(ExtensionContext context, List<Parameter> requestedParameters);

    /**
     * Called only client side when handshake response arrives.
     * <p/>
     * Can be used to process extension parameters returned from server side.
     *
     * @param context            extension context.
     * @param responseParameters extension parameters returned from the server.
     */
    void onHandshakeResponse(ExtensionContext context, List<Parameter> responseParameters);

    /**
     * Context lifecycle method. {@link org.glassfish.tyrus.core.ExtendedExtension.ExtensionContext} won't be used
     * after this method is called.
     *
     * @param context extension context to be destroyed.
     */
    void destroy(ExtensionContext context);

    /**
     * Context present as a parameter in all {@link org.glassfish.tyrus.core.ExtendedExtension} methods. Maintains per
     * connection state of current extension.
     * <p/>
     * Context is created right before {@link #onExtensionNegotiation(org.glassfish.tyrus.core.ExtendedExtension.ExtensionContext, java.util.List)} method
     * call (server-side) or {@link #onHandshakeResponse(org.glassfish.tyrus.core.ExtendedExtension.ExtensionContext, java.util.List)} method call (client-side).
     * Last chance to access it is within {@link #destroy(org.glassfish.tyrus.core.ExtendedExtension.ExtensionContext)}
     * method invocation.
     */
    interface ExtensionContext {

        /**
         * Mutable, not synchronised property map.
         * <p/>
         * Synchronisation is not necessary if you are accessing this map only during {@link org.glassfish.tyrus.core.ExtendedExtension}
         * methods invocation.
         *
         * @return property map.
         */
        Map<String, Object> getProperties();

    }
}
