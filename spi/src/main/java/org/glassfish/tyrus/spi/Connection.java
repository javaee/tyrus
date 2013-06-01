/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.spi;

import javax.websocket.SendHandler;
import java.io.Closeable;
import java.net.URI;

/**
 * Represents a websocket connection. Using this one could potentially
 * impement websocket protocol using diferent transports (for e.g HTTP
 * long polling). Tyrus will call {@link #close()} if it sends/receives close
 * frame.
 *
 * @author Jitendra Kotamraju
 */
public interface Connection extends Closeable {

    /**
     * Request URI of a WebSocket request.
     *
     * @return
     */
    URI getRequestURI();

    /**
     * Sets a read handler that knows how to handle the websocket frame data.
     * This will be called during registration of the connection.
     *
     * @param handler
     */
    void setReadHandler(ReadHandler handler);

    /**
     * Writes a complete websocket frame using the current transport
     * connection. A transport may queue up the frame and calls the
     * {@link SendHandler} after writing the frame.
     *
     * This method cannot be called concurrently as WebSocket frames need to be
     * written in order. However, once the method is returned(even if the
     * the frame is not actually written), one could call this method
     * immediately to write other frames.
     *
     * @param data complete frame data
     * @param sh callback to notify the write status
     */
    void write(byte[] data, SendHandler sh);
}
