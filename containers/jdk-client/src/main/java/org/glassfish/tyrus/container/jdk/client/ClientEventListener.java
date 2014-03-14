/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.container.jdk.client;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.CloseReason;

import org.glassfish.tyrus.core.CloseReasons;
import org.glassfish.tyrus.core.TyrusUpgradeResponse;
import org.glassfish.tyrus.spi.ClientEngine;
import org.glassfish.tyrus.spi.ClientEngine.TimeoutHandler;
import org.glassfish.tyrus.spi.CompletionHandler;
import org.glassfish.tyrus.spi.Connection;
import org.glassfish.tyrus.spi.Connection.CloseListener;
import org.glassfish.tyrus.spi.UpgradeRequest;
import org.glassfish.tyrus.spi.Writer;

/**
 * @author Petr Janouch (petr.janouch at oracle.com)
 */

class ClientEventListener {

    private static final Logger LOGGER = Logger.getLogger(ClientEventListener.class.getName());
    private final ClientEngine engine;
    private final URI uri;
    private final TimeoutHandler timeoutHandler;
    private final HttpResponseParser responseParser = new HttpResponseParser();

    private volatile Connection wsConnection;

    ClientEventListener(ClientEngine engine, URI uri, TimeoutHandler timeoutHandler) {
        this.engine = engine;
        this.uri = uri;
        this.timeoutHandler = timeoutHandler;
    }

    void onConnect(final AioConnection connection) {
        UpgradeRequest upgradeRequest = engine.createUpgradeRequest(uri, timeoutHandler);
        HttpRequestBuilder builder = new HttpRequestBuilder(upgradeRequest);
        connection.write(builder.build(), new CompletionHandler<ByteBuffer>() {
            @Override
            public void failed(Throwable throwable) {
                closeConnection(connection);
            }
        });
    }

    void onRead(AioConnection connection, ByteBuffer data) {
        if (wsConnection == null) {
            responseParser.appendData(data);
            if (!responseParser.isComplete()) {
                return;
            }
            TyrusUpgradeResponse tyrusUpgradeResponse;
            try {
                tyrusUpgradeResponse = responseParser.parse();
            } catch (ParseException e) {
                LOGGER.log(Level.SEVERE, "Parsing HTTP handshake response failed", e);
                closeConnection(connection);
                return;
            } finally {
                responseParser.destroy();
            }
            handleUpgradeResponse(connection, tyrusUpgradeResponse);
            if (wsConnection == null) {
                closeConnection(connection);
                return;
            }
        }
        wsConnection.getReadHandler().handle(data);
    }

    private void handleUpgradeResponse(final AioConnection connection, TyrusUpgradeResponse tyrusUpgradeResponse) {
        JdkWriter writer = new JdkWriter(connection);
        wsConnection = engine.processResponse(
                tyrusUpgradeResponse,
                writer,
                new CloseListener() {

                    public void close(CloseReason reason) {
                        closeConnection(connection);
                    }
                });
    }

    void onConnectionClosed() {
        if (wsConnection == null) {
            return;
        }
        wsConnection.close(CloseReasons.CLOSED_ABNORMALLY.getCloseReason());
    }

    private void closeConnection(final AioConnection connection) {
        try {
            connection.close();
        } catch (IOException e) {
            LOGGER.log(Level.INFO, "Could not close connection", e);
        }
    }

    private static class JdkWriter extends Writer {

        private final AioConnection connection;

        JdkWriter(AioConnection connection) {
            this.connection = connection;
        }

        @Override
        public void close() throws IOException {
            connection.close();
        }

        @Override
        public void write(ByteBuffer buffer, CompletionHandler<ByteBuffer> completionHandler) {
            connection.write(buffer, completionHandler);
        }

    }

}
