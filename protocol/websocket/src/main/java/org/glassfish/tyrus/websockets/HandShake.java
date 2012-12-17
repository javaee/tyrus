/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Justin Lee
 */
public abstract class HandShake {
    private boolean secure;
    private String origin;
    private String serverHostName;
    private int port = 80;
    private String resourcePath;
    private String location;
    //private final Map<String, String[]> queryParams = new TreeMap<String, String[]>();
    private List<String> subProtocols = new ArrayList<String>();
    private List<Extension> extensions = new ArrayList<Extension>(); // client extensions
    private WebSocketRequest request;
    public static final String HEADER_SEPARATOR = ", ";
    private HandShakeResponseListener responseListener;

    public HandShake(URI url) {
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

    public HandShake(WebSocketRequest request) {
        Map<String, List<String>> headers = request.getHeaders();
        checkForHeader(headers, "Upgrade", "WebSocket");
        checkForHeader(headers, "Connection", "Upgrade");

        origin = request.getFirstHeaderValue(WebSocketEngine.SEC_WS_ORIGIN_HEADER);

        if (origin == null) {
            origin = request.getFirstHeaderValue(WebSocketEngine.ORIGIN_HEADER);
        }
        determineHostAndPort(headers);
        subProtocols = headers.get(WebSocketEngine.SEC_WS_PROTOCOL_HEADER);
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

    protected final void buildLocation() {
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

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setResourcePath(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public String getServerHostName() {
        return serverHostName;
    }

    public void setServerHostName(String serverHostName) {
        this.serverHostName = serverHostName;
    }

    public List<String> getSubProtocols() {
        return subProtocols;
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

    public List<Extension> getExtensions() {
        return extensions;
    }

    protected List<String> getExtensionNames(List<Extension> exts) {
        List<String> extensionNames = new ArrayList<String>();

        for (Extension extension : exts) {
            extensionNames.add(extension.getName());
        }

        return extensionNames;
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


    private void checkForHeader(Map<String, List<String>> headers, String header, String validValue) {
        validate(header, validValue, headers.get(header));
    }

    private void validate(String header, String validValue, List<String> values) {
        boolean found = false;

        for (String value : values) {
            found |= value.equalsIgnoreCase(validValue);
        }

        if (!found) {
            throw new HandshakeException(String.format("Invalid %s header returned: '%s'", header, values));
        }
    }


    private void determineHostAndPort(Map<String, List<String>> headers) {
        String header = null;
        if((headers.get(WebSocketEngine.HOST)!=null) && (headers.get(WebSocketEngine.HOST).size()>0)){
            header = headers.get("host").get(0);
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
    public WebSocketRequest getRequest() {
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
        request.getHeaders().put("Host", Arrays.asList(host));
        request.getHeaders().put(WebSocketEngine.CONNECTION, Arrays.asList(WebSocketEngine.UPGRADE));
        request.getHeaders().put(WebSocketEngine.UPGRADE, Arrays.asList(WebSocketEngine.WEBSOCKET));

        if (!getSubProtocols().isEmpty()) {
            request.getHeaders().put(WebSocketEngine.SEC_WS_PROTOCOL_HEADER, getSubProtocols());
        }

        if (!getExtensions().isEmpty()) {
            request.getHeaders().put(WebSocketEngine.SEC_WS_EXTENSIONS_HEADER, getExtensionNames(extensions));
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
            responseListener.passResponseHeaders(response.getHeaders());
        }
    }

    void respond(Connection connection, WebSocketApplication application/*, WebSocketResponse response*/) {
        WebSocketResponse response = new WebSocketResponse();
        response.setStatus(101);

        response.getHeaders().put(WebSocketEngine.UPGRADE, Arrays.asList(WebSocketEngine.WEBSOCKET));
        response.getHeaders().put(WebSocketEngine.CONNECTION, Arrays.asList(WebSocketEngine.UPGRADE));
        setHeaders(response);
        if (getSubProtocols() != null && !getSubProtocols().isEmpty()) {
            response.getHeaders().put(WebSocketEngine.SEC_WS_PROTOCOL_HEADER, application.getSupportedProtocols(getSubProtocols()));
        }
        if (!application.getSupportedExtensions().isEmpty() && !getExtensions().isEmpty()) {
            List<Extension> intersection =
                    intersection(getExtensions(),
                            application.getSupportedExtensions());
            if (!intersection.isEmpty()) {
                application.onExtensionNegotiation(intersection);
                response.getHeaders().put(WebSocketEngine.SEC_WS_EXTENSIONS_HEADER, getExtensionNames(intersection));
            }
        }

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

    protected List<Extension> intersection(List<Extension> requested, List<Extension> supported) {
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

    protected final List<Extension> parseExtensionsHeader(final List<String> headerValues) {
        List<Extension> result = new ArrayList<Extension>();

        for (String value : headerValues) {
            result.add(new Extension(value));
        }

        return result;
    }

    protected final void parseParameters(String parameterString,
                                         List<Extension.Parameter> parameters) {
        String[] parts = parameterString.split(";");
        for (String part : parts) {
            int idx = part.indexOf('=');
            if (idx < 0) {
                parameters.add(new Extension.Parameter(part.trim(), null));
            } else {
                parameters.add(
                        new Extension.Parameter(part.substring(0, idx).trim(),
                                part.substring(idx + 1).trim()));
            }
        }
    }

    public WebSocketRequest initiate(/*FilterChainContext ctx*/) throws IOException {
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

    public void setResponseListener(HandShakeResponseListener responseListener) {
        this.responseListener = responseListener;
    }

}
