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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;

import javax.websocket.CloseReason;
import javax.websocket.SendHandler;

/**
 * Subset of {@link javax.websocket.RemoteEndpoint} interface which should be implemented
 * by container implementations.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public abstract class RemoteEndpoint {

    /**
     * Send text message.
     *
     * @param text the message to be sent.
     * @return {@link Future} related to send command.
     */
    public abstract Future<?> sendText(String text);

    /**
     * Send text message.
     *
     * @param text    the message to be sent.
     * @param handler notification handler. {@link SendHandler#onResult(javax.websocket.SendResult)} is called when send
     *                operation is completed.
     */
    public abstract void sendText(String text, SendHandler handler);

    /**
     * Send binary message.
     *
     * @param data the message to be sent.
     * @return {@link Future} related to send command.
     */
    public abstract Future<?> sendBinary(ByteBuffer data);

    /**
     * Send binary message.
     *
     * @param data    the message to be sent.
     * @param handler notification handler. {@link SendHandler#onResult(javax.websocket.SendResult)} is called when send
     *                operation is completed.
     */
    public abstract void sendBinary(ByteBuffer data, SendHandler handler);

    /**
     * Send text message in pieces, blocking until all of the message has been transmitted. The runtime
     * reads the message in order. Non-final pieces are sent with isLast set to false. The final piece
     * must be sent with isLast set to true.
     *
     * @param fragment the piece of the message being sent.
     * @param isLast   Whether the fragment being sent is the last piece of the message.
     */
    public abstract Future<?> sendText(String fragment, boolean isLast);

    /**
     * Send a binary message in pieces, blocking until all of the message has been transmitted. The runtime
     * reads the message in order. Non-final pieces are sent with isLast set to false. The final piece
     * must be sent with isLast set to true.
     *
     * @param partialByte the piece of the message being sent.
     * @param isLast      Whether the fragment being sent is the last piece of the message.
     */
    public abstract Future<?> sendBinary(ByteBuffer partialByte, boolean isLast); // or Iterable<byte[]>

    /**
     * Send a Ping message containing the given application data to the remote endpoint. The corresponding Pong message may be picked
     * up using the MessageHandler.Pong handler.
     *
     * @param applicationData the data to be carried in the ping request.
     */
    public abstract Future<?> sendPing(ByteBuffer applicationData) throws IOException;

    /**
     * Allows the developer to send an unsolicited Pong message containing the given application
     * data in order to serve as a unidirectional
     * heartbeat for the session.
     *
     * @param applicationData the application data to be carried in the pong response.
     */
    public abstract Future<?> sendPong(ByteBuffer applicationData) throws IOException;

    /**
     * Send a Close message.
     *
     * @param closeReason close reason.
     */
    public abstract void close(CloseReason closeReason);

    /**
     * Sets the timeout for the writing operation.
     *
     * @param timeoutMs timeout in milliseconds.
     */
    public abstract void setWriteTimeout(long timeoutMs);
}
