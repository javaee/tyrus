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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;

import org.glassfish.tyrus.core.Utils;
import org.glassfish.tyrus.spi.HandshakeResponse;
import org.glassfish.tyrus.spi.WebSocketEngine;
import org.glassfish.tyrus.spi.Writer;

/**
 * {@link org.glassfish.tyrus.spi.Writer} implementation used in Servlet integration.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
class TyrusServletWriter implements Writer, WriteListener, WebSocketEngine.ResponseWriter {

    private final TyrusHttpUpgradeHandler tyrusHttpUpgradeHandler;
    private final HttpServletResponse httpServletResponse;
    private final ArrayBlockingQueue<QueuedFrame> queue = new ArrayBlockingQueue<QueuedFrame>(32);


    private static final Logger LOGGER = Logger.getLogger(TyrusServletWriter.class.getName());

    // servlet output stream is not thread safe, we need to ensure it is not accessed from multiple threads at once.
    private final Object outputStreamLock = new Object();
    private ServletOutputStream servletOutputStream = null;

    private volatile boolean isReady = false;

    private static class QueuedFrame {
        public final CompletionHandler<byte[]> completionHandler;
        public final byte[] dataFrame;

        QueuedFrame(CompletionHandler<byte[]> completionHandler, byte[] dataFrame) {
            this.completionHandler = completionHandler;
            this.dataFrame = dataFrame;
        }
    }

    /**
     * Constructor.
     *
     * @param tyrusHttpUpgradeHandler encapsulated {@link TyrusHttpUpgradeHandler} instance.
     * @param httpServletResponse     response instance - upgrade process should set proper headers and status (101 or 5xx).
     */
    public TyrusServletWriter(TyrusHttpUpgradeHandler tyrusHttpUpgradeHandler, HttpServletResponse httpServletResponse) {
        this.tyrusHttpUpgradeHandler = tyrusHttpUpgradeHandler;
        this.httpServletResponse = httpServletResponse;
    }

    @Override
    public void onWritePossible() throws IOException {
        LOGGER.log(Level.FINEST, "OnWritePossible called");

        QueuedFrame queuedFrame = queue.poll();

        // this might seem weird but it cannot be another way, at least not without further synchronization logic.
        // servletOutputStream cannot be touched without synchronizing access via outputStreamLock, but this method is
        // also called from #write(...) when servletOutputStream.setWriteListener is invoked, but from different thread.
        // Calling servletOutputStream.isReady() here would result in deadlock.
        isReady = true;
        if (queuedFrame == null) {
            return;
        }

        while (isReady && queuedFrame != null) {
            _write(queuedFrame.dataFrame, queuedFrame.completionHandler);
            synchronized (outputStreamLock) {
                isReady = servletOutputStream.isReady();
            }
            if (isReady) {
                queuedFrame = queue.poll();
            }
        }
    }

    @Override
    public void onError(Throwable t) {
        LOGGER.log(Level.WARNING, "WriteListener.onError", t);
    }

    @Override
    public void write(final byte[] bytes, Writer.CompletionHandler<byte[]> completionHandler) {

        synchronized (outputStreamLock) {
            // first write
            if (servletOutputStream == null) {
                try {
                    servletOutputStream = tyrusHttpUpgradeHandler.getWebConnection().getOutputStream();
                } catch (IOException e) {
                    LOGGER.log(Level.CONFIG, "ServletOutputStream cannot be obtained", e);
                    completionHandler.failed(e);
                    return;
                }
                isReady = servletOutputStream.isReady();
                servletOutputStream.setWriteListener(this);
            } else {
                isReady = servletOutputStream.isReady();
            }
        }

        if (isReady) {
            _write(bytes, completionHandler);
        } else {
            final QueuedFrame queuedFrame = new QueuedFrame(completionHandler, bytes);
            try {
                queue.put(queuedFrame);
            } catch (InterruptedException e) {
                LOGGER.log(Level.CONFIG, "Cannot enqueue frame", e);
                completionHandler.failed(e);
            }
        }
    }

    public void _write(final byte[] bytes, Writer.CompletionHandler<byte[]> completionHandler) {

        try {
            synchronized (outputStreamLock) {
                servletOutputStream.write(bytes);
                servletOutputStream.flush();
            }

            if (completionHandler != null) {
                completionHandler.completed(bytes);
            }
        } catch (Exception e) {
            if (completionHandler != null) {
                completionHandler.failed(e);
            }
        }
    }

    @Override
    public void write(HandshakeResponse response) {
        httpServletResponse.setStatus(response.getStatus());
        for (Map.Entry<String, List<String>> entry : response.getHeaders().entrySet()) {
            httpServletResponse.addHeader(entry.getKey(), Utils.getHeaderFromList(entry.getValue()));
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
