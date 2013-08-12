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
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;

import org.glassfish.tyrus.websockets.Connection;
import org.glassfish.tyrus.websockets.DataFrame;
import org.glassfish.tyrus.websockets.WebSocketEngine;
import org.glassfish.tyrus.websockets.WebSocketResponse;
import org.glassfish.tyrus.websockets.frame.ClosingFrame;

/**
 * {@link Connection} implementation used in Servlet integration.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
class ConnectionImpl extends Connection implements WriteListener {

    private final TyrusHttpUpgradeHandler tyrusHttpUpgradeHandler;
    private final HttpServletResponse httpServletResponse;
    private final ArrayBlockingQueue<QueuedFrame> queue = new ArrayBlockingQueue<QueuedFrame>(32);

    private static final Logger LOGGER = Logger.getLogger(ConnectionImpl.class.getName());

    private ServletOutputStream servletOutputStream = null;
    private volatile boolean isReady = false;

    private static class QueuedFrame {
        public final CompletionHandler<DataFrame> completionHandler;
        public final DataFrame dataFrame;

        QueuedFrame(CompletionHandler<DataFrame> completionHandler, DataFrame dataFrame) {
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
    public ConnectionImpl(TyrusHttpUpgradeHandler tyrusHttpUpgradeHandler, HttpServletResponse httpServletResponse) {
        this.tyrusHttpUpgradeHandler = tyrusHttpUpgradeHandler;
        this.httpServletResponse = httpServletResponse;
    }

    @Override
    public void onWritePossible() throws IOException {
        LOGGER.log(Level.FINEST, "OnWritePossible called");

        QueuedFrame queuedFrame = queue.poll();
        isReady = servletOutputStream.isReady();

        while (isReady && queuedFrame != null) {
            _write(queuedFrame.dataFrame, queuedFrame.completionHandler);
            isReady = servletOutputStream.isReady();
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
    public void write(final DataFrame frame, Connection.CompletionHandler<DataFrame> completionHandler) {

        // first write
        if (servletOutputStream == null) {
            try {
                servletOutputStream = tyrusHttpUpgradeHandler.getWebConnection().getOutputStream();
            } catch (IOException e) {
                LOGGER.log(Level.CONFIG, "ServletOutputStream cannot be obtained", e);
                completionHandler.failed(e);
            }
            isReady = servletOutputStream.isReady();
            servletOutputStream.setWriteListener(this);
        } else {
            isReady = servletOutputStream.isReady();
        }

        if (isReady) {
            _write(frame, completionHandler);
        } else {
            final QueuedFrame queuedFrame = new QueuedFrame(completionHandler, frame);
            try {
                queue.put(queuedFrame);
            } catch (InterruptedException e) {
                LOGGER.log(Level.CONFIG, "Cannot enqueue frame", e);
                completionHandler.failed(e);
            }
        }
    }

    public void _write(final DataFrame frame, Connection.CompletionHandler<DataFrame> completionHandler) {

        final byte[] bytes = WebSocketEngine.getEngine().getWebSocketHolder(this).handler.frame(frame);

        try {
            synchronized (servletOutputStream) {
                servletOutputStream.write(bytes);
                servletOutputStream.flush();
            }

            if (completionHandler != null) {
                completionHandler.completed(frame);
            }

            if (frame.getType() instanceof ClosingFrame) {
                tyrusHttpUpgradeHandler.getWebConnection().close();
            }
        } catch (Exception e) {
            if (completionHandler != null) {
                completionHandler.failed(e);
            }
        }
    }

    @Override
    public void write(WebSocketResponse response) {
        httpServletResponse.setStatus(response.getStatus());
        for (Map.Entry<String, String> entry : response.getHeaders().entrySet()) {
            httpServletResponse.addHeader(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void addCloseListener(CloseListener closeListener) {

    }

    @Override
    public void closeSilently() {
        try {
            tyrusHttpUpgradeHandler.getWebConnection().close();
        } catch (Exception e) {
            // do nothing.
        }
    }

    @Override
    public Object getUnderlyingConnection() {
        return null;
    }
}
