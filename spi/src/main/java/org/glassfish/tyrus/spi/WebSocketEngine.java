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
package org.glassfish.tyrus.spi;

import java.nio.ByteBuffer;

/**
 * Web Socket engine is the main entry-point to WebSocket implementation.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public interface WebSocketEngine {

    // TODO return WebSocketConnection
    /**
     * Handles upgrade process, response is written using {@link ResponseWriter#write(UpgradeResponse)}.
     *
     * @param writer  used to write HTTP response.
     * @param request representation of HTTP request.
     * @return {@code true} if upgrade was successful, {@code false} otherwise.
     */
    boolean upgrade(Writer writer, UpgradeRequest request, ResponseWriter responseWriter);

    // TODO return WebSocketConnection
    /**
     * Handles upgrade process, response is written using {@link ResponseWriter#write(UpgradeResponse)}.
     *
     * @param writer          used to write HTTP response.
     * @param request         representation of HTTP request.
     * @param upgradeListener {@link WebSocketEngine.UpgradeListener#onUpgradeFinished()}
     *                        is invoked after handshake response is sent. Registering this listener transfer
     *                        responsibility for calling {@link #onConnect(Writer)} to this listener. This might be
     *                        useful especially when you need to wait for some other initialization (like Servlet update
     *                        mechanism); invoking {@link #onConnect(Writer)} means that {@link javax.websocket.OnOpen}
     *                        annotated method will be invoked which allows sending messages, so underlying connection
     *                        needs to be ready.
     * @return {@code true} if upgrade was successful, {@code false} otherwise.
     */
    boolean upgrade(Writer writer, UpgradeRequest request, ResponseWriter responseWriter, UpgradeListener upgradeListener);

    // TODO remove. The incoming data is received using IncomingDataHandler
    /**
     * Processes incoming data, including sending a response (if any).
     *
     * @param writer related writer instance (representing underlying connection).
     * @param data   incoming data.
     */
    void processData(Writer writer, ByteBuffer data);

    /**
     * Causes invocation if {@link javax.websocket.OnOpen} annotated method. Can be invoked only when
     * {@link #upgrade(Writer, UpgradeRequest, ResponseWriter, WebSocketEngine.UpgradeListener)} is used.
     *
     * @param writer related writer instance (representing underlying connection).
     */
    void onConnect(Writer writer);

    /**
     * Close the corresponding WebSocket with a close reason.
     * <p/>
     * This method is used for indicating that underlying connection was closed and/or other condition requires
     * closing socket.
     *
     * @param writer      related writer instance (representing underlying connection).
     * @param closeCode   close code.
     * @param closeReason close reason.
     */
    void close(Writer writer, int closeCode, String closeReason);

    /**
     * HTTP Upgrade listener.
     */
    interface UpgradeListener {

        /**
         * Called when request is upgraded. The responsibility for making {@link #onConnect(Writer)}
         * call is on listener when it is used.
         */
        void onUpgradeFinished();
    }

    /**
     * Responsible for writing HTTP response to underlying connection or container.
     */
    interface ResponseWriter {
        /**
         * Write {@link UpgradeResponse} to underlying connection.
         *
         * @param response response to be written.
         */
        public void write(UpgradeResponse response);
    }
}
