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

import java.net.SocketAddress;
import java.nio.ByteBuffer;

import org.glassfish.tyrus.spi.CompletionHandler;

/**
 * A filter can add functionality to JDK client transport. Filters are composed together to
 * create JDK client transport.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
class Filter {

    /**
     * Perform write operation for this filter and invokes write method on the next filter in the filter chain.
     *
     * @param data              on which write operation is performed.
     * @param completionHandler will be invoked when the write operation is completed or has failed.
     */
    void write(ByteBuffer data, CompletionHandler<ByteBuffer> completionHandler) {
    }

    /**
     * Close the filter, invokes close operation on the next filter in the filter chain.
     * <p/>
     * The filter is expected to clean up any allocated resources and pass the invocation to downstream filter.
     */
    void close() {
    }

    /**
     * Signal to turn on SSL, it is passed on in the filter chain until a filter responsible for SSL is reached.
     */
    void startSsl() {
    }

    /**
     * Initiate connect.
     *
     * @param address        an address where to connect (server or proxy).
     * @param upstreamFilter a filter positioned upstream.
     */
    void connect(SocketAddress address, Filter upstreamFilter) {
    }

    /**
     * An event listener that is called when a connection is set up.
     * This event travels up in the filter chain.
     */
    void onConnect() {
    }

    /**
     * An event listener that is called when some data is read.
     *
     * @param data that has been read.
     */
    void onRead(ByteBuffer data) {
    }

    /**
     * An event listener that is called when the connection is closed by the peer.
     */
    void onConnectionClosed() {
    }

    /**
     * An event listener that is called, when SSL completes its handshake.
     */
    void onSslHandshakeCompleted() {
    }

    /**
     * An event listener that is called when an error has occurred.
     * <p/>
     * Errors travel in direction from downstream filter to upstream filter.
     *
     * @param t an error that has occurred.
     */
    void onError(Throwable t) {
    }
}
