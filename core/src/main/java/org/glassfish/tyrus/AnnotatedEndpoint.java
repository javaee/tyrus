/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.websocket.CloseReason;
import javax.websocket.Decoder;
import javax.websocket.Encoder;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.WebSocketClient;
import javax.websocket.WebSocketClose;
import javax.websocket.server.WebSocketEndpoint;
import javax.websocket.WebSocketError;
import javax.websocket.WebSocketMessage;
import javax.websocket.WebSocketOpen;
import javax.websocket.server.WebSocketPathParam;

/**
 * AnnotatedEndpoint of a class annotated using the WebSocketEndpoint annotations
 *
 * @author Martin Matula (martin.matula at oracle.com)
 */
public class AnnotatedEndpoint extends Endpoint {
    private static final Logger LOGGER = Logger.getLogger(AnnotatedEndpoint.class.getName());

    private final Object annotatedInstance;
    private final Method onOpenMethod;
    private final Method onCloseMethod;
    private final Method onErrorMethod;
    private final ParameterExtractor[] onOpenParameters;
    private final ParameterExtractor[] onCloseParameters;
    private final ParameterExtractor[] onErrorParameters;
    private final EndpointConfiguration configuration;

    private Set<MessageHandlerFactory> messageHandlerFactories = new HashSet<MessageHandlerFactory>();

    public static AnnotatedEndpoint fromClass(Class<?> annotatedClass, boolean isServerEndpoint) {
        return new AnnotatedEndpoint(annotatedClass, null, createEndpointConfiguration(annotatedClass, isServerEndpoint));
    }

    public static AnnotatedEndpoint fromInstance(Object annotatedInstance, boolean isServerEndpoint) {
        return new AnnotatedEndpoint(annotatedInstance.getClass(), annotatedInstance,
                createEndpointConfiguration(annotatedInstance.getClass(), isServerEndpoint));
    }

    private static EndpointConfiguration createEndpointConfiguration(Class<?> annotatedClass, boolean isServerEndpoint) {
        final WebSocketEndpoint wseAnnotation = annotatedClass.getAnnotation(WebSocketEndpoint.class);

        Class<? extends Encoder>[] encoderClasses;
        Class<? extends Decoder>[] decoderClasses;
        String[] subProtocols;

        if (wseAnnotation == null) {
            WebSocketClient wscAnnotation = annotatedClass.getAnnotation(WebSocketClient.class);
            if (wscAnnotation == null) {
                return null;
            }
            encoderClasses = wscAnnotation.encoders();
            decoderClasses = wscAnnotation.decoders();
            subProtocols = wscAnnotation.subprotocols();
        } else {
            encoderClasses = wseAnnotation.encoders();
            decoderClasses = wseAnnotation.decoders();
            subProtocols = wseAnnotation.subprotocols();
        }

        List<Encoder> encoders = new ArrayList<Encoder>();
        if (encoderClasses != null) {
            //noinspection unchecked
            for (Class<? extends Encoder> encoderClass : encoderClasses) {
                Encoder encoder = ComponentProviderService.getInstance(encoderClass);
                if (encoder != null) {
                    encoders.add(encoder);
                }
            }
        }
        List<Decoder> decoders = new ArrayList<Decoder>();
        if (decoderClasses != null) {
            //noinspection unchecked
            for (Class<? extends Decoder> decoderClass : decoderClasses) {
                Class<?> decoderType = getDecoderClassType(decoderClass);
                Decoder decoder = ComponentProviderService.getInstance(decoderClass);
                if (decoder != null) {
                    decoders.add(new DecoderWrapper(decoder, decoderType, decoderClass));
                }
            }
        }

        DefaultEndpointConfiguration.Builder builder = isServerEndpoint ?
                // TODO: fix once origins is added to the @WebSocketEndpoint annotation
                new DefaultServerEndpointConfiguration.Builder(wseAnnotation.value())
                        .origins(Collections.<String>emptyList()) :
                new DefaultClientEndpointConfiguration.Builder();


        return builder.encoders(encoders).decoders(decoders).protocols(subProtocols == null ?
                Collections.<String>emptyList() : Arrays.asList(subProtocols)).build();
    }

    private static Class<?> getDecoderClassType(Class<?> decoder) {
        Class<?> rootClass = null;

        if (Decoder.Text.class.isAssignableFrom(decoder)) {
            rootClass = Decoder.Text.class;
        } else if (Decoder.Binary.class.isAssignableFrom(decoder)) {
            rootClass = Decoder.Binary.class;
        } else if (Decoder.TextStream.class.isAssignableFrom(decoder)) {
            rootClass = Decoder.TextStream.class;
        } else if (Decoder.BinaryStream.class.isAssignableFrom(decoder)) {
            rootClass = Decoder.BinaryStream.class;
        }

        ReflectionHelper.DeclaringClassInterfacePair p = ReflectionHelper.getClass(decoder, rootClass);
        Class[] as = ReflectionHelper.getParameterizedClassArguments(p);
        return as == null ? Object.class : (as[0] == null ? Object.class : as[0]);
    }

    private AnnotatedEndpoint(Class<?> annotatedClass, Object instance, EndpointConfiguration config) {
        this.configuration = config;

        // TODO: should be removed once the instance creation is delegated to lifecycle provider
        annotatedInstance = instance == null ? ComponentProviderService.getInstance(annotatedClass) : instance;
        if (annotatedInstance == null) {
            throw new RuntimeException("Unable to instantiate endpoint class: " + annotatedClass);
        }

        Method onOpen = null;
        Method onClose = null;
        Method onError = null;
        ParameterExtractor[] onOpenParameters = null;
        ParameterExtractor[] onCloseParameters = null;
        ParameterExtractor[] onErrorParameters = null;

        Map<Integer, Class<?>> unknownParams = new HashMap<Integer, Class<?>>();

        // TODO: how about methods from the superclass?
        for (Method m : annotatedClass.getDeclaredMethods()) {
            for (Annotation a : m.getAnnotations()) {
                // TODO: should we support multiple annotations on the same method?
                if (a instanceof WebSocketOpen) {
                    if (onOpen == null) {
                        onOpen = m;
                        onOpenParameters = getParameterExtractors(m, unknownParams);
                        if (!unknownParams.isEmpty()) {
                            LOGGER.warning("Unknown parameter(s) for " + annotatedClass.getName() + "." + m.getName() +
                                    " method annotated with @WebSocketOpen annotation: " + unknownParams + ". This" +
                                    " method will be ignored.");
                            onOpen = null;
                            onOpenParameters = null;
                        }
                    } else {
                        LOGGER.warning("Multiple methods using @WebSocketOpen annotation" +
                                " in class " + annotatedClass.getName() + ": " + onOpen.getName() + " and " +
                                m.getName() + ". The latter will be ignored.");
                    }
                } else if (a instanceof WebSocketClose) {
                    if (onClose == null) {
                        onClose = m;
                        onCloseParameters = getParameterExtractors(m, unknownParams);
                        if (unknownParams.size() == 1 && unknownParams.values().iterator().next() != CloseReason.class) {
                            onCloseParameters[unknownParams.keySet().iterator().next()] = new ParamValue(0);
                        } else if (!unknownParams.isEmpty()) {
                            LOGGER.warning("Unknown parameter(s) for " + annotatedClass.getName() + "." + m.getName() +
                                    " method annotated with @WebSocketClose annotation: " + unknownParams + ". This" +
                                    " method will be ignored.");
                            onClose = null;
                            onCloseParameters = null;
                        }
                    } else {
                        LOGGER.warning("Multiple methods using @WebSocketClose annotation" +
                                " in class " + annotatedClass.getName() + ": " + onClose.getName() + " and " +
                                m.getName() + ". The latter will be ignored.");
                    }
                } else if (a instanceof WebSocketError) {
                    if (onError == null) {
                        onError = m;
                        onErrorParameters = getParameterExtractors(m, unknownParams);
                        if (unknownParams.size() == 1 &&
                                Throwable.class == unknownParams.values().iterator().next()) {
                            onErrorParameters[unknownParams.keySet().iterator().next()] = new ParamValue(0);
                        } else if (!unknownParams.isEmpty()) {
                            LOGGER.warning("Unknown parameter(s) for " + annotatedClass.getName() + "." + m.getName() +
                                    " method annotated with @WebSocketError annotation: " + unknownParams + ". This" +
                                    " method will be ignored.");
                            onError = null;
                            onErrorParameters = null;
                        }
                    } else {
                        LOGGER.warning("Multiple methods using @WebSocketError annotation" +
                                " in class " + annotatedClass.getName() + ": " + onError.getName() + " and " +
                                m.getName() + ". The latter will be ignored.");
                    }
                } else if (a instanceof WebSocketMessage) {
                    final ParameterExtractor[] extractors = getParameterExtractors(m, unknownParams);

                    if (unknownParams.isEmpty()) {
                        LOGGER.warning("Method " + annotatedClass.getName() + "." + m.getName() + " is annotated with "
                                + "@WebSocketMessage annotation but does not have any parameter representing the" +
                                " message. This method will be ignored.");
                        continue;
                    } else if (unknownParams.size() == 1) {
                        Map.Entry<Integer, Class<?>> entry = unknownParams.entrySet().iterator().next();
                        extractors[entry.getKey()] = new ParamValue(0);
                        messageHandlerFactories.add(new BasicHandler(m, extractors, entry.getValue()));
                        continue;
                    } else if (unknownParams.size() == 2) {
                        Iterator<Map.Entry<Integer, Class<?>>> it = unknownParams.entrySet().iterator();
                        Map.Entry<Integer, Class<?>> message = it.next();
                        Map.Entry<Integer, Class<?>> last;
                        if (message.getValue() == boolean.class || message.getValue() == Boolean.class) {
                            last = message;
                            message = it.next();
                        } else {
                            last = it.next();
                        }
                        extractors[message.getKey()] = new ParamValue(0);
                        extractors[last.getKey()] = new ParamValue(1);
                        if (last.getValue() == boolean.class || last.getValue() == Boolean.class) {
                            messageHandlerFactories.add(new AsyncHandler(m, extractors, message.getValue()));
                            continue;
                        }
                    }
                    LOGGER.warning("Method " + annotatedClass.getName() + "." + m.getName() + " annotated with "
                            + "@WebSocketMessage annotation has unknown parameters: " + unknownParams + ". This " +
                            "method will be ignored.");
                }
            }
        }

        this.onOpenMethod = onOpen;
        this.onErrorMethod = onError;
        this.onCloseMethod = onClose;
        this.onOpenParameters = onOpenParameters;
        this.onErrorParameters = onErrorParameters;
        this.onCloseParameters = onCloseParameters;
    }

    private ParameterExtractor[] getParameterExtractors(Method method, Map<Integer, Class<?>> unknownParams) {
        ParameterExtractor[] result = new ParameterExtractor[method.getParameterTypes().length];
        unknownParams.clear();

        for (int i = 0; i < method.getParameterTypes().length; i++) {
            Class<?> type = method.getParameterTypes()[i];
            final String pathParamName = getPathParamName(method.getParameterAnnotations()[i]);
            if (pathParamName != null) {
                result[i] = new ParameterExtractor() {
                    @Override
                    public Object value(Session session, Object... values) {
                        return session.getPathParameters().get(pathParamName);
                    }
                };
            } else if (type == Session.class) {
                result[i] = new ParameterExtractor() {
                    @Override
                    public Object value(Session session, Object... values) {
                        return session;
                    }
                };
            } else {
                unknownParams.put(i, type);
            }
        }

        return result;
    }

    private String getPathParamName(Annotation[] annotations) {
        for (Annotation a : annotations) {
            if (a instanceof WebSocketPathParam) {
                return ((WebSocketPathParam) a).value();
            }
        }
        return null;
    }

    private Object callMethod(Method method, ParameterExtractor[] extractors, Session session, Object... params) {
        if (method != null) {
            Object endpoint = annotatedInstance;
            Object[] paramValues = new Object[extractors.length];
            for (int i = 0; i < paramValues.length; i++) {
                paramValues[i] = extractors[i].value(session, params);
            }
            try {
                return method.invoke(endpoint, paramValues);
            } catch (Exception e) {
                onError(e.getCause(), session);
            }
        }
        return null;
    }

    // TODO XXX FIXME: Add this method to javax.websocket.Endpoint (replace existing onError)
    public void onClose(CloseReason closeReason, Session session) {
        callMethod(onCloseMethod, onCloseParameters, session, closeReason);
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        onClose(closeReason, session);
    }

    // TODO XXX FIXME: Add this method to javax.websocket.Endpoint (replace existing onError)
    public void onError(Throwable thr, Session session) {
        callMethod(onErrorMethod, onErrorParameters, session, thr);
    }

    @Override
    public void onError(Session session, Throwable thr) {
        onError(thr, session);
    }

    //    @Override
    public EndpointConfiguration getEndpointConfiguration() {
        return configuration;
    }

    @Override
    public void onOpen(Session session, EndpointConfiguration configuration) {
        for (MessageHandlerFactory f : messageHandlerFactories) {
            session.addMessageHandler(f.create(session));
        }
        callMethod(onOpenMethod, onOpenParameters, session);
    }

    static interface ParameterExtractor {
        Object value(Session session, Object... paramValues);
    }

    static class ParamValue implements ParameterExtractor {
        private final int index;

        ParamValue(int index) {
            this.index = index;
        }

        @Override
        public Object value(Session session, Object... paramValues) {
            return paramValues[index];
        }
    }

    abstract class MessageHandlerFactory {
        final Method method;
        final ParameterExtractor[] extractors;
        final Class<?> type;

        MessageHandlerFactory(Method method, ParameterExtractor[] extractors, Class<?> type) {
            this.method = method;
            this.extractors = extractors;
            this.type = (PrimitivesToBoxing.getBoxing(type) == null) ? type : PrimitivesToBoxing.getBoxing(type);
        }

        abstract MessageHandler create(Session session);
    }

    class BasicHandler extends MessageHandlerFactory {
        BasicHandler(Method method, ParameterExtractor[] extractors, Class<?> type) {
            super(method, extractors, type);
        }

        @Override
        public MessageHandler create(final Session session) {
            return new BasicMessageHandler() {
                @Override
                public void onMessage(Object message) {
                    Object result = callMethod(method, extractors, session, message);
                    if (result != null) {
                        try {
                            session.getRemote().sendObject(result);
                        } catch (Exception e) {
                            throw new RuntimeException("Error trying to send the response.", e);
                        }
                    }
                }

                @Override
                public Class<?> getType() {
                    return type;
                }
            };
        }
    }

    class AsyncHandler extends MessageHandlerFactory {
        AsyncHandler(Method method, ParameterExtractor[] extractors, Class<?> type) {
            super(method, extractors, type);
        }

        @Override
        public MessageHandler create(final Session session) {
            return new AsyncMessageHandler() {

                @Override
                public void onMessage(Object partialMessage, boolean last) {
                    callMethod(method, extractors, session, partialMessage, last);
                }

                @Override
                public Class<?> getType() {
                    return type;
                }
            };
        }
    }
}
