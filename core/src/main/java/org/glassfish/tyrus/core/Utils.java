/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Utility methods shared among Tyrus modules.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class Utils {

    /**
     * Parse header value - splits multiple values (quoted, unquoted) separated by
     * comma.
     *
     * @param headerValue string containing header values.
     * @return split list of values.
     */
    public static List<String> parseHeaderValue(String headerValue) {
        List<String> values = new ArrayList<String>();

        // 0 - start of new header value
        // 1 - non-quoted value
        // 2 - quoted value
        // 3 - end of quoted value (after '\"', before ',')
        int state = 0;
        StringBuilder sb = new StringBuilder();

        for (char c : headerValue.toCharArray()) {
            switch (state) {
                case 0:
                    // ignore trailing whitespace
                    if (Character.isWhitespace(c)) {
                        break;
                    }
                    if (c == '\"') {
                        state = 2;
                        sb.append(c);
                        break;
                    }
                    sb.append(c);
                    state = 1;
                    break;
                case 1:
                    if (c != ',') {
                        sb.append(c);
                    } else {
                        values.add(sb.toString());
                        sb = new StringBuilder();
                        state = 0;
                    }
                    break;
                case 2:
                    if (c != '\"') {
                        sb.append(c);
                    } else {
                        sb.append(c);
                        values.add(sb.toString());
                        sb = new StringBuilder();
                        state = 3;
                    }
                    break;
                case 3:
                    if (Character.isWhitespace(c)) {
                        break;
                    }
                    if (c == ',') {
                        state = 0;
                    }

                    // error - ignore for now.
                    break;
                default:
                    // should not happen
                    break;
            }
        }

        if (sb.length() > 0) {
            values.add(sb.toString());
        }

        return values;
    }

    /**
     * Creates the array of bytes containing the bytes from the position to the limit of the {@link ByteBuffer}.
     *
     * @param buffer where the bytes are taken from.
     * @return array of bytes containing the bytes from the position to the limit of the {@link ByteBuffer}.
     */
    public static byte[] getRemainingArray(ByteBuffer buffer) {
        byte[] ret = new byte[buffer.remaining()];

        if (buffer.hasArray()) {
            byte[] array = buffer.array();
            System.arraycopy(array, buffer.arrayOffset() + buffer.position(), ret, 0, ret.length);
        } else {
            buffer.get(ret);
        }

        return ret;
    }

    /**
     * Creates single {@link String} value from provided List by calling {@link Object#toString()} on each item
     * and separating existing ones with {@code ", "}.
     *
     * @param list to be serialized.
     * @param <T>  item type.
     * @return single {@link String} containing all items from provided list.
     */
    public static <T> String getHeaderFromList(List<T> list) {
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

    /**
     * Check for null. Throws {@link IllegalArgumentException} if provided value is null.
     *
     * @param reference    object to check.
     * @param errorMessage message to be set to thrown {@link IllegalArgumentException}.
     * @param <T>          object type.
     */
    public static <T> void checkNotNull(T reference, String errorMessage) {
        if (reference == null) {
            throw new IllegalArgumentException(errorMessage);
        }
    }
}