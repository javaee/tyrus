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

import org.glassfish.tyrus.spi.SPIEndpoint;
import org.glassfish.tyrus.spi.SPIHandshakeRequest;

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

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wrapps the registered application class.
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
     * @param path  address of this endpoint as annotated by {@link javax.net.websocket.annotations.WebSocketEndpoint} annotation.
     * @param model model of the application class.
     */
    public EndpointWrapper(String path, Model model, EndpointConfiguration configuration, ClientContainer container) {
        this.path = path;
        this.model = model;
        this.configuration = configuration;
        this.annotated = model.wasAnnotated();
        this.container = container;
    }

    /**
     * Checks whether the provided dynamicPath matches with this endpoint, i.e. if there is a method that can process the request.
     *
     * @param remoteUri   path to be checked.
     * @param dynamicPath taken from the {@link javax.net.websocket.annotations.WebSocketMessage} method annotation.
     * @return {@code true} if the paths match, {@code false} otherwise.
     */
    protected boolean doesPathMatch(String remoteUri, String dynamicPath) {
        if (dynamicPath.equals("*")) {
            return true;
        } else if ((path + dynamicPath).equals(remoteUri)) {
            return true;
        }
        return false;
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

        ServerEndpointConfiguration sep;
        if(configuration instanceof ServerEndpointConfiguration){
            sep = (ServerEndpointConfiguration) configuration;
        }else{
            return false;
        }

        return hr.getRequestURI().matches(path) && sep.checkOrigin(hr.getHeader("Origin"));
    }

    @Override
    public void remove() {

    }

    @Override
    public EndpointConfiguration getConfiguration() {
        return configuration;
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
    public void onPartialMessage(RemoteEndpoint gs, String partialString, boolean last) {
        boolean handled = false;
        RemoteEndpointWrapper peer = getPeer(gs);
        for (MessageHandler handler : (Set<MessageHandler>) ((SessionImpl)peer.getSession()).getInvokableMessageHandlers()) {
            MessageHandler.AsyncText baseHandler = null;
            if (handler instanceof MessageHandler.AsyncText) {
                baseHandler = (MessageHandler.AsyncText) handler;
            }
            if (baseHandler != null) {
                baseHandler.onMessagePart(partialString, last);
                if (last) {
                    handled = true;
                }
            }
        }
        if (last && !handled) {
            System.out.println("Unhandled message in EndpointWrapper");
        }
    }




    /**
     * Processes just messages that come in one part, i.e. not streamed messages.
     *
     * @param gs       message sender.
     * @param o        message.
     * @param isString String / byte[] message.
     */
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
            for (Method m : model.getOnMessageMethods()) {
                Class<?>[] paramTypes = m.getParameterTypes();
                Class<?> type = null;

                for (Class<?> methodParamType : paramTypes) {
                    if (!methodParamType.equals(Session.class)) {
                        type = (PrimitivesToBoxing.getBoxing(methodParamType) == null) ? methodParamType : PrimitivesToBoxing.getBoxing(methodParamType);
                        break;
                    }
                }

                Object decodedMessageObject = this.decodeMessage(o, type, isString);

                if (decodedMessageObject != null) {
                    Object returned = invokeMethod(decodedMessageObject, m, peer);
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
        Object result;
        Class<?>[] paramTypes = method.getParameterTypes();
        int noOfParameters = paramTypes.length;
        Object param0 = model.getBean();
        Object param1, param2;

        if (!parameterBelongsToMethod(method, object)) {
            return null;
        }

        if (paramTypes[0].equals(object.getClass()) ||
                PrimitivesToBoxing.getBoxing(paramTypes[0]).equals(object.getClass())) {
            param1 = object;
            param2 = peer.getSession();
        } else {
            param1 = peer.getSession();
            param2 = object;
        }

        switch (noOfParameters) {
            case 1:
                result = method.invoke(param0, param1);
                break;
            case 2:
                result = method.invoke(param0, param1, param2);
                break;
            default:
                throw new RuntimeException("can't deal with " + noOfParameters + " parameters.");
        }

        return result;
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
//        for (Method m : model.getOnErrorMethods()) {
//            try {
//                System.out.println("Error replying to client " + e.getMessage());
//                e.printStackTrace();
//                m.invoke(model.getBean(), e, peer);
//            } catch (Exception ex) {
//                ex.printStackTrace();
//                throw new RuntimeException("Error invoking it.");
//            }
//        }
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
        }else{
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
        }else{
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
