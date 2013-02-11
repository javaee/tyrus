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
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;

/**
 * Manages registered {@link MessageHandler}s and checks whether the new ones may be registered.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @see MessageHandler
 * @see javax.websocket.WebSocketMessage
 */
class MessageHandlerManager {

    private static final List<Class<?>> TEXT_HANDLER_TYPES = Arrays.asList(String.class, Reader.class);
    private static final List<Class<?>> BINARY_HANDLER_TYPES = Arrays.asList(ByteBuffer.class, InputStream.class, byte[].class);
    private static final Class<?> PONG_HANDLER_TYPE = PongMessage.class;

    private boolean basicTextHandlerPresent = false;
    private boolean basicBinaryHandlerPresent = false;
    private boolean basicPongHandlerPresent = false;
    private final Map<Class<?>, MessageHandler> basicHandlers = new HashMap<Class<?>, MessageHandler>();

    private boolean asyncTextHandlerPresent = false;
    private boolean asyncBinaryHandlerPresent = false;
    private final Map<Class<?>, MessageHandler> asyncHandlers = new HashMap<Class<?>, MessageHandler>();

    private Set<MessageHandler> messageHandlerCache;

    /**
     * Add {@link MessageHandler} to the manager.
     *
     * @param handler {@link MessageHandler} to be added to the manager.
     */
    public void addMessageHandler(MessageHandler handler) throws IllegalStateException {

        if (!(handler instanceof MessageHandler.Basic) && !(handler instanceof MessageHandler.Async)) {
            throwException("MessageHandler must implement MessageHandler.Basic or MessageHandler.Async.");
        }

        final Class<?> handlerClass = getHandlerType(handler);

        if (handler instanceof MessageHandler.Basic) {
            // basic types
            // text
            if (TEXT_HANDLER_TYPES.contains(handlerClass)) {
                if (basicTextHandlerPresent) {
                    throwException("Text MessageHandler already registered.");
                } else {
                    basicTextHandlerPresent = true;
                }
            }

            // binary
            if (BINARY_HANDLER_TYPES.contains(handlerClass)) {
                if (basicBinaryHandlerPresent) {
                    throwException("Binary MessageHandler already registered.");
                } else {
                    basicBinaryHandlerPresent = true;
                }
            }

            // pong
            if (PONG_HANDLER_TYPE == handlerClass) {
                if (basicPongHandlerPresent) {
                    throwException("Pong MessageHander already registered.");
                } else {
                    basicPongHandlerPresent = true;
                }
            }

            // map of all registered handlers
            if (basicHandlers.containsKey(handlerClass)) {
                throwException(String.format("MessageHandler for type: %s already registered.", handlerClass));
            } else {
                basicHandlers.put(handlerClass, handler);
            }
        } else if (handler instanceof MessageHandler.Async) {
            //async types
            // text
            if (TEXT_HANDLER_TYPES.contains(handlerClass)) {
                if (asyncTextHandlerPresent) {
                    throwException("Text MessageHandler already registered.");
                } else {
                    asyncTextHandlerPresent = true;
                }
            }

            // binary
            if (BINARY_HANDLER_TYPES.contains(handlerClass)) {
                if (asyncBinaryHandlerPresent) {
                    throwException("Binary MessageHandler already registered.");
                } else {
                    asyncBinaryHandlerPresent = true;
                }
            }

            // map of all registered handlers
            if (asyncHandlers.containsKey(handlerClass)) {
                throwException(String.format("MessageHandler for type: %s already registered.", handlerClass));
            } else {
                asyncHandlers.put(handlerClass, handler);
            }
        }

        messageHandlerCache = null;
    }

    private void throwException(String text) throws IllegalStateException {
        throw new IllegalStateException(text);
    }

    /**
     * Remove {@link MessageHandler} from the manager.
     *
     * @param handler handler which will be removed.
     */
    public void removeMessageHandler(MessageHandler handler) {
        Iterator<Map.Entry<Class<?>, MessageHandler>> iterator = basicHandlers.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<Class<?>, MessageHandler> next = iterator.next();
            if (next.getValue().equals(handler)) {
                iterator.remove();
                messageHandlerCache = null;
            }
        }

        iterator = asyncHandlers.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<Class<?>, MessageHandler> next = iterator.next();
            if (next.getValue().equals(handler)) {
                iterator.remove();
                messageHandlerCache = null;
            }
        }
    }

    /**
     * Get all successfully registered {@link MessageHandler}s.
     *
     * @return unmodifiable {@link Set} of registered {@link MessageHandler}s.
     */
    public Set<MessageHandler> getMessageHandlers() {
        if (messageHandlerCache == null) {
            final HashSet<MessageHandler> messageHandlers = new HashSet<MessageHandler>();

            messageHandlers.addAll(basicHandlers.values());
            messageHandlers.addAll(asyncHandlers.values());

            messageHandlerCache = Collections.unmodifiableSet(messageHandlers);
        }

        return messageHandlerCache;
    }

    private Class<?> getHandlerType(MessageHandler handler) {
        Class<?> root;
        if (handler instanceof AsyncMessageHandler) {
            return ((AsyncMessageHandler) handler).getType();
        } else if (handler instanceof BasicMessageHandler) {
            return ((BasicMessageHandler) handler).getType();
        } else if (handler instanceof MessageHandler.Async) {
            root = MessageHandler.Async.class;
        } else if (handler instanceof MessageHandler.Basic) {
            root = MessageHandler.Basic.class;
        } else {
            throw new IllegalArgumentException(handler.getClass().getName()); // should never happen
        }
        Class<?> result = ReflectionHelper.getClassType(handler.getClass(), root);
        return result == null ? Object.class : result;
    }

}
