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

package org.glassfish.tyrus;

import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import javax.websocket.Decoder;
import javax.websocket.DeploymentException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfiguration;
import javax.websocket.PongMessage;

/**
 * Used when processing a class annotated with {@link @WebSocketEndpoint} to check that it complies with specification.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class AnnotatedClassValidityChecker {

    private static final String MULTIPLE_IDENTICAL_PARAMETERS = " has got multiple parameters of identical type.";
    private static final String MULTIPLE_METHODS_TEXT_PARTIAL = " has got multiple methods consuming partial text messages.";
    private static final String MULTIPLE_METHODS_BINARY_PARTIAL = " has got multiple methods consuming partial binary messages.";
    private static final String MULTIPLE_METHODS_TEXT = " has got multiple methods consuming text messages.";
    private static final String MULTIPLE_METHODS_BINARY = " has got multiple methods consuming binary messages.";
    private static final String MULTIPLE_METHODS_PONG = " has got multiple methods consuming pong messages.";
    private static final String FORBIDDEN_WEB_SOCKET_MESSAGE_PARAM = " is not allowed as parameter type for method annotated with @WebSocketMessage.";
    private static final String FORBIDDEN_WEB_SOCKET_OPEN_PARAM = " is not allowed as parameter type for method annotated with @WebSocketOpen.";
    private static final String FORBIDDEN_WEB_SOCKET_CLOSE_PARAMS = " @WebSocketClose has got different params than Session.";
    private static final String FORBIDDEN_WEB_SOCKET_ERROR_PARAM = " is not allowed as parameter type for method annotated with @WebSocketError.";
    private static final String MANDATORY_ERROR_PARAM_MISSING = " does not have mandatory Throwable param.";
    private static final String FORBIDDEN_PARAMETER_COMBINATION = " has got unsupported parameter combination.";
    private static final String FORBIDDEN_RETURN_TYPE = " has got unsupported return type.";

    private final Class<?> annotatedClass;

    /**
     * Keeps information on present onMessage methods with different valid parameters.
     * <p/>
     * 0 - String.
     * 1 - String and boolean.
     * 2 - byte[] or ByteBuffer.
     * 3 - (byte[] or ByteBuffer) and boolean.
     * 4 - PongMessage.
     */
    private final boolean[] onMessageCombinations = {false, false, false, false, false};
    private final List<Decoder> decoders;
    private final List<Encoder> encoders;
    private final ErrorCollector collector;

    /**
     * Construct the class validity checker.
     *
     * @param annotatedClass class for which this checker is constructed.
     * @param decoders       specified in the {@link javax.websocket.EndpointConfiguration}.
     */
    public AnnotatedClassValidityChecker(Class<?> annotatedClass, List<Decoder> decoders, List<Encoder> encoders, ErrorCollector collector) {
        this.annotatedClass = annotatedClass;
        this.decoders = decoders;
        this.encoders = encoders;
        this.collector = collector;
    }

    /**
     * Checks whether the params of the method annotated with {@link javax.websocket.WebSocketMessage}comply with the specification.
     * <p/>
     * Voluntary parameters of type {@link javax.websocket.Session} and parameters annotated with {@link javax.websocket.server.WebSocketPathParam}
     * are checked in advance in {@link AnnotatedEndpoint}.
     *
     * @param params to be checked.
     */
    public void checkOnMessageParams(Method method, Map<Integer, Class<?>> params) throws DeploymentException {
        final String errorPrefix = "Method: " + annotatedClass.getName() + "." + method.getName();
        boolean text = false;
        boolean binary = false;
        boolean partial = false;
        boolean pong = false;
        boolean decoded = false;

        checkOnMessageReturnType(method);

        Class<?>[] values = new Class<?>[params.size()];
        params.values().toArray(values);

        for (Class<?> value : values) {
            if (value == String.class || value == Reader.class) {
                if (text) {
                    logDeploymentException(new DeploymentException(errorPrefix + MULTIPLE_IDENTICAL_PARAMETERS));
                }
                text = true;
                continue;
            } else if (value == ByteBuffer.class || value == byte[].class || value == InputStream.class) {
                if (binary) {
                    logDeploymentException(new DeploymentException(errorPrefix + MULTIPLE_IDENTICAL_PARAMETERS));
                }
                binary = true;
                continue;
            } else if (value == boolean.class) {
                if (partial) {
                    logDeploymentException(new DeploymentException(errorPrefix + MULTIPLE_IDENTICAL_PARAMETERS));
                }
                partial = true;
                continue;
            } else if (value == PongMessage.class) {
                if (pong) {
                    logDeploymentException(new DeploymentException(errorPrefix + MULTIPLE_IDENTICAL_PARAMETERS));
                }
                pong = true;
                continue;
            } else if (checkDecoders(value)) {
                if (decoded) {
                    logDeploymentException(new DeploymentException(errorPrefix + MULTIPLE_IDENTICAL_PARAMETERS));
                }
                decoded = true;
                continue;
            }

            logDeploymentException(new DeploymentException(errorPrefix + ": " + value + FORBIDDEN_WEB_SOCKET_MESSAGE_PARAM));
        }

        if ((text && binary) || (text && pong) || (binary && pong) || (pong && partial) || (decoded && binary) || (decoded && text) || (decoded && pong)) {
            logDeploymentException(new DeploymentException(errorPrefix + FORBIDDEN_PARAMETER_COMBINATION));
        }

        if (text && partial) {
            if (onMessageCombinations[1]) {
                logDeploymentException(new DeploymentException(annotatedClass.getName() + MULTIPLE_METHODS_TEXT_PARTIAL));
            }
            onMessageCombinations[1] = true;
        } else if (binary && partial) {
            if (onMessageCombinations[3]) {
                logDeploymentException(new DeploymentException(annotatedClass.getName() + MULTIPLE_METHODS_BINARY_PARTIAL));
            }
            onMessageCombinations[3] = true;
        } else if (text) {
            if (onMessageCombinations[0]) {
                logDeploymentException(new DeploymentException(annotatedClass.getName() + MULTIPLE_METHODS_TEXT));
            }
            onMessageCombinations[0] = true;
        } else if (binary) {
            if (onMessageCombinations[2]) {
                logDeploymentException(new DeploymentException(annotatedClass.getName() + MULTIPLE_METHODS_BINARY));
            }
            onMessageCombinations[2] = true;
        } else if (pong) {
            if (onMessageCombinations[4]) {
                logDeploymentException(new DeploymentException(annotatedClass.getName() + MULTIPLE_METHODS_PONG));
            }
            onMessageCombinations[4] = true;
        }
    }

    private void checkOnMessageReturnType(Method method) {
        Class<?> returnType = method.getReturnType();

        if (returnType != void.class && returnType != String.class && returnType != ByteBuffer.class &&
                returnType != byte[].class && !returnType.isPrimitive() && !checkEncoders(returnType)) {
            logDeploymentException(new DeploymentException("Method: " + annotatedClass.getName() + "." + method.getName() + FORBIDDEN_RETURN_TYPE));
        }
    }

    /**
     * Checks whether the params of method annotated with {@link javax.websocket.WebSocketOpen} comply with the specification.
     * <p/>
     * Voluntary parameters of type {@link javax.websocket.Session} and parameters annotated with {@link javax.websocket.server.WebSocketPathParam}
     * are checked in advance in {@link AnnotatedEndpoint}.
     *
     * @param params to be checked.
     */
    public void checkOnOpenParams(Method method, Map<Integer, Class<?>> params) {
        final String errorPrefix = "Method: " + annotatedClass.getName() + "." + method.getName();

        for (Class<?> value : params.values()) {
            if (value != EndpointConfiguration.class) {
                logDeploymentException(new DeploymentException(errorPrefix + ": " + value + FORBIDDEN_WEB_SOCKET_OPEN_PARAM));
            }
        }
    }

    /**
     * Checks whether the params of method annotated with {@link javax.websocket.WebSocketClose} comply with the specification.
     *
     * @param params unknown params of the method.
     */
    public void checkOnCloseParams(Method method, Map<Integer, Class<?>> params) {
        final String errorPrefix = "Method: " + annotatedClass.getName() + "." + method.getName();

        if (params.size() > 0) {
            logDeploymentException(new DeploymentException(errorPrefix + FORBIDDEN_WEB_SOCKET_CLOSE_PARAMS));
        }
    }

    /**
     * Checks whether the params of method annotated with {@link javax.websocket.WebSocketError} comply with the specification.
     *
     * @param params unknown params of the method.
     */
    public void checkOnErrorParams(Method method, Map<Integer, Class<?>> params) {
        final String errorPrefix = "Method: " + annotatedClass.getName() + "." + method.getName();
        boolean throwablePresent = false;

        for (Class<?> value : params.values()) {
            if (value != Throwable.class) {
                logDeploymentException(new DeploymentException(errorPrefix + ": " + value + FORBIDDEN_WEB_SOCKET_ERROR_PARAM));
            } else {
                if (throwablePresent) {
                    logDeploymentException(new DeploymentException(errorPrefix + ": " + MULTIPLE_IDENTICAL_PARAMETERS));
                }
                throwablePresent = true;
            }
        }

        if (!throwablePresent) {
            logDeploymentException(new DeploymentException(errorPrefix + ": " + MANDATORY_ERROR_PARAM_MISSING));
        }
    }

    private boolean checkEncoders(Class<?> requiredType) {
        for (Encoder encoder : encoders) {
            if (encoder instanceof CoderWrapper) {
                if (((CoderWrapper<Encoder>) encoder).getType().isAssignableFrom(requiredType)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean checkDecoders(Class<?> requiredType) {
        for (Decoder decoder : decoders) {
            if (decoder instanceof CoderWrapper) {
                if (((CoderWrapper<Decoder>) decoder).getType().isAssignableFrom(requiredType)) {
                    return true;
                }
            }
        }

        return false;
    }

    private void logDeploymentException(DeploymentException de) {
        collector.addException(de);
    }
}
