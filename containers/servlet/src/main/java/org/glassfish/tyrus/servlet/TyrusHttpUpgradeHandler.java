/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2013 Oracle and/or its affiliates. All rights reserved.
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

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.WebConnection;

import org.glassfish.tyrus.websockets.DataFrame;
import org.glassfish.tyrus.websockets.FramingException;
import org.glassfish.tyrus.websockets.WebSocket;
import org.glassfish.tyrus.websockets.WebSocketEngine;
import org.glassfish.tyrus.websockets.draft06.ClosingFrame;

/**
 * @author Jitendra Kotamraju
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class TyrusHttpUpgradeHandler implements HttpUpgradeHandler, ReadListener {
    private ServletInputStream is;
    private ServletOutputStream os;
    private WebConnection wc;
    private ByteBuffer buf;

    private boolean closed = false;

    private static final Logger LOGGER = Logger.getLogger(TyrusHttpUpgradeHandler.class.getName());

    private WebSocketEngine.WebSocketHolder webSocketHolder;


    @Override
    public void init(WebConnection wc) {
        LOGGER.config("Servlet 3.1 Upgrade");
        try {
            is = wc.getInputStream();
            os = wc.getOutputStream();
            this.wc = wc;
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
                if (is.isReady()) {
                    fillBuf();
                }

                LOGGER.finest("Remaining Data = " + buf.remaining());

                final int remaining = buf.remaining();

                if (buf != null && buf.hasRemaining()) {
                    final DataFrame result = webSocketHolder.handler.unframe(buf);
                    if (result != null) {
                        result.respond(webSocketHolder.webSocket);
                    }
                }

                if (remaining == buf.remaining() && !is.isReady()) {
                    break;
                }

                // TODO buf has some data but not enough to decode,
                // TODO it will spin in this loop
            } while (buf.remaining() > 0 || is.isReady());
        } catch (FramingException e) {
            final String message = e.getMessage();
            webSocketHolder.webSocket.onClose(new ClosingFrame(e.getClosingCode(), message == null ? "No reason given." : message));
            try {
                if (!closed) {
                    wc.close();
                    closed = true;
                }
            } catch (Exception f) {
                LOGGER.log(Level.CONFIG, f.getMessage(), f);
            }
        } catch (Exception wse) {
            if (webSocketHolder.application.onError(webSocketHolder.webSocket, wse)) {
                final String message = wse.getMessage();
                webSocketHolder.webSocket.onClose(new ClosingFrame(1011, message == null ? "No reason given." : message));
            }
        } catch (Throwable e) {
            // TODO servlet container is swallowing, just print it for now
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void fillBuf() throws IOException {

        do {
            byte[] data = new byte[200];     // TODO testing purpose
            int len = is.read(data);
            if (len == 0) {
                throw new RuntimeException("No data available.");
            }
            if (buf == null) {
                LOGGER.finest("No Buffer. Allocating new one");
                buf = ByteBuffer.wrap(data);
                buf.limit(len);
            } else {
                int limit = buf.limit();
                int capacity = buf.capacity();
                int remaining = buf.remaining();

                if (capacity - limit >= len) {
                    // Remaining data need not be changed. New data is just appended
                    LOGGER.finest("Remaining data need not be moved. New data is just appended");
                    buf.mark();
                    buf.position(limit);
                    buf.limit(capacity);
                    buf.put(data, 0, len);
                    buf.limit(limit + len);
                    buf.reset();
                } else if (remaining + len < capacity) {
                    // Remaining data is moved to left. Then new data is appended
                    LOGGER.finest("Remaining data is moved to left. Then new data is appended");
                    buf.compact();
                    buf.put(data, 0, len);
                    buf.flip();
                } else {
                    // Remaining data + new > capacity. So allocate new one
                    LOGGER.finest("Remaining data + new > capacity. So allocate new one");
                    byte[] array = new byte[remaining + len];
                    buf.get(array, 0, remaining);
                    System.arraycopy(data, 0, array, remaining, len);
                    buf = ByteBuffer.wrap(array);
                    buf.limit(remaining + len);
                }
            }
        } while (is.isReady());
    }

    @Override
    public void onAllDataRead() {
        webSocketHolder.webSocket.onClose(new ClosingFrame(WebSocket.NORMAL_CLOSURE, null));
    }

    @Override
    public void onError(Throwable t) {
        if (t instanceof EOFException) {
            // nothing to do, connection already closed
            return;
        }

        final String message = (t == null ? null : t.getMessage());
        webSocketHolder.webSocket.onClose(new ClosingFrame(WebSocket.NORMAL_CLOSURE, message == null ? "No reason given." : message));
        try {
            if (!closed) {
                wc.close();
                closed = true;
            }
        } catch (Exception e) {
            LOGGER.log(Level.CONFIG, e.getMessage(), e);
        }
    }

    @Override
    public void destroy() {
    }

    public void setWebSocketHolder(WebSocketEngine.WebSocketHolder webSocketHolder) {
        this.webSocketHolder = webSocketHolder;
    }

    ServletOutputStream getOutputStream() {
        return os;
    }
}
