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

import java.io.Reader;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.MessageHandler;

/**
 * Buffer used for the case when partial messages are received by the {@link MessageHandler.Whole}.
 * </p>
 * For the first received message {@link MessageHandler.Whole#onMessage(Object)} is called in a new {@link Thread} to allow blocking reading of passed {@link Reader}.
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
class ReaderBuffer {

    private LinkedBlockingQueue<Character> queue = new LinkedBlockingQueue<Character>();
    private boolean receivedLast = false;
    private BufferedStringReader reader = null;
    private MessageHandler.Whole<Reader> messageHandler;
    private final Object lock;
    private int bufferSize;
    private int currentlyBuffered;
    private boolean buffering;
    private static final Logger LOGGER = Logger.getLogger(InputStreamBuffer.class.getName());

    /**
     * Constructor.
     */
    public ReaderBuffer() {
        this.lock = new Object();
        currentlyBuffered = 0;
        buffering = true;
    }

    /**
     * Set the {@link MessageHandler} that will consume the constructed {@link Reader}.
     *
     * @param messageHandler {@link MessageHandler} that will consume the constructed {@link Reader}.
     */
    public void setMessageHandler(MessageHandler.Whole<Reader> messageHandler) {
        this.messageHandler = messageHandler;
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
     * Get next received chars.
     *
     * @return next received chars.
     */
    public char[] getNextChars(int number) {
        if (this.queue.isEmpty()) {
            if (receivedLast) {
                this.reader = null;
                this.currentlyBuffered = 0;
                buffering = true;
                return null;
            } else { // there's more to come...so wait here...
                blockOnReaderThread();
            }
        }

        char[] chrs = new char[number > queue.size() ? queue.size() : number];
        for (int i = 0; i < chrs.length; i++) {
            chrs[i] = queue.poll();
        }
        return chrs;
    }


    /**
     * Finish reading of the buffer.
     */
    public void finishReading() {
        this.queue = new LinkedBlockingQueue<Character>();
        this.reader = null;
    }

    /**
     * Append next message part to the buffer.
     *
     * @param message the message.
     * @param last    should be {@code true} iff this is the last part of the message, {@code false} otherwise.
     */
    public void appendMessagePart(String message, boolean last) {
        synchronized (lock) {
            this.receivedLast = last;

            currentlyBuffered += message.length();
            if (currentlyBuffered <= bufferSize) {
                char[] chars = message.toCharArray();
                for (char c : chars) {
                    queue.add(c);

                }
            } else {
                if (buffering) {
                    buffering = false;
                    final MessageTooBigException messageTooBigException = new MessageTooBigException("Partial message could not be delivered due to buffer overflow.");
                    LOGGER.log(Level.FINE, "Partial message could not be delivered due to buffer overflow.", messageTooBigException);
                    receivedLast = true;
                    throw messageTooBigException;
                }
            }

            this.lock.notifyAll();
        }

        if (reader == null) {
            reader = new BufferedStringReader(this);
            Thread t = new Thread() {
                public void run() {
                    messageHandler.onMessage(reader);
                }
            };
            t.start();
        }
    }

    /**
     * Reset the buffer size.
     *
     * @param bufferSize the size to be set.
     */
    public void resetBuffer(int bufferSize) {
        this.bufferSize = bufferSize;
        currentlyBuffered = 0;
        buffering = true;
        queue.clear();
    }
}
