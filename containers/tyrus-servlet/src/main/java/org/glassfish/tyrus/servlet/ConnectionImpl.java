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
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import org.glassfish.tyrus.websockets.Connection;
import org.glassfish.tyrus.websockets.DataFrame;
import org.glassfish.tyrus.websockets.WebSocketEngine;
import org.glassfish.tyrus.websockets.WebSocketResponse;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class ConnectionImpl extends Connection {

    private final TyrusProtocolHandler tyrusProtocolHandler;
    private final HttpServletResponse httpServletResponse;

    public ConnectionImpl(TyrusProtocolHandler tyrusProtocolHandler, HttpServletResponse httpServletResponse) {
        this.tyrusProtocolHandler = tyrusProtocolHandler;
        this.httpServletResponse = httpServletResponse;
    }

    @Override
    public Future<DataFrame> write(final DataFrame frame, CompletionHandler completionHandler) {
        final ServletOutputStream outputStream = tyrusProtocolHandler.getOutputStream();

        if(outputStream.canWrite()) {
            try {

                // TODO
//                outputStream.setWriteListener(new WriteListener() {
//                    @Override
//                    public void onWritePossible() {
//                        // TODO: Implement.
//                    }
//
//                    @Override
//                    public void onError(Throwable throwable) {
//                        // TODO: Implement.
//                    }
//                });

                // TODO: change signature to
                // TODO: Future<DataFrame> write(byte[] frame, CompletionHandler completionHandler)?
                final byte[] bytes = WebSocketEngine.getEngine().getWebSocketHolder(this).handler.frame(frame);


                StringBuffer sb = new StringBuffer();
                for(Byte b : bytes) {
                    sb.append((char)b.intValue());
                }
                System.out.print("#### message written: " + sb.toString());

                outputStream.write(bytes);
                outputStream.flush();
                outputStream.println("TESTESTESTESTESTEST");
                outputStream.flush();

                if(completionHandler != null) {
                    completionHandler.completed(frame);
                }

            } catch (IOException e) {
                if(completionHandler != null) {
                    completionHandler.failed(e);
                }
            }
        }

        return new Future<DataFrame>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return true;
            }

            @Override
            public DataFrame get() throws InterruptedException, ExecutionException {
                return frame;
            }

            @Override
            public DataFrame get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                return frame;
            }
        };
    }

    @Override
    public void write(WebSocketResponse response) {
        httpServletResponse.setStatus(response.getStatus());
        for(Map.Entry<String, String> entry : response.getHeaders().entrySet()) {
            httpServletResponse.addHeader(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void addCloseListener(CloseListener closeListener) {
        // TODO: Implement.
    }

    @Override
    public void closeSilently() {
        // TODO: Implement.
    }

    @Override
    public Object getUnderlyingConnection() {
        return null;  // TODO: Implement.
    }
}
