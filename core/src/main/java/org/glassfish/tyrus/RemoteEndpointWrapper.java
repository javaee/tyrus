/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import javax.websocket.CloseReason;
import javax.websocket.EncodeException;
import javax.websocket.RemoteEndpoint;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import javax.websocket.Session;

import org.glassfish.tyrus.spi.SPIRemoteEndpoint;

/**
 * Wraps the {@link RemoteEndpoint} and represents the other side of the websocket connection.
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Martin Matula (martin.matula at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public final class RemoteEndpointWrapper implements RemoteEndpoint {

    private final SPIRemoteEndpoint remoteEndpoint;
    private final SessionImpl session;
    private final EndpointWrapper endpointWrapper;

    RemoteEndpointWrapper(SessionImpl session, SPIRemoteEndpoint remoteEndpoint, EndpointWrapper endpointWrapper) {
        this.remoteEndpoint = remoteEndpoint;
        this.endpointWrapper = endpointWrapper;
        this.session = session;
    }

    @Override
    public void sendString(String data) throws IOException {
        this.remoteEndpoint.sendString(data);
    }

    @Override
    public void sendBytes(ByteBuffer data) throws IOException {
        this.remoteEndpoint.sendBytes(data);
    }

    @Override
    public void sendPartialString(String fragment, boolean isLast) throws IOException {
        this.remoteEndpoint.sendPartialString(fragment, isLast);
    }


    @Override
    public void sendPartialBytes(ByteBuffer byteBuffer, boolean isLast) throws IOException {
        this.remoteEndpoint.sendPartialBytes(byteBuffer, isLast);
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
    public void sendObject(Object o) throws IOException, EncodeException {
        sendPolymorphic(o);
    }

    @Override
    public void sendStringByCompletion(String s, SendHandler sendHandler) {
        SendCompletionAdapter goesAway = new SendCompletionAdapter(this, SendCompletionAdapter.State.TEXT);
        goesAway.send(s, sendHandler);
    }

    @Override
    public Future<SendResult> sendStringByFuture(String s) {
        SendCompletionAdapter goesAway = new SendCompletionAdapter(this, SendCompletionAdapter.State.TEXT);
        return goesAway.send(s, null);
    }

    @Override
    public Future<SendResult> sendBytesByFuture(ByteBuffer byteBuffer) {
        SendCompletionAdapter goesAway = new SendCompletionAdapter(this, SendCompletionAdapter.State.BINARY);
        return goesAway.send(byteBuffer, null);
    }

    @Override
    public void sendBytesByCompletion(ByteBuffer byteBuffer, SendHandler sendHandler) {
        SendCompletionAdapter goesAway = new SendCompletionAdapter(this, SendCompletionAdapter.State.BINARY);
        goesAway.send(byteBuffer, sendHandler);
    }

    @Override
    public Future<SendResult> sendObjectByFuture(Object t) {
        SendCompletionAdapter goesAway = new SendCompletionAdapter(this, SendCompletionAdapter.State.OBJECT);
        return goesAway.send(t, null);
    }

    @Override
    public void sendObjectByCompletion(Object t, SendHandler sendHandler) {
        SendCompletionAdapter goesAway = new SendCompletionAdapter(this, SendCompletionAdapter.State.BINARY);
        goesAway.send(t, sendHandler);
    }

    @Override
    public void sendPing(ByteBuffer applicationData) {
        this.remoteEndpoint.sendPing(applicationData);
    }

    @Override
    public void sendPong(ByteBuffer applicationData) {
        this.remoteEndpoint.sendPong(applicationData);
    }

    @Override
    public String toString() {
        return "Wrapped: " + getClass().getSimpleName();
    }

    @Override
    public void setBatchingAllowed(boolean allowed) {
        // TODO: Implement.
    }

    @Override
    public boolean getBatchingAllowed() {
        return false;  // TODO: Implement.
    }

    @Override
    public void flushBatch() {
        // TODO: Implement.
    }

    @Override
    public long getAsyncSendTimeout() {
        return 0;  // TODO: Implement.
    }

    @Override
    public void setAsyncSendTimeout(long timeoutmillis) {
        // TODO: Implement.
    }

    private void sendPrimitiveMessage(Object data) throws IOException, EncodeException {
        if (isPrimitiveData(data)) {
            this.sendString(data.toString());
        } else {
            throw new EncodeException(data, "Object " + data + " is not a primitive type.");
        }
    }

    @SuppressWarnings("unchecked")
    private void sendPolymorphic(Object o) throws IOException, EncodeException {
        if (o instanceof String) {
            this.sendString((String) o);
        } else if (isPrimitiveData(o)) {
            this.sendPrimitiveMessage(o);
        } else {
            Object toSend = endpointWrapper.doEncode(o);
            if(toSend instanceof String){
                this.sendString((String)toSend);
            } else if(toSend instanceof ByteBuffer){
                this.sendBytes((ByteBuffer) toSend);
            } else if(toSend instanceof StringWriter){
                StringWriter writer = (StringWriter) toSend;
                StringBuffer sb = writer.getBuffer();
                this.sendString(sb.toString());
            } else if(toSend instanceof ByteArrayOutputStream){
                ByteArrayOutputStream baos = (ByteArrayOutputStream) toSend;
                ByteBuffer buffer = ByteBuffer.wrap(baos.toByteArray());
                this.sendBytes(buffer);
            }

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
        Logger.getLogger(RemoteEndpointWrapper.class.getName()).info("Close  public void close(CloseReason cr): " + cr);
//        TODO: implement
//        this.remoteEndpoint.close(1000, null);
    }

    public Session getSession() {
        return session;
    }
}
