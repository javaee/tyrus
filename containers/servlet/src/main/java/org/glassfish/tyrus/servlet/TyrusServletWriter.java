/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2015 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Deque;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

import org.glassfish.tyrus.spi.CompletionHandler;
import org.glassfish.tyrus.spi.Writer;

/**
 * {@link org.glassfish.tyrus.spi.Writer} implementation used in Servlet integration.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
class TyrusServletWriter extends Writer implements WriteListener {

    private final TyrusHttpUpgradeHandler tyrusHttpUpgradeHandler;
    private final Deque<QueuedFrame> queue = new LinkedList<QueuedFrame>();

    private static final Logger LOGGER = Logger.getLogger(TyrusServletWriter.class.getName());

    /**
     * ServletOutputStream is not thread safe, must be synchronized.
     * <p/>
     * Access synchronized via "this" - Tyrus creates one instance of TyrusServletWriter per WebSocket connection, so
     * that should be ok.
     */
    private ServletOutputStream servletOutputStream = null;

    private boolean isListenerSet;

    private static class QueuedFrame {
        public final CompletionHandler<ByteBuffer> completionHandler;
        public final ByteBuffer dataFrame;

        QueuedFrame(CompletionHandler<ByteBuffer> completionHandler, ByteBuffer dataFrame) {
            this.completionHandler = completionHandler;
            this.dataFrame = dataFrame;
        }
    }

    /**
     * Constructor.
     *
     * @param tyrusHttpUpgradeHandler encapsulated {@link TyrusHttpUpgradeHandler} instance.
     */
    public TyrusServletWriter(TyrusHttpUpgradeHandler tyrusHttpUpgradeHandler) {
        this.tyrusHttpUpgradeHandler = tyrusHttpUpgradeHandler;
    }

    @Override
    public synchronized void onWritePossible() throws IOException {
        LOGGER.log(Level.FINEST, "OnWritePossible called");

        while (!queue.isEmpty() && servletOutputStream.isReady()) {
            final QueuedFrame queuedFrame = queue.poll();
            assert queuedFrame != null;

            _write(queuedFrame.dataFrame, queuedFrame.completionHandler);
        }
    }

    @Override
    public synchronized void onError(Throwable t) {
        LOGGER.log(Level.WARNING, "TyrusServletWriter.onError", t);

        QueuedFrame queuedFrame;
        while ((queuedFrame = queue.poll()) != null) {
            queuedFrame.completionHandler.failed(t);
        }
    }

    @Override
    public synchronized void write(final ByteBuffer buffer, CompletionHandler<ByteBuffer> completionHandler) {

        // first write
        if (servletOutputStream == null) {
            try {
                servletOutputStream = tyrusHttpUpgradeHandler.getWebConnection().getOutputStream();
            } catch (IOException e) {
                LOGGER.log(Level.CONFIG, "ServletOutputStream cannot be obtained", e);
                completionHandler.failed(e);
                return;
            }
        }

        if (queue.isEmpty() && servletOutputStream.isReady()) {
            _write(buffer, completionHandler);
        } else {
            final QueuedFrame queuedFrame = new QueuedFrame(completionHandler, buffer);
            queue.offer(queuedFrame);

            if (!isListenerSet) {
                isListenerSet = true;
                servletOutputStream.setWriteListener(this);
            }
        }
    }

    private void _write(ByteBuffer buffer, CompletionHandler<ByteBuffer> completionHandler) {

        try {
            if (buffer.hasArray()) {
                byte[] array = buffer.array();
                servletOutputStream.write(array, buffer.arrayOffset() + buffer.position(), buffer.remaining());
            } else {
                final int remaining = buffer.remaining();
                final byte[] array = new byte[remaining];
                buffer.get(array);
                servletOutputStream.write(array);
            }

            servletOutputStream.flush();

            if (completionHandler != null) {
                completionHandler.completed(buffer);
            }
        } catch (Exception e) {
            if (completionHandler != null) {
                completionHandler.failed(e);
            }
        }
    }

    @Override
    public void close() {
        try {
            tyrusHttpUpgradeHandler.getWebConnection().close();
        } catch (Exception e) {
            // do nothing.
        }
    }
}
