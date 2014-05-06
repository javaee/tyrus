/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2014 Oracle and/or its affiliates. All rights reserved.
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

import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.websocket.Decoder;
import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;

import org.glassfish.tyrus.core.coder.CoderWrapper;
import org.glassfish.tyrus.core.l10n.LocalizationMessages;

/**
 * Manages registered {@link MessageHandler}s and checks whether the new ones may be registered.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 * @see MessageHandler
 * @see javax.websocket.OnMessage
 */
class MessageHandlerManager {

    private static final List<Class<?>> WHOLE_TEXT_HANDLER_TYPES = Arrays.<Class<?>>asList(String.class, Reader.class);
    private static final Class<?> PARTIAL_TEXT_HANDLER_TYPE = String.class;
    private static final List<Class<?>> WHOLE_BINARY_HANDLER_TYPES = Arrays.<Class<?>>asList(ByteBuffer.class, InputStream.class, byte[].class);
    private static final List<Class<?>> PARTIAL_BINARY_HANDLER_TYPES = Arrays.<Class<?>>asList(ByteBuffer.class, byte[].class);
    private static final Class<?> PONG_HANDLER_TYPE = PongMessage.class;

    private boolean textHandlerPresent = false;
    private boolean textWholeHandlerPresent = false;
    private boolean binaryHandlerPresent = false;
    private boolean binaryWholeHandlerPresent = false;
    private boolean pongHandlerPresent = false;
    private boolean readerHandlerPresent = false;
    private boolean inputStreamHandlerPresent = false;
    private final Map<Class<?>, MessageHandler> registeredHandlers = new HashMap<Class<?>, MessageHandler>();
    private final List<Class<? extends Decoder>> decoders;

    private Set<MessageHandler> messageHandlerCache;

    /**
     * Construct manager with no decoders.
     */
    MessageHandlerManager() {
        this(Collections.<Class<? extends Decoder>>emptyList());
    }

    /**
     * Construct manager.
     *
     * @param decoders registered {@link Decoder}s.
     */
    MessageHandlerManager(List<Class<? extends Decoder>> decoders) {
        this.decoders = decoders;
    }

    /**
     * Construct manager.
     *
     * @param decoders registered {@link Decoder}s.
     */
    static MessageHandlerManager fromDecoderInstances(List<Decoder> decoders) {
        List<Class<? extends Decoder>> decoderList = new ArrayList<Class<? extends Decoder>>();
        for (Decoder decoder : decoders) {
            if (decoder instanceof CoderWrapper) {
                decoderList.add(((CoderWrapper<? extends Decoder>) decoder).getCoderClass());
            } else {
                decoderList.add(decoder.getClass());
            }
        }

        return new MessageHandlerManager(decoderList);
    }

    /**
     * Add {@link MessageHandler} to the manager.
     *
     * @param handler {@link MessageHandler} to be added to the manager.
     */
    public void addMessageHandler(MessageHandler handler) throws IllegalStateException {

        if (!(handler instanceof MessageHandler.Whole) && !(handler instanceof MessageHandler.Partial)) {
            throwException(LocalizationMessages.MESSAGE_HANDLER_WHOLE_OR_PARTIAL());
        }

        final Class<?> handlerClass = getHandlerType(handler);

        if (handler instanceof MessageHandler.Whole) { //WHOLE MESSAGE HANDLER
            if (WHOLE_TEXT_HANDLER_TYPES.contains(handlerClass)) { // text
                if (textHandlerPresent) {
                    throwException(LocalizationMessages.MESSAGE_HANDLER_ALREADY_REGISTERED_TEXT());
                } else {
                    if (Reader.class.isAssignableFrom(handlerClass)) {
                        readerHandlerPresent = true;
                    }
                    textHandlerPresent = true;
                    textWholeHandlerPresent = true;
                }
            } else if (WHOLE_BINARY_HANDLER_TYPES.contains(handlerClass)) { // binary
                if (binaryHandlerPresent) {
                    throwException(LocalizationMessages.MESSAGE_HANDLER_ALREADY_REGISTERED_BINARY());
                } else {
                    if (InputStream.class.isAssignableFrom(handlerClass)) {
                        inputStreamHandlerPresent = true;
                    }
                    binaryHandlerPresent = true;
                    binaryWholeHandlerPresent = true;
                }
            } else if (PONG_HANDLER_TYPE == handlerClass) { // pong
                if (pongHandlerPresent) {
                    throwException(LocalizationMessages.MESSAGE_HANDLER_ALREADY_REGISTERED_PONG());
                } else {
                    pongHandlerPresent = true;
                }
            } else {
                boolean viable = false;

                if (checkTextDecoders(handlerClass)) {//decodable text
                    if (textHandlerPresent) {
                        throwException(LocalizationMessages.MESSAGE_HANDLER_ALREADY_REGISTERED_TEXT());
                    } else {
                        textHandlerPresent = true;
                        textWholeHandlerPresent = true;
                        viable = true;
                    }
                }

                if (checkBinaryDecoders(handlerClass)) {//decodable binary
                    if (binaryHandlerPresent) {
                        throwException(LocalizationMessages.MESSAGE_HANDLER_ALREADY_REGISTERED_BINARY());
                    } else {
                        binaryHandlerPresent = true;
                        binaryWholeHandlerPresent = true;
                        viable = true;
                    }
                }

                if (!viable) {
                    throwException(LocalizationMessages.MESSAGE_HANDLER_ALREADY_REGISTERED_TYPE(handlerClass));
                }
            }
        } else { // PARTIAL MESSAGE HANDLER
            boolean viable = false;

            if (PARTIAL_TEXT_HANDLER_TYPE.equals(handlerClass)) { // text
                if (textHandlerPresent) {
                    throwException(LocalizationMessages.MESSAGE_HANDLER_ALREADY_REGISTERED_TEXT());
                } else {
                    textHandlerPresent = true;
                    viable = true;
                }
            }

            if (PARTIAL_BINARY_HANDLER_TYPES.contains(handlerClass)) { // binary
                if (binaryHandlerPresent) {
                    throwException(LocalizationMessages.MESSAGE_HANDLER_ALREADY_REGISTERED_BINARY());
                } else {
                    binaryHandlerPresent = true;
                    viable = true;
                }
            }

            if (!viable) {
                throwException(LocalizationMessages.MESSAGE_HANDLER_PARTIAL_INVALID_TYPE(handlerClass.getName()));
            }
        }

        // map of all registered handlers
        if (registeredHandlers.containsKey(handlerClass)) {
            throwException(LocalizationMessages.MESSAGE_HANDLER_ALREADY_REGISTERED_TYPE(handlerClass));
        } else {
            registeredHandlers.put(handlerClass, handler);
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
        boolean wasRegistered = false;
        Iterator<Map.Entry<Class<?>, MessageHandler>> iterator = registeredHandlers.entrySet().iterator();
        final Class<?> handlerClass = getHandlerType(handler);

        while (iterator.hasNext()) {
            final Map.Entry<Class<?>, MessageHandler> next = iterator.next();
            if (next.getValue().equals(handler)) {
                iterator.remove();
                messageHandlerCache = null;
                wasRegistered = true;
                break;
            }
        }

        if (!wasRegistered) {
            return;
        }

        if (handler instanceof MessageHandler.Whole) { //WHOLE MESSAGE HANDLER
            if (WHOLE_TEXT_HANDLER_TYPES.contains(handlerClass)) { // text
                textHandlerPresent = false;
                textWholeHandlerPresent = false;

            } else if (WHOLE_BINARY_HANDLER_TYPES.contains(handlerClass)) { // binary
                binaryHandlerPresent = false;
                binaryWholeHandlerPresent = false;

            } else if (PONG_HANDLER_TYPE == handlerClass) { // pong
                pongHandlerPresent = false;
            } else {
                if (checkTextDecoders(handlerClass)) {//decodable text
                    textHandlerPresent = false;
                    textWholeHandlerPresent = false;

                } else if (checkBinaryDecoders(handlerClass)) {//decodable binary
                    binaryHandlerPresent = false;
                    binaryWholeHandlerPresent = false;
                }
            }
        } else { // PARTIAL MESSAGE HANDLER
            if (PARTIAL_TEXT_HANDLER_TYPE.equals(handlerClass)) { // text
                textHandlerPresent = false;

            } else if (PARTIAL_BINARY_HANDLER_TYPES.contains(handlerClass)) { // binary
                binaryHandlerPresent = false;
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
            messageHandlerCache = Collections.unmodifiableSet(new HashSet<MessageHandler>(registeredHandlers.values()));
        }

        return messageHandlerCache;
    }

    static Class<?> getHandlerType(MessageHandler handler) {
        Class<?> root;
        if (handler instanceof AsyncMessageHandler) {
            return ((AsyncMessageHandler) handler).getType();
        } else if (handler instanceof BasicMessageHandler) {
            return ((BasicMessageHandler) handler).getType();
        } else if (handler instanceof MessageHandler.Partial) {
            root = MessageHandler.Partial.class;
        } else if (handler instanceof MessageHandler.Whole) {
            root = MessageHandler.Whole.class;
        } else {
            throw new IllegalArgumentException(LocalizationMessages.MESSAGE_HANDLER_ILLEGAL_ARGUMENT(handler));
        }
        Class<?> result = ReflectionHelper.getClassType(handler.getClass(), root);
        return result == null ? Object.class : result;
    }

    private boolean checkTextDecoders(Class<?> requiredType) {
        for (Class<? extends Decoder> decoderClass : decoders) {
            if (isTextDecoder(decoderClass) && requiredType.isAssignableFrom(AnnotatedEndpoint.getDecoderClassType(decoderClass))) {
                return true;
            }
        }

        return false;
    }

    private boolean checkBinaryDecoders(Class<?> requiredType) {
        for (Class<? extends Decoder> decoderClass : decoders) {
            if (isBinaryDecoder(decoderClass) && requiredType.isAssignableFrom(AnnotatedEndpoint.getDecoderClassType(decoderClass))) {
                return true;
            }
        }

        return false;
    }

    private boolean isTextDecoder(Class<? extends Decoder> decoderClass) {
        return Decoder.Text.class.isAssignableFrom(decoderClass) || Decoder.TextStream.class.isAssignableFrom(decoderClass);
    }

    private boolean isBinaryDecoder(Class<? extends Decoder> decoderClass) {
        return Decoder.Binary.class.isAssignableFrom(decoderClass) || Decoder.BinaryStream.class.isAssignableFrom(decoderClass);
    }

    boolean isWholeTextHandlerPresent() {
        return textWholeHandlerPresent;
    }

    boolean isWholeBinaryHandlerPresent() {
        return binaryWholeHandlerPresent;
    }

    boolean isPartialTextHandlerPresent() {
        return textHandlerPresent && !textWholeHandlerPresent;
    }

    boolean isPartialBinaryHandlerPresent() {
        return binaryHandlerPresent && !binaryWholeHandlerPresent;
    }

    public boolean isReaderHandlerPresent() {
        return readerHandlerPresent;
    }

    public boolean isInputStreamHandlerPresent() {
        return inputStreamHandlerPresent;
    }

    boolean isPongHandlerPresent() {
        return pongHandlerPresent;
    }
}
