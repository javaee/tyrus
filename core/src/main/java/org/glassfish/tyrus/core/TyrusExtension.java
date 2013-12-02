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
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import javax.websocket.Extension;

/**
 * WebSocket {@link Extension} implementation.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class TyrusExtension implements Extension {

    private static final Logger LOGGER = Logger.getLogger(TyrusExtension.class.getName());
    private final String name;
    private final List<Parameter> parameters;

    /**
     * Create {@link Extension} with specific name.
     *
     * @param name extension name.
     * @throws IllegalArgumentException when name is null or empty string.
     */
    public TyrusExtension(String name) {
        this(name, null);
    }

    /**
     * Create {@link Extension} with name and parameters.
     *
     * @param name       extension name.
     * @param parameters extension parameters.
     */
    public TyrusExtension(String name, List<Parameter> parameters) {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException();
        }

        this.name = name;
        if (parameters != null) {
            this.parameters = Collections.unmodifiableList(new ArrayList<Parameter>(parameters));
        } else {
            this.parameters = Collections.unmodifiableList(Collections.<Parameter>emptyList());
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<Parameter> getParameters() {
        return parameters;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TyrusExtension{");
        sb.append("name='").append(name).append('\'');
        sb.append(", parameters=").append(parameters);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TyrusExtension that = (TyrusExtension) o;

        return name.equals(that.name) && parameters.equals(that.parameters);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + parameters.hashCode();
        return result;
    }

    /**
     * Returns defined representation for HTTP headers.
     *
     * @param extension {@link Extension} instance.
     * @return String containing {@link Extension} representation as defined in RFC 6455.
     */
    static String toString(Extension extension) {
        final StringBuilder sb = new StringBuilder();
        sb.append(extension.getName());
        final List<Parameter> extensionParameters = extension.getParameters();
        if (extensionParameters != null && !extensionParameters.isEmpty()) {
            for (Extension.Parameter p : extensionParameters) {
                sb.append("; ");
                sb.append(TyrusParameter.toString(p));
            }
        }
        return sb.toString();
    }

    /**
     * Parsing of one {@link Extension}.
     *
     * @param s {@link List} of {@link String} containing {@link Extension Extensions}.
     * @return List of extensions represented as {@link TyrusExtension}.
     */
    public static List<Extension> fromString(List<String> s) {
        return fromHeaders(s);
    }

    private enum ParserState {
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
        if (extensionHeaders == null) {
            return extensions;
        }

        for (String singleHeader : extensionHeaders) {
            if (singleHeader == null) {
                break;
            }
            final char[] chars = singleHeader.toCharArray();
            int i = 0;
            ParserState next = ParserState.NAME;
            StringBuilder name = new StringBuilder();
            StringBuilder paramName = new StringBuilder();
            StringBuilder paramValue = new StringBuilder();
            List<Parameter> params = new ArrayList<Parameter>();

            do {
                switch (next) {

                    case NAME:
                        switch (chars[i]) {
                            case ';':
                                next = ParserState.PARAM_NAME;
                                break;
                            case ',':
                                if (name.length() > 0) {
                                    extensions.add(new TyrusExtension(name.toString().trim(), params));
                                    name = new StringBuilder();
                                    paramName = new StringBuilder();
                                    paramValue = new StringBuilder();
                                    params.clear();
                                }

                                next = ParserState.NAME;
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
                                next = ParserState.PARAM_NAME;
                                params.add(new TyrusParameter(paramName.toString().trim(), null));
                                paramName = new StringBuilder();
                                paramValue = new StringBuilder();
                                break;
                            case ',':
                                next = ParserState.NAME;
                                params.add(new TyrusParameter(paramName.toString().trim(), null));
                                paramName = new StringBuilder();
                                paramValue = new StringBuilder();
                                if (name.length() > 0) {
                                    extensions.add(new TyrusExtension(name.toString().trim(), params));
                                    name = new StringBuilder();
                                    paramName = new StringBuilder();
                                    paramValue = new StringBuilder();
                                    params.clear();
                                }
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
                                params.add(new TyrusParameter(paramName.toString().trim(), paramValue.toString().trim()));
                                paramName = new StringBuilder();
                                paramValue = new StringBuilder();
                                break;
                            case ',':
                                next = ParserState.NAME;
                                params.add(new TyrusParameter(paramName.toString().trim(), paramValue.toString().trim()));
                                paramName = new StringBuilder();
                                paramValue = new StringBuilder();
                                if (name.length() > 0) {
                                    extensions.add(new TyrusExtension(name.toString().trim(), params));
                                    name = new StringBuilder();
                                    paramName = new StringBuilder();
                                    paramValue = new StringBuilder();
                                    params.clear();
                                }

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
                                params.add(new TyrusParameter(paramName.toString().trim(), paramValue.toString()));
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
                                next = ParserState.NAME;
                                if (name.length() > 0) {
                                    extensions.add(new TyrusExtension(name.toString().trim(), params));
                                    name = new StringBuilder();
                                    paramName = new StringBuilder();
                                    paramValue = new StringBuilder();
                                    params.clear();
                                }

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
                                next = ParserState.NAME;
                                if (name.length() > 0) {
                                    extensions.add(new TyrusExtension(name.toString().trim(), params));
                                    name = new StringBuilder();
                                    paramName = new StringBuilder();
                                    paramValue = new StringBuilder();
                                    params.clear();
                                }

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
                    params.add(new TyrusParameter(paramName.toString().trim(), paramValue.toString()));
                }
                extensions.add(new TyrusExtension(name.toString().trim(), params));
                params.clear();
            } else {
                LOGGER.fine(String.format("Unable to parse Extension: %s", name));
            }
        }

        return extensions;
    }

    /**
     * WebSocket {@link Parameter} implementation.
     */
    public static class TyrusParameter implements Parameter {

        private final String name;
        private final String value;

        /**
         * Create {@link Parameter} with name and value.
         *
         * @param name  parameter name.
         * @param value parameter value.
         */
        public TyrusParameter(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("TyrusParameter{");
            sb.append("name='").append(name).append('\'');
            sb.append(", value='").append(value).append('\'');
            sb.append('}');
            return sb.toString();
        }

        /**
         * Returns defined representation for HTTP headers.
         *
         * @param parameter {@link Parameter} instance.
         * @return String containing {@link Parameter} representation as defined in RFC 6455.
         */
        static String toString(Parameter parameter) {
            final StringBuilder sb = new StringBuilder();
            sb.append(parameter.getName());
            final String value = parameter.getValue();
            if (value != null) {
                sb.append('=').append(value);
            }
            return sb.toString();
        }
    }
}
