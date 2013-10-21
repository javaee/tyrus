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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.CloseReason;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.WebConnection;

import org.glassfish.tyrus.spi.Connection;
import org.glassfish.tyrus.spi.WebSocketEngine;
import org.glassfish.tyrus.spi.Writer;

/**
 * {@link HttpUpgradeHandler} and {@link ReadListener} implementation.
 * <p/>
 * Reads data from {@link ServletInputStream} and passes it further to the
 * Tyrus runtime.
 *
 * @author Jitendra Kotamraju
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class TyrusHttpUpgradeHandler implements HttpUpgradeHandler, ReadListener {

    public static final String FRAME_BUFFER_SIZE = "org.glassfish.tyrus.servlet.incoming-buffer-size";

    private final CountDownLatch connectionLatch = new CountDownLatch(1);

    private ServletInputStream is;
    private ServletOutputStream os;
    private WebConnection wc;
    private ByteBuffer buf;

    private volatile boolean closed = false;
    private int incomingBufferSize = 4194315; // 4M (payload) + 11 (frame overhead)

    private static final Logger LOGGER = Logger.getLogger(TyrusHttpUpgradeHandler.class.getName());

    private Connection connection;
    private WebSocketEngine.UpgradeInfo upgradeInfo;
    private Writer writer;


    private boolean authenticated = false;

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

        try {
            is.setReadListener(this);
        } catch (IllegalStateException e) {
            LOGGER.log(Level.WARNING, e.getMessage(), e);
        }

        connection = upgradeInfo.createConnection(writer, new Connection.CloseListener() {
            @Override
            public void close(CloseReason reason) {
                try {
                    TyrusHttpUpgradeHandler.this.getWebConnection().close();
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, e.getMessage(), e);
                }
            }
        });

        connectionLatch.countDown();
    }

    public void preInit(WebSocketEngine.UpgradeInfo upgradeInfo, Writer writer, boolean authenticated) {
        this.upgradeInfo = upgradeInfo;
        this.writer = writer;
        this.authenticated = authenticated;
    }

    @Override
    public void onDataAvailable() {
        try {
            connectionLatch.await();
        } catch (InterruptedException e) {
            // do nothing.
        }

        do {
            try {
                int available = is.available();
                while (available > 0) {
                    int toRead = (buf == null ?
                            (available > incomingBufferSize ? incomingBufferSize : available) :
                            buf.remaining() + available > incomingBufferSize ? incomingBufferSize - buf.remaining() : buf.remaining() + available
                    );

                    if (toRead == 0) {
                        throw new IOException(String.format("Tyrus input buffer exceeded. Current buffer size is %s bytes.",
                                incomingBufferSize));
                    }

                    available -= fillBuf(toRead);

                    LOGGER.finest(String.format("Remaining Data = %d", buf.remaining()));

                    if (buf.hasRemaining()) {
                        connection.getReadHandler().handle(buf);
                    }
                }
            } catch (IOException e) {
                connection.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, null));
            }
        } while (!closed && is.isReady());
    }

    /**
     * Fill the buf with some more websocket protocol data.
     *
     * @param length length of data available to read.
     * @return legth of actually read data.
     * @throws IOException if some other I/O error occurs.
     */
    private int fillBuf(int length) throws IOException {
        byte[] data = new byte[length];
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

        return len;
    }

    @Override
    public void onAllDataRead() {
        close(CloseReason.CloseCodes.NORMAL_CLOSURE.getCode(), null);
    }

    @Override
    public void onError(Throwable t) {
        close(CloseReason.CloseCodes.CLOSED_ABNORMALLY.getCode(), t.getMessage() == null ? "No reason given." : t.getMessage());
    }

    @Override
    public void destroy() {
        close(CloseReason.CloseCodes.CLOSED_ABNORMALLY.getCode(), "No reason given.");
    }

    /**
     * Called when related {@link javax.servlet.http.HttpSession} is destroyed or invalidated.
     * <p/>
     * Implementation is required to call onClose() on server-side with corresponding close code (1008, see
     * WebSocket spec 7.2) - only when there is an authorized user for this session.
     */
    public void sessionDestroyed() {
        if (authenticated) {
            // websocket spec 7.2 [WSC-7.2-3]
            httpSessionForcedClose(CloseReason.CloseCodes.VIOLATED_POLICY.getCode(), "No reason given.");
        }

        // else do nothing.
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TyrusHttpUpgradeHandler{");
        sb.append("is=").append(is);
        sb.append(", os=").append(os);
        sb.append(", wc=").append(wc);
        sb.append(", closed=").append(closed);
        sb.append('}');
        return sb.toString();
    }

    public void setIncomingBufferSize(int incomingBufferSize) {
        this.incomingBufferSize = incomingBufferSize;
    }

    private void httpSessionForcedClose(int closeCode, String closeReason) {
        if (!closed) {
            try {
                // TODO
                // initiates connection close without sending close frame to the client - session is already invalidated
                // so we should not send anything.
                // ((TyrusWebSocket) ((TyrusWebSocketEngine) engine).getWebSocketHolder(writer).webSocket).setClosed();
                connection.close(new CloseReason(CloseReason.CloseCodes.getCloseCode(closeCode), closeReason));
                closed = true;
                wc.close();
            } catch (Exception e) {
                LOGGER.log(Level.CONFIG, e.getMessage(), e);
            }
        }
    }

    private void close(int closeCode, String closeReason) {
        if (!closed) {
            try {
                connection.close(new CloseReason(CloseReason.CloseCodes.getCloseCode(closeCode), closeReason));
                closed = true;
                wc.close();
            } catch (Exception e) {
                LOGGER.log(Level.CONFIG, e.getMessage(), e);
            }
        }
    }

    WebConnection getWebConnection() {
        if (wc == null) {
            throw new IllegalStateException();
        }
        return wc;
    }
}
