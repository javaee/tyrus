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

package org.glassfish.tyrus.core;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.websocket.Extension;

import org.glassfish.tyrus.spi.UpgradeRequest;
import org.glassfish.tyrus.spi.UpgradeResponse;

/**
 * @author Justin Lee
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public final class Handshake {

    public static final int RESPONSE_CODE_VALUE = 101;

    private static final String VERSION = "13";

    private boolean secure;
    private String origin;
    private String serverHostName;
    private int port = 80;
    private String resourcePath;
    private List<String> subProtocols = new ArrayList<String>();
    private List<Extension> extensions = new ArrayList<Extension>(); // client extensions
    // client side request!
    private UpgradeRequest request;
    private UpgradeRequest incomingRequest;
    private ExtendedExtension.ExtensionContext extensionContext;
    private SecKey secKey;


    private Handshake() {
    }

    static Handshake createClientHandShake(UpgradeRequest webSocketRequest) {
        final Handshake handshake = new Handshake();
        handshake.request = webSocketRequest;

        final URI uri = webSocketRequest.getRequestURI();
        handshake.resourcePath = uri.getPath();
        if ("".equals(handshake.resourcePath)) {
            handshake.resourcePath = "/";
        }
        if (uri.getQuery() != null) {
            handshake.resourcePath += "?" + uri.getQuery();
        }
        handshake.serverHostName = uri.getHost();
        handshake.secure = webSocketRequest.isSecure();
        handshake.port = uri.getPort();
        handshake.origin = appendPort(new StringBuilder(uri.getHost()), handshake.port, handshake.secure).toString();
        handshake.secKey = new SecKey();

        return handshake;
    }

    static Handshake createServerHandShake(UpgradeRequest request, ExtendedExtension.ExtensionContext extensionContext) {
        final Handshake handshake = new Handshake();

        handshake.incomingRequest = request;
        handshake.extensionContext = extensionContext;
        checkForHeader(request.getHeader(UpgradeRequest.UPGRADE), UpgradeRequest.UPGRADE, "WebSocket");
        checkForHeader(request.getHeader(UpgradeRequest.CONNECTION), UpgradeRequest.CONNECTION, UpgradeRequest.UPGRADE);

        handshake.origin = request.getHeader(UpgradeRequest.SEC_WS_ORIGIN_HEADER);

        if (handshake.origin == null) {
            handshake.origin = request.getHeader(UpgradeRequest.ORIGIN_HEADER);
        }
        Handshake.determineHostAndPort(handshake, request);

        // TODO - trim?
        final String protocolHeader = request.getHeader(UpgradeRequest.SEC_WEBSOCKET_PROTOCOL);
        handshake.subProtocols = (protocolHeader == null ? Collections.<String>emptyList() : Arrays.asList(protocolHeader.split(",")));

        if (handshake.serverHostName == null) {
            throw new HandshakeException("Missing required headers for WebSocket negotiation");
        }
        handshake.resourcePath = request.getRequestUri();
        final String queryString = request.getQueryString();
        if (queryString != null) {
            if (!queryString.isEmpty()) {
                handshake.resourcePath += "?" + queryString;
            }
//            Parameters queryParameters = new Parameters();
//            queryParameters.processParameters(queryString);
//            final Set<String> names = queryParameters.getParameterNames();
//            for (String name : names) {
//                queryParams.put(name, queryParameters.getParameterValues(name));
//            }
        }

        List<String> value = request.getHeaders().get(UpgradeRequest.SEC_WEBSOCKET_EXTENSIONS);
        if (value != null) {
            handshake.extensions = TyrusExtension.fromHeaders(value);
        }
        handshake.secKey = SecKey.generateServerKey(new SecKey(request.getHeader(UpgradeRequest.SEC_WEBSOCKET_KEY)));

        return handshake;
    }

    private static void checkForHeader(String currentValue, String header, String validValue) {
        validate(header, validValue, currentValue);
    }

    private static void validate(String header, String validValue, String value) {
        // http://java.net/jira/browse/TYRUS-55
        // Firefox workaround (it sends "Connections: keep-alive, upgrade").
        if (header.equalsIgnoreCase(UpgradeRequest.CONNECTION)) {
            if (!value.toLowerCase().contains(validValue.toLowerCase())) {
                throw new HandshakeException(String.format("Invalid %s header returned: '%s'", header, value));
            }
        } else {
            if (!value.equalsIgnoreCase(validValue)) {
                throw new HandshakeException(String.format("Invalid %s header returned: '%s'", header, value));
            }
        }
    }

    private static void determineHostAndPort(Handshake handshake, UpgradeRequest request) {
        String header = request.getHeader(UpgradeRequest.HOST);

        final int i = header == null ? -1 : header.indexOf(":");
        if (i == -1) {
            handshake.serverHostName = header;
            handshake.port = 80;
        } else {
            handshake.serverHostName = header.substring(0, i);
            handshake.port = Integer.valueOf(header.substring(i + 1));
        }
    }

    private static StringBuilder appendPort(StringBuilder builder, int port, boolean secure) {
        if (secure) {
            if (port != 443 && port != -1) {
                builder.append(':').append(port);
            }
        } else {
            if (port != 80 && port != -1) {
                builder.append(':').append(port);
            }
        }
        return builder;
    }

    String getOrigin() {
        return origin;
    }

    int getPort() {
        return port;
    }

    String getServerHostName() {
        return serverHostName;
    }

    List<String> getSubProtocols() {
        return subProtocols;
    }

    public void setSubProtocols(List<String> subProtocols) {
        this.subProtocols = subProtocols;
    }

    /**
     * Define to {@link String} conversion for various types.
     *
     * @param <T> type for which is conversion defined.
     */
    static abstract class Stringifier<T> {

        /**
         * Convert object to {@link String}.
         *
         * @param t object to be converted.
         * @return {@link String} representation of given object.
         */
        abstract String toString(T t);
    }

    <T> String getHeaderFromList(List<T> list, Stringifier<T> stringifier) {
        StringBuilder sb = new StringBuilder();
        Iterator<T> it = list.iterator();
        while (it.hasNext()) {
            if (stringifier != null) {
                sb.append(stringifier.toString(it.next()));
            } else {
                sb.append(it.next());
            }
            if (it.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    <T> List<String> getStringList(List<T> list, Stringifier<T> stringifier) {
        List<String> result = new ArrayList<String>();
        for (T item : list) {
            if (stringifier != null) {
                result.add(stringifier.toString(item));
            } else {
                result.add(item.toString());
            }
        }
        return result;
    }

    List<Extension> getExtensions() {
        return extensions;
    }

    public void setExtensions(List<Extension> extensions) {
        this.extensions = extensions;
    }

    /**
     * Gets the {@link UpgradeRequest}.
     *
     * @return {@link UpgradeRequest} created on this HandShake.
     */
    public UpgradeRequest getRequest() {
        return request;
    }

    /**
     * Compose the {@link UpgradeRequest} and store it for further use.
     *
     * @return composed {@link UpgradeRequest}.
     */
    public UpgradeRequest prepareRequest() {
        String host = getServerHostName();
        if (port != -1 && port != 80 && port != 443) {
            host += ":" + getPort();
        }

        putSingleHeader(request, UpgradeRequest.HOST, host);
        putSingleHeader(request, UpgradeRequest.CONNECTION, UpgradeRequest.UPGRADE);
        putSingleHeader(request, UpgradeRequest.UPGRADE, UpgradeRequest.WEBSOCKET);

        putSingleHeader(request, UpgradeRequest.SEC_WEBSOCKET_KEY, secKey.toString());
        putSingleHeader(request, UpgradeRequest.SEC_WS_ORIGIN_HEADER, getOrigin());
        putSingleHeader(request, UpgradeRequest.SEC_WEBSOCKET_VERSION, VERSION);

        if (!getSubProtocols().isEmpty()) {
            putSingleHeader(request, UpgradeRequest.SEC_WEBSOCKET_PROTOCOL, getHeaderFromList(subProtocols, null));
        }

        if (!getExtensions().isEmpty()) {
            putSingleHeader(request, UpgradeRequest.SEC_WEBSOCKET_EXTENSIONS, getHeaderFromList(getExtensions(), new Stringifier<Extension>() {
                @Override
                String toString(Extension extension) {
                    return TyrusExtension.toString(extension);
                }
            }));
        }

        final String headerValue = request.getHeader(UpgradeRequest.SEC_WS_ORIGIN_HEADER);
        request.getHeaders().remove(UpgradeRequest.SEC_WS_ORIGIN_HEADER);
        putSingleHeader(request, UpgradeRequest.ORIGIN_HEADER, headerValue);
        return request;
    }

    private void putSingleHeader(UpgradeRequest request, String headerName, String headerValue) {
        request.getHeaders().put(headerName, Arrays.asList(headerValue));
    }

    public void validateServerResponse(UpgradeResponse response) {
        if (RESPONSE_CODE_VALUE != response.getStatus()) {
            throw new HandshakeException(String.format("Response code was not %s: %s",
                    RESPONSE_CODE_VALUE, response.getStatus()));
        }

        checkForHeader(response.getFirstHeaderValue(UpgradeRequest.UPGRADE), UpgradeRequest.UPGRADE, UpgradeRequest.WEBSOCKET);
        checkForHeader(response.getFirstHeaderValue(UpgradeRequest.CONNECTION), UpgradeRequest.CONNECTION, UpgradeRequest.UPGRADE);

//        if (!getSubProtocols().isEmpty()) {
//            checkForHeader(response.getHeaders(), WebSocketEngine.SEC_WS_PROTOCOL_HEADER, WebSocketEngine.SEC_WS_PROTOCOL_HEADER);
//        }

        secKey.validateServerKey(response.getFirstHeaderValue(UpgradeResponse.SEC_WEBSOCKET_ACCEPT));
    }

    void respond(UpgradeResponse response, WebSocketApplication application/*, TyrusUpgradeResponse response*/) {
        response.setStatus(101);

        response.getHeaders().put(UpgradeRequest.UPGRADE, Arrays.asList(UpgradeRequest.WEBSOCKET));
        response.getHeaders().put(UpgradeRequest.CONNECTION, Arrays.asList(UpgradeRequest.UPGRADE));
        response.setReasonPhrase(UpgradeRequest.RESPONSE_CODE_MESSAGE);
        response.getHeaders().put(UpgradeResponse.SEC_WEBSOCKET_ACCEPT, Arrays.asList(secKey.getSecKey()));

        if (subProtocols != null && !subProtocols.isEmpty()) {
            List<String> appProtocols = application.getSupportedProtocols(subProtocols);
            if (!appProtocols.isEmpty()) {
                response.getHeaders().put(UpgradeRequest.SEC_WEBSOCKET_PROTOCOL, getStringList(appProtocols, null));
            }
        }

        if (!application.getSupportedExtensions().isEmpty()) {
            response.getHeaders().put(UpgradeRequest.SEC_WEBSOCKET_EXTENSIONS, getStringList(application.getSupportedExtensions(), new Stringifier<Extension>() {
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
                                // null is there because extension is wrapped and the original parameters are stored
                                // in the wrapped instance.
                                return ((ExtendedExtension) extension).onExtensionNegotiation(extensionContext, null);
                            }
                        });
                    } else {
                        return TyrusExtension.toString(extension);
                    }
                }
            }));
        }
        application.onHandShakeResponse(incomingRequest, response);
    }
}
