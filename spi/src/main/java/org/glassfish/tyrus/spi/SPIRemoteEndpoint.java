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
package org.glassfish.tyrus.spi;

import java.io.IOException;

/**
 * This interface represents the unique object which represents the
 * other end of the web socket connection (i.e. the remote client)
 * to the SDK. So the provider must supply one of these for each
 * web socket connection. There must be precisely one
 * instance of this type per connection. The provider must not
 * manufacture a new instance of this type per incoming message,
 * for example.
 *
 * @author dannycoward
 */
public interface SPIRemoteEndpoint {

    /**
     * Return true iff the connection is active.
     *
     * @return <code>true</code> if connected, <code>false</code> otherwise.
     */
    public boolean isConnected();

    /**
     * Send the given string, throwing the IOException if the message is not sent.
     *
     * @param data String that is going to be sent.
     */
    public void send(String data) throws IOException;

    /**
     * Send the given data, throwing the IOException if the message is not sent.
     *
     * @param data byte[] that is going to be sent.
     */
    public void send(byte[] data) throws IOException;

    /**
     * Close the underlying connection using the web socket defined codes.
     *
     * @param code the closing code defined in the web socket protocol.
     * @param reason for closing.
     */
    public void close(int code, String reason) throws IOException;

    /**
     * Returns the URI http request as a String.
     *
     * @return URI as a String.
     */
    public String getUri();
}
