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

/**
 * The Decoder interface holds member interfaces that define how a developer can provide
 * the web socket container a way web socket messages into developer defined custom objects.
 * @author dannycoward
 * @since DRAFT 002
 */
public interface Decoder {

   /**  This interface defines how binary messages are converted. */
    public interface Binary<T> extends Decoder {

        /** Decode the given bytes into an object of type T. */
        public T decode(byte[] bytes) throws javax.net.websocket.DecodeException;
        /** Answer whether the given bytes can be decoded into an object of type T. */
        public boolean willDecode(byte[] bytes);
    }

    /**  This interface defines how text messages are converted. */
    public interface Text<T> extends Decoder {
        /** Decode the given String into an object of type T. */
        public T decode(String s) throws javax.net.websocket.DecodeException;
        /** Answer whether the given String can be decoded into an object of type T. */
        public boolean willDecode(String s);
    }


}
