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

package org.glassfish.tyrus.servlet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.ProtocolHandler;
import javax.servlet.http.WebConnection;

import org.glassfish.tyrus.websockets.DataFrame;
import org.glassfish.tyrus.websockets.FramingException;
import org.glassfish.tyrus.websockets.WebSocketEngine;
import org.glassfish.tyrus.websockets.draft06.ClosingFrame;

/**
 * @author Jitendra Kotamraju
 */
public class TyrusProtocolHandler implements ProtocolHandler, ReadListener {
    private ServletInputStream is;
    private ServletOutputStream os;
    private ByteBuffer buf;
    private static final Logger LOGGER = Logger.getLogger(TyrusProtocolHandler.class.getName());

    private WebSocketEngine.WebSocketHolder webSocketHolder;

    @Override
    public void init(WebConnection wc) {
        LOGGER.info("Servlet 3.1 Upgrade");
        try {
            is = wc.getInputStream();
            os = wc.getOutputStream();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }

        is.setReadListener(this);

        // TODO: servlet bug ?? why is this need to be called explicitly ?
        if (is.isReady()) {
            onDataAvailable();
        }
    }

    @Override
    public void onDataAvailable() {
        try {
            do {
                if(is.isReady()) {
                    fillBuf();
                }

                LOGGER.info("Remaining Data = " + buf.remaining());

                if(buf != null && buf.hasRemaining()) {
                    if(webSocketHolder.buffer != null) {
                        // TODO
                        // webSocketHolder.buffer.append(buf);
                    }

                    final DataFrame result = webSocketHolder.handler.unframe(buf);
                    if (result == null) {
                        webSocketHolder.buffer = buf;
//                        break;
                    } else {
                        result.respond(webSocketHolder.webSocket);
                    }
                }

                // TODO buf has some data but not enough to decode,
                // TODO it will spin in this loop
            } while (buf.remaining() > 0 || is.isReady());

            buf = null;
        } catch (FramingException e) {
            webSocketHolder.webSocket.onClose(new ClosingFrame(e.getClosingCode(), e.getMessage()));
        } catch (Exception wse) {
            if (webSocketHolder.application.onError(webSocketHolder.webSocket, wse)) {
                webSocketHolder.webSocket.onClose(new ClosingFrame(1011, wse.getMessage()));
            }
        } catch (Throwable e) {
            // TODO servlet container is swallowing, just print it for now
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void fillBuf() throws IOException {

//        int len;
//        int index = 0;
//        byte b[] = new byte[1024];
//        ByteBuffer bb = ByteBuffer.allocate(1024);
//        while (is.isReady() && (len = is.read(b)) != -1) {
//            bb.put(b, index, len);
//            String data = new String(b, 0, len);
//            System.out.println("--> " + data);
//            index += len;
//        }
//
//        buf = bb;

        byte[] data = new byte[200];     // TODO testing purpose
        int len = is.read(data);
        if (len == 0) {
            throw new RuntimeException("No data available.");
        }
        if (buf == null) {
            LOGGER.info("No Buffer. Allocating new one");
            buf = ByteBuffer.wrap(data);
            buf.limit(len);
        } else {
            int limit = buf.limit();
            int capacity = buf.capacity();
            int remaining = buf.remaining();

            if (capacity - limit >= len) {
                // Remaining data need not be changed. New data is just appended
                LOGGER.info("Remaining data need not be moved. New data is just appended");
                buf.mark();
                buf.position(limit);
                buf.limit(capacity);
                buf.put(data, 0, len);
                buf.limit(limit+len);
                buf.reset();
            } else if (remaining+len < capacity) {
                // Remaining data is moved to left. Then new data is appended
                LOGGER.info("Remaining data is moved to left. Then new data is appended");
                buf.compact();
                buf.put(data, 0, len);
                buf.flip();
            } else {
                // Remaining data + new > capacity. So allocate new one
                LOGGER.info("Remaining data + new > capacity. So allocate new one");
                byte[] array = new byte[remaining+len];
                buf.get(array, 0, remaining);
                System.arraycopy(data, 0, array, remaining, len);
                buf = ByteBuffer.wrap(array);
                buf.limit(remaining+len);
            }
        }
    }

    @Override
    public void onAllDataRead() {
    }

    @Override
    public void onError(Throwable t) {
    }

    public void setWebSocketHolder(WebSocketEngine.WebSocketHolder webSocketHolder) {
        this.webSocketHolder = webSocketHolder;
    }

    ServletOutputStream getOutputStream() {
        return this.os;
    }
}
