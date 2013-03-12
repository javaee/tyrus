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
import java.util.*;
import java.util.logging.Logger;

/**
 * @author Justin Lee
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public abstract class HandShake {

    private static final String HEADER_SEPARATOR = ", ";

    private static final Logger LOGGER = Logger.getLogger(HandShake.class.getName());

    private boolean secure;
    private String origin;
    private String serverHostName;
    private int port = 80;
    private String resourcePath;
    private String location;
    //private final Map<String, String[]> queryParams = new TreeMap<String, String[]>();
    private List<String> subProtocols = new ArrayList<String>();
    private List<Extension> extensions = new ArrayList<Extension>(); // client extensions
    // client side request!
    private WebSocketRequest request;
    private HandShakeResponseListener responseListener;
    private WebSocketRequest incomingRequest;

    protected HandShake(URI url) {
        resourcePath = url.getPath();
        if ("".equals(resourcePath)) {
            resourcePath = "/";
        }
        if (url.getQuery() != null) {
            resourcePath += "?" + url.getQuery();
        }
        serverHostName = url.getHost();
        secure = "wss://".equals(url.getScheme());
        port = url.getPort();
        origin = appendPort(new StringBuilder(url.getHost())).toString();
        buildLocation();
    }

    protected HandShake(WebSocketRequest request) {
        this.incomingRequest = request;
        Map<String, String> headers = request.getHeaders();
        checkForHeader(headers, "Upgrade", "WebSocket");
        checkForHeader(headers, "Connection", "Upgrade");

        origin = request.getFirstHeaderValue(WebSocketEngine.SEC_WS_ORIGIN_HEADER);

        if (origin == null) {
            origin = request.getFirstHeaderValue(WebSocketEngine.ORIGIN_HEADER);
        }
        determineHostAndPort(headers);

        // TODO - trim?
        final String protocolHeader = headers.get(WebSocketEngine.SEC_WS_PROTOCOL_HEADER);
        subProtocols = (protocolHeader == null ? Collections.<String>emptyList() : Arrays.asList(protocolHeader.split(",")));

        if (serverHostName == null) {
            throw new HandshakeException("Missing required headers for WebSocket negotiation");
        }
        resourcePath = request.getRequestURI();
        final String queryString = request.getQueryString();
        if (queryString != null) {
            if (!queryString.isEmpty()) {
                resourcePath += "?" + queryString;
            }
//            Parameters queryParameters = new Parameters();
//            queryParameters.processParameters(queryString);
//            final Set<String> names = queryParameters.getParameterNames();
//            for (String name : names) {
//                queryParams.put(name, queryParameters.getParameterValues(name));
//            }
        }
        buildLocation();
    }

    final void buildLocation() {
        StringBuilder builder = new StringBuilder((isSecure() ? "wss" : "ws") + "://" + serverHostName);
        appendPort(builder);
        if (resourcePath == null || !resourcePath.startsWith("/") && !"".equals(resourcePath)) {
            builder.append("/");
        }
        builder.append(resourcePath);
        location = builder.toString();
    }


    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    protected String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    int getPort() {
        return port;
    }

    void setPort(int port) {
        this.port = port;
    }

    public void setResourcePath(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    String getResourcePath() {
        return resourcePath;
    }

    boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    String getServerHostName() {
        return serverHostName;
    }

    void setServerHostName(String serverHostName) {
        this.serverHostName = serverHostName;
    }

    protected List<String> getSubProtocols() {
        return subProtocols;
    }

    protected <T> String getHeaderFromList(List<T> list) {
        StringBuilder sb = new StringBuilder();
        Iterator<T> it = list.iterator();
        while(it.hasNext()) {
            sb.append(it.next());
            if (it.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    public void setSubProtocols(List<String> subProtocols) {
        this.subProtocols = subProtocols;
    }

    private void sanitize(List<String> strings) {
        if (strings != null) {
            for (int i = 0; i < strings.size(); i++) {
                strings.set(i, strings.get(i) == null ? null : strings.get(i).trim());
            }
        }
    }

    protected List<Extension> getExtensions() {
        return extensions;
    }

    public void setExtensions(List<Extension> extensions) {
        this.extensions = extensions;
    }

    protected final String joinExtensions(List<Extension> extensions) {
        StringBuilder sb = new StringBuilder();
        for (Extension e : extensions) {
            if (sb.length() != 0) {
                sb.append(HEADER_SEPARATOR);
            }
            sb.append(e.toString());
        }
        return sb.toString();
    }

    protected String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String s : values) {
            if (builder.length() != 0) {
                builder.append(HEADER_SEPARATOR);
            }
            builder.append(s);
        }
        return builder.toString();
    }


    private void checkForHeader(Map<String, String> headers, String header, String validValue) {
        validate(header, validValue, headers.get(header));
    }

    private void validate(String header, String validValue, String value) {
        // http://java.net/jira/browse/TYRUS-55
        // Firefox workaround (it sends "Connections: keep-alive, upgrade").
        if (header.equalsIgnoreCase("Connection")) {
            if (!value.toLowerCase().contains(validValue.toLowerCase())) {
                throw new HandshakeException(String.format("Invalid %s header returned: '%s'", header, value));
            }
        } else {
            if (!value.equalsIgnoreCase(validValue)) {
                throw new HandshakeException(String.format("Invalid %s header returned: '%s'", header, value));
            }
        }
    }


    private void determineHostAndPort(Map<String, String> headers) {
        String header = null;
        if ((headers.get(WebSocketEngine.HOST) != null)) {
            header = headers.get("host");
        }
        final int i = header == null ? -1 : header.indexOf(":");
        if (i == -1) {
            setServerHostName(header);
            setPort(80);
        } else {
            setServerHostName(header.substring(0, i));
            setPort(Integer.valueOf(header.substring(i + 1)));
        }
    }

    /**
     * Gets the {@link WebSocketRequest}. If the request hasn't been composed before using the {@code composeRequest()}
     * method, this method is called first.
     *
     * @return {@link WebSocketRequest} created on this HandShake.
     */
    protected WebSocketRequest getRequest() {
        if (request == null) {
            composeRequest();
        }
        return request;
    }

    /**
     * Compose the {@link WebSocketRequest} and store it for further use.
     *
     * @return composed {@link WebSocketRequest}.
     */
    public WebSocketRequest composeRequest() {
        String host = getServerHostName();
        if (port != -1 && port != 80 && port != 443) {
            host += ":" + getPort();
        }

        request = new WebSocketRequest();

        request.setRequestPath(getResourcePath());
        request.getHeaders().put("Host", host);
        request.getHeaders().put(WebSocketEngine.CONNECTION, WebSocketEngine.UPGRADE);
        request.getHeaders().put(WebSocketEngine.UPGRADE, WebSocketEngine.WEBSOCKET);

        if (!getSubProtocols().isEmpty()) {
            request.getHeaders().put(WebSocketEngine.SEC_WS_PROTOCOL_HEADER, getHeaderFromList(subProtocols));
        }

        if (!getExtensions().isEmpty()) {
            request.getHeaders().put(WebSocketEngine.SEC_WS_EXTENSIONS_HEADER, getHeaderFromList(extensions));
        }

        return request;
    }

    public void validateServerResponse(WebSocketResponse response) {
        if (WebSocketEngine.RESPONSE_CODE_VALUE != response.getStatus()) {
            throw new HandshakeException(String.format("Response code was not %s: %s",
                    WebSocketEngine.RESPONSE_CODE_VALUE, response.getStatus()));
        }
        checkForHeader(response.getHeaders(), WebSocketEngine.UPGRADE, WebSocketEngine.WEBSOCKET);
        checkForHeader(response.getHeaders(), WebSocketEngine.CONNECTION, WebSocketEngine.UPGRADE);
//        if (!getSubProtocols().isEmpty()) {
//            checkForHeader(response.getHeaders(), WebSocketEngine.SEC_WS_PROTOCOL_HEADER, WebSocketEngine.SEC_WS_PROTOCOL_HEADER);
//        }
        if (responseListener != null) {
            responseListener.onResponseHeaders(response.getHeaders());
        }
    }

    void respond(Connection connection, WebSocketApplication application/*, WebSocketResponse response*/) {
        WebSocketResponse response = new WebSocketResponse();
        response.setStatus(101);

        response.getHeaders().put(WebSocketEngine.UPGRADE, WebSocketEngine.WEBSOCKET);
        response.getHeaders().put(WebSocketEngine.CONNECTION, WebSocketEngine.UPGRADE);
        setHeaders(response);

        if (subProtocols != null && !subProtocols.isEmpty()) {
            List<String> appProtocols = application.getSupportedProtocols(subProtocols);
            if (!appProtocols.isEmpty()) {
                response.getHeaders().put(WebSocketEngine.SEC_WS_PROTOCOL_HEADER, getHeaderFromList(appProtocols));
            }
        }
        if (!application.getSupportedExtensions().isEmpty() && !getExtensions().isEmpty()) {
            List<Extension> intersection =
                    intersection(getExtensions(),
                            application.getSupportedExtensions());
            if (!intersection.isEmpty()) {
                application.onExtensionNegotiation(intersection);
                response.getHeaders().put(WebSocketEngine.SEC_WS_EXTENSIONS_HEADER, getHeaderFromList(intersection));
            }
        }

        application.onHandShakeResponse(incomingRequest, response);

        connection.write(response);
    }


    protected abstract void setHeaders(WebSocketResponse response);

    protected final List<String> split(final String header) {
        if (header == null) {
            return Collections.emptyList();
        } else {
            final List<String> list = Arrays.asList(header.split(","));
            sanitize(list);
            return list;
        }
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

    /**
     * Parsing of one {@link Extension}.
     *
     * @param s {@link String} containing {@link Extension}.
     * @return extension represented as {@link Extension}.
     */
    public static List<Extension> fromString(String s) {
        return fromHeaders(Arrays.asList(s));
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
     * Parse {@link Extension} from headers (represented as {@link List} of strings).
     *
     * @param extensionHeaders Http Extension headers.
     * @return list of parsed {@link Extension Extensions}.
     */
    public static List<Extension> fromHeaders(List<String> extensionHeaders) {
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


    public WebSocketRequest initiate(/*FilterChainContext ctx*/) {
        return getRequest();
    }

    private StringBuilder appendPort(StringBuilder builder) {
        if (isSecure()) {
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

    /**
     * Set response listener.
     *
     * @param responseListener {@link HandShakeResponseListener#onResponseHeaders(java.util.Map)} will be called when
     *                         response is ready and validated.
     */
    public void setResponseListener(HandShakeResponseListener responseListener) {
        this.responseListener = responseListener;
    }

    /**
     * Used to register with {@link HandShake}. If the handshake response is received, this listener is called.
     *
     * @author Stepan Kopriva (stepan.kopriva at oracle.com)
     */
    public interface HandShakeResponseListener {

        /**
         * Gets called when the handshake response is received in {@link HandShake}.
         *
         * @param headers of the handshake response.
         */
        public void onResponseHeaders(Map<String, String> headers);
    }
}
