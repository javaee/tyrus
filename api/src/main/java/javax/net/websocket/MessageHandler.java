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

import java.io.InputStream;
import java.io.Reader;
import java.nio.channels.AsynchronousByteChannel;

/**
 * Developers implement MessageHandlers in order to receive incoming messages
 * during a web socket conversation.
 * Each web socket session uses no more than one thread at a time to call its MessageHandlers. This means
 * that, provided each message handler instance is used to handle messages for one web socket session, at most
 * one thread at a time can be calling any of its methods. Developers who wish to handle messages from multiple
 * clients within the same message handlers may do so by adding the same instance as a handler on each of the Session
 * objects for the clients. In that case, they will need to code with the possibility of their MessageHandler
 * being called concurrently by multiple threads, each one arising from a different client session.
 * @since DRAFT 001
 * @author dannycoward
 */
public interface MessageHandler {

    /** This kind of listener listens for text messages. If the message is received in parts,
     * the container buffers it until it is has been fully received before this method is called.
     * @since DRAFT 002
     */
    public interface Text extends MessageHandler {
         /** Called when the text message has been fully received.
         * @param tex the binary message data.
         */
        public void onMessage(String text);
    }
    /** This kind of listener listens for binary messages. If the message is received in parts,
     * the container buffers it until it is has been fully received before this method is called.
     * @since DRAFT 002
     */
    public interface Binary extends MessageHandler {
        /** Called when the binary message has been fully received.
         * @param data the binary message data.
         */
        public void onMessage(byte[] data);
    }

    /** This kind of handler is called to process for binary messages which may arrive in multiple parts. A single binary
     * message may consist of 0 to n calls to this method where @param last is false followed by a single call with @param last set to
     * true. Messages do not interleave and the parts arrive in order.
     */

    public interface AsyncBinary extends MessageHandler {
         /** Called when part of a binary message has been received.
         *
         * @param part The fragment of the message received.
         * @param last Whether or not this is last in the sequence of parts of the message.
         */
         public void onMessagePart(byte[] bytes, boolean last);
    }

     /** This kind of handler is called to process for text messages which may arrive in multiple parts. A single text
     * message may consist of 0 to n calls to this method where @param last is false followed by a single call with @param last set to
     * true. Messages do not interleave and the parts arrive in order.
     */

    public interface AsyncText extends MessageHandler {
        /** Called when part of a text message has been received.
         *
         * @param part The fragment of the message received.
         * @param last Whether or not this is last in the sequence of parts of the message.
         */
        public void onMessagePart(String part, boolean last);
    }

     /** This kind of listener listens for messages that the container knows how to decode into an object of type T.
      * This will involve providing the endpoint configuration a decoder for objects of type T.
      * @since DRAFT 002
     */
    public interface DecodedObject<T> extends MessageHandler {
        /** Called when the container receives a message that it has been able to decode
         * into an object of type T.
         * @param customObject
         */
        public void onMessage(T customObject);
    }
     /** This kind of handler is called when a new binary message arrives that is to be read using a blocking stream.
      * @since DRAFT 002
     */
    public interface BinaryStream extends MessageHandler {
        /** This method is called when a new binary message has begun to arrive. The InputStream passed in allows
         * implementors of this handler to read the message in a blocking manner. The read methods on the
         * InputStream block until message data is available. A new input stream is created for each incoming
         * message.
         * @param is
         */
        public void onMessage(InputStream is);
    }
      /** This kind of handler is called when a new text message arrives that is to be read using a blocking stream.
      * @since DRAFT 002
     */
    public interface CharacterStream extends MessageHandler {
         /** This method is called when a new text message has begun to arrive. The Reader passed in allows
         * implementors of this handler to read the message in a blocking manner. The read methods on the
         * Reader block until message data is available. A new reader is created for each incoming
         * message.
         * @param is
         */

        public void onMessage(Reader r);
    }

    /** This handler is called back by the container when the container receives a pong message. */
    public interface Pong extends MessageHandler {
        /** Called when the container receives a pong message containing the given application data. */
        public void onPong(byte[] applicationData);
    }


}
