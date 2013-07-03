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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods shared among Tyrus modules.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class Utils {
    /**
     * RegExp patter for {@link #checkHeaderValue(String)} method.
     */
    private static final Pattern whiteSpace = Pattern.compile("[\\s].*");

    /**
     * Decides whether there is a need to encapsulate header value with
     * quotation marks and performs it.
     *
     * @param headerValue original header value.
     * @return quoted header value, if original contains whitespaces.
     */
    public static String checkHeaderValue(String headerValue) {
        Matcher m = whiteSpace.matcher(headerValue);
        if(m.find()) {
            return String.format("\"%s\"", headerValue);
        }
        return headerValue;
    }

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

        for(char c : headerValue.toCharArray()) {
            switch (state) {
                case 0:
                    // ignore trailing whitespace
                    if(Character.isWhitespace(c)) {
                        break;
                    }
                    if(c == '\"') {
                        state = 2;
                        break;
                    }
                    sb.append(c);
                    state = 1;
                    break;
                case 1:
                    if(c != ',') {
                        sb.append(c);
                    } else {
                        values.add(sb.toString());
                        sb = new StringBuilder();
                        state = 0;
                    }
                    break;
                case 2:
                    if(c != '\"') {
                        sb.append(c);
                    } else {
                        values.add(sb.toString());
                        sb = new StringBuilder();
                        state = 3;
                    }
                    break;
                case 3:
                    if(Character.isWhitespace(c)) {
                        break;
                    }
                    if(c == ',') {
                        state = 0;
                    }

                    // error - ignore for now.
                    break;
            }
        }

        if(sb.length() > 0) {
            values.add(sb.toString());
        }

        return values;
    }
}
