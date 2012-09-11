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

package org.glassfish.websocket.api;

import java.io.*;
import java.util.concurrent.Future;

/**
 * An object that represents the 'other end' of the web socket conversation
 * with a web socket end point.
 *
 * @author dannycoward
 */

public interface RemoteEndpoint<T> {
    public boolean isConnected();

    public Session getSession();


    /**
     * Send a text message, blocking until all of the message has been transmitted.
     */
    public void sendString(String text) throws IOException;

    /**
     * Send a binary message, returning when all of the message has been transmitted.
     */
    public void sendBytes(byte[] data) throws IOException;

    public void sendPartialString(String fragment, boolean isLast) throws IOException;

    /**
     * Send a binary message, blocking until all of the message has been transmitted. The container
     * reads the message from the caller of the API through the Iterable parameter until all the message has
     * been sent.
     */
    public void sendPartialBytes(byte[] partialByte, boolean isLast) throws IOException; // or Iterable<byte[]>

    public OutputStream getSendStream() throws IOException;

    public Writer getSendWriter() throws IOException;

    /**
     * Sends a custom developer object, blocking until it has been transmitted. Containers will by default be able to encode
     * java primitive types, their object equivalents, and arrays or collections thereof. The developer will have provided an encoder for this object
     * type in the endpoint configuration.
     *
     * @param o object to be sent.
     * @return
     */
    public void sendObject(T o) throws IOException, EncodeException;

    /**
     * Initiates the asynchronous transmission of a text message. This method returns before the message
     * is transmitted. Developers may provide a callback to be notified when the message has been
     * transmitted, or may use the returned Future object to track progress of the transmission. Errors
     * in transmission are given to the developer in the SendResult object in either case.
     *
     * @param text
     * @param completion
     * @return
     */
    public Future<SendResult> sendString(String text, SendHandler completion);

    /**
     * Initiates the asynchronous transmission of a binary message. This method returns before the message
     * is transmitted. Developers may provide a callback to be notified when the message has been
     * transmitted, or may use the returned Future object to track progress of the transmission. Errors
     * in transmission are given to the developer in the SendResult object in either case.
     *
     * @param data
     * @param completion
     * @return
     */
    public Future<SendResult> sendBytes(byte[] data, SendHandler completion);


    /**
     * Initiates the transmission of a custom developer object. The developer will have provided an encoder for this object
     * type in the endpoint configuration. Containers will by default be able to encode
     * java primitive types, their object equivalents, and arrays or collections thereof. Progress can be tracked using the Future object, or the developer can wait
     * for a provided callback object to be notified when transmission is complete.
     *
     * @param o
     * @param handler
     * @return
     */
    public Future<SendResult> sendObject(T o, SendHandler handler);

    /**
     * Send a Ping message containing the given application data to the remote endpoint. The corresponding Pong message may be picked
     * up using the MessageHandler.Pong handler.
     *
     * @param applicationData
     */
    public void sendPing(byte[] applicationData);

    /**
     * Allows the developer to send an unsolicited Pong message containing the given application
     * data in order to serve as a unidirectional
     * heartbeat for the session.
     */
    public void sendPong(byte[] applicationData);
}
