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

package org.glassfish.tyrus.core;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.websocket.MessageHandler;

/**
 * Buffer used for the case when partial messages are received by the {@link MessageHandler.Whole}.
 * </p>
 * For the first received message {@link MessageHandler.Whole#onMessage(Object)} is called in a new {@link Thread} to allow blocking reading of passed {@link java.io.InputStream}.
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
class InputStreamBuffer {

    private List<ByteBuffer> bufferedFragments = new ArrayList<ByteBuffer>();
    private boolean receivedLast = false;
    private BufferedInputStream inputStream = null;
    private MessageHandler.Whole<InputStream> messageHandler;
    private final Object lock;

    /**
     * Constructor.
     */
    public InputStreamBuffer() {
        this.lock = new Object();
    }

    private void blockOnReaderThread() {
        synchronized (lock) {
            try {
                this.lock.wait();
            } catch (InterruptedException e) {
                // thread unblocked
            }
        }
    }

    /**
     * Get next received bytes.
     *
     * @return next received bytes.
     */
    public byte getNextByte() {
        if (this.bufferedFragments.isEmpty()) {
            if (receivedLast) {
                this.inputStream = null;
                return -1;
            } else { // there's more to come...so wait here...
                blockOnReaderThread();
            }
        }

        ByteBuffer firstBuffer = bufferedFragments.get(0);
        byte result = firstBuffer.get();

        if(!firstBuffer.hasRemaining()){
            bufferedFragments.remove(0);
        }

        return result;
    }

    /**
     * Finish reading of the buffer.
     */
    public void finishReading() {
        this.bufferedFragments = new ArrayList<ByteBuffer>();
        this.inputStream = null;
    }

    /**
     * Append next message part to the buffer.
     *
     * @param message the message.
     * @param last should be {@code true} iff this is the last part of the message, {@code false} otherwise.
     */
    public void appendMessagePart(ByteBuffer message, boolean last) {
        this.receivedLast = last;
        bufferedFragments.add(message);

        synchronized (lock) {
            this.lock.notifyAll();
        }

        if (this.inputStream == null) {
            this.inputStream = new BufferedInputStream(this);
            Thread t = new Thread() {
                public void run() {
                    messageHandler.onMessage(inputStream);
                }
            };
            t.start();
        }
    }

    /**
     * Set the {@link MessageHandler} that will consume the constructed {@link java.io.InputStream}.
     *
     * @param messageHandler {@link MessageHandler} that will consume the constructed {@link java.io.InputStream}.
     */
    public void setMessageHandler(MessageHandler.Whole<InputStream> messageHandler) {
        this.messageHandler = messageHandler;
    }
}
