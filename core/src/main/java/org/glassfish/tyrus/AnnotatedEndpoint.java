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
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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
import javax.websocket.DefaultClientConfiguration;
import javax.websocket.DeploymentException;
import javax.websocket.Encoder;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.WebSocketClient;
import javax.websocket.WebSocketClose;
import javax.websocket.WebSocketError;
import javax.websocket.WebSocketMessage;
import javax.websocket.WebSocketOpen;
import javax.websocket.server.DefaultServerConfiguration;
import javax.websocket.server.WebSocketEndpoint;
import javax.websocket.server.WebSocketPathParam;

/**
 * AnnotatedEndpoint of a class annotated using the WebSocketEndpoint annotations.
 *
 * @author Martin Matula (martin.matula at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class AnnotatedEndpoint extends Endpoint {
    private static final Logger LOGGER = Logger.getLogger(AnnotatedEndpoint.class.getName());

    private final Object annotatedInstance;
    private final Class<?> annotatedClass;
    private final Method onOpenMethod;
    private final Method onCloseMethod;
    private final Method onErrorMethod;
    private final ParameterExtractor[] onOpenParameters;
    private final ParameterExtractor[] onCloseParameters;
    private final ParameterExtractor[] onErrorParameters;
    private final EndpointConfiguration configuration;
    private final ErrorCollector collector;
    private final ComponentProviderService componentProvider;

    private final Set<MessageHandlerFactory> messageHandlerFactories = new HashSet<MessageHandlerFactory>();

    /**
     * Create {@link AnnotatedEndpoint} from class.
     *
     * @param annotatedClass    annotated class.
     * @param componentProvider used for instantiating.
     * @param isServerEndpoint  {@code true} iff annotated endpoint is deployed on server side.
     * @param collector         error collector.
     * @return new instance.
     * @throws DeploymentException TODO remove
     */
    public static AnnotatedEndpoint fromClass(Class<?> annotatedClass, ComponentProviderService componentProvider, boolean isServerEndpoint, ErrorCollector collector) throws DeploymentException {
        return new AnnotatedEndpoint(annotatedClass, null, componentProvider, isServerEndpoint, collector);
    }

    /**
     * Create {@link AnnotatedEndpoint} from instance.
     *
     * @param annotatedInstance annotated instance.
     * @param componentProvider used for instantiating.
     * @param isServerEndpoint  {@code true} iff annotated endpoint is deployed on server side.
     * @param collector         error collector.
     * @return new instance.
     * @throws DeploymentException TODO remove
     */
    public static AnnotatedEndpoint fromInstance(Object annotatedInstance, ComponentProviderService componentProvider, boolean isServerEndpoint, ErrorCollector collector) throws DeploymentException {
        return new AnnotatedEndpoint(annotatedInstance.getClass(), annotatedInstance, componentProvider, isServerEndpoint, collector);
    }

    private AnnotatedEndpoint(Class<?> annotatedClass, Object instance, ComponentProviderService componentProvider, Boolean isServerEndpoint, ErrorCollector collector) throws DeploymentException {
        this.collector = collector;
        this.configuration = createEndpointConfiguration(annotatedClass, isServerEndpoint);
        this.annotatedInstance = instance;
        this.annotatedClass = annotatedClass;
        this.componentProvider = componentProvider;

        Method onOpen = null;
        Method onClose = null;
        Method onError = null;
        ParameterExtractor[] onOpenParameters = null;
        ParameterExtractor[] onCloseParameters = null;
        ParameterExtractor[] onErrorParameters = null;

        Map<Integer, Class<?>> unknownParams = new HashMap<Integer, Class<?>>();
        AnnotatedClassValidityChecker validityChecker = new AnnotatedClassValidityChecker(annotatedClass, configuration.getEncoders(), collector);

        // TODO: how about methods from the superclass?
        for (Method m : annotatedClass.getDeclaredMethods()) {
            for (Annotation a : m.getAnnotations()) {
                // TODO: should we support multiple annotations on the same method?
                if (a instanceof WebSocketOpen) {
                    if (onOpen == null) {
                        onOpen = m;
                        onOpenParameters = getParameterExtractors(m, unknownParams);
                        validityChecker.checkOnOpenParams(m, unknownParams);
                    } else {
                        collector.addException(new DeploymentException("Multiple methods using @WebSocketOpen annotation" +
                                " in class " + annotatedClass.getName() + ": " + onOpen.getName() + " and " +
                                m.getName() + ". The latter will be ignored."));
                    }
                } else if (a instanceof WebSocketClose) {
                    if (onClose == null) {
                        onClose = m;
                        onCloseParameters = getOnCloseParameterExtractors(m, unknownParams);
                        validityChecker.checkOnCloseParams(m, unknownParams);
                        if (unknownParams.size() == 1 && unknownParams.values().iterator().next() != CloseReason.class) {
                            onCloseParameters[unknownParams.keySet().iterator().next()] = new ParamValue(0);
                        }
                    } else {
                        collector.addException(new DeploymentException("Multiple methods using @WebSocketClose annotation" +
                                " in class " + annotatedClass.getName() + ": " + onClose.getName() + " and " +
                                m.getName() + ". The latter will be ignored."));
                    }
                } else if (a instanceof WebSocketError) {
                    if (onError == null) {
                        onError = m;
                        onErrorParameters = getParameterExtractors(m, unknownParams);
                        validityChecker.checkOnErrorParams(m, unknownParams);
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
                        collector.addException(new DeploymentException("Multiple methods using @WebSocketError annotation" +
                                " in class " + annotatedClass.getName() + ": " + onError.getName() + " and " +
                                m.getName()));
                    }
                } else if (a instanceof WebSocketMessage) {
                    final long maxMessageSize = ((WebSocketMessage) a).maxMessageSize();
                    final ParameterExtractor[] extractors = getParameterExtractors(m, unknownParams);
                    MessageHandlerFactory handlerFactory;

                    if (unknownParams.size() == 1) {
                        Map.Entry<Integer, Class<?>> entry = unknownParams.entrySet().iterator().next();
                        extractors[entry.getKey()] = new ParamValue(0);
                        handlerFactory = new BasicHandler(m, extractors, entry.getValue(), maxMessageSize);
                        messageHandlerFactories.add(handlerFactory);
                        validityChecker.checkOnMessageParams(m, handlerFactory.create(null));
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
                            handlerFactory = new AsyncHandler(m, extractors, message.getValue(), maxMessageSize);
                            messageHandlerFactories.add(handlerFactory);
                            validityChecker.checkOnMessageParams(m, handlerFactory.create(null));
                        } else {
                            collector.addException(new DeploymentException(String.format("Method: %s.%s: has got wrong number of params.", annotatedClass.getName(), m.getName())));
                        }
                    } else {
                        collector.addException(new DeploymentException(String.format("Method: %s.%s: has got wrong number of params.", annotatedClass.getName(), m.getName())));
                    }
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

    private EndpointConfiguration createEndpointConfiguration(Class<?> annotatedClass, boolean isServerEndpoint) {
        if (isServerEndpoint) {
            final WebSocketEndpoint wseAnnotation = annotatedClass.getAnnotation(WebSocketEndpoint.class);

            if (wseAnnotation == null) {
                collector.addException(new DeploymentException(String.format("@WebSocketEndpoint annotation not found on class %s", annotatedClass.getName())));
                return null;
            }

            // default value
            if (wseAnnotation.configuration().equals(DefaultServerConfiguration.class)) {
                Class<? extends Encoder>[] encoderClasses;
                Class<? extends Decoder>[] decoderClasses;
                String[] subProtocols;

                encoderClasses = wseAnnotation.encoders();
                decoderClasses = wseAnnotation.decoders();
                subProtocols = wseAnnotation.subprotocols();

                List<Encoder> encoders = new ArrayList<Encoder>();
                if (encoderClasses != null) {
                    //noinspection unchecked
                    for (Class<? extends Encoder> encoderClass : encoderClasses) {
                        Class<?> encoderType = getEncoderClassType(encoderClass);
                        Encoder encoder = ReflectionHelper.getInstance(encoderClass, collector);
                        if (encoder != null) {
                            encoders.add(new CoderWrapper<Encoder>(encoder, encoderType));
                        }
                    }
                }
                List<Decoder> decoders = new ArrayList<Decoder>();
                if (decoderClasses != null) {
                    //noinspection unchecked
                    for (Class<? extends Decoder> decoderClass : decoderClasses) {
                        Class<?> decoderType = getDecoderClassType(decoderClass);
                        Decoder decoder = ReflectionHelper.getInstance(decoderClass, collector);
                        if (decoder != null) {
                            decoders.add(new CoderWrapper<Decoder>(decoder, decoderType));
                        }
                    }
                }

                DefaultServerConfiguration dsc =
                        // TODO: fix once origins is added to the @WebSocketEndpoint annotation
                        // TODO: fix once DefaultServerConfiguration has usable constructor
                        new TyrusServerEndpointConfiguration(null, wseAnnotation.value(),   /* annotatedClass, */ Collections.<String>emptyList());

                dsc.setEncoders(encoders);
                dsc.setDecoders(decoders);
                dsc.setSubprotocols(Arrays.asList(subProtocols));

                return dsc;
            } else {
                try {
                    final Class<? extends DefaultServerConfiguration> configClass = wseAnnotation.configuration();
                    Constructor<? extends DefaultServerConfiguration> constructor;
                    try {
                        constructor = configClass.getConstructor(Class.class, String.class);
                    } catch (NoSuchMethodException e) {
                        constructor = configClass.getConstructor();
                    }

                    // component provider?
                    return constructor.newInstance(annotatedClass, wseAnnotation.value());

                } catch (NoSuchMethodException e) {
                    return null;
                } catch (InvocationTargetException e) {
                    return null;
                } catch (InstantiationException e) {
                    return null;
                } catch (IllegalAccessException e) {
                    return null;
                }
            }

            // client endpoint
        } else {
            final WebSocketClient wscAnnotation = annotatedClass.getAnnotation(WebSocketClient.class);

            if (wscAnnotation == null) {
                collector.addException(new DeploymentException(String.format("@WebSocketClient annotation not found on class %s", annotatedClass.getName())));
                return null;
            }

            // TODO - wscAnnotation.configuration()?

            Class<? extends Encoder>[] encoderClasses;
            Class<? extends Decoder>[] decoderClasses;
            String[] subProtocols;

            encoderClasses = wscAnnotation.encoders();
            decoderClasses = wscAnnotation.decoders();
            subProtocols = wscAnnotation.subprotocols();

            List<Encoder> encoders = new ArrayList<Encoder>();
            if (encoderClasses != null) {
                //noinspection unchecked
                for (Class<? extends Encoder> encoderClass : encoderClasses) {
                    Encoder encoder = ReflectionHelper.getInstance(encoderClass, collector);
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
                    Decoder decoder = ReflectionHelper.getInstance(decoderClass, collector);
                    if (decoder != null) {
                        decoders.add(new CoderWrapper<Decoder>(decoder, decoderType));
                    }
                }
            }

            DefaultClientConfiguration dcc =
                    // TODO: fix once origins is added to the @WebSocketEndpoint annotation
                    new DefaultClientConfiguration();
            dcc.setEncoders(encoders);
            dcc.setDecoders(decoders);
            dcc.setPreferredSubprotocols(Arrays.asList(subProtocols));

            return dcc;
        }
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

    private static Class<?> getEncoderClassType(Class<?> encoder) {
        Class<?> rootClass = null;

        if (Encoder.Text.class.isAssignableFrom(encoder)) {
            rootClass = Encoder.Text.class;
        } else if (Encoder.Binary.class.isAssignableFrom(encoder)) {
            rootClass = Encoder.Binary.class;
        } else if (Encoder.TextStream.class.isAssignableFrom(encoder)) {
            rootClass = Encoder.TextStream.class;
        } else if (Encoder.BinaryStream.class.isAssignableFrom(encoder)) {
            rootClass = Encoder.BinaryStream.class;
        }

        ReflectionHelper.DeclaringClassInterfacePair p = ReflectionHelper.getClass(encoder, rootClass);
        Class[] as = ReflectionHelper.getParameterizedClassArguments(p);
        return as == null ? Object.class : (as[0] == null ? Object.class : as[0]);
    }

    private ParameterExtractor[] getOnCloseParameterExtractors(Method method, Map<Integer, Class<?>> unknownParams) {
        return getParameterExtractors(method, unknownParams, new HashSet<Class<?>>(Arrays.asList(CloseReason.class)));
    }

    private ParameterExtractor[] getParameterExtractors(Method method, Map<Integer, Class<?>> unknownParams) {
        return getParameterExtractors(method, unknownParams, Collections.<Class<?>>emptySet());
    }

    private ParameterExtractor[] getParameterExtractors(Method method, Map<Integer, Class<?>> unknownParams, Set<Class<?>> params) {
        ParameterExtractor[] result = new ParameterExtractor[method.getParameterTypes().length];
        boolean sessionPresent = false;
        unknownParams.clear();

        for (int i = 0; i < method.getParameterTypes().length; i++) {
            final Class<?> type = method.getParameterTypes()[i];
            final String pathParamName = getPathParamName(method.getParameterAnnotations()[i]);
            if (pathParamName != null) {
                result[i] = new ParameterExtractor() {
                    @Override
                    public Object value(Session session, Object... values) {
                        return session.getPathParameters().get(pathParamName);
                    }
                };
            } else if (type == Session.class) {
                if (sessionPresent) {
                    collector.addException(new DeploymentException(String.format("Method  %s  has got two or more Session parameters.", method.getName())));
                } else {
                    sessionPresent = true;
                }
                result[i] = new ParameterExtractor() {
                    @Override
                    public Object value(Session session, Object... values) {
                        return session;
                    }
                };
            } else if (type == EndpointConfiguration.class) {
                result[i] = new ParameterExtractor() {
                    @Override
                    public Object value(Session session, Object... values) {
                        return getEndpointConfiguration();
                    }
                };
            } else if (params.contains(type)) {
                result[i] = new ParameterExtractor() {
                    @Override
                    public Object value(Session session, Object... values) {
                        for (Object value : values) {
                            if (value != null && type.isAssignableFrom(value.getClass())) {
                                return value;
                            }
                        }

                        return null;
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
            Object[] paramValues = new Object[extractors.length];

            final Object endpoint = annotatedInstance != null ? annotatedInstance :
                    componentProvider.getInstance(annotatedClass, session, collector);

            for (int i = 0; i < paramValues.length; i++) {
                paramValues[i] = extractors[i].value(session, params);
            }
            try {
                return method.invoke(endpoint, paramValues);
            } catch (Exception e) {
                onError(e, session);
            }
        }
        return null;
    }

    void onClose(CloseReason closeReason, Session session) {
        callMethod(onCloseMethod, onCloseParameters, session, closeReason);
        componentProvider.removeSession(session);
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        onClose(closeReason, session);
    }

    void onError(Throwable thr, Session session) {
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
        final long maxMessageSize;

        MessageHandlerFactory(Method method, ParameterExtractor[] extractors, Class<?> type, long maxMessageSize) {
            this.method = method;
            this.extractors = extractors;
            this.type = (PrimitivesToWrappers.getPrimitiveWrapper(type) == null) ? type : PrimitivesToWrappers.getPrimitiveWrapper(type);
            this.maxMessageSize = maxMessageSize;
        }

        public final long getMaxMessageSize() {
            return maxMessageSize;
        }

        abstract MessageHandler create(Session session);
    }

    class BasicHandler extends MessageHandlerFactory {
        BasicHandler(Method method, ParameterExtractor[] extractors, Class<?> type, long maxMessageSize) {
            super(method, extractors, type, maxMessageSize);
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
                            onError(e, session);
                        }
                    }
                }

                @Override
                public Class<?> getType() {
                    return type;
                }

                @Override
                public long getMaxMessageSize() {
                    return maxMessageSize;
                }
            };
        }
    }

    class AsyncHandler extends MessageHandlerFactory {
        AsyncHandler(Method method, ParameterExtractor[] extractors, Class<?> type, long maxMessageSize) {
            super(method, extractors, type, maxMessageSize);
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

                @Override
                public long getMaxMessageSize() {
                    return maxMessageSize;
                }
            };
        }
    }
}
