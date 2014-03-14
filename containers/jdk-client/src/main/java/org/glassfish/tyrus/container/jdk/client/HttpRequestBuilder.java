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

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map.Entry;

import org.glassfish.tyrus.spi.UpgradeRequest;

/**
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
class HttpRequestBuilder {

    private static final String ENCODING = "ISO-8859-1";
    private static final String LINE_SEPARATOR = "\r\n";
    private static final String METHOD = "GET";
    private static final String HTTP_VERSION = "HTTP/1.1";
    private final UpgradeRequest upgradeRequest;
    private final StringBuilder request = new StringBuilder();

    HttpRequestBuilder(UpgradeRequest upgradeRequest) {
        this.upgradeRequest = upgradeRequest;
    }

    private String getUriPath() {
        StringBuilder sb = new StringBuilder();
        final URI uri = URI.create(upgradeRequest.getRequestUri());
        sb.append(uri.getPath());
        final String query = uri.getQuery();
        if (query != null) {
            sb.append('?').append(query);
        }
        if (sb.length() == 0) {
            sb.append('/');
        }
        return sb.toString();
    }

    private void appendHeaders() {
        for (Entry<String, List<String>> header : upgradeRequest.getHeaders().entrySet()) {
            StringBuilder value = new StringBuilder();
            for (String valuePart : header.getValue()) {
                if (value.length() != 0) {
                    value.append(", ");
                }
                value.append(valuePart);
            }
            appendHeader(header.getKey(), value.toString());
        }
    }

    private void appendHeader(String key, String value) {
        request.append(key);
        request.append(":");
        request.append(value);
        request.append(LINE_SEPARATOR);
    }

    ByteBuffer build() {
        request.append(METHOD);
        request.append(" ");
        request.append(getUriPath());
        request.append(" ");
        request.append(HTTP_VERSION);
        request.append(LINE_SEPARATOR);
        appendHeaders();
        request.append(LINE_SEPARATOR);
        String requestStr = request.toString();
        byte[] bytes = requestStr.getBytes(Charset.forName(ENCODING));
        return ByteBuffer.wrap(bytes);
    }

}
