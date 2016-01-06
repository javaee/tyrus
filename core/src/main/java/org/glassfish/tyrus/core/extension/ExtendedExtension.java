/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2016 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.core.extension;

import java.util.List;
import java.util.Map;

import javax.websocket.Extension;

import org.glassfish.tyrus.core.frame.Frame;

/**
 * WebSocket {@link Extension}.
 * <p>
 * Capable of parameters negotiation, incoming and outgoing frames processing.
 * <p>
 * Extensions are ordered as they appear in handshake response headers, as per RFC 6455, chapter 9.1. It does not state
 * any ordering in regards of sender/receiver side and current implementation reflects that. See TODO below for
 * possible
 * issue related to ordering.
 * <p>
 * Let's say we have negotiated two extensions, ext1 and ext2 in this order without parameters, so handshake response
 * headers will be: "sec-websocket-extensions: ext1, ext2". Prefix "c_" means client side, prefix "s_" server side.
 * <pre>
 *   client -&gt; server
 *
 *                +--------+   +--------+                  +--------+   +--------+
 *   client  &gt;----| c_ext1 |--&gt;| c_ext2 |--&gt; [network] --&gt; | s_ext1 |--&gt;| s_ext2 |--&gt; server
 *                +--------+   +--------+                  +--------+   +--------+
 *
 *   client &lt;- server
 *
 *                +--------+   +--------+                  +--------+   +--------+
 *   client  &lt;----| c_ext2 |&lt;--| c_ext1 |&lt;-- [network] &lt;-- | s_ext2 |&lt;--| s_ext1 |&lt;-- server
 *                +--------+   +--------+                  +--------+   +--------+
 * </pre>
 * <p>
 * Any exception thrown from processIncoming or processOutgoing will be logged. Rest of extension chain will be invoked
 * without any modifications done in "faulty" extension. {@link javax.websocket.OnError} won't be triggered. (this
 * might
 * change).
 * <pre>TODO:\
 * - naming.
 * - ordering - we might need to ensure that compression/decompression is invoked first when receiving and last when
 * sending message (to enable access to uncompressed data for other extensions).
 * - param negotiation.
 * - param validation.
 * - general validation - two extensions using same rsv bit cannot be "negotiated" for same session/connection.
 * - negotiation exception handling (onExtensionNegotiation)
 * </pre>
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public interface ExtendedExtension extends Extension {

    /**
     * Process incoming frame.
     * <p>
     * Passed frame is unmasked in case it was masked when received (server to client communication).
     *
     * @param context per-connection/session context.
     * @param frame   websocket frame representation.
     * @return processed frame. Can be new instance.
     */
    Frame processIncoming(ExtensionContext context, Frame frame);

    /**
     * Process outgoing frame.
     * <p>
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
     * <p>
     * TODO: Seems like list of all "requested" extensions should be passed (at least all with the same name) - the
     * TODO: extension implementation should be able to choose which version (parameter set) will be used for the
     * TODO: established WebSocket session. (We should also properly describe that this method will be called only once
     * TODO: per extension per websocket session and have the possibility to NOT add this extension to negotiated
     * TODO: extensions).
     *
     * @param context             extension context.
     * @param requestedParameters requested parameters (from handshake request).
     * @return parameters to be present in handshake response.
     */
    List<Parameter> onExtensionNegotiation(ExtensionContext context, List<Parameter> requestedParameters);

    /**
     * Called only on the client side when handshake response arrives.
     * <p>
     * Can be used to process extension parameters returned from server side.
     *
     * @param context            extension context.
     * @param responseParameters extension parameters returned from the server.
     */
    void onHandshakeResponse(ExtensionContext context, List<Parameter> responseParameters);

    /**
     * Context lifecycle method. {@link ExtendedExtension.ExtensionContext} won't be used
     * after this method is called.
     *
     * @param context extension context to be destroyed.
     */
    void destroy(ExtensionContext context);

    /**
     * Context present as a parameter in all {@link ExtendedExtension} methods. Maintains per
     * connection state of current extension.
     * <p>
     * Context is created right before {@link #onExtensionNegotiation(ExtendedExtension.ExtensionContext,
     * java.util.List)} method call (server-side) or {@link #onHandshakeResponse(ExtendedExtension.ExtensionContext,
     * java.util.List)} method call (client-side). Last chance to access it is within {@link
     * #destroy(ExtendedExtension.ExtensionContext)} method invocation.
     */
    interface ExtensionContext {

        /**
         * Mutable, not synchronised property map.
         * <p>
         * Synchronisation is not necessary if you are accessing this map only during {@link ExtendedExtension} methods
         * invocation.
         *
         * @return property map.
         */
        Map<String, Object> getProperties();

    }
}
