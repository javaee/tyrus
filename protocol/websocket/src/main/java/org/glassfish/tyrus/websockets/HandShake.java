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

package org.glassfish.tyrus.websockets;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import org.glassfish.tyrus.spi.HandshakeRequest;
import org.glassfish.tyrus.spi.HandshakeResponse;

/**
 * @author Justin Lee
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public final class Handshake {

    private static final String HEADER_SEPARATOR = ", ";
    private static final Logger LOGGER = Logger.getLogger(Handshake.class.getName());
    private final List<String> enabledExtensions = Collections.emptyList();
    private boolean secure;
    private String origin;
    private String serverHostName;
    private int port = 80;
    private String resourcePath;
    //private final Map<String, String[]> queryParams = new TreeMap<String, String[]>();
    private List<String> subProtocols = new ArrayList<String>();
    private List<Extension> extensions = new ArrayList<Extension>(); // client extensions
    // client side request!
    private WebSocketRequest request;
    private HandshakeResponseListener responseListener;
    private HandshakeRequest incomingRequest;
    private SecKey secKey;


    private Handshake() {
    }

    static Handshake createClientHandShake(WebSocketRequest webSocketRequest) {
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

    static Handshake createServerHandShake(HandshakeRequest request) {
        final Handshake handshake = new Handshake();

        handshake.incomingRequest = request;
        checkForHeader(request.getFirstHeaderValue(org.glassfish.tyrus.websockets.WebSocketEngine.UPGRADE), org.glassfish.tyrus.websockets.WebSocketEngine.UPGRADE, "WebSocket");
        checkForHeader(request.getHeader(org.glassfish.tyrus.websockets.WebSocketEngine.CONNECTION), org.glassfish.tyrus.websockets.WebSocketEngine.CONNECTION, org.glassfish.tyrus.websockets.WebSocketEngine.UPGRADE);

        handshake.origin = request.getFirstHeaderValue(org.glassfish.tyrus.websockets.WebSocketEngine.SEC_WS_ORIGIN_HEADER);

        if (handshake.origin == null) {
            handshake.origin = request.getFirstHeaderValue(org.glassfish.tyrus.websockets.WebSocketEngine.ORIGIN_HEADER);
        }
        Handshake.determineHostAndPort(handshake, request);

        // TODO - trim?
        final String protocolHeader = request.getFirstHeaderValue(org.glassfish.tyrus.websockets.WebSocketEngine.SEC_WS_PROTOCOL_HEADER);
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

        List<String> value = request.getHeaders().get(org.glassfish.tyrus.websockets.WebSocketEngine.SEC_WS_EXTENSIONS_HEADER);
        if (value != null) {
            handshake.extensions = Handshake.fromHeaders(value);
        }
        handshake.secKey = SecKey.generateServerKey(new SecKey(request.getFirstHeaderValue(org.glassfish.tyrus.websockets.WebSocketEngine.SEC_WS_KEY_HEADER)));

        return handshake;
    }

    private static void checkForHeader(String currentValue, String header, String validValue) {
        validate(header, validValue, currentValue);
    }

    private static void validate(String header, String validValue, String value) {
        // http://java.net/jira/browse/TYRUS-55
        // Firefox workaround (it sends "Connections: keep-alive, upgrade").
        if (header.equalsIgnoreCase(org.glassfish.tyrus.websockets.WebSocketEngine.CONNECTION)) {
            if (!value.toLowerCase().contains(validValue.toLowerCase())) {
                throw new HandshakeException(String.format("Invalid %s header returned: '%s'", header, value));
            }
        } else {
            if (!value.equalsIgnoreCase(validValue)) {
                throw new HandshakeException(String.format("Invalid %s header returned: '%s'", header, value));
            }
        }
    }

    private static void determineHostAndPort(Handshake handshake, HandshakeRequest request) {
        String header = request.getFirstHeaderValue(org.glassfish.tyrus.websockets.WebSocketEngine.HOST);

        final int i = header == null ? -1 : header.indexOf(":");
        if (i == -1) {
            handshake.serverHostName = header;
            handshake.port = 80;
        } else {
            handshake.serverHostName = header.substring(0, i);
            handshake.port = Integer.valueOf(header.substring(i + 1));
        }
    }

    /**
     * Parse {@link Extension} from headers (represented as {@link List} of strings).
     *
     * @param extensionHeaders Http Extension headers.
     * @return list of parsed {@link Extension Extensions}.
     */
    private static List<Extension> fromHeaders(List<String> extensionHeaders) {
        List<Extension> extensions = new ArrayList<Extension>();

        for (String singleHeader : extensionHeaders) {
            if (singleHeader == null) {
                break;
            }
            final char[] chars = singleHeader.toCharArray();
            int i = 0;
            ParserState next = ParserState.NAME_START;
            StringBuilder name = new StringBuilder();
            StringBuilder paramName = new StringBuilder();
            StringBuilder paramValue = new StringBuilder();
            List<Extension.Parameter> params = new ArrayList<Extension.Parameter>();

            do {
                switch (next) {
                    case NAME_START:
                        if (name.length() > 0) {
                            final Extension extension = new Extension(name.toString().trim());
                            extension.getParameters().addAll(params);
                            extensions.add(extension);
                            name = new StringBuilder();
                            paramName = new StringBuilder();
                            paramValue = new StringBuilder();
                            params.clear();
                        }

                        next = ParserState.NAME;

                    case NAME:
                        switch (chars[i]) {
                            case ';':
                                next = ParserState.PARAM_NAME;
                                break;
                            case ',':
                                next = ParserState.NAME_START;
                                break;
                            case '=':
                                next = ParserState.ERROR;
                                break;
                            default:
                                name.append(chars[i]);
                        }

                        break;

                    case PARAM_NAME:

                        switch (chars[i]) {
                            case ';':
                                next = ParserState.ERROR;
                                break;
                            case '=':
                                next = ParserState.PARAM_VALUE;
                                break;
                            default:
                                paramName.append(chars[i]);
                        }

                        break;

                    case PARAM_VALUE:

                        switch (chars[i]) {
                            case '"':
                                if (paramValue.length() > 0) {
                                    next = ParserState.ERROR;
                                } else {
                                    next = ParserState.PARAM_VALUE_QUOTED;
                                }
                                break;
                            case ';':
                                next = ParserState.PARAM_NAME;
                                params.add(new Extension.Parameter(paramName.toString().trim(), paramValue.toString().trim()));
                                paramName = new StringBuilder();
                                paramValue = new StringBuilder();
                                break;
                            case ',':
                                next = ParserState.NAME_START;
                                params.add(new Extension.Parameter(paramName.toString().trim(), paramValue.toString().trim()));
                                paramName = new StringBuilder();
                                paramValue = new StringBuilder();
                                break;
                            case '=':
                                next = ParserState.ERROR;
                                break;
                            default:
                                paramValue.append(chars[i]);
                        }

                        break;

                    case PARAM_VALUE_QUOTED:

                        switch (chars[i]) {
                            case '"':
                                next = ParserState.PARAM_VALUE_QUOTED_POST;
                                params.add(new Extension.Parameter(paramName.toString().trim(), paramValue.toString()));
                                paramName = new StringBuilder();
                                paramValue = new StringBuilder();
                                break;
                            case '\\':
                                next = ParserState.PARAM_VALUE_QUOTED_QP;
                                break;
                            case '=':
                                next = ParserState.ERROR;
                                break;
                            default:
                                paramValue.append(chars[i]);
                        }

                        break;

                    case PARAM_VALUE_QUOTED_QP:

                        next = ParserState.PARAM_VALUE_QUOTED;
                        paramValue.append(chars[i]);
                        break;

                    case PARAM_VALUE_QUOTED_POST:

                        switch (chars[i]) {
                            case ',':
                                next = ParserState.NAME_START;
                                break;
                            case ';':
                                next = ParserState.PARAM_NAME;
                                break;
                            default:
                                next = ParserState.ERROR;
                                break;
                        }

                        break;

                    // defensive error handling - just skip this one and try to parse rest.
                    case ERROR:
                        LOGGER.fine(String.format("Error during parsing Extension: %s", name));

                        if (name.length() > 0) {
                            name = new StringBuilder();
                            paramName = new StringBuilder();
                            paramValue = new StringBuilder();
                            params.clear();
                        }

                        switch (chars[i]) {
                            case ',':
                                next = ParserState.NAME_START;
                                break;
                            case ';':
                                next = ParserState.PARAM_NAME;
                                break;
                            default:
                                break;
                        }

                        break;
                }

                i++;
            } while (i < chars.length);

            if ((name.length() > 0) && (next != ParserState.ERROR)) {
                if (paramName.length() > 0) {
                    params.add(new Extension.Parameter(paramName.toString().trim(), paramValue.toString()));
                }
                final Extension extension = new Extension(name.toString().trim());
                extension.getParameters().addAll(params);
                extensions.add(extension);
                params.clear();
            } else {
                LOGGER.fine(String.format("Unable to parse Extension: %s", name));
            }
        }

        return extensions;
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

    String getResourcePath() {
        return resourcePath;
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

    <T> String getHeaderFromList(List<T> list) {
        StringBuilder sb = new StringBuilder();
        Iterator<T> it = list.iterator();
        while (it.hasNext()) {
            sb.append(it.next());
            if (it.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    <T> List<String> getStringList(List<T> list) {
        List<String> result = new ArrayList<String>();
        for (T item : list) {
            result.add(item.toString());
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
     * Gets the {@link WebSocketRequest}.
     *
     * @return {@link WebSocketRequest} created on this HandShake.
     */
    public WebSocketRequest getRequest() {
        return request;
    }

    /**
     * Compose the {@link WebSocketRequest} and store it for further use.
     *
     * @return composed {@link WebSocketRequest}.
     */
    public WebSocketRequest prepareRequest() {
        String host = getServerHostName();
        if (port != -1 && port != 80 && port != 443) {
            host += ":" + getPort();
        }

        request.setRequestPath(getResourcePath());
        request.putSingleHeader(org.glassfish.tyrus.websockets.WebSocketEngine.HOST, host);
        request.putSingleHeader(org.glassfish.tyrus.websockets.WebSocketEngine.CONNECTION, org.glassfish.tyrus.websockets.WebSocketEngine.UPGRADE);
        request.putSingleHeader(org.glassfish.tyrus.websockets.WebSocketEngine.UPGRADE, org.glassfish.tyrus.websockets.WebSocketEngine.WEBSOCKET);

        if (!getSubProtocols().isEmpty()) {
            request.putSingleHeader(org.glassfish.tyrus.websockets.WebSocketEngine.SEC_WS_PROTOCOL_HEADER, getHeaderFromList(subProtocols));
        }

        if (!getExtensions().isEmpty()) {
            request.putSingleHeader(org.glassfish.tyrus.websockets.WebSocketEngine.SEC_WS_EXTENSIONS_HEADER, getHeaderFromList(extensions));
        }

        request.putSingleHeader(org.glassfish.tyrus.websockets.WebSocketEngine.SEC_WS_KEY_HEADER, secKey.toString());
        request.putSingleHeader(org.glassfish.tyrus.websockets.WebSocketEngine.SEC_WS_ORIGIN_HEADER, getOrigin());
        request.putSingleHeader(org.glassfish.tyrus.websockets.WebSocketEngine.SEC_WS_VERSION, getVersion() + "");
        if (!getExtensions().isEmpty()) {
            request.putSingleHeader(org.glassfish.tyrus.websockets.WebSocketEngine.SEC_WS_EXTENSIONS_HEADER, getHeaderFromList(getExtensions()));
        }
        final String headerValue = request.getFirstHeaderValue(org.glassfish.tyrus.websockets.WebSocketEngine.SEC_WS_ORIGIN_HEADER);
        request.getHeaders().remove(org.glassfish.tyrus.websockets.WebSocketEngine.SEC_WS_ORIGIN_HEADER);
        request.putSingleHeader(org.glassfish.tyrus.websockets.WebSocketEngine.ORIGIN_HEADER, headerValue);
        return request;
    }

    public void validateServerResponse(HandshakeResponse response) {
        if (org.glassfish.tyrus.websockets.WebSocketEngine.RESPONSE_CODE_VALUE != response.getStatus()) {
            throw new HandshakeException(String.format("Response code was not %s: %s",
                    org.glassfish.tyrus.websockets.WebSocketEngine.RESPONSE_CODE_VALUE, response.getStatus()));
        }

        checkForHeader(response.getFirstHeaderValue(org.glassfish.tyrus.websockets.WebSocketEngine.UPGRADE), org.glassfish.tyrus.websockets.WebSocketEngine.UPGRADE, org.glassfish.tyrus.websockets.WebSocketEngine.WEBSOCKET);
        checkForHeader(response.getFirstHeaderValue(org.glassfish.tyrus.websockets.WebSocketEngine.CONNECTION), org.glassfish.tyrus.websockets.WebSocketEngine.CONNECTION, org.glassfish.tyrus.websockets.WebSocketEngine.UPGRADE);

//        if (!getSubProtocols().isEmpty()) {
//            checkForHeader(response.getHeaders(), WebSocketEngine.SEC_WS_PROTOCOL_HEADER, WebSocketEngine.SEC_WS_PROTOCOL_HEADER);
//        }

        secKey.validateServerKey(response.getFirstHeaderValue(org.glassfish.tyrus.websockets.WebSocketEngine.SEC_WS_ACCEPT));
    }

    void respond(org.glassfish.tyrus.spi.WebSocketEngine.ResponseWriter writer, WebSocketApplication application/*, WebSocketResponse response*/) {
        WebSocketResponse response = new WebSocketResponse();
        response.setStatus(101);

        response.getHeaders().put(org.glassfish.tyrus.websockets.WebSocketEngine.UPGRADE, Arrays.asList(org.glassfish.tyrus.websockets.WebSocketEngine.WEBSOCKET));
        response.getHeaders().put(org.glassfish.tyrus.websockets.WebSocketEngine.CONNECTION, Arrays.asList(org.glassfish.tyrus.websockets.WebSocketEngine.UPGRADE));
        response.setReasonPhrase(org.glassfish.tyrus.websockets.WebSocketEngine.RESPONSE_CODE_MESSAGE);
        response.getHeaders().put(org.glassfish.tyrus.websockets.WebSocketEngine.SEC_WS_ACCEPT, Arrays.asList(secKey.getSecKey()));
        if (!getEnabledExtensions().isEmpty()) {
            response.getHeaders().put(org.glassfish.tyrus.websockets.WebSocketEngine.SEC_WS_EXTENSIONS_HEADER, getSubProtocols());
        }

        if (subProtocols != null && !subProtocols.isEmpty()) {
            List<String> appProtocols = application.getSupportedProtocols(subProtocols);
            if (!appProtocols.isEmpty()) {
                response.getHeaders().put(org.glassfish.tyrus.websockets.WebSocketEngine.SEC_WS_PROTOCOL_HEADER, getStringList(appProtocols));
            }
        }
        if (!application.getSupportedExtensions().isEmpty() && !getExtensions().isEmpty()) {
            List<Extension> intersection =
                    intersection(getExtensions(),
                            application.getSupportedExtensions());
            if (!intersection.isEmpty()) {
                application.onExtensionNegotiation(intersection);
                response.getHeaders().put(org.glassfish.tyrus.websockets.WebSocketEngine.SEC_WS_EXTENSIONS_HEADER, getStringList(intersection));
            }
        }

        application.onHandShakeResponse(incomingRequest, response);

        writer.write(response);
    }

    List<Extension> intersection(List<Extension> requested, List<Extension> supported) {
        List<Extension> intersection = new ArrayList<Extension>(supported.size());
        for (Extension e : requested) {
            for (Extension s : supported) {
                if (e.getName().equals(s.getName())) {
                    intersection.add(e);
                    break;
                }
            }
        }
        return intersection;
    }

    public HandshakeRequest initiate(/*FilterChainContext ctx*/) {
        return request;
    }

    /**
     * Get response listener
     *
     * @return registered response listener.
     */
    public HandshakeResponseListener getResponseListener() {
        return responseListener;
    }

    /**
     * Set response listener.
     *
     * @param responseListener {@link org.glassfish.tyrus.websockets.Handshake.HandshakeResponseListener#onHandShakeResponse(org.glassfish.tyrus.spi.HandshakeResponse)}
     *                         will be called when response is ready and validated.
     */
    public void setResponseListener(HandshakeResponseListener responseListener) {
        this.responseListener = responseListener;
    }

    List<String> getEnabledExtensions() {
        return enabledExtensions;
    }

    int getVersion() {
        return 13;
    }

    private enum ParserState {
        NAME_START,
        NAME,
        PARAM_NAME,
        PARAM_VALUE,
        PARAM_VALUE_QUOTED,
        PARAM_VALUE_QUOTED_POST,
        // quoted-pair - '\' escaped character
        PARAM_VALUE_QUOTED_QP,
        ERROR
    }

    /**
     * Used to register with {@link Handshake}. If the handshake response is received, this listener is called.
     *
     * @author Stepan Kopriva (stepan.kopriva at oracle.com)
     */
    public interface HandshakeResponseListener {

        /**
         * Called when correct handshake response is received in {@link Handshake}.
         *
         * @param response received response.
         */
        public void onHandShakeResponse(HandshakeResponse response);

        /**
         * Called when an error is found in handshake response.
         *
         * @param exception error found during handshake response check.
         */
        public void onError(HandshakeException exception);
    }
}
