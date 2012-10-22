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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.net.websocket.Decoder;
import javax.net.websocket.Encoder;
import javax.net.websocket.annotations.WebSocketClose;
import javax.net.websocket.annotations.WebSocketEndpoint;
import javax.net.websocket.annotations.WebSocketMessage;
import javax.net.websocket.annotations.WebSocketOpen;

/**
 * Model of a class annotated using the WebSocketEndpoint annotations
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class Model {
    private Set<Method> onOpenMethods;
    private Set<Method> onCloseMethods;
    //    private Set<Method> onErrorMethods;
    private Set<Method> onMessageMethods;
    private List<Encoder> encoders;
    private List<Decoder> decoders;
    private List<String> subprotocols;
    private Object myBean;
    private boolean annotated = false;

    public Model(Class<?> annotatedClass) {
        this(annotatedClass, null);
    }

    public Model(Object endpoint) throws IllegalAccessException, InstantiationException {
        this(endpoint.getClass(), endpoint);
    }

    private Model(Class<?> annotatedClass, Object instance) {
        onOpenMethods = parseAnnotatedMethods(annotatedClass, WebSocketOpen.class);
        onCloseMethods = parseAnnotatedMethods(annotatedClass, WebSocketClose.class);
//        onErrorMethods = parseAnnotatedMethods(annotatedClass, WebSocketError.class);
        onMessageMethods = parseAnnotatedMethods(annotatedClass, WebSocketMessage.class);
        initEncoders(parseEncoders(annotatedClass));
        initDecoders(parseDecoders(annotatedClass));
        subprotocols = parseSubprotocols(annotatedClass);

        if (instance == null) {
            annotated = true;
            try {
                this.myBean = annotatedClass.newInstance();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        } else {
            this.myBean = instance;
        }
    }


    private Set<Class<?>> parseDecoders(Class<?> wsClass) {
        Set<Class<?>> decs = new HashSet<Class<?>>();
        WebSocketEndpoint wsClassAnnotation = wsClass.getAnnotation(WebSocketEndpoint.class);
        if (wsClassAnnotation != null) {
            Collections.addAll(decs, (Class<?>[]) wsClassAnnotation.decoders());
        }
        return decs;
    }


    private Set<Class<?>> parseEncoders(Class<?> wsClass) {
        Set<Class<?>> encs = new HashSet<Class<?>>();
        WebSocketEndpoint wsClassAnnotation = wsClass.getAnnotation(WebSocketEndpoint.class);
        if (wsClassAnnotation != null) {
            Collections.addAll(encs, (Class<?>[]) wsClassAnnotation.encoders());
        }
        return encs;
    }

    public static Set<Method> parseAnnotatedMethods(Class<?> wsClass, Class<? extends Annotation> annotationClass) {
        Set<Method> meths = new HashSet<Method>();
        for (Method m : wsClass.getDeclaredMethods()) {
            if (m.getAnnotation(annotationClass) != null) {
                meths.add(m);
            }
        }
        return meths;
    }

    public static List<String> parseSubprotocols(Class<?> wsClass) {
        WebSocketEndpoint ws = wsClass.getAnnotation(WebSocketEndpoint.class);
        if (ws != null) {
            return Arrays.asList(ws.subprotocols());
        } else {
            return Collections.emptyList();
        }
    }

    private void initEncoders(Set<Class<?>> clientEncoders) {
        encoders = new ArrayList<Encoder>();
        if (clientEncoders != null) {
            for (Class<?> encoderClass : clientEncoders) {
                try {
                    Object encoder = encoderClass.newInstance();
                    if (encoder instanceof Encoder) {
                        encoders.add((Encoder) encoder);
                    } else {
                        throw new Exception("Provided encoder does not implement Encoder interface");
                    }
                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        encoders.add(NoOpBinaryCoder.INSTANCE);
        encoders.add(NoOpTextCoder.INSTANCE);
        encoders.add(ToStringEncoder.INSTANCE);
    }

    private void initDecoders(Set<Class<?>> clientDecoders) {
        decoders = new ArrayList<Decoder>();
        if (clientDecoders != null) {
            for (Class<?> encoderClass : clientDecoders) {
                try {
                    Object decoder = encoderClass.newInstance();
                    if (decoder instanceof Decoder) {
                        decoders.add((Decoder) decoder);
                    } else {
                        throw new Exception("Provided encoder does not implement Decoder interface");
                    }
                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        decoders.addAll(PrimitiveDecoders.ALL);
        decoders.add(NoOpBinaryCoder.INSTANCE);
        decoders.add(NoOpTextCoder.INSTANCE);
    }

    public Set<Method> getOnOpenMethods() {
        return onOpenMethods;
    }

    public Set<Method> getOnCloseMethods() {
        return onCloseMethods;
    }

//    public Set<Method> getOnErrorMethods() {
//        return onErrorMethods;
//    }

    public Set<Method> getOnMessageMethods() {
        return onMessageMethods;
    }

    public Object getBean() {
        return myBean;
    }

    public List<Encoder> getEncoders() {
        return encoders;
    }

    public List<Decoder> getDecoders() {
        return decoders;
    }

    @SuppressWarnings("UnusedDeclaration")
    public List<String> getSubprotocols() {
        return subprotocols;
    }

    public boolean wasAnnotated() {
        return annotated;
    }
}
