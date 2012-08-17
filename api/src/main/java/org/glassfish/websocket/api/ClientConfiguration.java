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

import java.util.List;
import org.glassfish.websocket.api.extension.Extension;
/**
 * The ClientConfiguration is a special kind of endpoint configuration object that contains
 * web socket configuration information specific only to client endpoints.
 * @author dannycoward
 * @since DRAFT 001
 */
public interface ClientConfiguration extends EndpointConfiguration {

    /** The ordered list of sub protocols a client endpoint would like to use. 
     * This list is used to generate the Sec-WebSocket-Protocol header in the opening
     * handshake for clients using this configuration. The first protocol name is the most preferred. 
     * See <a href="http://tools.ietf.org/html/rfc6455#section-4.1">Client Opening Handshake</a>
     */
    public List<String> getPreferredSubprotocols();
    
    /** Return the list of all the extensions that this client supports. These are the extensions that will
     be used to populate the Sec-WebSocket-Extensions header in the opening handshake for clients
     * using this configuration. The first extension in the list is the most preferred extension.
     * See <a href="http://tools.ietf.org/html/rfc6455#section-9.1">Negotiating Extensions</a>
     
     */
    public List<Extension> getExtensions();
   

}


