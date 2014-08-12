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

package org.glassfish.tyrus.client;

import javax.websocket.DeploymentException;
import javax.websocket.WebSocketContainer;

import org.glassfish.tyrus.core.HandshakeException;
import org.glassfish.tyrus.spi.UpgradeResponse;

/**
 * This exception is set as a cause of {@link DeploymentException} thrown from {@link WebSocketContainer}.connectToServer(...)
 * when HTTP response status code {@code 503 - Service Unavailable} is received.
 *
 * @author Ondrej Kosatka (ondrej.kosatka at oracle.com)
 * @see ClientManager.ReconnectHandler
 * @see ClientProperties#RETRY_AFTER_SERVICE_UNAVAILABLE
 */
public class RetryAfterException extends HandshakeException {

    private final Long delay;

    /**
     * Constructor.
     *
     * @param message the detail message. The detail message is saved for later retrieval by the {@link #getMessage()} method.
     * @param delay   a delay to the time received handshake response in  header.
     */
    public RetryAfterException(String message, Long delay) {
        super(503, message);
        this.delay = delay;
    }

    /**
     * Get a delay specified in {@value UpgradeResponse#RETRY_AFTER} response header in seconds.
     *
     * @return a delay in seconds or {@code null} when response does not contain {@value UpgradeResponse#RETRY_AFTER} or
     * the value cannot be parsed as long ot {@code http-date}.
     */
    public Long getDelay() {
        return delay;
    }
}
