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

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.http.HttpSession;
import org.glassfish.websocket.api.*;
import org.glassfish.websocket.api.annotations.WebSocketMessage;
import org.glassfish.websocket.spi.SPIEndpoint;
import org.glassfish.websocket.spi.SPIHandshakeRequest;
import org.glassfish.websocket.spi.SPIRemoteEndpoint;


/**
 * @author dannycoward
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */


public class WebSocketEndpoint implements SPIEndpoint {

    private String path; // this is relative to the servlet context path
    private Object myBean;
    private Set<Class<?>> decoders;
    private Set<Class<?>> encoders;
    private Set<Class<?>> allDecoders = Collections.newSetFromMap(new ConcurrentHashMap<Class<?>, Boolean>());
    private Set<Class<?>> allEncoders = Collections.newSetFromMap(new ConcurrentHashMap<Class<?>, Boolean>());
    private EndpointContextImpl endpointContext;
    private HttpSession httpSessionForNextOnConnect = null;
    private String originAddressForNextOnConnect = null;
    private String fullPathForNextOnConnect = "";
    private List supportedSubprotocols = new ArrayList();
    private Set<Method> onOpenMethods;
    private Set<Method> onCloseMethods;
    private Set<Method> onErrorMethods;
    private ArrayList<Method> onMessageMethods;
    private Field contextField;
    private Class remoteInterface;
    private boolean server = true;

    public WebSocketEndpoint(ServerContainer containerContext) {
        this.endpointContext = new EndpointContextImpl(containerContext, this);
    }

    public void doInit(String subPath,Model model) {
        if (!model.getRemoteInterface().isInterface()) {
            throw new IllegalArgumentException(remoteInterface + " is not an interface");
        }
        this.setPath(subPath);
        this.setBean(model.getInstance());
        this.setEncoders(model.getEncoders());
        this.setDecoders(model.getDecoders());
        this.onOpenMethods = model.getOnOpenMethods();
        this.onCloseMethods = model.getOnCloseMethods();
        this.onErrorMethods = model.getOnErrorMethods();
        this.onMessageMethods = new ArrayList<Method>(model.getOnMessageMethods());
        Collections.sort(this.onMessageMethods,new MethodComparator());
        this.contextField = model.getContextField();
        this.supportedSubprotocols = model.getSubprotocols();
        this.remoteInterface = model.getRemoteInterface();
        this.init();
    }

    public void doInit(String subPath,Model model, Boolean server) {
        this.server = server;
        doInit(subPath,model);
    }

    private void init() {
        if (this.contextField != null) {
            try {
                if (!contextField.isAccessible()) {
                    contextField.setAccessible(true);
                }
                this.contextField.set(myBean, this.getEndpointContext());
            } catch (Exception e) {
                throw new RuntimeException("Oops, error setting context");
            }
        }
        this.initAllDecoders();
        this.initAllEncoders();
    }

    protected void setPath(String path) {
        this.path = path;
    }

    protected void setEncoders(Set<Class<?>> encoders) {
        this.encoders = encoders;
    }

    protected void setDecoders(Set<Class<?>> decoders) {
        this.decoders = decoders;
    }

    protected void setBean(Object myBean) {
        this.myBean = myBean;
    }

    // rudimentary path matching algorithm
    protected boolean doesPathMatch(String dynamicPath) {
        if (dynamicPath.equals("*")) {
            return true;
        } else if ((path + dynamicPath).equals(fullPathForNextOnConnect)) {
            return true;
        }
        return false;
    }

    protected String getPathSegment() {
        return fullPathForNextOnConnect.substring(path.length(), fullPathForNextOnConnect.length());
    }


    private void initAllEncoders() {
        allEncoders.addAll(encoders);
        allEncoders.add(org.glassfish.websocket.platform.encoders.StringEncoderNoOp.class);
        allEncoders.add(org.glassfish.websocket.platform.encoders.BooleanEncoder.class);
        allEncoders.add(org.glassfish.websocket.platform.encoders.ByteEncoder.class);
        allEncoders.add(org.glassfish.websocket.platform.encoders.CharEncoder.class);
        allEncoders.add(org.glassfish.websocket.platform.encoders.DoubleEncoder.class);
        allEncoders.add(org.glassfish.websocket.platform.encoders.FloatEncoder.class);
        allEncoders.add(org.glassfish.websocket.platform.encoders.IntEncoder.class);
        allEncoders.add(org.glassfish.websocket.platform.encoders.LongEncoder.class);
        allEncoders.add(org.glassfish.websocket.platform.encoders.ShortEncoder.class);

    }

    private void initAllDecoders() {
        allDecoders.addAll(decoders);
        allDecoders.add(org.glassfish.websocket.platform.decoders.StringDecoderNoOp.class);
        allDecoders.add(org.glassfish.websocket.platform.decoders.BooleanDecoder.class);
        allDecoders.add(org.glassfish.websocket.platform.decoders.IntegerDecoder.class);
        allDecoders.add(org.glassfish.websocket.platform.decoders.LongDecoder.class);
        allDecoders.add(org.glassfish.websocket.platform.decoders.ShortDecoder.class);
        allDecoders.add(org.glassfish.websocket.platform.decoders.FloatDecoder.class);
        allDecoders.add(org.glassfish.websocket.platform.decoders.DoubleDecoder.class);
        allDecoders.add(org.glassfish.websocket.platform.decoders.CharDecoder.class);
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

    public Constructor getStringConstructor(Class c) throws Exception {
        for (Constructor nextConstructor : c.getConstructors()) {
            if (nextConstructor.getParameterTypes().length == 1) {
                if (nextConstructor.getParameterTypes()[0].equals(java.lang.String.class)) {
                    return nextConstructor;
                }
            }
        }
        return null;
    }

    public Method getFactoryMethodWithStringParameter(Class c) throws Exception {
        for (Method m : c.getMethods()) {
            if (Modifier.isStatic(m.getModifiers()) && m.getReturnType().equals(c)) {
                if (m.getParameterTypes().length == 1 && m.getParameterTypes()[0].equals(java.lang.String.class)) {
                    return m;
                }
            }
        }
        return null;
    }

    // decodes a string into the given type.
    public Object doDecode(String message, String possiblyPrimitiveType) throws DecodeException {
        String type = getClassTypeForTypeThatMightBePrimitive(possiblyPrimitiveType);
        //System.out.println("Decode: " + message + " into a " + type);

        for (Object next : allDecoders) {
            Class nextClass = (Class) next;
            //System.out.println("Checking... " + nextClass);
            List interfaces = Arrays.asList(nextClass.getInterfaces());
            if (interfaces.contains(org.glassfish.websocket.api.Decoder.Text.class)) {
                //System.out.println("Might work...");
                try {
                    Method m = nextClass.getDeclaredMethod("decode", String.class);
                    //System.out.println("found decode method !");
                    Class returnC = m.getReturnType();
                    //System.out.println("Return decoder " + returnC);
                    ClassLoader cl = null;
                    if(server){
                        cl = ((ContainerContextImpl) endpointContext.getContainerContext()).getApplicationLevelClassLoader();
                    }else{
                        cl = nextClass.getClassLoader();
                    }
                    Class proposedType = cl.loadClass(type);
                    //System.out.println("Return needed " + proposedType);
                    if (proposedType.equals(returnC)) {
                        // we are in luck.

                        Decoder.Text decoder = (Decoder.Text) nextClass.newInstance();

                        boolean willItDecode = decoder.willDecode(message);
                        //System.out.println("but does it want to ?: " + willItDecode);
                        if (willItDecode) {
                            //System.out.println("Found a decoder (" + decoder+")to decode: " + message + " (string) into a " + type);
                            return decoder.decode(message);
                        }
                    }
                } catch (DecodeException ce) {
                    throw ce;
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        }

        return null;
    }

    public String doEncode(Object o) throws EncodeException {
        //System.out.println("Find encoder for " + o + " of class " + o.getClass());
        for (Object next : this.allEncoders) { // look for encoders that can handle this
            Class type = o.getClass();
            Class nextClass = (Class) next;
            List interfaces = Arrays.asList(nextClass.getInterfaces());
            if (interfaces.contains(org.glassfish.websocket.api.Encoder.Text.class)) {
                //System.out.println("This might be it: " + nextClass);
                try {
                    Method m = nextClass.getMethod("encode", o.getClass());
                    //System.out.println("got it : " + nextClass);
                    Encoder.Text te = (Encoder.Text) nextClass.newInstance();
                    return te.encode(o);

                } catch (java.lang.NoSuchMethodException nsme) {
                    // just continue looping
                } catch (EncodeException ce) {
                    throw ce;
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
    public EndpointContextImpl getEndpointContext() {
        return this.endpointContext;
    }

    @Override
    public boolean checkHandshake(SPIHandshakeRequest hr) {
        boolean match = hr.getRequestURI().startsWith(path);
        httpSessionForNextOnConnect = this.getHttpSession(hr);
        this.originAddressForNextOnConnect = hr.getHeader("Origin");
        if (match) {
            fullPathForNextOnConnect = hr.getRequestURI();
        } else {
            fullPathForNextOnConnect = "";
        }
        return match;
    }

    // I don't really know what Grizzly does with this for sure, but
    // I'm going to guess that it wants a sublist of all the supplied
    // subprotocol names that this application will support...
    @Override
    public List<String> getSupportedProtocols(List<String> subProtocol) {
        List<String> supported = new ArrayList<String>();
        for (String nextRequestedProtocolName : subProtocol) {
            if (this.supportedSubprotocols.contains(nextRequestedProtocolName)) {
                supported.add(nextRequestedProtocolName);
            }
        }
        return supported;
    }

    public HttpSession getHttpSession(SPIHandshakeRequest hr) {
        //System.out.println(o.getHeaders());

        /** TO DO FIX THIS
         Cookies cookies = o.getCookies();
         int count = cookies.getCookieCount();
         //System.out.println("There are : " + count + " cookies on the ws request.");
         for (int i = 0; i < count; i++) {
         ServerCookie sc = cookies.getCookie(i);
         //System.out.println("---" + sc.getValue());
         String potentialSessionID = sc.getValue().toString();
         HttpSession session = HttpSessionManager.getInstance().findSessionByID(potentialSessionID);
         if (session != null) {
         //System.out.println("got " + session);
         return session;
         }
         }
         */
        return null;

    }

    @Override
    public void remove() {

    }

    @Override
    public void onConnect(SPIRemoteEndpoint gs) {
        RemoteEndpoint peer = getPeer(gs);
        WebSocketWrapper wsw = WebSocketWrapper.getWebSocketWrapper(peer);
        wsw.setAddress(this.originAddressForNextOnConnect);
        if (ContainerContextImpl.WEB_MODE) {
            if (httpSessionForNextOnConnect != null) {
                ((WebSocketConversationImpl) wsw.getConversation()).setHttpSession(httpSessionForNextOnConnect);
                httpSessionForNextOnConnect = null;
            } else {
//                throw new RuntimeException("Failed to connect the http session with this web socket session");
            }
        }
        if(server){
            this.onGeneratedBeanConnect(wsw);
        }else{
            this.onGeneratedBeanConnect(peer);
        }

    }

    public void onMessage(SPIRemoteEndpoint gs, byte[] messageBytes) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onMessage(SPIRemoteEndpoint gs, String messageString) {
        RemoteEndpoint peer = getPeer(gs);
        for (Method m : this.onMessageMethods) {
            // check path...
            try {
                WebSocketMessage wsm = m.getAnnotation(WebSocketMessage.class);
                String dynamicPath = wsm.dynamicPath();

                if (!server || this.doesPathMatch(dynamicPath)) {

                    int noOfParameters = m.getParameterTypes().length;
                    Object decodedMessageObject = this.doDecode(messageString, m.getParameterTypes()[0].getName());

                    if (decodedMessageObject != null) {
                        Object returned = null;
                        //System.out.println("Invoke " + m.getName() + " on " + this.myBean + " with " + m.getParameterTypes().length + " parameters");
                        //System.out.println("decoded message object is " + decodedMessageObject);
                        if (noOfParameters == 1) {
                            returned = m.invoke(this.myBean, decodedMessageObject);
                        } else if (noOfParameters == 2) {
                            if (m.getParameterTypes()[1].equals(String.class)) {
                                returned = m.invoke(this.myBean, decodedMessageObject, dynamicPath);
                            } else {
                                returned = m.invoke(this.myBean, decodedMessageObject, peer);
                            }
                        } else if (noOfParameters == 3) {
                            if (m.getParameterTypes()[1].equals(String.class)) {
                                returned = m.invoke(this.myBean, decodedMessageObject, dynamicPath, peer);
                            } else {
                                returned = m.invoke(this.myBean, decodedMessageObject, peer, dynamicPath);
                            }
                        } else {
                            throw new RuntimeException("can't deal with " + noOfParameters + " parameters.");
                        }
                        if (returned != null) {
                            String messageToSendAsString = this.doEncode(returned);
                            peer.sendMessage(messageToSendAsString);
//                            one / all messages are called.
//                            break;
                        }
                    }
                }
            } catch (IOException ioe) {
                this.handleGeneratedBeanException(peer, ioe);
            } catch (DecodeException ce) {
                this.handleGeneratedBeanException(peer, ce);
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
        for (Method m : this.onErrorMethods) {
            try {
                System.out.println("Error replying to client " + e.getMessage());
                e.printStackTrace();
                m.invoke(myBean, e, peer.getConversation());
            } catch (Exception ex) {
                ex.printStackTrace();
                throw new RuntimeException("Error invoking it");
            }
        }
    }

    public void onGeneratedBeanConnect(RemoteEndpoint peer) {
        for (Method m : this.onOpenMethods) {
            try {
                //System.out.println("invoke: " + m + " on " + myBean);
                m.invoke(myBean, peer);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Error invoking it");
            }
        }

    }

    public void onGeneratedBeanClose(RemoteEndpoint peer) {
        for (Method m : this.onCloseMethods) {
            try {
                m.invoke(myBean, peer);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Error invoking it");
            }
        }
    }

    protected final RemoteEndpoint getPeer(SPIRemoteEndpoint gs) {
        return WebSocketWrapper.getPeer(gs, this, remoteInterface,server);
    }

    /**
     * Sorts the methods in the list in a way that the methods with (dynamicPath == "*") are in the end of the list.
     */
    private class MethodComparator implements Comparator<Method>{

        @Override
        public int compare(Method m1, Method m2) {
            WebSocketMessage wsm1 = m1.getAnnotation(WebSocketMessage.class);
            String dynamicPath1 = wsm1.dynamicPath();
            WebSocketMessage wsm2 = m2.getAnnotation(WebSocketMessage.class);
            String dynamicPath2 = wsm2.dynamicPath();

            if(dynamicPath1.equals("*")){
                if(dynamicPath2.equals("*")){
                    return 0;
                }else{
                    return 1;
                }
            } else{
                if(dynamicPath2.equals("*")){
                    return -1;
                }else{
                    return 0;
                }
            }
        }
    }

}
