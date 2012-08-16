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

import org.glassfish.websocket.api.annotations.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Arrays;
import java.util.Collections;

/**
 * Model of a class annotated using the WebSocket annotations
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class Model {
    private Set<Method> onOpenMethods;
    private Set<Method> onCloseMethods;
    private Set<Method> onErrorMethods;
    private Set<Method> onMessageMethods;
    private Set<Class<?>> encoders;
    private Set<Class<?>> decoders;
    private Field contextField;
    private List<String> subprotocols;
    private Class remoteInterface;
    private Object myBean;

    public Model(Class<?> annotatedClass){
        this(annotatedClass, null);
    }

    public Model(Object endpoint)  throws IllegalAccessException, InstantiationException  {
        this(endpoint.getClass(),endpoint);
    }

    private Model(Class<?> annotatedClass, Object instance){
        onOpenMethods = parseAnnotatedMethods(annotatedClass, WebSocketOpen.class);
        onCloseMethods = parseAnnotatedMethods(annotatedClass, WebSocketClose.class);
        onErrorMethods = parseAnnotatedMethods(annotatedClass, WebSocketError.class);
        onMessageMethods = parseAnnotatedMethods(annotatedClass, WebSocketMessage.class);
        encoders = parseEncoders(annotatedClass);
        decoders = parseDecoders(annotatedClass);
        contextField = parseContextField(annotatedClass);
        subprotocols = parseSubprotocols(annotatedClass);
        remoteInterface = parseRemoteInterface(annotatedClass);
        if(instance == null){
            try {
                this.myBean = annotatedClass.newInstance();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }else{
            this.myBean = instance;
        }
    }


    private Set<Class<?>> parseDecoders(Class wsClass){
        Set<Class<?>> decs = new HashSet<Class<?>>();
        org.glassfish.websocket.api.annotations.WebSocket wsClassAnnotation = (org.glassfish.websocket.api.annotations.WebSocket) wsClass.getAnnotation(org.glassfish.websocket.api.annotations.WebSocket.class);
        if(wsClassAnnotation != null){
            for (Class decoder : wsClassAnnotation.decoders()) {
                decs.add(decoder);
            }
        }
        return decs;
    }


    private Set<Class<?>> parseEncoders(Class wsClass){
        Set<Class<?>> encs = new HashSet<Class<?>>();
        org.glassfish.websocket.api.annotations.WebSocket wsClassAnnotation = (org.glassfish.websocket.api.annotations.WebSocket) wsClass.getAnnotation(org.glassfish.websocket.api.annotations.WebSocket.class);
        if(wsClassAnnotation != null){
            for (Class encoder : wsClassAnnotation.encoders()) {
                encs.add(encoder);
            }
        }
        return encs;
    }

    public static Set<Method> parseAnnotatedMethods(Class wsClass, Class annotationClass) {
        Set<Method> meths = new HashSet<Method>();
        for (Method m : wsClass.getDeclaredMethods()) {
            if (m.getAnnotation(annotationClass) != null) {
                meths.add(m);
            }
        }
        return meths;
    }

    private Field parseContextField(Class wsClass) {
        for (Field f : wsClass.getDeclaredFields()) {
            if (f.getAnnotation(WebSocketContext.class) != null) {
                return f;
            }
        }
        return null;
    }

    public static List<String> parseSubprotocols(Class wsClass) {
        WebSocket ws = (WebSocket) wsClass.getAnnotation(WebSocket.class);
        if(ws != null){
            return Arrays.asList(ws.subprotocols());
        }else{
            return Collections.emptyList();
        }
    }

    public static Class parseRemoteInterface(Class wsClass) {
        WebSocket ws = (WebSocket) wsClass.getAnnotation(WebSocket.class);
        if(ws!=null){
            return ws.remote();
        }else{
            return null;
        }
    }

    public Set<Method> getOnOpenMethods() {
        return onOpenMethods;
    }

    public Set<Method> getOnCloseMethods() {
        return onCloseMethods;
    }

    public Set<Method> getOnErrorMethods() {
        return onErrorMethods;
    }

    public Set<Method> getOnMessageMethods() {
        return onMessageMethods;
    }

    public Object getBean() {
        return myBean;
    }

    public Set<Class<?>> getEncoders() {
        return encoders;
    }

    public Set<Class<?>> getDecoders() {
        return decoders;
    }

    public Field getContextField() {
        return contextField;
    }

    public List<String> getSubprotocols() {
        return subprotocols;
    }

    public Class getRemoteInterface() {
        return remoteInterface;
    }
}
