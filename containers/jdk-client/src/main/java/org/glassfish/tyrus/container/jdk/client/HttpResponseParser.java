/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.container.jdk.client;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.glassfish.tyrus.core.TyrusUpgradeResponse;
import org.glassfish.tyrus.core.Utils;

/**
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
class HttpResponseParser {

    private static final String ENCODING = "ISO-8859-1";
    private static final String LINE_SEPARATOR = "\r\n";
    private static final int BUFFER_STEP_SIZE = 256;
    private static final int BUFFER_MAX_SIZE = 1024;

    private volatile boolean complete = false;
    private volatile ByteBuffer buffer;
    private volatile State findEndState = State.INIT;

    HttpResponseParser() {
        buffer = ByteBuffer.allocate(BUFFER_MAX_SIZE);
        buffer.flip(); //buffer created for read
    }

    TyrusUpgradeResponse parseUpgradeResponse() throws ParseException {
        String response = bufferToString();
        String[] tokens = response.split(LINE_SEPARATOR);
        TyrusUpgradeResponse tyrusUpgradeResponse = new TyrusUpgradeResponse();
        parseFirstLine(tokens, tyrusUpgradeResponse);
        List<String> lines = new LinkedList<>();
        lines.addAll(Arrays.asList(tokens).subList(1, tokens.length));
        Map<String, String> headers = parseHeaders(lines);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            final List<String> values = tyrusUpgradeResponse.getHeaders().get(entry.getKey());
            if (values == null) {
                tyrusUpgradeResponse.getHeaders().put(entry.getKey(), Utils.parseHeaderValue(entry.getValue()));
            } else {
                values.addAll(Utils.parseHeaderValue(entry.getValue()));
            }
        }
        return tyrusUpgradeResponse;
    }

    boolean isComplete() {
        return complete;
    }

    void appendData(ByteBuffer data) {
        if (buffer == null) {
            // parser was already destroyed.
            return;
        }

        int responseEndPosition = getEndPosition(data);
        if (responseEndPosition == -1) {
            buffer = Utils.appendBuffers(buffer, data, BUFFER_MAX_SIZE, BUFFER_STEP_SIZE);
            return;
        }
        int limit = data.limit();
        data.limit(responseEndPosition + 1);
        buffer = Utils.appendBuffers(buffer, data, BUFFER_MAX_SIZE, BUFFER_STEP_SIZE);
        data.limit(limit);
        data.position(responseEndPosition + 1);
        complete = true;
    }

    private void parseFirstLine(String[] responseLines, TyrusUpgradeResponse tyrusUpgradeResponse) throws ParseException {
        if (responseLines.length == 0) {
            throw new ParseException("Empty HTTP response");
        }
        String firstLine = responseLines[0];
        int versionEndIndex = firstLine.indexOf(' ');
        if (versionEndIndex == -1) {
            throw new ParseException("Unexpected format of the first line of a HTTP response: " + firstLine);
        }
        int statusCodeEndIndex = firstLine.indexOf(' ', versionEndIndex + 1);
        if (statusCodeEndIndex == -1) {
            throw new ParseException("Unexpected format of the first line of a HTTP response: " + firstLine);
        }
        String statusCode = firstLine.substring(versionEndIndex + 1, statusCodeEndIndex);
        String reasonPhrase = firstLine.substring(statusCodeEndIndex + 1);
        int status;
        try {
            status = Integer.parseInt(statusCode);
        } catch (Exception e) {
            throw new ParseException("Invalid format of status code: " + statusCode);
        }
        tyrusUpgradeResponse.setStatus(status);
        tyrusUpgradeResponse.setReasonPhrase(reasonPhrase);

    }

    private Map<String, String> parseHeaders(List<String> headerLines) {
        Map<String, String> headers = new HashMap<>();
        for (String headerLine : headerLines) {
            int separatorIndex = headerLine.indexOf(':');
            if (separatorIndex != -1) {
                String headerKey = headerLine.substring(0, separatorIndex);
                String headerValue = headerLine.substring(separatorIndex + 1);

                if (headers.containsKey(headerKey)) {
                    headers.put(headerKey, headers.get(headerKey) + ", " + headerValue);
                } else {
                    headers.put(headerKey, headerValue);
                }
            }
        }
        return headers;
    }

    private String bufferToString() {
        byte[] bytes = Utils.getRemainingArray(buffer);
        String str;
        try {
            str = new String(bytes, ENCODING);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Unsupported encoding" + ENCODING, e);
        }
        return str;
    }

    void destroy() {
        buffer = null;
    }

    void clear() {
        buffer.clear();
        buffer.flip();
        complete = false;
        findEndState = State.INIT;
    }

    private int getEndPosition(ByteBuffer buffer) {
        byte[] bytes = buffer.array();

        for (int i = buffer.position(); i < buffer.limit(); i++) {
            byte b = bytes[i];
            switch (findEndState) {
                case INIT: {
                    if (b == '\r') {
                        findEndState = State.R;
                    }
                    break;
                }
                case R: {
                    if (b == '\n') {
                        findEndState = State.RN;
                    } else {
                        findEndReset(b);
                    }
                    break;
                }
                case RN: {
                    if (b == '\r') {
                        findEndState = State.RNR;
                    } else {
                        findEndState = State.INIT;
                    }
                    break;
                }
                case RNR: {
                    if (b == '\n') {
                        return i;
                    } else {
                        findEndReset(b);
                    }
                    break;
                }
            }
        }
        return -1;
    }

    private void findEndReset(byte b) {
        findEndState = State.INIT;
        if (b == '\r') {
            findEndState = State.R;
        }
    }

    private enum State {
        INIT,
        R,
        RN,
        RNR,
    }
}
