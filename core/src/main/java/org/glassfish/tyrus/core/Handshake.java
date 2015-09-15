/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.core;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;

import org.glassfish.tyrus.core.extension.ExtendedExtension;
import org.glassfish.tyrus.core.l10n.LocalizationMessages;
import org.glassfish.tyrus.spi.UpgradeRequest;
import org.glassfish.tyrus.spi.UpgradeResponse;

/**
 * Class responsible for performing and validating handshake.
 *
 * @author Justin Lee
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public final class Handshake {

    private static final int RESPONSE_CODE_VALUE = 101;
    private static final String VERSION = "13";

    private List<String> subProtocols = new ArrayList<String>();
    private List<Extension> extensions = new ArrayList<Extension>(); // client extensions
    // client side handshake request
    private RequestContext request;
    // server side handshake request
    private UpgradeRequest incomingRequest;
    private ExtendedExtension.ExtensionContext extensionContext;
    private SecKey secKey;

    /**
     * @see #createClientHandshake(org.glassfish.tyrus.core.RequestContext)
     * @see #createServerHandshake(org.glassfish.tyrus.spi.UpgradeRequest,
     * org.glassfish.tyrus.core.extension.ExtendedExtension.ExtensionContext)
     */
    private Handshake() {
    }

    /**
     * Client-side handshake.
     *
     * @param webSocketRequest request representation to be modified for use as WebSocket handshake request.
     * @return handshake instance.
     */
    public static Handshake createClientHandshake(RequestContext webSocketRequest) {
        final Handshake handshake = new Handshake();
        handshake.request = webSocketRequest;
        handshake.secKey = new SecKey();

        return handshake;
    }

    /**
     * Client side only - get the {@link UpgradeRequest}.
     *
     * @return {@link UpgradeRequest} created on this HandShake.
     */
    public RequestContext getRequest() {
        return request;
    }

    /**
     * Client side only - set the list of supported subprotocols.
     *
     * @param subProtocols list of supported subprotocol.
     */
    public void setSubProtocols(List<String> subProtocols) {
        this.subProtocols = subProtocols;
    }

    /**
     * Client side only - set the list of supported extensions.
     *
     * @param extensions list of supported extensions.
     */
    public void setExtensions(List<Extension> extensions) {
        this.extensions = extensions;
    }

    /**
     * Client side only - compose the {@link UpgradeRequest} and store it for further use.
     *
     * @return composed {@link UpgradeRequest}.
     */
    public UpgradeRequest prepareRequest() {

        Map<String, List<String>> requestHeaders = request.getHeaders();

        updateHostAndOrigin(request);

        requestHeaders.put(UpgradeRequest.CONNECTION, Collections.singletonList(UpgradeRequest.UPGRADE));
        requestHeaders.put(UpgradeRequest.UPGRADE, Collections.singletonList(UpgradeRequest.WEBSOCKET));

        requestHeaders.put(HandshakeRequest.SEC_WEBSOCKET_KEY, Collections.singletonList(secKey.toString()));
        requestHeaders.put(HandshakeRequest.SEC_WEBSOCKET_VERSION, Collections.singletonList(VERSION));

        if (!subProtocols.isEmpty()) {
            requestHeaders.put(HandshakeRequest.SEC_WEBSOCKET_PROTOCOL,
                               Collections.singletonList(Utils.getHeaderFromList(subProtocols, null)));
        }

        if (!extensions.isEmpty()) {
            requestHeaders.put(HandshakeRequest.SEC_WEBSOCKET_EXTENSIONS,
                               Collections.singletonList(
                                       Utils.getHeaderFromList(extensions, new Utils.Stringifier<Extension>() {
                                           @Override
                                           String toString(Extension extension) {
                                               return TyrusExtension.toString(extension);
                                           }
                                       }))
            );
        }

        return request;
    }

    /**
     * Client side only - validate server response.
     *
     * @param response response to be validated.
     * @throws HandshakeException when HTTP Status of received response is not 101 - Switching protocols.
     */
    public void validateServerResponse(UpgradeResponse response) throws HandshakeException {
        if (RESPONSE_CODE_VALUE != response.getStatus()) {
            throw new HandshakeException(response.getStatus(), LocalizationMessages
                    .INVALID_RESPONSE_CODE(RESPONSE_CODE_VALUE, response.getStatus()));
        }

        checkForHeader(response.getFirstHeaderValue(UpgradeRequest.UPGRADE), UpgradeRequest.UPGRADE,
                       UpgradeRequest.WEBSOCKET);
        checkForHeader(response.getFirstHeaderValue(UpgradeRequest.CONNECTION), UpgradeRequest.CONNECTION,
                       UpgradeRequest.UPGRADE);

//        if (!getSubProtocols().isEmpty()) {
//            checkForHeader(response.getHeaders(), WebSocketEngine.SEC_WS_PROTOCOL_HEADER,
//                           WebSocketEngine.SEC_WS_PROTOCOL_HEADER);
//        }

        secKey.validateServerKey(response.getFirstHeaderValue(HandshakeResponse.SEC_WEBSOCKET_ACCEPT));
    }

    /**
     * Client side only - Generate host and origin header and put them to the upgrade request headers.
     *
     * @param upgradeRequest upgrade request to be updated.
     */
    public static void updateHostAndOrigin(final UpgradeRequest upgradeRequest) {
        URI requestUri = upgradeRequest.getRequestURI();

        String host = requestUri.getHost();
        int port = requestUri.getPort();
        if (upgradeRequest.isSecure()) {
            if (port != 443 && port != -1) {
                host += ":" + port;
            }
        } else {
            if (port != 80 && port != -1) {
                host += ":" + port;
            }
        }

        Map<String, List<String>> requestHeaders = upgradeRequest.getHeaders();
        requestHeaders.put(UpgradeRequest.HOST, Collections.singletonList(host));
        requestHeaders.put(UpgradeRequest.ORIGIN_HEADER, Collections.singletonList(host));
    }

    /**
     * Server-side handshake.
     *
     * @param request          received handshake request.
     * @param extensionContext extension context.
     * @return created handshake.
     * @throws HandshakeException when there is problem with received {@link UpgradeRequest}.
     */
    static Handshake createServerHandshake(UpgradeRequest request,
                                           ExtendedExtension.ExtensionContext extensionContext) throws
            HandshakeException {
        final Handshake handshake = new Handshake();

        handshake.incomingRequest = request;
        handshake.extensionContext = extensionContext;
        checkForHeader(request.getHeader(UpgradeRequest.UPGRADE), UpgradeRequest.UPGRADE, "WebSocket");
        checkForHeader(request.getHeader(UpgradeRequest.CONNECTION), UpgradeRequest.CONNECTION, UpgradeRequest.UPGRADE);

        // TODO - trim?
        final String protocolHeader = request.getHeader(HandshakeRequest.SEC_WEBSOCKET_PROTOCOL);
        handshake.subProtocols =
                (protocolHeader == null ? Collections.<String>emptyList() : Arrays.asList(protocolHeader.split(",")));

        if (request.getHeader(UpgradeRequest.HOST) == null) {
            throw new HandshakeException(LocalizationMessages.HEADERS_MISSING());
        }

//        final String queryString = request.getQueryString();
//        if (queryString != null) {
//            if (!queryString.isEmpty()) {
//            }
////            Parameters queryParameters = new Parameters();
////            queryParameters.processParameters(queryString);
////            final Set<String> names = queryParameters.getParameterNames();
////            for (String name : names) {
////                queryParams.put(name, queryParameters.getParameterValues(name));
////            }
//        }

        List<String> value = request.getHeaders().get(HandshakeRequest.SEC_WEBSOCKET_EXTENSIONS);
        if (value != null) {
            handshake.extensions = TyrusExtension.fromHeaders(value);
        }
        handshake.secKey = SecKey.generateServerKey(new SecKey(request.getHeader(HandshakeRequest.SEC_WEBSOCKET_KEY)));

        return handshake;
    }

    private static void checkForHeader(String currentValue, String header, String validValue) throws
            HandshakeException {
        validate(header, validValue, currentValue);
    }

    private static void validate(String header, String validValue, String value) throws HandshakeException {
        // http://java.net/jira/browse/TYRUS-55
        // Firefox workaround (it sends "Connections: keep-alive, upgrade").
        if (header.equalsIgnoreCase(UpgradeRequest.CONNECTION)) {
            if (value == null || !value.toLowerCase().contains(validValue.toLowerCase())) {
                throw new HandshakeException(LocalizationMessages.INVALID_HEADER(header, value));
            }
        } else {
            if (!validValue.equalsIgnoreCase(value)) {
                throw new HandshakeException(LocalizationMessages.INVALID_HEADER(header, value));
            }
        }
    }

    // server side
    List<Extension> respond(UpgradeRequest request, UpgradeResponse response, TyrusEndpointWrapper endpointWrapper
    /*,TyrusUpgradeResponse response*/) {
        response.setStatus(101);

        response.getHeaders().put(UpgradeRequest.UPGRADE, Arrays.asList(UpgradeRequest.WEBSOCKET));
        response.getHeaders().put(UpgradeRequest.CONNECTION, Arrays.asList(UpgradeRequest.UPGRADE));
        response.setReasonPhrase(UpgradeRequest.RESPONSE_CODE_MESSAGE);
        response.getHeaders().put(HandshakeResponse.SEC_WEBSOCKET_ACCEPT, Arrays.asList(secKey.getSecKey()));

        final List<String> protocols = request.getHeaders().get(HandshakeRequest.SEC_WEBSOCKET_PROTOCOL);
        final List<Extension> extensions =
                TyrusExtension.fromString(request.getHeaders().get(HandshakeRequest.SEC_WEBSOCKET_EXTENSIONS));

        if (subProtocols != null && !subProtocols.isEmpty()) {
            String protocol = endpointWrapper.getNegotiatedProtocol(protocols);
            if (protocol != null && !protocol.isEmpty()) {
                response.getHeaders().put(HandshakeRequest.SEC_WEBSOCKET_PROTOCOL, Arrays.asList(protocol));
            }
        }

        final List<Extension> negotiatedExtensions = endpointWrapper.getNegotiatedExtensions(extensions);
        if (!negotiatedExtensions.isEmpty()) {
            response.getHeaders().put(
                    HandshakeRequest.SEC_WEBSOCKET_EXTENSIONS,
                    Utils.getStringList(negotiatedExtensions, new Utils.Stringifier<Extension>() {
                        @Override
                        String toString(final Extension extension) {
                            if (extension instanceof ExtendedExtension) {
                                return TyrusExtension.toString(new Extension() {
                                    @Override
                                    public String getName() {
                                        return extension.getName();
                                    }

                                    @Override
                                    public List<Parameter> getParameters() {
                                        // TODO! XXX FIXME
                                        // null is there because extension is wrapped and the
                                        // original parameters are stored
                                        // in the wrapped instance.
                                        return ((ExtendedExtension) extension)
                                                .onExtensionNegotiation(extensionContext, null);
                                    }
                                });
                            } else {
                                return TyrusExtension.toString(extension);
                            }
                        }
                    }));
        }
        endpointWrapper.onHandShakeResponse(incomingRequest, response);

        return negotiatedExtensions;
    }
}
