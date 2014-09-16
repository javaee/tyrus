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

import java.util.Collections;

import javax.websocket.ClientEndpoint;
import javax.websocket.DeploymentException;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * JDK client has a hard limit for the maximal upgrade response size
 * ({@link org.glassfish.tyrus.container.jdk.client.HttpResponseParser#BUFFER_MAX_SIZE}). This test tests that
 * the situation when the limit is exceeded is correctly handled.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class TooLargeResponseTest extends TestContainer {

    @Test
    public void testUpgradeResponse() {
        Server server = null;
        try {
            server = startServer(AnnotatedServerEndpoint.class);
            ClientManager clientManager = ClientManager.createClient(JdkClientContainer.class.getName());
            try {
                clientManager.connectToServer(AnnotatedClientEndpoint.class, getURI(AnnotatedServerEndpoint.class));
                fail();
            } catch (DeploymentException e) {
                // the DeploymentException should wrap a ParseException, this just checks that the test tests the right thing
                assertTrue(ParseException.class.equals(e.getCause().getClass()));
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            stopServer(server);
        }
    }

    @ClientEndpoint
    public static class AnnotatedClientEndpoint {

    }

    @ServerEndpoint(value = "/tooLargeResponseEndpoint", configurator = ServerConfig.class)
    public static class AnnotatedServerEndpoint {

    }

    public static class ServerConfig extends ServerEndpointConfig.Configurator {

        @Override
        public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
            int addedHeadersSize = 0;
            int headerCounter = 0;
            String headerKey = "header";
            StringBuilder sb = new StringBuilder();
            // create 1k header value (Grizzly has a 100 headers limit, so there can't be many small headers)
            for (int i = 0; i < 1000; i++) {
                sb.append("A");
            }
            String headerValue = sb.toString();

            /* add at least as much headers to exceed JDK client limit
            (the standard parts of upgrade response are not counted into this for convenience) */
            while (addedHeadersSize < HttpResponseParser.BUFFER_MAX_SIZE) {
                response.getHeaders().put(headerKey + headerCounter, Collections.singletonList(headerValue));
                addedHeadersSize += headerKey.length() + headerValue.length() + 4; // 4 -> :, \r, \n, headerCounter
                headerCounter++;
            }
        }
    }
}
