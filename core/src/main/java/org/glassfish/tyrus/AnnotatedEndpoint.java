/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 - 2012 Oracle and/or its affiliates. All rights reserved.
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

import javax.net.websocket.CloseReason;
import javax.net.websocket.Endpoint;
import javax.net.websocket.MessageHandler;
import javax.net.websocket.Session;
import javax.net.websocket.annotations.WebSocketClose;
import javax.net.websocket.annotations.WebSocketError;
import javax.net.websocket.annotations.WebSocketMessage;
import javax.net.websocket.annotations.WebSocketOpen;
import javax.net.websocket.annotations.WebSocketPathParam;

import java.io.InputStream;
import java.io.Reader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

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

    private Set<MessageHandlerFactory> messageHandlerFactories = new HashSet<MessageHandlerFactory>();

    public AnnotatedEndpoint(Class<?> annotatedClass, Object annotatedInstance) {

        // TODO: should be removed once the instance creation is delegated to lifecycle provider
        if (annotatedInstance != null) {
            this.annotatedInstance = annotatedInstance;
        } else {
            try {
                this.annotatedInstance = annotatedClass.newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Unable to instantiate endpoint class: " + annotatedClass, e);
            }
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
                        if (entry.getValue() == String.class) {
                            messageHandlerFactories.add(new Text(m, extractors));
                        } else if (entry.getValue() == ByteBuffer.class) {
                            messageHandlerFactories.add(new Binary(m, extractors));
                        } else if (entry.getValue() == InputStream.class) {
                            messageHandlerFactories.add(new BinaryStream(m, extractors));
                        } else if (entry.getValue() == Reader.class) {
                            messageHandlerFactories.add(new CharacterStream(m, extractors));
                        } else {
                            messageHandlerFactories.add(new DecodedObject(m, extractors, entry.getValue()));
                        }
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
                            if (message.getValue() == String.class) {
                                messageHandlerFactories.add(new AsyncText(m, extractors));
                                continue;
                            } else if (message.getValue() == ByteBuffer.class) {
                                messageHandlerFactories.add(new AsyncBinary(m, extractors));
                                continue;
                            }
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
                        return ((SessionImpl) session).getPathParameters().get(pathParamName);
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
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        callMethod(onCloseMethod, onCloseParameters, session, closeReason);
    }

    @Override
    public void onError(Throwable thr, Session session) {
        callMethod(onErrorMethod, onErrorParameters, session, thr);
    }

    @Override
    public void onOpen(Session session) {
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

        MessageHandlerFactory(Method method, ParameterExtractor[] extractors) {
            this.method = method;
            this.extractors = extractors;
        }

        abstract MessageHandler create(Session session);
    }

    class AsyncText extends MessageHandlerFactory {
        AsyncText(Method method, ParameterExtractor[] extractors) {
            super(method, extractors);
        }

        @Override
        public MessageHandler create(final Session session) {
            return new MessageHandler.AsyncText() {
                @Override
                public void onMessagePart(String part, boolean last) {
                    callMethod(method, extractors, session, part, last);
                }
            };
        }
    }

    class AsyncBinary extends MessageHandlerFactory {
        AsyncBinary(Method method, ParameterExtractor[] extractors) {
            super(method, extractors);
        }

        @Override
        public MessageHandler create(final Session session) {
            return new MessageHandler.AsyncBinary() {
                @Override
                public void onMessagePart(ByteBuffer part, boolean last) {
                    callMethod(method, extractors, session, part, last);
                }
            };
        }
    }

    class Binary extends MessageHandlerFactory {
        Binary(Method method, ParameterExtractor[] extractors) {
            super(method, extractors);
        }

        @Override
        public MessageHandler create(final Session session) {
            return new MessageHandler.Binary() {
                @Override
                public void onMessage(ByteBuffer message) {
                    Object result = callMethod(method, extractors, session, message);
                    if (result != null) {
                        try {
                            session.getRemote().sendObject(result);
                        } catch (Exception e) {
                            throw new RuntimeException("Error trying to send the response.", e);
                        }
                    }
                }
            };
        }
    }

    class Text extends MessageHandlerFactory {
        Text(Method method, ParameterExtractor[] extractors) {
            super(method, extractors);
        }

        @Override
        public MessageHandler create(final Session session) {
            return new MessageHandler.Text() {
                @Override
                public void onMessage(String message) {
                    Object result = callMethod(method, extractors, session, message);
                    if (result != null) {
                        try {
                            session.getRemote().sendObject(result);
                        } catch (Exception e) {
                            throw new RuntimeException("Error trying to send the response.", e);
                        }
                    }
                }
            };
        }
    }

    class BinaryStream extends MessageHandlerFactory {
        BinaryStream(Method method, ParameterExtractor[] extractors) {
            super(method, extractors);
        }

        @Override
        public MessageHandler create(final Session session) {
            return new MessageHandler.BinaryStream() {
                @Override
                public void onMessage(InputStream message) {
                    Object result = callMethod(method, extractors, session, message);
                    if (result != null) {
                        try {
                            session.getRemote().sendObject(result);
                        } catch (Exception e) {
                            throw new RuntimeException("Error trying to send the response.", e);
                        }
                    }
                }
            };
        }
    }

    class CharacterStream extends MessageHandlerFactory {
        CharacterStream(Method method, ParameterExtractor[] extractors) {
            super(method, extractors);
        }

        @Override
        public MessageHandler create(final Session session) {
            return new MessageHandler.CharacterStream() {
                @Override
                public void onMessage(Reader message) {
                    Object result = callMethod(method, extractors, session, message);
                    if (result != null) {
                        try {
                            session.getRemote().sendObject(result);
                        } catch (Exception e) {
                            throw new RuntimeException("Error trying to send the response.", e);
                        }
                    }
                }
            };
        }
    }

    class DecodedObject extends MessageHandlerFactory {
        private final Class<?> type;

        DecodedObject(Method method, ParameterExtractor[] extractors, Class<?> type) {
            super(method, extractors);
            this.type = (PrimitivesToBoxing.getBoxing(type) == null) ? type : PrimitivesToBoxing.getBoxing(type);
        }

        @Override
        public MessageHandler create(final Session session) {
            return new DecodedObjectMessageHandler() {
                @Override
                public Class<?> getType() {
                    return type;
                }

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
            };
        }
    }
}
