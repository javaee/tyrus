/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.net.websocket.ClientContainer;
import javax.net.websocket.CloseReason;
import javax.net.websocket.DecodeException;
import javax.net.websocket.Decoder;
import javax.net.websocket.EncodeException;
import javax.net.websocket.Encoder;
import javax.net.websocket.Endpoint;
import javax.net.websocket.EndpointConfiguration;
import javax.net.websocket.MessageHandler;
import javax.net.websocket.RemoteEndpoint;
import javax.net.websocket.ServerEndpointConfiguration;
import javax.net.websocket.Session;
import javax.net.websocket.annotations.WebSocketPathParam;

import org.glassfish.tyrus.internal.PathPattern;
import org.glassfish.tyrus.spi.SPIEndpoint;
import org.glassfish.tyrus.spi.SPIHandshakeRequest;

/**
 * Wraps the registered application class.
 * There is one {@link EndpointWrapper} for each application class, which handles all the methods.
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class EndpointWrapper extends SPIEndpoint {
    /**
     * The container for this session.
     */
    private ClientContainer container;

    /**
     * Server configuration.
     */
    private EndpointConfiguration configuration;

    /**
     * Path relative to the servlet context path
     */
    private final String path;

    /**
     * Map filled with template variables (if present).
     * <p/>
     * Keys represent names, values are corresponding values.
     */
    private final Map<String, String> templateValues = new HashMap<String, String>();

    /**
     * Model representing the annotated bean class.
     */
    private Model model;

    /**
     * Remote endpoints for the {@link Endpoint} represented by this wrapper.
     */
    private ConcurrentHashMap<RemoteEndpoint, RemoteEndpointWrapper> wrappers
            = new ConcurrentHashMap<RemoteEndpoint, RemoteEndpointWrapper>();

    /**
     * The corresponding {@link Endpoint} was created from annotated class / instance.
     */
    private boolean annotated;

    /**
     * Creates new endpoint wrapper.
     *
     * @param path          address of this endpoint as annotated by {@link javax.net.websocket.annotations.WebSocketEndpoint} annotation.
     * @param model         model of the application class.
     * @param configuration endpoint configuration.
     * @param container     TODO.
     */
    public EndpointWrapper(String path, Model model, EndpointConfiguration configuration, ClientContainer container) {
        this.path = path;
        this.model = model;
        this.configuration = configuration;
        this.annotated = model.wasAnnotated();
        this.container = container;
    }

    ClientContainer getContainer() {
        return this.container;
    }

    private Object decodeMessage(Object message, Class<?> type, boolean isString) throws DecodeException {
        for (Decoder dec : configuration.getDecoders()) {
            try {
                if (isString && (dec instanceof Decoder.Text)) {
                    Method m = dec.getClass().getDeclaredMethod("decode", String.class);
                    if (type != null && type.isAssignableFrom(m.getReturnType())) {
                        if (((Decoder.Text) dec).willDecode((String) message)) {
                            return ((Decoder.Text) dec).decode((String) message);
                        }
                    }
                } else if (!isString && (dec instanceof Decoder.Binary)) {
                    Method m = dec.getClass().getDeclaredMethod("decode", ByteBuffer.class);
                    if (type != null && type.equals(m.getReturnType())) {
                        if (((Decoder.Binary) dec).willDecode((ByteBuffer) message)) {
                            return ((Decoder.Binary) dec).decode((ByteBuffer) message);
                        }
                    }
                }
            } catch (DecodeException de) {
                de.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    String doEncode(Object o) throws EncodeException {
        for (Encoder enc : configuration.getEncoders()) {
            List interfaces = Arrays.asList(enc.getClass().getInterfaces());
            if (interfaces.contains(Encoder.Text.class)) {
                try {
                    Method m = enc.getClass().getMethod("encode", o.getClass());
                    if (m != null) {
                        return ((Encoder.Text) enc).encode(o);
                    }
                } catch (java.lang.NoSuchMethodException nsme) {
                    // just continue looping
                } catch (EncodeException ee) {
                    ee.printStackTrace();
                    throw ee;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        throw new EncodeException("Unable to encode ", o);
    }

    public String getPath() {
        return this.path;
    }

    @Override
    public boolean checkHandshake(SPIHandshakeRequest hr) {
        ServerEndpointConfiguration sec;

        if (configuration instanceof ServerEndpointConfiguration) {
            sec = (ServerEndpointConfiguration) configuration;
        } else {
            return false;
        }

        final PathPattern pathPattern = new PathPattern(path);

        final boolean match = pathPattern.match(hr.getRequestURI(), pathPattern.getTemplate().getTemplateVariables(), templateValues);
        return match && sec.checkOrigin(hr.getHeader("Origin"));
    }

    @Override
    public List<String> getNegotiatedExtensions(List<String> clientExtensions) {
        ServerEndpointConfiguration sec;

        if (configuration instanceof ServerEndpointConfiguration) {
            sec = (ServerEndpointConfiguration) configuration;
        } else {
            return null;
        }

        return sec.getNegotiatedExtensions(clientExtensions);
    }

    @Override
    public String getNegotiatedProtocol(List<String> clientProtocols) {
        ServerEndpointConfiguration sec;

        if (configuration instanceof ServerEndpointConfiguration) {
            sec = (ServerEndpointConfiguration) configuration;
        } else {
            return null;
        }

        return sec.getNegotiatedSubprotocol(clientProtocols);
    }

    @Override
    public void remove() {

    }

    @Override
    public void onConnect(RemoteEndpoint gs) {
        RemoteEndpointWrapper peer = getPeer(gs);
//        peer.setAddress(gs.getUri()); TODO set the correct address!
        peer.setAddress(null);
        this.onGeneratedBeanConnect(peer);
    }

    @Override
    public void onMessage(RemoteEndpoint gs, ByteBuffer messageBytes) {
        RemoteEndpointWrapper peer = getPeer(gs);
        peer.updateLastConnectionActivity();
        processCompleteMessage(gs, messageBytes, false);
    }

    @Override
    public void onMessage(RemoteEndpoint gs, String messageString) {
        RemoteEndpointWrapper peer = getPeer(gs);
        peer.updateLastConnectionActivity();
        processCompleteMessage(gs, messageString, true);
    }

    /*
     * Initial implementation policy:
     * - if there is a streaming message handler invoke it
     * - if there is a blocking handler, use an adapter to invoke it
     */
    @Override
    @SuppressWarnings("unchecked")
    public void onPartialMessage(RemoteEndpoint gs, String partialString, boolean last) {
        boolean handled = false;
        RemoteEndpointWrapper peer = getPeer(gs);
        for (MessageHandler handler : (Set<MessageHandler>) ((SessionImpl) peer.getSession()).getInvokableMessageHandlers()) {
            MessageHandler.AsyncText baseHandler;
            if (handler instanceof MessageHandler.AsyncText) {
                baseHandler = (MessageHandler.AsyncText) handler;
                baseHandler.onMessagePart(partialString, last);
                if (last) {
                    handled = true;
                }
            }
        }
        if (last && !handled) {
            System.out.println("Unhandled text message in EndpointWrapper");
        }
    }

    @Override
    public void onPartialMessage(RemoteEndpoint gs, ByteBuffer partialBytes, boolean last) {
        RemoteEndpointWrapper peer = getPeer(gs);
        //System.out.println("EndpointWrapper----" + ((SessionImpl) peer.getSession()).getInvokableMessageHandlers());
        boolean handled = false;
        for (MessageHandler handler : (Set<MessageHandler>) ((SessionImpl) peer.getSession()).getInvokableMessageHandlers()) {
            if (handler instanceof MessageHandler.AsyncBinary) {
                //System.out.println("async binary");
                ((MessageHandler.AsyncBinary) handler).onMessagePart(partialBytes, last);
                handled = true;
            }
        }
        if (!handled) {
            System.out.println("Unhandled partial binary message in EndpointWrapper");
        }

    }


    @Override
    public void onPong(RemoteEndpoint gs, ByteBuffer bytes) {
        RemoteEndpointWrapper peer = getPeer(gs);
        //System.out.println("EndpointWrapper----" + ((SessionImpl) peer.getSession()).getInvokableMessageHandlers());
        boolean handled = false;
        for (MessageHandler handler : (Set<MessageHandler>) (peer.getSession()).getMessageHandlers()) {
            if (handler instanceof MessageHandler.Pong) {
                //System.out.println("async binary");
                ((MessageHandler.Pong) handler).onPong(bytes);
                handled = true;
            }
        }
        if (!handled) {
            System.out.println("Unhandled pong message in EndpointWrapper");
        }

    }

    // the endpoint needs to respond as soon as possible (see the websocket RFC)
    // no involvement from application layer, there is no ping listener
    public void onPing(RemoteEndpoint gs, ByteBuffer bytes) {
        RemoteEndpointWrapper peer = getPeer(gs);
        peer.sendPong(bytes);
    }

    /**
     * Processes just messages that come in one part, i.e. not streamed messages.
     *
     * @param gs       message sender.
     * @param o        message.
     * @param isString String / byte[] message.
     */
    @SuppressWarnings("unchecked")
    private void processCompleteMessage(RemoteEndpoint gs, Object o, boolean isString) {
        RemoteEndpointWrapper peer = getPeer(gs);
        boolean decoded = false;

        for (MessageHandler handler : (Set<MessageHandler>) peer.getSession().getMessageHandlers()) {
            if (isString) {
                if (handler instanceof MessageHandler.Text) {
                    ((MessageHandler.Text) handler).onMessage((String) o);
                    decoded = true;
                }
            } else {
                if (handler instanceof MessageHandler.Binary) {
                    ((MessageHandler.Binary) handler).onMessage((ByteBuffer) o);
                    decoded = true;
                }
            }
        }

        try {
            for (Method method : model.getOnMessageMethods()) {
                Class<?>[] paramTypes = method.getParameterTypes();
                Class<?> type = null;

                for (Class<?> methodParamType : paramTypes) {
                    if (!methodParamType.equals(Session.class)) {
                        type = (PrimitivesToBoxing.getBoxing(methodParamType) == null) ? methodParamType : PrimitivesToBoxing.getBoxing(methodParamType);
                        break;
                    }
                }

                Object decodedMessageObject = this.decodeMessage(o, type, isString);

                if (decodedMessageObject != null) {
                    Object returned = invokeMethod(decodedMessageObject, method, peer);
                    if (returned != null) {
                        if (o instanceof String) {
                            String messageToSendAsString = this.doEncode(returned);
                            peer.sendString(messageToSendAsString);
                        } else if (returned instanceof byte[]) {
                            peer.sendBytes((ByteBuffer) returned);
                        }
                    }
                    decoded = true;

                }
            }

            if (!decoded) {
                throw new Exception("Couldn't decode");
            }

        } catch (IOException ioe) {
            this.handleGeneratedBeanException(peer, ioe);
        } catch (DecodeException de) {
            this.handleGeneratedBeanException(peer, de);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException("Error invoking message decoding");
        }
    }

    private Object invokeMethod(Object object, Method method, RemoteEndpointWrapper peer) throws Exception {
        Class<?>[] paramTypes = method.getParameterTypes();

        Object param0 = model.getBean();

        if (!parameterBelongsToMethod(method, object)) {
            return null;
        }

        final Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        final Object[] params = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            final Class<?> paramType = paramTypes[i];

            final WebSocketPathParam webSocketParam = getWebSocketParam(parameterAnnotations[i]);
            // WebSocketPathParam not present
            if (webSocketParam == null) {
                if (paramType.equals(Session.class)) {
                    params[i] = peer.getSession();
                } else if (paramType.equals(object.getClass()) ||
                        PrimitivesToBoxing.getBoxing(paramType).equals(object.getClass())) {
                    params[i] = object;
                } else {
                    Logger.getLogger(EndpointWrapper.class.getName()).warning("Cannot inject parameter. Method: " + method +
                            " Parameter index: " + i + " type: " + paramType + ".");
                }
            } else {
                // we are supporting only String with WebSocketPathParam annotation
                if (paramType.equals(String.class)) {
                    params[i] = this.templateValues.get(webSocketParam.value());
                } else {
                    Logger.getLogger(EndpointWrapper.class.getName()).warning("Cannot inject @WebSocketPathParam " +
                            paramType + ".");
                }
            }

        }

        return method.invoke(param0, params);
    }

    private WebSocketPathParam getWebSocketParam(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().equals(WebSocketPathParam.class)) {
                return (WebSocketPathParam) annotation;
            }
        }

        return null;
    }

    private boolean parameterBelongsToMethod(Method method, Object parameter) {
        Class<?>[] paramTypes = method.getParameterTypes();

        for (Class<?> paramType : paramTypes) {
            if (PrimitivesToBoxing.getBoxing(paramType).equals(parameter.getClass())) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void onClose(RemoteEndpoint gs) {
        RemoteEndpointWrapper wsw = getPeer(gs);
        this.onGeneratedBeanClose(wsw);
        wrappers.remove(gs);
    }

    public void handleGeneratedBeanException(RemoteEndpoint peer, Exception e) {
        e.printStackTrace();
        throw new RuntimeException("Error handling not supported yet.");
    }

    public void onGeneratedBeanConnect(RemoteEndpointWrapper peer) {
        if (annotated) {

            for (Method m : model.getOnOpenMethods()) {
                try {
                    m.invoke(model.getBean(), peer.getSession());
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException("Error invoking it.");
                }
            }

        } else if (model.getBean() instanceof Endpoint) {
            ((Endpoint) model.getBean()).onOpen(peer.getSession());
        } else {
            try {
                throw new Exception("onConnect could not be invoked.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void onGeneratedBeanClose(RemoteEndpointWrapper peer) {
        if (annotated) {
            for (Method m : model.getOnCloseMethods()) {
                try {
                    m.invoke(model.getBean(), peer.getSession());
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException("Error invoking it.");
                }
            }
        } else if (model.getBean() instanceof Endpoint) {
            ((Endpoint) model.getBean()).onClose(peer.getSession(), new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "Normal Closure."));
        } else {
            try {
                throw new Exception("onClose could not be invoked.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected final RemoteEndpointWrapper getPeer(RemoteEndpoint gs) {
        RemoteEndpointWrapper result = wrappers.get(gs);

        if (result == null) {
            result = RemoteEndpointWrapper.getRemoteWrapper(gs, this);
        }

        wrappers.put(gs, result);
        return result;
    }
}
