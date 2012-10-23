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
package org.glassfish.tyrus.client;

import java.io.IOException;
import java.io.Reader;
import java.util.concurrent.CountDownLatch;
import javax.net.websocket.Endpoint;
import javax.net.websocket.MessageHandler;
import javax.net.websocket.Session;
import static org.junit.Assert.assertEquals;

/**
 * @author Danny Coward (danny.coward at oracle.com)
 */
public class BlockingStreamingTextClient extends Endpoint {
    String receivedMessage;
    private final CountDownLatch messageLatch;

    public BlockingStreamingTextClient(CountDownLatch messageLatch) {
        this.messageLatch = messageLatch;
    }

    public void onOpen(Session session) {
        System.out.println("BLOCKINGCLIENT opened !");

        send(session);

        session.addMessageHandler(new MessageHandler.CharacterStream() {
            StringBuilder sb = new StringBuilder();

            public void onMessage(Reader r) {
                try {
                    for (int i = 0; i < 10; i++) {
                        char c = (char) r.read();
                        sb.append(c);
                        System.out.println("Reading char on the client: " + c);
                        assertEquals(Character.forDigit(i, 10), c);
                        System.out.println("Resuming the server");
                        synchronized (BlockingStreamingTextServer.class) {
                            BlockingStreamingTextServer.class.notify();
                        }
                    }
                    char c = (char) r.read();
                    System.out.println("Reading #");
                    sb.append(c);
                    assertEquals('#', c);
                    receivedMessage = sb.toString();
                    messageLatch.countDown();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }


            }
        });
    }

    public void send(Session session) {
        try {
            for (int i = 0; i < 10; i++) {
                System.out.println("Sending bulk #" + i);
                session.getRemote().sendPartialString("blk" + i, false);
                System.out.println("Waiting for the server to process it");
                synchronized (BlockingStreamingTextServer.class) {
                    BlockingStreamingTextServer.class.wait(5000);
                }
            }
            session.getRemote().sendPartialString("END", true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
