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

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;
import javax.net.websocket.CloseReason;
import javax.net.websocket.EncodeException;
import javax.net.websocket.RemoteEndpoint;
import javax.net.websocket.SendHandler;
import javax.net.websocket.SendResult;
import javax.net.websocket.Session;

/**
 * Wrapps the {@link RemoteEndpoint} and represents the other side of the websocket connection.
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Martin Matula (martin.matula at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public final class RemoteEndpointWrapper<T> implements RemoteEndpoint<T> {

    /**
     * Remote endpoint.
     */
    private final RemoteEndpoint providedRemoteEndpoint;

    /**
     * WebSocket Session implementation.
     */
    private final SessionImpl webSocketSession;

    /**
     * Endpoint to which is this class the other side of connection.
     */
    private final EndpointWrapper correspondingEndpoint;

    /**
     * URI to which this remote endpoint connected during the handshake phase.
     */
    private String connectedToAddress;

    /**
     * Get the RemoteEndpoint wrapper.
     *
     * @param socket      socket corresponding to the required wrapper
     * @param application web socket endpoint for which the wrapper represents the other side of the connection
     * @return wrapper corresponding to socket
     */
    public static RemoteEndpointWrapper getRemoteWrapper(RemoteEndpoint socket, EndpointWrapper application) {
        return new RemoteEndpointWrapper(socket, application);
    }

    @SuppressWarnings("LeakingThisInConstructor")
    private RemoteEndpointWrapper(RemoteEndpoint providedRemoteEndpoint, EndpointWrapper correspondingEndpoint) {
        this.providedRemoteEndpoint = providedRemoteEndpoint;
        this.correspondingEndpoint = correspondingEndpoint;
        this.webSocketSession = new SessionImpl(correspondingEndpoint.getContainer());
        this.webSocketSession.setPeer(this);
    }

    /**
     * URI to which the remote originally connected.
     *
     * @return URI
     */
    public String getAddress() {
        return this.connectedToAddress;
    }

    /**
     * The endpoint is connected.
     *
     * @return {@code true} iff the endpoint is connected, {@code false} otherwise.
     */
    public boolean isConnected() {
//        return this.providedRemoteEndpoint.isConnected(); TODO change
        return true;
    }

    @Override
    public void sendString(String data) throws IOException {
        this.providedRemoteEndpoint.sendString(data);
        this.webSocketSession.updateLastConnectionActivity();
    }

    @Override
    public void sendBytes(ByteBuffer data) throws IOException {
        this.providedRemoteEndpoint.sendBytes(data);
        this.webSocketSession.updateLastConnectionActivity();
    }

    @Override
    public void sendPartialString(String fragment, boolean isLast) throws IOException {
        this.providedRemoteEndpoint.sendPartialString(fragment, isLast);
        this.webSocketSession.updateLastConnectionActivity();
    }


    @Override
    public void sendPartialBytes(ByteBuffer byteBuffer, boolean isLast) throws IOException {
        this.providedRemoteEndpoint.sendPartialBytes(byteBuffer, isLast);
        this.webSocketSession.updateLastConnectionActivity();
    }

    @Override
    public OutputStream getSendStream() throws IOException {
        return new OutputStreamToAsyncBinaryAdapter(this);
    }

    @Override
    public Writer getSendWriter() throws IOException {
        return new WriterToAsyncTextAdapter(this);
    }

    @Override
    public void sendObject(T o) throws IOException, EncodeException {
        this.webSocketSession.updateLastConnectionActivity();
        sendPolymorphic(o);
    }

    @Override
    public Future<SendResult> sendString(String text, SendHandler completion) {
        SendCompletionAdapter goesAway = new SendCompletionAdapter(this);
        Future<SendResult> fsr = goesAway.sendString(text, completion);
        this.webSocketSession.updateLastConnectionActivity();
        return fsr;
    }

    @Override
    public Future<SendResult> sendBytes(ByteBuffer data, SendHandler completion) {
        this.webSocketSession.updateLastConnectionActivity();
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Future<SendResult> sendObject(T o, SendHandler handler) {
        this.webSocketSession.updateLastConnectionActivity();
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void sendPing(ByteBuffer applicationData) {
        this.providedRemoteEndpoint.sendPing(applicationData);
        this.webSocketSession.updateLastConnectionActivity();
    }

    @Override
    public void sendPong(ByteBuffer applicationData) {
        this.providedRemoteEndpoint.sendPong(applicationData);
        this.webSocketSession.updateLastConnectionActivity();
    }

    @Override
    public String toString() {
        return "Wrapped: " + getClass().getSimpleName();
    }

    void setAddress(String clientAddress) {
        this.connectedToAddress = clientAddress;
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
        } else if (isPrimitiveData(o)) {
            this.sendPrimitiveMessage(o);
        } else {
            String stringToSend = correspondingEndpoint.doEncode(o);
            this.sendString(stringToSend);
        }
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
        System.out.println("Close  public void close(CloseReason cr): " + cr);
        // TODO: implement
//        this.providedRemoteEndpoint.close(1000, null);
    }

    public Session getSession() {
        return webSocketSession;
    }

    public void updateLastConnectionActivity() {
        webSocketSession.updateLastConnectionActivity();
    }
}
