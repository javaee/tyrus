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

import org.glassfish.websocket.spi.SPIRemoteEndpoint;

import javax.net.websocket.CloseReason;
import javax.net.websocket.EncodeException;
import javax.net.websocket.Encoder;
import javax.net.websocket.RemoteEndpoint;
import javax.net.websocket.SendHandler;
import javax.net.websocket.SendResult;
import javax.net.websocket.Session;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;


/**
 * Service class for the WebSocketEndpointImpl.
 *
 * @author Danny Coward
 * @author Martin Matula (martin.matula at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public final class WebSocketWrapper<T> implements RemoteEndpoint<T>, InvocationHandler {
    private final SPIRemoteEndpoint providedRemoteEndpoint;
    private final SessionImpl webSocketSession;
    private final Date activationTime;
    private final Class[] encoders;
    private String clientAddress;
    private static Set<WebSocketWrapper> wrappers = Collections.newSetFromMap(new ConcurrentHashMap<WebSocketWrapper, Boolean>());

    //    /**
//     * Provides a Peer for the given socket. If Peer does not exist yet, it is created.
//     *
//     * @param socket          remote endpoint which is represented by returned Peer
//     * @param application     used to create new Peer if it doesn't exist yet
//     * @param remoteInterface instance of this interface is going to be created
//     * @param serverEndpoint  server / client Endpoint
//     * @param <T>
//     * @return Peer corresponding to socket
//     */
    @SuppressWarnings("unchecked")
//    public static <T extends RemoteEndpoint> T getPeer(SPIRemoteEndpoint socket, WebSocketEndpointImpl application, Class<T> remoteInterface, boolean serverEndpoint) {
//
//        T result = (T) WebSocketWrapper.findWebSocketWrapper(socket);
//        if (result == null) {
//            // no wrapper cached - create one
//            if (RemoteEndpoint.class == remoteInterface) {
//                result = (T) new WebSocketWrapper(socket,  null);
//            } else if (RemoteEndpoint.class.isAssignableFrom(remoteInterface)) {
//                XWebSocketRemote wsrAnnotation = remoteInterface.getAnnotation(XWebSocketRemote.class);
//                Class[] encoders = wsrAnnotation == null ? null : wsrAnnotation.encoders();
//                ClassLoader cl = null;
//                if (serverEndpoint) {
//                    cl = ((ServerContainerImpl) application.getContainerContext())
//                            .getApplicationLevelClassLoader();
//                } else {
//                    cl = remoteInterface.getClassLoader();
//                }
//                result = (T) Proxy.newProxyInstance(cl, new Class[]{remoteInterface},
//                        new WebSocketWrapper(socket, encoders));
//            } else {
//                throw new IllegalArgumentException(remoteInterface.getName() + " does not implement Peer.");
//            }
//
//            if (serverEndpoint) {
//                getWebSocketWrapper(result).setConversationRemote(result);
//            }
//            wrappers.add(result);
//        }
//        return result;
//    }

    public static WebSocketWrapper getPeer(SPIRemoteEndpoint socket, WebSocketEndpointImpl application, boolean serverEndpoint) {

        WebSocketWrapper result = WebSocketWrapper.findWebSocketWrapper(socket);
        if (result == null) {
            result = new WebSocketWrapper(socket, null);
            if (serverEndpoint) {
                getWebSocketWrapper(result).setConversationRemote(result);
            }
            wrappers.add(result);
        }
        return result;
    }

    private WebSocketWrapper(SPIRemoteEndpoint providedRemoteEndpoint, Class[] encoders) {
        this.activationTime = new Date();
        this.providedRemoteEndpoint = providedRemoteEndpoint;
        this.encoders = encoders;
        this.webSocketSession = new SessionImpl();
        this.webSocketSession.setPeer(this);
    }

    private void setConversationRemote(RemoteEndpoint remote) {
        webSocketSession.setPeer(remote);
    }

    public String getAddress() {
        return this.clientAddress;
    }

    public boolean isConnected() {
        return this.providedRemoteEndpoint.isConnected();
    }

    @Override
    public void sendString(String data) throws IOException {
        this.providedRemoteEndpoint.send(data);
    }

    @Override
    public void sendBytes(byte[] data) throws IOException {
        this.providedRemoteEndpoint.send(data);
    }

    public void sendPartialString(String fragment, boolean isLast) throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");
    }


    public void sendPartialBytes(byte[] partialByte, boolean isLast) throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public OutputStream getSendStream() throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");

    }

    public Writer getSendWriter() throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void sendObject(T o) throws IOException, EncodeException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public Future<SendResult> sendString(String text, SendHandler completion) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public Future<SendResult> sendBytes(byte[] data, SendHandler completion) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public Future<SendResult> sendObject(T o, SendHandler handler) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void sendPing(byte[] applicationData) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void sendPong(byte[] applicationData) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public String toString() {
        return "Wrapped: " + getClass().getSimpleName();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Method wswMethod;
        try {
            wswMethod = getClass().getMethod(method.getName(), method.getParameterTypes());
            try {
                Object.class.getMethod(method.getName(), method.getParameterTypes());
            } catch (NoSuchMethodException e) {
                RemoteEndpoint.class.getMethod(method.getName(), method.getParameterTypes());
            }
        } catch (NoSuchMethodException e) {
            wswMethod = null;
        }

        if (wswMethod != null) {
            // delegate
            return wswMethod.invoke(this, args);
        } else {
            if (args.length != 1) {
                throw new RuntimeException("Can't invoke " + method + ". Only single argument peer methods are supported.");
            }
            sendPolymorphic(args[0]);
            return null;
        }
    }

    static WebSocketWrapper getWebSocketWrapper(RemoteEndpoint peer) {
        return (WebSocketWrapper) (peer instanceof WebSocketWrapper ? peer : Proxy.getInvocationHandler(peer));
    }

    static WebSocketWrapper findWebSocketWrapper(SPIRemoteEndpoint re) {
        for (WebSocketWrapper peer : getPeers()) {
            WebSocketWrapper wsw = getWebSocketWrapper(peer);
            if (wsw.providedRemoteEndpoint == re) {
                return peer;
            }
        }
        return null;
    }

    Date getActivationTime() {
        return activationTime;
    }

    void setAddress(String clientAddress) {
        this.clientAddress = clientAddress;
    }

    private static Set<WebSocketWrapper> getPeers() {
        weedExpiredWebSocketWrappers();
        return wrappers;
    }

    private static void weedExpiredWebSocketWrappers() {
        Set<RemoteEndpoint> expired = new HashSet<RemoteEndpoint>();
        for (RemoteEndpoint wsw : wrappers) {
//            if (!(wsw).isConnected()) {
                expired.add(wsw);
//            }
        }
        for (RemoteEndpoint toRemove : expired) {
            wrappers.remove(toRemove);
        }
    }

    private void sendPrimitiveMessage(Object data) throws IOException, EncodeException {
        if (isPrimitiveData(data)) {
            this.sendString(data.toString());
        } else {
            throw new EncodeException("object " + data + " is not a primitive type.", data);
        }
    }

    @SuppressWarnings("unchecked")
    private void sendPolymorphic(Object o) throws IOException, EncodeException {
        if (o instanceof String) {
            this.sendString((String) o);
            return;
        }
        if (encoders != null) {
            for (Class encoder : encoders) {
                try {
                    List interfaces = Arrays.asList(encoder.getInterfaces());
                    if (interfaces.contains(Encoder.Text.class)) {
                        try {
                            Method m = encoder.getMethod("encode", o.getClass());
                            if (m != null) {
                                Encoder.Text te = (Encoder.Text) encoder.newInstance();
                                String toSendString = te.encode(o);
                                this.sendString(toSendString);
                                return;
                            }
                        } catch (NoSuchMethodException nsme) {
                            //skip it, wrong parameter type.
                            nsme.printStackTrace();
                        }
                    }
                } catch (EncodeException e) {
                    throw e;
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new IOException(e.getMessage() + ": Could not send message object " + o
                            + " which is of type " + o.getClass()
                            + " with encoders " + Arrays.asList(encoders));
                }
            }
        }
        if (isPrimitiveData(o)) {
            this.sendPrimitiveMessage(o);
            return;
        }
        throw new RuntimeException("Could not send message object " + o
                + " which is of type " + o.getClass()
                + " with encoders " + (encoders == null ? null : Arrays.asList(encoders)));
    }

    private boolean isPrimitiveData(Object data) {
        Class dataClass = data.getClass();
        return (dataClass.equals(Integer.class) ||
                dataClass.equals(Byte.class) ||
                dataClass.equals(Short.class) ||
                dataClass.equals(Long.class) ||
                dataClass.equals(Float.class) ||
                dataClass.equals(Double.class) ||
                dataClass.equals(Boolean.class) ||
                dataClass.equals(Character.class));
    }

    public void close(CloseReason cr) throws IOException {
        this.providedRemoteEndpoint.close(1000, null);
    }

    public Session getSession(){
        return webSocketSession;
    }
}
