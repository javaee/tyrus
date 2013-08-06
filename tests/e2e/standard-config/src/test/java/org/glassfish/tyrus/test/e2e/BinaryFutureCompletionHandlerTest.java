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
package org.glassfish.tyrus.test.e2e;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.testing.TestUtilities;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests the basic client behavior, sending and receiving message
 *
 * @author Danny Coward (danny.coward at oracle.com)
 */
@Ignore
public class BinaryFutureCompletionHandlerTest extends TestUtilities{

    @Test
    public void testFastClient() {
        final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

        Server server = new Server(BinaryFutureCompletionHandlerEndpoint.class);

        try {
            server.start();
            CountDownLatch messageLatch = new CountDownLatch(2);
            BinaryFutureCompletionHandlerEndpoint.messageLatch = messageLatch;

            HelloBinaryClient htc = new HelloBinaryClient(messageLatch);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(htc, cec, getURI(BinaryFutureCompletionHandlerEndpoint.class));
            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertTrue("The client got the echo back", htc.echoWorked);
//            Assert.assertNotNull(BinaryFutureCompletionHandlerEndpoint.sr);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }


    /**
     * @author Danny Coward (danny.coward at oracle.com)
     */
    @ServerEndpoint(value = "/binaryhellocompletionhandlerfuture")
    public static class BinaryFutureCompletionHandlerEndpoint {
        static Future<Void> fsr = null;
        static SendResult sr = null;
        static CountDownLatch messageLatch;

        @OnOpen
        public void init(Session session) {
            System.out.println("BINARYCFSERVER opened");
        }

        @OnMessage
        public void sayHello(ByteBuffer message, Session session) {
            System.out.println("BINARYCFSERVER got message: " + message + " from session " + session);

            System.out.println("BINARYCFSERVER lets send one back in async mode with a future and completion handler");
            SendHandler sh = new SendHandler() {
                public void onResult(SendResult sr) {
                    if (!sr.isOK()) {
                        throw new RuntimeException(sr.getException());
                    }
                    BinaryFutureCompletionHandlerEndpoint.sr = sr;
                }
            };

            session.getAsyncRemote().sendBinary(ByteBuffer.wrap(HelloBinaryClient.MESSAGE.getBytes()), sh);

            System.out.println("BINARYCFSERVER send complete - wait on get()");
            try {
                fsr.get();
                System.out.println("BINARYCFSERVER get returned");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            messageLatch.countDown();
        }
    }
}
