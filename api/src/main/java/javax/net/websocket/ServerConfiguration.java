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

import javax.net.websocket.extensions.Extension;
import java.net.URI;
import java.util.List;

/**
 * The ServerConfiguration is a special kind of endpoint configuration object that contains
 * web socket configuration information specific only to server endpoints.
 * @author dannycoward
 * @since DRAFT 001
 */
public interface ServerConfiguration extends EndpointConfiguration {

    /** Return the subprotocol this server endpoint has chosen from the requested
     * list supplied by a client who wishes to connect, or none if there wasn't one
     * this server endpoint liked. See <a href="http://tools.ietf.org/html/rfc6455#section-4.2.2">Sending the Server's Opening Handshake</a>
     * @param requestedSubprotocols
     * @return
     */
    public String getNegotiatedSubprotocol(List<String> requestedSubprotocols);

    /** Return the ordered list of extensions that this server will support given the requested
     * extension list passed in. See <a href="http://tools.ietf.org/html/rfc6455#section-9.1">Negotiating Extensions</a>
     * @param requestedExtensions
     * @return
     */
    public List<Extension> getNegotiatedExtensions(List<Extension> requestedExtensions);

    /** Check the value of the Origin header (<a href="http://tools.ietf.org/html/rfc6454">See definition</a>) the client passed during the opening
     * handshake.
     *
     * @param originHeaderValue
     * @return
     */
    public boolean checkOrigin(String originHeaderValue);

    /**
     * Answers whether the current configuration matches the given URI.
     * @param uri
     * @return
     */

    public boolean matchesURI(URI uri);


   /** Called by the container after it has formulated a handshake response resulting from
    * a well-formed handshake request. The container has already has already checked that this configuration
    * has a matching URI, determined the validity of the origin using the checkOrigin method, and filled
    * out the negotiated subprotocols and extensions based on this configuration.
    * Custom configurations may override this method in order to inspect
    * the request parameters and modify the handshake response.
     * and the URI checking also.
     * @param request
     * @param response
     * @return
     */
    public void modifyHandshake(HandshakeRequest request, HandshakeResponse response);

}
