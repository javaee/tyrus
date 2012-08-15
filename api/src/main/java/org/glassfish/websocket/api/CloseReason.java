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
package org.glassfish.websocket.api;

/**
 * A class encapsulating the reason why a web socket has been closed, or why it is being asked to 
 * close. Note the acceptable uses of codes and reason phrase defined in FRC 6455.
 * @author dannycoward
 * @since DRAFT 001
 */

public class CloseReason {
    private Code closeCode;
    private String reasonPhrase;
    
    /** Creates a reason for closing a web socket connection with the given
     * code and reason phrase.
     * @param closeCode
     * @param reasonPhrase 
     */
    public CloseReason(Code closeCode, String reasonPhrase) {
        this.closeCode = closeCode;
        this.reasonPhrase = reasonPhrase;
    }
    
    public Code getCode() {
        return closeCode;
    }
    
    public String getReasonPhrase() {
        return this.reasonPhrase;
    }
     
    /** Enumeration of status codes for a web socket close. */
    public enum Code {
        /* 1000 */
        NORMAL_CLOSURE(1000),
        /* 1001 */
        GOING_AWAY(1001) ,
        /* 1002 */
        PROTOCOL_ERROR(1002),
        /* 1003 */
        CANNOT_ACCEPT(1003),
        /* 1004 */
        RESERVED(1004
        /* 1005 */),
        NO_STATUS_CODE(1005),
        /* 1006 */
        CLOSED_ABNORMALLY(1006),
        /* 1007 */
        NOT_CONSISTENT(1007),
        /* 1008 */
        VIOLATED_POLICY(1008),
        /* 1009 */
        TOO_BIG(1009),
        /* 101 */
        NO_EXTENSION(1010),
        /* 1011 */
        UNEXPECTED_CONDITION(1011),
        /* 1015 */
        TLS_HANDSHAKE_FAILURE(1015);

        
        Code(int code) {
            this.code = code;
        }
        
        /** Return the code number of this status code. */
        public int getCode() {
            return code;
        }
        private int code;
    }
} 

