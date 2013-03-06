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
import java.io.Reader;
import java.nio.ByteBuffer;
import java.util.logging.Level;

import javax.websocket.RemoteEndpoint.Basic;

import javax.websocket.Session;

import org.glassfish.tyrus.tests.qa.lifecycle.SessionConversation;
import org.glassfish.tyrus.tests.qa.lifecycle.SessionLifeCycle;
import sun.awt.image.ByteBandedRaster;

/**
 *
 * @author michal.conos at oracle.com
 */
public class ByteSessionImpl extends SessionLifeCycle<byte[]> implements SessionConversation {

    @Override
    public SessionLifeCycle getSessionConversation(boolean partial) {
        return new ByteSessionImpl(1024, true, partial);
    }
    int messageSize;
    byte[] messageToSend;
    ByteBuffer gotPartial, wholeMessage;

    public ByteSessionImpl(int messageSize, boolean directIO, boolean partial) {
        super(partial);
        this.messageSize = messageSize;
        messageToSend = new byte[messageSize];
        gotPartial = ByteBuffer.allocate(messageSize * 5);
        wholeMessage = ByteBuffer.allocate(messageSize * 5);
        initSendBuffer();

    }

    private void initSendBuffer() {
        for (int idx = 0; idx < messageSize; idx++) {
            messageToSend[idx] = (byte) idx;
        }

        wholeMessage.put(ByteBuffer.wrap(messageToSend));
        wholeMessage.put(ByteBuffer.wrap(messageToSend));
        wholeMessage.put(ByteBuffer.wrap(messageToSend));
        wholeMessage.put(ByteBuffer.wrap(messageToSend));
        wholeMessage.put(ByteBuffer.wrap(messageToSend));

    }

    boolean bb_equal(final byte[] b1, final byte[] b2) {
        if (b1.length != b2.length) {
            logger.log(Level.SEVERE, "arrays not equal! {0} {1}", new Object[]{b1.length, b2.length});
            return false;
        }
        for (int idx = 0; idx < b1.length; idx++) {
            if (b1[idx] != b2[idx]) {
                logger.log(Level.SEVERE, "Arrays mismatch at index: {0}", idx);
                return false;
            }
        }
        return true;
    }

    @Override
    public void onClientMessageHandler(byte[] message, Session session) throws IOException {
        if (0 == ByteBuffer.wrap(message).compareTo(ByteBuffer.wrap(messageToSend))) {
            closeTheSessionFromClient(session);
        }
    }

    @Override
    public void onServerMessageHandler(byte[] message, Session session) throws IOException {
        session.getBasicRemote().sendBinary(ByteBuffer.wrap(message));
    }

    @Override
    public void startTalk(Session s) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(messageToSend);
        s.getBasicRemote().sendBinary(bb);
    }

    @Override
    public void onServerMessageHandler(byte[] message, Session session, boolean last) throws IOException {
        session.getBasicRemote().sendBinary(ByteBuffer.wrap(message), last);
    }

    @Override
    public void onClientMessageHandler(byte[] message, Session session, boolean last) throws IOException {
        gotPartial.put(ByteBuffer.wrap(message));
        if (last) {
            logger.log(Level.INFO, "got Last one:{0}", gotPartial);
            if (0 == gotPartial.compareTo(wholeMessage)) {
                closeTheSessionFromClient(session);
            }
        }

    }

    @Override
    public void startTalkPartial(Session s) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(messageToSend);
        Basic remote = s.getBasicRemote();
        remote.sendBinary(bb, false);
        remote.sendBinary(bb, false);
        remote.sendBinary(bb, false);
        remote.sendBinary(bb, false);
        remote.sendBinary(bb, true);
    }
}
