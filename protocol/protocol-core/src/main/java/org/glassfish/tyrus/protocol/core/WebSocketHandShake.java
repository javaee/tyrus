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

package org.glassfish.tyrus.protocol.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * @author Jitendra Kotamraju
 */
public abstract class WebSocketHandShake<RequestType, ResponseType> {
    public static final String SEC_WS_ACCEPT = "Sec-WebSocket-Accept";
    public static final String SEC_WS_KEY_HEADER = "Sec-WebSocket-Key";
    public static final String SEC_WS_ORIGIN_HEADER = "Sec-WebSocket-Origin";
    public static final String ORIGIN_HEADER = "Origin";
    public static final String SEC_WS_PROTOCOL_HEADER = "Sec-WebSocket-Protocol";
    public static final String SEC_WS_EXTENSIONS_HEADER = "Sec-WebSocket-Extensions";
    public static final String SEC_WS_VERSION = "Sec-WebSocket-Version";
    public static final String WEBSOCKET = "websocket";
    public static final String RESPONSE_CODE_MESSAGE = "Switching Protocols";
    public static final String RESPONSE_CODE_HEADER = "Response Code";
    public static final int RESPONSE_CODE_VALUE = 101;
    public static final String UPGRADE = "upgrade";
    public static final String CONNECTION = "connection";
    public static final String CLIENT_WS_ORIGIN_HEADER = "Origin";
    public static final int INITIAL_BUFFER_SIZE = 8192;


    private static final Logger LOGGER = Logger.getLogger(WebSocketHandShake.class.getName());
    private boolean secure;
    private String origin;
    private String serverHostName;
    private int port = 80;
    private String resourcePath;
    private String location;
    private final Map<String, String[]> queryParams = new TreeMap<String, String[]>();
    private List<String> subProtocol = new ArrayList<String>();
    private List<String> extensions = new ArrayList<String>();
    private SecKey secKey;

    protected abstract RequestWrapper<RequestType> createRequestWrapper(RequestType request);

    protected abstract ResponseWrapper<ResponseType> createResponseWrapper(ResponseType response);



    public void doUpgrade(RequestType req, ResponseType res) {
        RequestWrapper<RequestType> requestWrapper = createRequestWrapper(req);
        ResponseWrapper<ResponseType> responseWrapper = createResponseWrapper(res);
        checkForHeader(requestWrapper, "Upgrade", "WebSocket");
        checkForHeader(requestWrapper, "Connection", "Upgrade");
        origin = requestWrapper.getHeader(SEC_WS_ORIGIN_HEADER);
        if (origin == null) {
            origin = requestWrapper.getHeader(ORIGIN_HEADER);
        }
        determineHostAndPort(requestWrapper);
        subProtocol = split(requestWrapper.getHeader(SEC_WS_PROTOCOL_HEADER));
        if (serverHostName == null) {
            throw new WebSocketHandshakeException("Missing required headers for WebSocket negotiation");
        }
        resourcePath = requestWrapper.getRequestURI();
//        final String queryString = request.getQueryString();
//        if (queryString != null) {
//            if (!queryString.isEmpty()) {
//                resourcePath += "?" + queryString;
//            }
//            Parameters queryParameters = new Parameters();
//            queryParameters.processParameters(queryString);
//            final Set<String> names = queryParameters.getParameterNames();
//            for (String name : names) {
//                queryParams.put(name, queryParameters.getParameterValues(name));
//            }
//        }
        buildLocation();


        setExtensions(split(requestWrapper.getHeader(SEC_WS_EXTENSIONS_HEADER)));
        secKey = SecKey.generateServerKey(new SecKey(requestWrapper.getHeader(SEC_WS_KEY_HEADER)));
        setHeaders(responseWrapper);
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

    public List<String> getSubProtocol() {
        return subProtocol;
    }

    public void setSubProtocol(List<String> subProtocol) {
        this.subProtocol = subProtocol;
    }

    private void sanitize(List<String> strings) {
        if (strings != null) {
            for (int i = 0; i < strings.size(); i++) {
                strings.set(i, strings.get(i) == null ? null : strings.get(i).trim());
            }
        }
    }

    public List<String> getExtensions() {
        return extensions;
    }

    public void setExtensions(List<String> extensions) {
        sanitize(extensions);
        this.extensions = extensions;
    }

    protected String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String s : values) {
            if (builder.length() != 0) {
                builder.append(", ");
            }
            builder.append(s);
        }
        return builder.toString();
    }

    private void checkForHeader(RequestWrapper<RequestType> req, String header, String validValue) {
        validate(header, validValue, req.getHeader(header));
    }

    private void validate(String header, String validValue, String value) {
        boolean found = false;
        if (value.contains(",")) {
            for (String part : value.split(",")) {
                found |= part.trim().equalsIgnoreCase(validValue);
            }
        } else {
            found = value.equalsIgnoreCase(validValue);
        }
        if (!found) {
            throw new WebSocketHandshakeException(String.format("Invalid %s header returned: '%s'", header, value));
        }
    }


    private void determineHostAndPort(RequestWrapper<RequestType> req) {
        String header;
        header = req.getHeader("host");
        final int i = header == null ? -1 : header.indexOf(":");
        if (i == -1) {
            setServerHostName(header);
            setPort(80);
        } else {
            setServerHostName(header.substring(0, i));
            setPort(Integer.valueOf(header.substring(i + 1)));
        }
    }


    protected final List<String> split(final String header) {
        if (header == null) {
            return Collections.<String>emptyList();
        } else {
            final List<String> list = Arrays.asList(header.split(","));
            sanitize(list);
            return list;
        }
    }

    private StringBuilder appendPort(StringBuilder builder) {
        if (isSecure()) {
            if (port != 443) {
                builder.append(':').append(port);
            }
        } else {
            if (port != 80) {
                builder.append(':').append(port);
            }
        }
        return builder;
    }

    private List<String> enabledExtensions = Collections.emptyList();
    private List<String> enabledProtocols = Collections.emptyList();


    public void setHeaders(ResponseWrapper<ResponseType> response) {
        //response.setProtocol(Protocol.HTTP_1_1);
        response.setStatus(101);
//        response.setReasonPhrase(RESPONSE_CODE_MESSAGE);
        response.setHeader("Upgrade", "websocket");
        response.setHeader("Connection", "Upgrade");
//        if (!getSubProtocol().isEmpty()) {
//            response.setHeader(SEC_WS_PROTOCOL_HEADER,
//                    join(application.getSupportedProtocols(getSubProtocol())));
//        }
//
//        if (!getEnabledExtensions().isEmpty()) {
//            response.setHeader(SEC_WS_EXTENSIONS_HEADER, join(getSubProtocol()));
//        }
        response.setHeader(SEC_WS_ACCEPT, secKey.getSecKey());
    }

    public List<String> getEnabledExtensions() {
        return enabledExtensions;
    }

    public List<String> getEnabledProtocols() {
        return enabledProtocols;
    }
}
