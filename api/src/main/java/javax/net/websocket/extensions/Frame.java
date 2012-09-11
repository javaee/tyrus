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
package javax.net.websocket.extensions;

import javax.net.websocket.CloseReason;

/**
 * Frame is the top level interface that represents some kind of web socket frame.
 * @since DRAFT 003
 * @author dannycoward
 */
public interface Frame {
    /** Common super-type for all the web socket frames that carry application data. */
    public interface Data extends Frame {

        /** Return data used by a web socket extension in this frame. */
        public byte[] getExtensionData();
        /** A text data frame. */
        public interface Text extends Data {
            /** Return the textual data in this text frame. */
            public String getText();
            /** A kind of text frame that represents a fragment of a message in a series of such frames
             * that, re-assembled, form a complete text message.
             */
            public interface Continuation extends Text {

                /** Indicates whether this text message fragment
                 * is the last in the series or not.
                 * @return
                 */
                public boolean isLast();
            }
        }
        /** A binary data frame  */
        public interface Binary extends Data {
            /** The application data in the binary frame. */
            public byte[] getData();
             /** A kind of binary frame that represents a fragment of a message in a series of such frames
             * that, re-assembled, form a complete text message.
             */
            public interface Continuation extends Binary {
                 /** Indicates whether this text message fragment
                 * is the last in the series or not.
                 * @return
                 */
                public boolean isLast();
            }
        }
    }
    /** Super type for all the websocket control frames.*/
    public interface Control extends Frame {
        /** A web socket Ping frame.*/
        public interface Ping extends Control  {
             /** The application data within the Ping frame.*/
            public byte[] getApplicationData();
        }
        /** A web socket Pong frame.*/
        public interface Pong extends Control {
            /** The application data within the Pong frame.*/
            public byte[] getApplicationData();
        }
         /** A web socket Close frame.*/
        public interface Close extends Control {
            /** The reason phrase for this close.*/
            public String getReasonPhrase();
            /** The close code for this close.*/
            public CloseReason.Code getCloseCode();
        }
    }
}
