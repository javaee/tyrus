/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2014 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.Annotation;
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
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.ClientEndpoint;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.DeploymentException;
import javax.websocket.Encoder;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import org.glassfish.tyrus.core.coder.PrimitiveDecoders;
import org.glassfish.tyrus.core.l10n.LocalizationMessages;
import org.glassfish.tyrus.core.monitoring.EndpointEventListener;

/**
 * {@link Endpoint} descendant which represents deployed annotated endpoint.
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
    private final EndpointConfig configuration;
    private final ComponentProviderService componentProvider;
    private final EndpointEventListener endpointEventListener;

    private final Set<MessageHandlerFactory> messageHandlerFactories = new HashSet<MessageHandlerFactory>();

    /**
     * Create {@link AnnotatedEndpoint} from class.
     *
     * @param annotatedClass        annotated class.
     * @param componentProvider     used for instantiating.
     * @param isServerEndpoint      {@code true} iff annotated endpoint is deployed on server side.
     * @param incomingBufferSize    size limit of the incoming buffer.
     * @param collector             error collector.
     * @param endpointEventListener listener of monitored endpoint events.
     * @return new instance.
     */
    public static AnnotatedEndpoint fromClass(Class<?> annotatedClass, ComponentProviderService componentProvider, boolean isServerEndpoint, int incomingBufferSize, ErrorCollector collector, EndpointEventListener endpointEventListener) {
        return new AnnotatedEndpoint(annotatedClass, null, componentProvider, isServerEndpoint, incomingBufferSize, collector, endpointEventListener);
    }

    /**
     * Create {@link AnnotatedEndpoint} from instance.
     *
     * @param annotatedInstance  annotated instance.
     * @param componentProvider  used for instantiating.
     * @param isServerEndpoint   {@code true} iff annotated endpoint is deployed on server side.
     * @param incomingBufferSize size limit of the incoming buffer
     * @param collector          error collector.
     * @return new instance.
     */
    public static AnnotatedEndpoint fromInstance(Object annotatedInstance, ComponentProviderService componentProvider,
                                                 boolean isServerEndpoint, int incomingBufferSize, ErrorCollector collector) {
        return new AnnotatedEndpoint(annotatedInstance.getClass(), annotatedInstance, componentProvider, isServerEndpoint,
                incomingBufferSize, collector, EndpointEventListener.NO_OP);
    }

    private AnnotatedEndpoint(Class<?> annotatedClass, Object instance, ComponentProviderService componentProvider,
                              Boolean isServerEndpoint, int incomingBufferSize, ErrorCollector collector, EndpointEventListener endpointEventListener) {
        this.configuration = createEndpointConfig(annotatedClass, isServerEndpoint, collector);
        this.annotatedInstance = instance;
        this.annotatedClass = annotatedClass;
        this.endpointEventListener = endpointEventListener;
        this.componentProvider = isServerEndpoint ? new ComponentProviderService(componentProvider) {
            @Override
            public <T> Object getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
                return ((ServerEndpointConfig) configuration).getConfigurator().getEndpointInstance(endpointClass);
            }
        } : componentProvider;

        Method onOpen = null;
        Method onClose = null;
        Method onError = null;
        ParameterExtractor[] onOpenParameters = null;
        ParameterExtractor[] onCloseParameters = null;
        ParameterExtractor[] onErrorParameters = null;

        Map<Integer, Class<?>> unknownParams = new HashMap<Integer, Class<?>>();
        AnnotatedClassValidityChecker validityChecker = new AnnotatedClassValidityChecker(annotatedClass, configuration.getEncoders(), configuration.getDecoders(), collector);

        for (Method m : annotatedClass.getMethods()) {
            if (m.isBridge()) {
                continue;
            }

            for (Annotation a : m.getAnnotations()) {
                // TODO: should we support multiple annotations on the same method?
                if (a instanceof OnOpen) {
                    if (onOpen == null) {
                        onOpen = m;
                        onOpenParameters = getParameterExtractors(m, unknownParams, collector);
                        validityChecker.checkOnOpenParams(m, unknownParams);
                    } else {
                        collector.addException(new DeploymentException(
                                LocalizationMessages.ENDPOINT_MULTIPLE_METHODS(
                                        OnOpen.class.getSimpleName(), annotatedClass.getName(), onOpen.getName(), m.getName()
                                )
                        ));
                    }
                } else if (a instanceof OnClose) {
                    if (onClose == null) {
                        onClose = m;
                        onCloseParameters = getOnCloseParameterExtractors(m, unknownParams, collector);
                        validityChecker.checkOnCloseParams(m, unknownParams);
                        if (unknownParams.size() == 1 && unknownParams.values().iterator().next() != CloseReason.class) {
                            onCloseParameters[unknownParams.keySet().iterator().next()] = new ParamValue(0);
                        }
                    } else {
                        collector.addException(new DeploymentException(
                                LocalizationMessages.ENDPOINT_MULTIPLE_METHODS(
                                        OnClose.class.getSimpleName(), annotatedClass.getName(), onClose.getName(), m.getName()
                                )
                        ));
                    }
                } else if (a instanceof OnError) {
                    if (onError == null) {
                        onError = m;
                        onErrorParameters = getParameterExtractors(m, unknownParams, collector);
                        validityChecker.checkOnErrorParams(m, unknownParams);
                        if (unknownParams.size() == 1 &&
                                Throwable.class == unknownParams.values().iterator().next()) {
                            onErrorParameters[unknownParams.keySet().iterator().next()] = new ParamValue(0);
                        } else if (!unknownParams.isEmpty()) {
                            LOGGER.warning(LocalizationMessages.ENDPOINT_UNKNOWN_PARAMS(annotatedClass.getName(), m.getName(), unknownParams));
                            onError = null;
                            onErrorParameters = null;
                        }
                    } else {
                        collector.addException(new DeploymentException(
                                LocalizationMessages.ENDPOINT_MULTIPLE_METHODS(
                                        OnError.class.getSimpleName(), annotatedClass.getName(), onError.getName(), m.getName()
                                )
                        ));
                    }
                } else if (a instanceof OnMessage) {
                    final long maxMessageSize = ((OnMessage) a).maxMessageSize();
                    if (maxMessageSize > incomingBufferSize) {
                        LOGGER.config(LocalizationMessages.ENDPOINT_MAX_MESSAGE_SIZE_TOO_LONG(maxMessageSize, m.getName(), annotatedClass.getName(), incomingBufferSize));
                    }
                    final ParameterExtractor[] extractors = getParameterExtractors(m, unknownParams, collector);
                    MessageHandlerFactory handlerFactory;

                    if (unknownParams.size() == 1) {
                        Map.Entry<Integer, Class<?>> entry = unknownParams.entrySet().iterator().next();
                        extractors[entry.getKey()] = new ParamValue(0);
                        handlerFactory = new WholeHandler(componentProvider.getInvocableMethod(m), extractors, entry.getValue(), maxMessageSize);
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
                            handlerFactory = new PartialHandler(componentProvider.getInvocableMethod(m), extractors, message.getValue(), maxMessageSize);
                            messageHandlerFactories.add(handlerFactory);
                            validityChecker.checkOnMessageParams(m, handlerFactory.create(null));
                        } else {
                            collector.addException(new DeploymentException(LocalizationMessages.ENDPOINT_WRONG_PARAMS(annotatedClass.getName(), m.getName())));
                        }
                    } else {
                        collector.addException(new DeploymentException(LocalizationMessages.ENDPOINT_WRONG_PARAMS(annotatedClass.getName(), m.getName())));
                    }
                }
            }
        }

        this.onOpenMethod = onOpen == null ? null : componentProvider.getInvocableMethod(onOpen);
        this.onErrorMethod = onError == null ? null : componentProvider.getInvocableMethod(onError);
        this.onCloseMethod = onClose == null ? null : componentProvider.getInvocableMethod(onClose);
        this.onOpenParameters = onOpenParameters;
        this.onErrorParameters = onErrorParameters;
        this.onCloseParameters = onCloseParameters;
    }

    private EndpointConfig createEndpointConfig(Class<?> annotatedClass, boolean isServerEndpoint, ErrorCollector collector) {
        if (isServerEndpoint) {
            final ServerEndpoint wseAnnotation = annotatedClass.getAnnotation(ServerEndpoint.class);

            if (wseAnnotation == null) {
                collector.addException(new DeploymentException(LocalizationMessages.ENDPOINT_ANNOTATION_NOT_FOUND(ServerEndpoint.class.getSimpleName(), annotatedClass.getName())));
                return null;
            }

            List<Class<? extends Encoder>> encoderClasses = new ArrayList<Class<? extends Encoder>>();
            List<Class<? extends Decoder>> decoderClasses = new ArrayList<Class<? extends Decoder>>();
            String[] subProtocols;

            encoderClasses.addAll(Arrays.asList(wseAnnotation.encoders()));
            decoderClasses.addAll(Arrays.asList(wseAnnotation.decoders()));
            subProtocols = wseAnnotation.subprotocols();

            decoderClasses.addAll(TyrusEndpointWrapper.getDefaultDecoders());

            final MaxSessions wseMaxSessionsAnnotation = annotatedClass.getAnnotation(MaxSessions.class);

            if (wseMaxSessionsAnnotation != null) {
                TyrusServerEndpointConfig.Builder builder = TyrusServerEndpointConfig.Builder.create(annotatedClass, wseAnnotation.value()).
                        encoders(encoderClasses).decoders(decoderClasses).subprotocols(Arrays.asList(subProtocols));
                if (!wseAnnotation.configurator().equals(ServerEndpointConfig.Configurator.class)) {
                    builder = builder.configurator(ReflectionHelper.getInstance(wseAnnotation.configurator(), collector));
                }
                builder.maxSessions(wseMaxSessionsAnnotation.value());
                return builder.build();
            } else {
                ServerEndpointConfig.Builder builder = ServerEndpointConfig.Builder.create(annotatedClass, wseAnnotation.value()).
                        encoders(encoderClasses).decoders(decoderClasses).subprotocols(Arrays.asList(subProtocols));
                if (!wseAnnotation.configurator().equals(ServerEndpointConfig.Configurator.class)) {
                    builder = builder.configurator(ReflectionHelper.getInstance(wseAnnotation.configurator(), collector));
                }
                return builder.build();
            }

            // client endpoint
        } else {
            final ClientEndpoint wscAnnotation = annotatedClass.getAnnotation(ClientEndpoint.class);

            if (wscAnnotation == null) {
                collector.addException(new DeploymentException(LocalizationMessages.ENDPOINT_ANNOTATION_NOT_FOUND(ClientEndpoint.class.getSimpleName(), annotatedClass.getName())));
                return null;
            }

            List<Class<? extends Encoder>> encoderClasses = new ArrayList<Class<? extends Encoder>>();
            List<Class<? extends Decoder>> decoderClasses = new ArrayList<Class<? extends Decoder>>();
            String[] subProtocols;

            encoderClasses.addAll(Arrays.asList(wscAnnotation.encoders()));
            decoderClasses.addAll(Arrays.asList(wscAnnotation.decoders()));
            subProtocols = wscAnnotation.subprotocols();

            decoderClasses.addAll(TyrusEndpointWrapper.getDefaultDecoders());

            ClientEndpointConfig.Configurator configurator = ReflectionHelper.getInstance(wscAnnotation.configurator(), collector);

            return ClientEndpointConfig.Builder.create().encoders(encoderClasses).decoders(decoderClasses).
                    preferredSubprotocols(Arrays.asList(subProtocols)).configurator(configurator).build();
        }
    }

    static Class<?> getDecoderClassType(Class<? extends Decoder> decoder) {
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

    static Class<?> getEncoderClassType(Class<? extends Encoder> encoder) {
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

    private ParameterExtractor[] getOnCloseParameterExtractors(final Method method, Map<Integer, Class<?>> unknownParams, ErrorCollector collector) {
        return getParameterExtractors(method, unknownParams, new HashSet<Class<?>>(Arrays.asList((Class<?>) CloseReason.class)), collector);
    }

    private ParameterExtractor[] getParameterExtractors(final Method method, Map<Integer, Class<?>> unknownParams, ErrorCollector collector) {
        return getParameterExtractors(method, unknownParams, Collections.<Class<?>>emptySet(), collector);
    }

    private ParameterExtractor[] getParameterExtractors(final Method method, Map<Integer, Class<?>> unknownParams, Set<Class<?>> params, ErrorCollector collector) {
        ParameterExtractor[] result = new ParameterExtractor[method.getParameterTypes().length];
        boolean sessionPresent = false;
        unknownParams.clear();

        for (int i = 0; i < method.getParameterTypes().length; i++) {
            final Class<?> type = method.getParameterTypes()[i];
            final String pathParamName = getPathParamName(method.getParameterAnnotations()[i]);
            if (pathParamName != null) {
                if (!(PrimitivesToWrappers.isPrimitiveWrapper(type) || type.isPrimitive() || type.equals(String.class))) {
                    collector.addException(new DeploymentException(LocalizationMessages.ENDPOINT_WRONG_PATH_PARAM(method.getName(), type.getName())));
                }

                result[i] = new ParameterExtractor() {

                    final Decoder.Text<?> decoder = PrimitiveDecoders.ALL_INSTANCES.get(PrimitivesToWrappers.getPrimitiveWrapper(type));

                    @Override
                    public Object value(Session session, Object... values) throws DecodeException {
                        Object result = null;

                        if (decoder != null) {
                            result = decoder.decode(session.getPathParameters().get(pathParamName));
                        } else if (type.equals(String.class)) {
                            result = session.getPathParameters().get(pathParamName);
                        }

                        return result;
                    }
                };
            } else if (type == Session.class) {
                if (sessionPresent) {
                    collector.addException(new DeploymentException(LocalizationMessages.ENDPOINT_MULTIPLE_SESSION_PARAM(method.getName())));
                } else {
                    sessionPresent = true;
                }
                result[i] = new ParameterExtractor() {
                    @Override
                    public Object value(Session session, Object... values) {
                        return session;
                    }
                };
            } else if (type == EndpointConfig.class) {
                result[i] = new ParameterExtractor() {
                    @Override
                    public Object value(Session session, Object... values) {
                        return getEndpointConfig();
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
            if (a instanceof PathParam) {
                return ((PathParam) a).value();
            }
        }
        return null;
    }

    private Object callMethod(Method method, ParameterExtractor[] extractors, Session session, boolean callOnError, Object... params) {
        ErrorCollector collector = new ErrorCollector();
        Object[] paramValues = new Object[extractors.length];

        try {
            final Object endpoint = annotatedInstance != null ? annotatedInstance :
                    componentProvider.getInstance(annotatedClass, session, collector);

            // TYRUS-325: Server do not close session properly if non-instantiable endpoint class is provided
            if (callOnError && endpoint == null) {
                if (!collector.isEmpty()) {
                    Throwable t = collector.composeComprehensiveException();
                    LOGGER.log(Level.FINE, t.getMessage(), t);
                }
                try {
                    session.close(CloseReasons.UNEXPECTED_CONDITION.getCloseReason());
                } catch (Exception e) {
                    LOGGER.log(Level.FINEST, e.getMessage(), e);
                }
                return null;
            }

            if (!collector.isEmpty()) {
                throw collector.composeComprehensiveException();
            }

            for (int i = 0; i < paramValues.length; i++) {
                paramValues[i] = extractors[i].value(session, params);
            }

            return method.invoke(endpoint, paramValues);
        } catch (Exception e) {
            if (callOnError) {
                onError(session, (e instanceof InvocationTargetException ? e.getCause() : e));
            } else {
                LOGGER.log(Level.INFO, LocalizationMessages.ENDPOINT_EXCEPTION_FROM_ON_ERROR(method), e);
            }
        }

        return null;
    }

    void onClose(CloseReason closeReason, Session session) {
        try {
            if (onCloseMethod != null) {
                callMethod(onCloseMethod, onCloseParameters, session, true, closeReason);
            }
        } finally {
            componentProvider.removeSession(session);
        }
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        onClose(closeReason, session);
    }

    @Override
    public void onError(Session session, Throwable thr) {
        if (onErrorMethod != null) {
            callMethod(onErrorMethod, onErrorParameters, session, false, thr);
        } else {
            LOGGER.log(Level.INFO, LocalizationMessages.ENDPOINT_UNHANDLED_EXCEPTION(annotatedClass.getCanonicalName()), thr);
        }
        endpointEventListener.onError(session, thr);
    }

    //    @Override
    public EndpointConfig getEndpointConfig() {
        return configuration;
    }

    @Override
    public void onOpen(Session session, EndpointConfig configuration) {
        for (MessageHandlerFactory f : messageHandlerFactories) {
            session.addMessageHandler(f.create(session));
        }

        if (onOpenMethod != null) {
            callMethod(onOpenMethod, onOpenParameters, session, true);
        }
    }

    static interface ParameterExtractor {
        Object value(Session session, Object... paramValues) throws DecodeException;
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

    private abstract class MessageHandlerFactory {
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

        abstract MessageHandler create(Session session);
    }

    private class WholeHandler extends MessageHandlerFactory {
        WholeHandler(Method method, ParameterExtractor[] extractors, Class<?> type, long maxMessageSize) {
            super(method, extractors, type, maxMessageSize);
        }

        @Override
        public MessageHandler create(final Session session) {
            return new BasicMessageHandler() {
                @Override
                public void onMessage(Object message) {
                    Object result = callMethod(method, extractors, session, true, message);
                    if (result != null) {
                        try {
                            session.getBasicRemote().sendObject(result);
                        } catch (Exception e) {
                            onError(session, e);
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

    private class PartialHandler extends MessageHandlerFactory {
        PartialHandler(Method method, ParameterExtractor[] extractors, Class<?> type, long maxMessageSize) {
            super(method, extractors, type, maxMessageSize);
        }

        @Override
        public MessageHandler create(final Session session) {
            return new AsyncMessageHandler() {

                @Override
                public void onMessage(Object partialMessage, boolean last) {
                    Object result = callMethod(method, extractors, session, true, partialMessage, last);
                    if (result != null) {
                        try {
                            session.getBasicRemote().sendObject(result);
                        } catch (Exception e) {
                            onError(session, e);
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
}
