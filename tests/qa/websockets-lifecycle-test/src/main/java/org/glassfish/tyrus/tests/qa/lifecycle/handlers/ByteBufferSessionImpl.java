/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.tests.qa.lifecycle.handlers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.websocket.Session;
import org.glassfish.tyrus.tests.qa.lifecycle.SessionConversation;
import org.glassfish.tyrus.tests.qa.lifecycle.SessionLifeCycle;

/**
 *
 * @author michal.conos at oracle.com
 */
public class ByteBufferSessionImpl extends SessionLifeCycle<ByteBuffer, String> implements SessionConversation {

    int messageSize;
    ByteBuffer messageToSend;
    String textMessageToSend;

    public ByteBufferSessionImpl(int messageSize, boolean directIO) {
        super();
        this.messageSize = messageSize;
        if (directIO) {
            this.messageToSend = ByteBuffer.allocate(messageSize);
        } else {
            this.messageToSend = ByteBuffer.allocateDirect(messageSize);
        }
        initSendBuffer();
    }

    private void initSendBuffer() {
        for (int idx = 0; idx < messageSize; idx++) {
            messageToSend.put((byte) idx);
            textMessageToSend+=(char)idx;
        }
    }
    
    private boolean bb_equals(ByteBuffer b1, ByteBuffer b2) {
        logger.log(Level.INFO, "compare:{0}", b1.compareTo(b2));
        //return 0==b1.compareTo(b2);
        //return b1.array().equals(b2.array());
        return bb_equal(b1.array(), b2.array());
    }
    
    boolean bb_equal(final byte [] b1, final byte [] b2) {
        if(b1.length != b2.length) {
            logger.log(Level.SEVERE, "arrays not equal! {0} {1}", new Object[] {b1.length, b2.length  });
            return false;
        }
        for(int idx=0; idx<b1.length; idx++) {
            if(b1[idx]!=b2[idx]) {
                logger.log(Level.SEVERE, "Arrays mismatch at index: {0}", idx);
                return false;
            }
        }
        return true;
    }

    @Override
    public void onClientMessageHandler(ByteBuffer message, Session session) throws IOException {
        if (bb_equals(message, messageToSend)) {
            closeTheSessionFromClient(session);
        }
    }

    @Override
    public void startTalk(final Session s) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(messageSize);
        s.getBasicRemote().sendBinary(messageToSend);
        List<Thread> partialMsgWorkers = new ArrayList<>();
        final CountDownLatch done = new CountDownLatch(3);
        partialMsgWorkers.add(new Thread() {
            @Override
            public void run() {
                try {
                    s.getBasicRemote().sendText(textMessageToSend, false);
                    done.countDown();
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
        });
        partialMsgWorkers.add(new Thread() {
            @Override
            public void run() {
                try {
                    s.getBasicRemote().sendText(textMessageToSend, false);
                    done.countDown();
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
        });
        partialMsgWorkers.add(new Thread() {
            @Override
            public void run() {
                try {
                    s.getBasicRemote().sendText(textMessageToSend, false);
                    done.countDown();
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
        });
        
        for(Thread t: partialMsgWorkers) {
            t.start();
        }
        
        try {
            done.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        
        s.getBasicRemote().sendBinary(messageToSend, true);
    }

    @Override
    public void onServerMessageHandler(ByteBuffer message, Session session) throws IOException {
        session.getBasicRemote().sendBinary(message);
    }
    
    @Override
    public void onServerMessageHandler(String message, Session session, boolean last) throws IOException {
        session.getBasicRemote().sendText(message, last);
    }

    @Override
    public SessionLifeCycle getSessionConversation() {
        return new ByteBufferSessionImpl(1024, true);
    }

    @Override
    public void onClientMessageHandler(String message, Session session, boolean last) throws IOException {
        logger.log(Level.INFO, "message:{0}", message);
        logger.log(Level.INFO, "last:{0}", last);
    }
}
