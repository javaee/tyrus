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

import org.glassfish.tyrus.protocol.core.WebSocketFrame;
import org.glassfish.tyrus.protocol.core.WebSocketProtocolDecoder;
import org.glassfish.tyrus.protocol.core.WebSocketProtocolEncoder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.ProtocolHandler;
import javax.servlet.http.WebConnection;

/**
 * @author Jitendra Kotamraju
 */
public class WebSocketProtocolHandler implements ProtocolHandler, ReadListener {
    private ServletInputStream is;
    private ServletOutputStream os;
    private ByteBuffer buf;
    private static final Logger LOGGER = Logger.getLogger(WebSocketProtocolHandler.class.getName());

    private boolean inFragmentation;
    private WebSocketProtocolDecoder decoder;

    @Override
    public void init(WebConnection wc) {
        LOGGER.info("Servlet 3.1 Upgrade");
        try {
            is = wc.getInputStream();
            os = wc.getOutputStream();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        decoder = new WebSocketProtocolDecoder(inFragmentation);
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
                LOGGER.info("Remaining Data = " + buf.remaining());
                WebSocketFrame frame = decoder.decode(buf);
                if (frame != null) {
                    LOGGER.info("Got a WebSocket frame = " + frame);
                    if (!frame.getFrameType().isControlFrame()) {
                        inFragmentation = !frame.isFinalFragment();
                    }
                    decoder = new WebSocketProtocolDecoder(inFragmentation);

                    // Let us echo it back for testing purposes
                    // Not taking advantage of NIO for sending
                    WebSocketProtocolEncoder encoder = new WebSocketProtocolEncoder(false);
                    byte[] echo = encoder.encode(frame);
                    os.write(echo);
                    os.flush();
                }

                // TODO buf has some data but not enough to decode,
                // TODO it will spin in this loop
            } while (buf.remaining() > 0 || is.isReady());
        } catch (Throwable e) {
            // TODO servlet container is swallowing, just print it for now
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void fillBuf() throws IOException {
        byte[] data = new byte[40];     // TODO testing purpose
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

}
