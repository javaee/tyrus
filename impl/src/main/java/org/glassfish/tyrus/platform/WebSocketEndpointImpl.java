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

package org.glassfish.tyrus.platform;

import org.glassfish.tyrus.platform.utils.PrimitivesToBoxing;
import org.glassfish.tyrus.spi.SPIEndpoint;
import org.glassfish.tyrus.spi.SPIHandshakeRequest;

import javax.net.websocket.DecodeException;
import javax.net.websocket.Decoder;
import javax.net.websocket.EncodeException;
import javax.net.websocket.Encoder;
import javax.net.websocket.RemoteEndpoint;
import javax.net.websocket.ServerContainer;
import javax.net.websocket.Session;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the registered application class.
 * There is one WebSocketEndpoint for each application class, which handles all the methods (even for various dynamic paths).
 *
 * @author dannycoward
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class WebSocketEndpointImpl extends SPIEndpoint {

    /**
     * Path relative to the servlet context path
     */
    private final String path;

    /**
     * Message decoders (user provided and SDK provided).
     */
    private Set<Class<?>> decoders = Collections.newSetFromMap(new ConcurrentHashMap<Class<?>, Boolean>());

    /**
     * Message decoders (user provided and SDK provided).
     */
    private Set<Class<?>> encoders = Collections.newSetFromMap(new ConcurrentHashMap<Class<?>, Boolean>());

    /**
     * Server / client endpoint.
     */
    private boolean server = true;

    /**
     * Model representing the annotated bean class.
     */
    private Model model;

    private ServerContainer containerContext;

    /**
     * Creates new endpoint.
     *
     * @param containerContext container context.
     * @param path             address of this endpoint as annotated by {@link javax.net.websocket.annotations.WebSocketEndpoint} annotation.
     * @param model            model of the application class.
     */
    public WebSocketEndpointImpl(ServerContainer containerContext, String path, Model model) {
//        this.remoteInterface = model.getRemoteInterface();
        this.containerContext = containerContext;

//        if (!model.getRemoteInterface().isInterface()) {
//            throw new IllegalArgumentException(remoteInterface + " is not an interface");
//        }

        this.path = path;
        this.model = model;
        this.init(model.getEncoders(), model.getDecoders());
    }

    /**
     * Creates new endpoint - see above.
     *
     * @param containerContext container context.
     * @param path             address of this endpoint as annotated by {@link javax.net.websocket.annotations.WebSocketEndpoint} annotation.
     * @param model            model of the application class.
     * @param server           server / client endpoint.
     */
    public WebSocketEndpointImpl(ServerContainerImpl containerContext, String path, Model model, Boolean server) {
        this(containerContext, path, model);
        this.server = server;
    }

    private void init(Set<Class<?>> encodersToInit, Set<Class<?>> decodersToInit) {
//        if (model.getContextField() != null) {
//            try {
//                if (!model.getContextField().isAccessible()) {
//                    model.getContextField().setAccessible(true);
//                }
//                this.model.getContextField().set(model.getBean(), this.getEndpointContext());
//            } catch (Exception e) {
//                throw new RuntimeException("Oops, error setting context");
//            }
//        }
        this.initDecoders(decodersToInit);
        this.initEncoders(encodersToInit);
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

    private void initEncoders(Set<Class<?>> encodersToInit) {
        encoders.addAll(encodersToInit);
        encoders.add(org.glassfish.tyrus.platform.encoders.StringEncoderNoOp.class);
        encoders.add(org.glassfish.tyrus.platform.encoders.BinaryEncoderNoOp.class);
        encoders.add(org.glassfish.tyrus.platform.encoders.BooleanEncoder.class);
        encoders.add(org.glassfish.tyrus.platform.encoders.ByteEncoder.class);
        encoders.add(org.glassfish.tyrus.platform.encoders.CharEncoder.class);
        encoders.add(org.glassfish.tyrus.platform.encoders.DoubleEncoder.class);
        encoders.add(org.glassfish.tyrus.platform.encoders.FloatEncoder.class);
        encoders.add(org.glassfish.tyrus.platform.encoders.IntegerEncoder.class);
        encoders.add(org.glassfish.tyrus.platform.encoders.LongEncoder.class);
        encoders.add(org.glassfish.tyrus.platform.encoders.ShortEncoder.class);

    }

    private void initDecoders(Set<Class<?>> decodersToInit) {
        decoders.addAll(decodersToInit);
        decoders.add(org.glassfish.tyrus.platform.decoders.StringDecoderNoOp.class);
        decoders.add(org.glassfish.tyrus.platform.decoders.BinaryDecoderNoOp.class);
        decoders.add(org.glassfish.tyrus.platform.decoders.BooleanDecoder.class);
        decoders.add(org.glassfish.tyrus.platform.decoders.ByteDecoder.class);
        decoders.add(org.glassfish.tyrus.platform.decoders.IntegerDecoder.class);
        decoders.add(org.glassfish.tyrus.platform.decoders.LongDecoder.class);
        decoders.add(org.glassfish.tyrus.platform.decoders.ShortDecoder.class);
        decoders.add(org.glassfish.tyrus.platform.decoders.FloatDecoder.class);
        decoders.add(org.glassfish.tyrus.platform.decoders.DoubleDecoder.class);
        decoders.add(org.glassfish.tyrus.platform.decoders.CharDecoder.class);
    }

    private Object decodeMessage(Object message, Class<?>[] methodParamTypes, boolean isString) throws DecodeException {
        Class<?> type = null;

        for (Class<?> methodParamType : methodParamTypes) {
            if (!methodParamType.equals(Session.class)) {
                type = (PrimitivesToBoxing.getBoxing(methodParamType) == null) ? methodParamType : PrimitivesToBoxing.getBoxing(methodParamType);
                break;
            }
        }

        for (Class dec : decoders) {
            try {
                List interfaces = Arrays.asList(dec.getInterfaces());

                if (isString && interfaces.contains(Decoder.Text.class)) {
                    Method m = dec.getDeclaredMethod("decode", String.class);
                    if (type != null && type.equals(m.getReturnType())) {
                        Decoder.Text decoder = (Decoder.Text) dec.newInstance();
                        if (decoder.willDecode((String) message)) {
                            return decoder.decode((String) message);
                        }
                    }
                } else if (!isString && interfaces.contains(Decoder.Binary.class)) {
                    Method m = dec.getDeclaredMethod("decode", byte[].class);
                    if (type != null && type.equals(m.getReturnType())) {
                        Decoder.Binary decoder = (Decoder.Binary) dec.newInstance();
                        if (decoder.willDecode((byte[]) message)) {
                            return decoder.decode((byte[]) message);
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
        for (Object next : this.encoders) {
            Class nextClass = (Class) next;
            List interfaces = Arrays.asList(nextClass.getInterfaces());
            if (interfaces.contains(Encoder.Text.class)) {
                try {
                    Method m = nextClass.getMethod("encode", o.getClass());
                    if (m != null) {
                        Encoder.Text te = (Encoder.Text) nextClass.newInstance();
                        return te.encode(o);
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

    String getPath() {
        return this.path;
    }

    @Override
    public boolean checkHandshake(SPIHandshakeRequest hr) {
        return hr.getRequestURI().startsWith(path);
    }

    @Override
    public List<String> getSupportedProtocols(List<String> subProtocol) {
        List<String> supported = new ArrayList<String>();
        for (String nextRequestedProtocolName : subProtocol) {
            if (model.getSubprotocols().contains(nextRequestedProtocolName)) {
                supported.add(nextRequestedProtocolName);
            }
        }
        return supported;
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
    public void onMessage(RemoteEndpoint gs, byte[] messageBytes) {
        processMessage(gs, messageBytes, false);
    }

    @Override
    public void onMessage(RemoteEndpoint gs, String messageString) {
        processMessage(gs, messageString, true);
    }

    private void processMessage(RemoteEndpoint gs, Object o, boolean isString) {
        RemoteEndpointWrapper peer = getPeer(gs);
        peer.updateLastConnectionActivity();

        try {

            for (Method m : model.getOnMessageMethods()) {

                Class<?>[] paramTypes = m.getParameterTypes();
                Object decodedMessageObject = this.decodeMessage(o, paramTypes, isString);

                if (decodedMessageObject != null) {
                    Object returned = invokeMethod(decodedMessageObject, m, peer);

                    if (returned != null) {
                        if (o instanceof String) {
                            String messageToSendAsString = this.doEncode(returned);
                            peer.sendString(messageToSendAsString);
                        } else if (returned instanceof byte[]) {
                            peer.sendBytes((byte[]) returned);
                        }
                    }
                return;
                }
            }

            throw new DecodeException();
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
        wsw.discard();
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
        for (Method m : model.getOnOpenMethods()) {
            try {
                m.invoke(model.getBean(), peer.getSession());
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Error invoking it.");
            }
        }
    }

    public void onGeneratedBeanClose(RemoteEndpointWrapper peer) {
        for (Method m : model.getOnCloseMethods()) {
            try {
                m.invoke(model.getBean(), peer.getSession());
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Error invoking it.");
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected final RemoteEndpointWrapper getPeer(RemoteEndpoint gs) {
        return RemoteEndpointWrapper.getRemoteWrapper(gs, this, server);
    }
}
