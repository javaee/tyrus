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

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import javax.websocket.CloseReason;
import javax.websocket.Decoder;
import javax.websocket.DeploymentException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;

/**
 * Used when processing a class annotated with {@link @ServerEndpoint} to check that it complies with specification.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
class AnnotatedClassValidityChecker {

    private static final String MULTIPLE_IDENTICAL_PARAMETERS = " has got multiple parameters of identical type.";
    private static final String FORBIDDEN_WEB_SOCKET_OPEN_PARAM = " is not allowed as parameter type for method annotated with @OnOpen.";
    private static final String FORBIDDEN_WEB_SOCKET_CLOSE_PARAMS = " @OnClose has got different params than Session or CloseReason.";
    private static final String FORBIDDEN_WEB_SOCKET_ERROR_PARAM = " is not allowed as parameter type for method annotated with @OnError.";
    private static final String MANDATORY_ERROR_PARAM_MISSING = " does not have mandatory Throwable param.";
    private static final String FORBIDDEN_RETURN_TYPE = " has got unsupported return type.";

    private final Class<?> annotatedClass;
    private final List<Class<? extends Encoder>> encoders;
    private final ErrorCollector collector;
    private final MessageHandlerManager handlerManager;

    /**
     * Construct the class validity checker.
     *
     * @param annotatedClass class for which this checker is constructed.
     */
    public AnnotatedClassValidityChecker(Class<?> annotatedClass, List<Class<? extends Encoder>> encoders, List<Class<? extends Decoder>> decoders, ErrorCollector collector) {
        this.annotatedClass = annotatedClass;
        this.encoders = encoders;
        this.collector = collector;
        this.handlerManager = new MessageHandlerManager(decoders);
    }

    /**
     * Checks whether the params of the method annotated with {@link javax.websocket.OnMessage} comply with the specification.
     * <p/>
     * Voluntary parameters of type {@link javax.websocket.Session} and parameters annotated with {@link javax.websocket.server.PathParam}
     * are checked in advance in {@link AnnotatedEndpoint}.
     */

    public void checkOnMessageParams(Method method, MessageHandler handler) throws DeploymentException {
        try {
            handlerManager.addMessageHandler(handler);
        } catch (IllegalStateException ise) {
            collector.addException(new DeploymentException(String.format("Class: %s. %s", annotatedClass.getCanonicalName(), ise.getMessage()), ise.getCause()));
        }

        checkOnMessageReturnType(method);
    }

    private void checkOnMessageReturnType(Method method) {
        Class<?> returnType = method.getReturnType();

        if (returnType != void.class && returnType != String.class && returnType != ByteBuffer.class &&
                returnType != byte[].class && !returnType.isPrimitive() && !checkEncoders(returnType) && !PrimitivesToWrappers.isPrimitiveWrapper(returnType)) {
            logDeploymentException(new DeploymentException(String.format("Method: %s.%s %s", annotatedClass.getName(), method.getName(), FORBIDDEN_RETURN_TYPE)));
        }
    }

    /**
     * Checks whether the params of method annotated with {@link javax.websocket.OnOpen} comply with the specification.
     * <p/>
     * Voluntary parameters of type {@link javax.websocket.Session} and parameters annotated with {@link javax.websocket.server.PathParam}
     * are checked in advance in {@link AnnotatedEndpoint}.
     *
     * @param params to be checked.
     */
    public void checkOnOpenParams(Method method, Map<Integer, Class<?>> params) {
        for (Class<?> value : params.values()) {
            if (value != EndpointConfig.class) {
                logDeploymentException(new DeploymentException(String.format("%s:%s %s", getPrefix(method.getName()), value, FORBIDDEN_WEB_SOCKET_OPEN_PARAM)));
            }
        }
    }

    /**
     * Checks whether the params of method annotated with {@link javax.websocket.OnClose} comply with the specification.
     *
     * @param params unknown params of the method.
     */
    public void checkOnCloseParams(Method method, Map<Integer, Class<?>> params) {
        for (Class<?> value : params.values()) {
            if (value != CloseReason.class) {
                logDeploymentException(new DeploymentException(String.format("%s %s", getPrefix(method.getName()), FORBIDDEN_WEB_SOCKET_CLOSE_PARAMS)));
            }
        }
    }

    /**
     * Checks whether the params of method annotated with {@link javax.websocket.OnError} comply with the specification.
     *
     * @param params unknown params of the method.
     */
    public void checkOnErrorParams(Method method, Map<Integer, Class<?>> params) {
        boolean throwablePresent = false;

        for (Class<?> value : params.values()) {
            if (value != Throwable.class) {
                logDeploymentException(new DeploymentException(String.format("%s%s%s", getPrefix(method.getName()), value, FORBIDDEN_WEB_SOCKET_ERROR_PARAM)));
            } else {
                if (throwablePresent) {
                    logDeploymentException(new DeploymentException(String.format("%s%s", getPrefix(method.getName()), MULTIPLE_IDENTICAL_PARAMETERS)));
                }
                throwablePresent = true;
            }
        }

        if (!throwablePresent) {
            logDeploymentException(new DeploymentException(String.format("%s%s", getPrefix(method.getName()), MANDATORY_ERROR_PARAM_MISSING)));
        }
    }

    private String getPrefix(String methodName) {
        return String.format("Method:  %s.%s:", annotatedClass.getName(), methodName);
    }

    private boolean checkEncoders(Class<?> requiredType) {
        for (Class<? extends Encoder> encoderClass : encoders) {
            if(AnnotatedEndpoint.getEncoderClassType(encoderClass).isAssignableFrom(requiredType)) {
                return true;
            }
        }

        return false;
    }

    private void logDeploymentException(DeploymentException de) {
        collector.addException(de);
    }
}
