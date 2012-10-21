/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 - 2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.test.basic;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.DefaultClientEndpointConfiguration;
import org.glassfish.tyrus.server.Server;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import javax.net.websocket.Session;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests the dynamic path including the * path pattern
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class DynamicPathTest {

    private CountDownLatch messageLatch;

    private String receivedMessage;

    private static final String BASIC_MESSAGE = "Hello World";

    private static final String FOO_BAR_SEGMENT = "/foo/bar";

    private static final String DYNAMIC_SEGMENT = "/dynamo";

    @Ignore
    @Test
    public void testDynamicPath(){
        Server server = new Server(org.glassfish.tyrus.test.basic.bean.DynamicPathTestBean.class);
        server.start();

        try{
            this.testPath("",BASIC_MESSAGE,"A");
            this.testPath(FOO_BAR_SEGMENT,BASIC_MESSAGE,"FB");
            this.testPath(DYNAMIC_SEGMENT,BASIC_MESSAGE,"*");
        }catch (Exception e){
            e.printStackTrace();
        }finally{
            server.stop();
        }
    }

    private void testPath(String segmentPath, final String message,final String response) {
        messageLatch = new CountDownLatch(1);
        try {
            final DefaultClientEndpointConfiguration.Builder builder = new DefaultClientEndpointConfiguration.Builder(new URI("ws://localhost:8025/websockets/tests/dynamicpath" + segmentPath));
            final DefaultClientEndpointConfiguration dcec = builder.build();

            ClientManager client = ClientManager.createClient();
            client.connectToServer(new TestEndpointAdapter() {
                //            client.openSocket("ws://localhost:8025/websockets/tests/dynamicpath" + segmentPath, 10000,new TestEndpointAdapter() {
                @Override
                public void onOpen(Session session) {
                    try {
                        session.getRemote().sendString(message);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onMessage(String message) {
                    receivedMessage = message;
                    messageLatch.countDown();
                }
            }, dcec);
            messageLatch.await(5, TimeUnit.SECONDS);
            System.out.println("Expected response:"+response+".");
            System.out.println("Real response:"+receivedMessage+".");
            Assert.assertTrue("The received message does not equal the required response", receivedMessage.equals(response));
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
