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

import org.glassfish.tyrus.platform.decoder.BinaryDecoderNoOp;
import org.glassfish.tyrus.platform.decoder.BooleanDecoder;
import org.glassfish.tyrus.platform.decoder.ByteDecoder;
import org.glassfish.tyrus.platform.decoder.CharDecoder;
import org.glassfish.tyrus.platform.decoder.DoubleDecoder;
import org.glassfish.tyrus.platform.decoder.FloatDecoder;
import org.glassfish.tyrus.platform.decoder.IntegerDecoder;
import org.glassfish.tyrus.platform.decoder.LongDecoder;
import org.glassfish.tyrus.platform.decoder.ShortDecoder;
import org.glassfish.tyrus.platform.decoder.StringDecoderNoOp;
import org.glassfish.tyrus.platform.encoder.BinaryEncoderNoOp;
import org.glassfish.tyrus.platform.encoder.BooleanEncoder;
import org.glassfish.tyrus.platform.encoder.ByteEncoder;
import org.glassfish.tyrus.platform.encoder.CharEncoder;
import org.glassfish.tyrus.platform.encoder.DoubleEncoder;
import org.glassfish.tyrus.platform.encoder.FloatEncoder;
import org.glassfish.tyrus.platform.encoder.IntegerEncoder;
import org.glassfish.tyrus.platform.encoder.LongEncoder;
import org.glassfish.tyrus.platform.encoder.ShortEncoder;
import org.glassfish.tyrus.platform.encoder.StringEncoderNoOp;

import javax.net.websocket.Decoder;
import javax.net.websocket.Encoder;
import javax.net.websocket.annotations.WebSocketClose;
import javax.net.websocket.annotations.WebSocketMessage;
import javax.net.websocket.annotations.WebSocketOpen;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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


    private Set<Class<?>> parseDecoders(Class wsClass) {
        Set<Class<?>> decs = new HashSet<Class<?>>();
        javax.net.websocket.annotations.WebSocketEndpoint wsClassAnnotation = (javax.net.websocket.annotations.WebSocketEndpoint) wsClass.getAnnotation(javax.net.websocket.annotations.WebSocketEndpoint.class);
        if (wsClassAnnotation != null) {
            for (Class decoder : wsClassAnnotation.decoders()) {
                decs.add(decoder);
            }
        }
        return decs;
    }


    private Set<Class<?>> parseEncoders(Class wsClass) {
        Set<Class<?>> encs = new HashSet<Class<?>>();
        javax.net.websocket.annotations.WebSocketEndpoint wsClassAnnotation = (javax.net.websocket.annotations.WebSocketEndpoint) wsClass.getAnnotation(javax.net.websocket.annotations.WebSocketEndpoint.class);
        if (wsClassAnnotation != null) {
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

    public static List<String> parseSubprotocols(Class wsClass) {
        javax.net.websocket.annotations.WebSocketEndpoint ws = (javax.net.websocket.annotations.WebSocketEndpoint) wsClass.getAnnotation(javax.net.websocket.annotations.WebSocketEndpoint.class);
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
        encoders.add(new StringEncoderNoOp());
        encoders.add(new BinaryEncoderNoOp());
        encoders.add(new BooleanEncoder());
        encoders.add(new ByteEncoder());
        encoders.add(new CharEncoder());
        encoders.add(new DoubleEncoder());
        encoders.add(new FloatEncoder());
        encoders.add(new IntegerEncoder());
        encoders.add(new LongEncoder());
        encoders.add(new ShortEncoder());
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
        decoders.add(new StringDecoderNoOp());
        decoders.add(new BinaryDecoderNoOp());
        decoders.add(new BooleanDecoder());
        decoders.add(new ByteDecoder());
        decoders.add(new IntegerDecoder());
        decoders.add(new LongDecoder());
        decoders.add(new ShortDecoder());
        decoders.add(new FloatDecoder());
        decoders.add(new DoubleDecoder());
        decoders.add(new CharDecoder());
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

    public List<String> getSubprotocols() {
        return subprotocols;
    }

    public boolean wasAnnotated() {
        return annotated;
    }
}
