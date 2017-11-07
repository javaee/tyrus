/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Test;

import javax.websocket.ContainerProvider;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.*;

public class UnknownHostTest {

    private static final Logger LOG = Logger.getLogger(UnknownHostTest.class.getName());


    @Test
    public void testIncreaseFileDescriptorsOnTyrusImplementationInCaseOfUnresolvedAddressException() throws Exception {
        LOG.log(Level.INFO, "BEGIN COUNT: {0}", getOpenFileDescriptorCount());
        String unreachedHostURL = "ws://unreachedhost:8025/e2e-test/echo1";
        URI uri = new URI(unreachedHostURL);
        WebSocketClientEndpoint webSocketClientEndpoint = new WebSocketClientEndpoint();

        //Warmup
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(webSocketClientEndpoint, uri);
        } catch (Exception e) {
            LOG.log(Level.FINE, e.getMessage());
            assertTrue(e.getMessage().contains("Connection failed"));
        }

        LOG.log(Level.INFO, "AFTER WARMUP COUNT: {0}", getOpenFileDescriptorCount());

        long fileDescriptorsBefore = getOpenFileDescriptorCount();

        //When
        int reconnectCount = 10;
        Session session = null;
        for (int i = 0; i < reconnectCount; i++) {
            try {
                WebSocketContainer container = ContainerProvider.getWebSocketContainer();
                session = container.connectToServer(webSocketClientEndpoint, uri);
            } catch (Exception e) {
                LOG.log(Level.FINE, e.getMessage());
                assertTrue(e.getMessage().contains("Connection failed"));
                assertNull(session);

            }
        }

        long fileDescriptorsAfter = getOpenFileDescriptorCount();

        //Then
        LOG.log(Level.INFO, "END COUNT: {0}", getOpenFileDescriptorCount());
        assertEquals(fileDescriptorsBefore, fileDescriptorsAfter);

    }

    private long getOpenFileDescriptorCount() {
        return (((com.sun.management.UnixOperatingSystemMXBean) java.lang.management.ManagementFactory
                .getOperatingSystemMXBean()).getOpenFileDescriptorCount());
    }

    private static class WebSocketClientEndpoint extends Endpoint {

        @Override
        public void onOpen(Session session, EndpointConfig config) {

        }
    }
}
