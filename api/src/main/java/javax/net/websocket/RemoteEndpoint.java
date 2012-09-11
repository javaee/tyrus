/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package javax.net.websocket;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.concurrent.Future;
/**
 * The RemoteEndpoint object is supplied by the container and represents the 'other end' of the Web Socket conversation.
 * In particular, objects of this kind include numerous ways to send web socket messages. There is no guarantee of the success
 * of receipt of a web socket message, but if the action of sending a message causes a known error, the API throws it.
 * This object includes a variety of ways to send messages to the other end of a web socket session: by whole message, in pieces
 * and asynchronously, where the point of completion is defined when all the supplied data had been written to the underlying connection.
 * The completion handlers for the asynchronous methods are always called with a different thread from that which initiated the send.
 * @author dannycoward
 * @since DRAFT 001
 */


public interface RemoteEndpoint<T> {
        /** Send a text message, blocking until all of the message has been transmitted.*/
        public void sendString(String text) throws IOException;
        /** Send a binary message, returning when all of the message has been transmitted.*/
        public void sendBytes(byte[] data) throws IOException;

        public void sendPartialString(String fragment, boolean isLast) throws IOException;
         /** Send a binary message, blocking until all of the message has been transmitted. The container
        * reads the message from the caller of the API through the Iterable parameter until all the message has
        * been sent.*/
        public void sendPartialBytes(byte[] partialByte, boolean isLast) throws IOException; // or Iterable<byte[]>

        public OutputStream getSendStream() throws IOException;
        public Writer getSendWriter() throws IOException;
        /** Sends a custom developer object, blocking until it has been transmitted. Containers will by default be able to encode
         * java primitive types, their object equivalents, and arrays or collections thereof. The developer will have provided an encoder for this object
         * type in the endpoint configuration.
         * @param o
         * @return
         */
        public void sendObject(T o) throws IOException, javax.net.websocket.EncodeException;

        /** Initiates the asynchronous transmission of a text message. This method returns before the message
         * is transmitted. Developers may provide a callback to be notified when the message has been
         * transmitted, or may use the returned Future object to track progress of the transmission. Errors
         * in transmission are given to the developer in the SendResult object in either case.
         * @param text
         * @param completion
         * @return
         */
        public Future<SendResult> sendString(String text, SendHandler completion);

        /** Initiates the asynchronous transmission of a binary message. This method returns before the message
         * is transmitted. Developers may provide a callback to be notified when the message has been
         * transmitted, or may use the returned Future object to track progress of the transmission. Errors
         * in transmission are given to the developer in the SendResult object in either case.
         * @param data
         * @param completion
         * @return
         */
        public Future<SendResult> sendBytes(byte[] data, SendHandler completion);


        /** Initiates the transmission of a custom developer object. The developer will have provided an encoder for this object
         * type in the endpoint configuration. Containers will by default be able to encode
         * java primitive types, their object equivalents, and arrays or collections thereof. Progress can be tracked using the Future object, or the developer can wait
         * for a provided callback object to be notified when transmission is complete.
         * @param o
         * @param handler
         * @return
         */
        public Future<SendResult> sendObject(T o, SendHandler handler);

        /** Send a Ping message containing the given application data to the remote endpoint. The corresponding Pong message may be picked
         * up using the MessageHandler.Pong handler.
         * @param applicationData
         */
        public void sendPing(byte[] applicationData);
        /** Allows the developer to send an unsolicited Pong message containing the given application
         * data in order to serve as a unidirectional
         * heartbeat for the session. */
        public void sendPong(byte[] applicationData);

}

