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
package org.glassfish.tyrus;

import java.util.ArrayList;
import java.util.List;
import javax.net.websocket.MessageHandler;
import java.nio.*;

/**
 * A simple adapter that acts as a listens for text message fragments on one thread and offers
 * a (blocking) Reader that can be read on another thread, buffering content along the way.
 *
 * @author Danny Coward (danny.coward at oracle.com)
 */
class AsyncBinaryToOutputStreamAdapter implements BufferedBinaryDataSource, MessageHandler.AsyncBinary {
    /* Configurable buffer size. */
    private static final long MAX_BUFFER_SIZE = 8 * 1024;

    /* The text message pieces currently being buffered. */
    private List<ByteBuffer> bufferedFragments = new ArrayList<ByteBuffer>();
    /* Has this adapter received the last in a sequence of fragments. */
    private boolean receivedLast = false;
    /* The reader implementation this adapter will offer. */
    private BufferedBinaryDataSourceReader reader = null;
    /* The MessageHandler that will be invoked when a new message starts. */
    private MessageHandler.BinaryStream mh;
    /* Statelock to mediate between the notification thread for message fragments and the
     * thread reading the Reader data.
     */
    private final Object stateLock;

    public AsyncBinaryToOutputStreamAdapter(MessageHandler.BinaryStream mh) {
        this.mh = mh;
        this.stateLock = new Object();
    }

    private void blockOnReaderThread() {
        synchronized (stateLock) {
            try {
                this.stateLock.wait();
            } catch (InterruptedException e) {
                // thread unblocked
            }
        }
    }
    


    @Override
    public byte[] getNextBytes(int numberOfBytes) {
        if (this.bufferedFragments.isEmpty()) {
            if (receivedLast) {
                this.reader = null;
                return null;
            } else { // there's more to come...so wait here...
                blockOnReaderThread();
            }
        }
        byte[] bytes = new byte[1];
        ByteBuffer nextFragment = this.bufferedFragments.get(0);
        bytes[0] = nextFragment.array()[0];
        this.bufferedFragments.remove(0);
        if (nextFragment.array().length > 1) {
            byte[] newBytes = new byte[nextFragment.array().length - 1];
            System.arraycopy(nextFragment.array(), 1, newBytes, 0, nextFragment.array().length - 1);
            
            this.bufferedFragments.add(0, ByteBuffer.wrap(newBytes));
        }
        return bytes;
    }

    @Override
    public void finishedReading() {
        this.bufferedFragments = new ArrayList<ByteBuffer>();
        this.reader = null;
    }

    private void checkForBufferOverflow(ByteBuffer part) {
        int numberOfBytes = 0;
        for (ByteBuffer fragment : this.bufferedFragments) {
            numberOfBytes = numberOfBytes + fragment.limit();
        }
        if (MAX_BUFFER_SIZE < numberOfBytes + part.limit()) {
            throw new IllegalStateException("Buffer overflow");
        }
    }

    @Override
    public void onMessagePart(ByteBuffer part, boolean last) {
        this.receivedLast = last;
        this.checkForBufferOverflow(part);
        bufferedFragments.add(part);

        synchronized (stateLock) {
            this.stateLock.notifyAll();
        }

        if (this.reader == null) {
            this.reader = new BufferedBinaryDataSourceReader(this);
            Thread t = new Thread() {
                public void run() {
                    mh.onMessage(reader);
                }
            };
            t.start();
        }
    }
}


