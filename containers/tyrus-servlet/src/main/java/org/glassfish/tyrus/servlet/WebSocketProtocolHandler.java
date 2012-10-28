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
            LOGGER.info("*** Data Availabe=" + is.available());
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        decoder = new WebSocketProtocolDecoder(inFragmentation);
        is.setReadListener(this);

        // TODO: servlet bug ?? why is this need to be called explicitly ?
        try {
            if (is.available() > 0) {
                onDataAvailable();
            }
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    @Override
    public void onDataAvailable() {
        LOGGER.info("OnDataAvailable() is called");
        try {
            do {
                fillBuf();
                WebSocketFrame frame = decoder.decode(buf);
                if (frame != null) {
                    LOGGER.info("Got a DataFrame");
                    if (!frame.getFrameType().isControlFrame()) {
                        inFragmentation = !frame.isFinalFragment();
                    }
                }
            } while (is.available() > 0);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    private void fillBuf() throws IOException {
        byte[] data = new byte[4096];
        int len = is.read(data);
        if (len == 0) {
            throw new RuntimeException("No data available.");
        }
        if (buf == null) {
            buf = ByteBuffer.wrap(data);
        } else {
            int rem = buf.remaining();
            byte[] orig = buf.array();
            byte[] b = new byte[rem + data.length];
            System.arraycopy(orig, orig.length - rem, b, 0, rem);
            System.arraycopy(data, 0, b, rem, data.length);
            buf = ByteBuffer.wrap(b);
        }
    }

    @Override
    public void onAllDataRead() {
    }

    @Override
    public void onError(Throwable t) {
    }

}
