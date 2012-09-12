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

package org.glassfish.websocket.platform;

/**
 * @author dannycoward
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */


import org.glassfish.websocket.platform.utils.PrimitivesToBoxing;
import org.glassfish.websocket.spi.SPIEndpoint;
import org.glassfish.websocket.spi.SPIHandshakeRequest;
import org.glassfish.websocket.spi.SPIRemoteEndpoint;

import javax.net.websocket.DecodeException;
import javax.net.websocket.Decoder;
import javax.net.websocket.EncodeException;
import javax.net.websocket.Encoder;
import javax.net.websocket.RemoteEndpoint;
import javax.net.websocket.ServerContainer;
import javax.net.websocket.annotations.WebSocketMessage;
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
public class WebSocketEndpointImpl implements SPIEndpoint {

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

//    /**
//     * Remote Interface (represents the other endpoint of the communication) Class.
//     */
//    private final Class remoteInterface;

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
        encoders.add(org.glassfish.websocket.platform.encoders.StringEncoderNoOp.class);
        encoders.add(org.glassfish.websocket.platform.encoders.BooleanEncoder.class);
        encoders.add(org.glassfish.websocket.platform.encoders.ByteEncoder.class);
        encoders.add(org.glassfish.websocket.platform.encoders.CharEncoder.class);
        encoders.add(org.glassfish.websocket.platform.encoders.DoubleEncoder.class);
        encoders.add(org.glassfish.websocket.platform.encoders.FloatEncoder.class);
        encoders.add(org.glassfish.websocket.platform.encoders.IntEncoder.class);
        encoders.add(org.glassfish.websocket.platform.encoders.LongEncoder.class);
        encoders.add(org.glassfish.websocket.platform.encoders.ShortEncoder.class);

    }

    private void initDecoders(Set<Class<?>> decodersToInit) {
        decoders.addAll(decodersToInit);
        decoders.add(org.glassfish.websocket.platform.decoders.StringDecoderNoOp.class);
        decoders.add(org.glassfish.websocket.platform.decoders.BooleanDecoder.class);
        decoders.add(org.glassfish.websocket.platform.decoders.IntegerDecoder.class);
        decoders.add(org.glassfish.websocket.platform.decoders.LongDecoder.class);
        decoders.add(org.glassfish.websocket.platform.decoders.ShortDecoder.class);
        decoders.add(org.glassfish.websocket.platform.decoders.FloatDecoder.class);
        decoders.add(org.glassfish.websocket.platform.decoders.DoubleDecoder.class);
        decoders.add(org.glassfish.websocket.platform.decoders.CharDecoder.class);
    }

    public static String getClassTypeForTypeThatMightBePrimitive(String possiblyPrimitiveType) {
        String type = possiblyPrimitiveType;
        if (possiblyPrimitiveType.equals("boolean")) {
            type = "java.lang.Boolean";
        } else if (possiblyPrimitiveType.equals("char")) {
            type = "java.lang.Character";
        } else if (possiblyPrimitiveType.equals("double")) {
            type = "java.lang.Double";
        } else if (possiblyPrimitiveType.equals("float")) {
            type = "java.lang.Float";
        } else if (possiblyPrimitiveType.equals("int")) {
            type = "java.lang.Integer";
        } else if (possiblyPrimitiveType.equals("long")) {
            type = "java.lang.Long";
        } else if (possiblyPrimitiveType.equals("short")) {
            type = "java.lang.Short";
        }
        return type;
    }

    private Object doDecode(String message, String possiblyPrimitiveType) throws DecodeException {
        String type = getClassTypeForTypeThatMightBePrimitive(possiblyPrimitiveType);
        for (Object next : decoders) {
            Class nextClass = (Class) next;
            List interfaces = Arrays.asList(nextClass.getInterfaces());

            if (interfaces.contains(Decoder.Text.class)) {
                try {
                    Method m = nextClass.getDeclaredMethod("decode", String.class);
                    Class returnC = m.getReturnType();
                    ClassLoader cl;

//                    if (server) {
//                        cl = ((ServerContainerImpl) endpointContext.getContainerContext()).getApplicationLevelClassLoader();
//                    } else {
                        cl = nextClass.getClassLoader();
//                    }

                    Class proposedType = cl.loadClass(type);

                    if (proposedType.equals(returnC)) {
                        Decoder.Text decoder = (Decoder.Text) nextClass.newInstance();
                        boolean willItDecode = decoder.willDecode(message);

                        if (willItDecode) {
                            return decoder.decode(message);
                        }
                    }

                } catch (DecodeException de) {
                    de.printStackTrace();
                    throw de;
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
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

        throw new EncodeException("Unable to encode ",o);
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
    public void onConnect(SPIRemoteEndpoint gs) {
        WebSocketWrapper peer = getPeer(gs);
        WebSocketWrapper wsw = WebSocketWrapper.getWebSocketWrapper(peer);
        wsw.setAddress(gs.getUri());
        if (server) {
            this.onGeneratedBeanConnect(wsw);
        } else {
            this.onGeneratedBeanConnect(peer);
        }
    }

    @Override
    public void onMessage(SPIRemoteEndpoint gs, byte[] messageBytes) {
        processMessage(gs, messageBytes);
    }

    @Override
    public void onMessage(SPIRemoteEndpoint gs, String messageString) {
        processMessage(gs, messageString);
    }

    public void processMessage(SPIRemoteEndpoint gs, Object o) {
        WebSocketWrapper peer = getPeer(gs);
        for (Method m : model.getOnMessageMethods()) {
            try {
                Class<?>[] paramTypes = m.getParameterTypes();
                if (paramTypes[0].equals(byte[].class) && (o instanceof String)) {
                    continue;
                }

                WebSocketMessage wsm = m.getAnnotation(WebSocketMessage.class);
//                String dynamicPath = wsm.XdynamicPath();

//                if (!server || this.doesPathMatch(gs.getUri(), dynamicPath)) {
//                if (!server) {
                    int noOfParameters = m.getParameterTypes().length;

                    Object decodedMessageObject = o;

                    if (o instanceof String) {
                        decodedMessageObject = this.doDecode((String) o, paramTypes[0].getName());
                    }

                    if (decodedMessageObject != null) {
                        Object returned = null;

                        if (paramTypes[0].equals(decodedMessageObject.getClass()) ||
                                PrimitivesToBoxing.getBoxing(paramTypes[0]).equals(decodedMessageObject.getClass())) {
                            switch (noOfParameters) {
                                case 1:
                                    returned = m.invoke(model.getBean(), decodedMessageObject);
                                    break;
                                case 2:

                                    if (paramTypes[1].equals(String.class)) {
//                                        returned = m.invoke(model.getBean(), decodedMessageObject, dynamicPath);
                                    } else {
                                        returned = m.invoke(model.getBean(), decodedMessageObject, peer.getSession());
                                    }

                                    break;
                                case 3:

                                    if (paramTypes[1].equals(String.class)) {
//                                        returned = m.invoke(model.getBean(), decodedMessageObject, dynamicPath, peer.getSession());
                                    } else {
//                                        returned = m.invoke(model.getBean(), decodedMessageObject, peer.getSession(), dynamicPath);
                                    }

                                    break;
                                default:
                                    throw new RuntimeException("can't deal with " + noOfParameters + " parameters.");
                            }

                            if (returned != null) {
                                if (o instanceof String) {
                                    String messageToSendAsString = this.doEncode(returned);
                                    peer.sendString(messageToSendAsString);
                                } else if (returned instanceof byte[]) {
                                    peer.sendBytes((byte[]) returned);
                                }
                            }
                        }
                    }
//                }
            } catch (IOException ioe) {
                this.handleGeneratedBeanException(peer, ioe);
            } catch (DecodeException de) {
                this.handleGeneratedBeanException(peer, de);
            } catch (Exception ex) {
                ex.printStackTrace();
                throw new RuntimeException("Error invoking " + m);
            }
        }
    }

    @Override
    public void onClose(SPIRemoteEndpoint gs) {
        this.onGeneratedBeanClose(getPeer(gs));
    }

    public void handleGeneratedBeanException(RemoteEndpoint peer, Exception e) {
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

    public void onGeneratedBeanConnect(WebSocketWrapper peer) {
        for (Method m : model.getOnOpenMethods()) {
            try {
                m.invoke(model.getBean(), peer.getSession());
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Error invoking it.");
            }
        }
    }

    public void onGeneratedBeanClose(WebSocketWrapper peer) {
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
    protected final WebSocketWrapper getPeer(SPIRemoteEndpoint gs) {
        return WebSocketWrapper.getPeer(gs, this, server);
    }

    public ServerContainer getContainerContext() {
        return containerContext;
    }
}
