/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2016 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.Reader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.MessageHandler;

import org.glassfish.tyrus.core.l10n.LocalizationMessages;

/**
 * Buffer used for the case when partial messages are received by the {@link MessageHandler.Whole}.
 * <p>
 * For the first received message {@link MessageHandler.Whole#onMessage(Object)} is called within a new executor to
 * allow blocking reading of passed {@link Reader}.
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
class ReaderBuffer {

    private final AtomicBoolean buffering = new AtomicBoolean(true);
    private final ExecutorService executorService;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    private static final Logger LOGGER = Logger.getLogger(ReaderBuffer.class.getName());

    private volatile boolean receivedLast = false;
    private volatile int bufferSize;
    private volatile int currentlyBuffered;
    private volatile StringBuffer buffer;
    private volatile BufferedStringReader reader = null;
    private volatile MessageHandler.Whole<Reader> messageHandler;
    private volatile boolean sessionClosed = false;

    /**
     * Constructor.
     */
    public ReaderBuffer(ExecutorService executorService) {
        this.buffer = new StringBuffer();
        this.executorService = executorService;
        currentlyBuffered = 0;
    }

    /**
     * Get next received chars.
     *
     * @return next received chars.
     */
    public char[] getNextChars(int number) throws IOException {
        lock.lock();
        try {
            if (buffer.length() == 0) {
                if (receivedLast) {
                    this.reader = null;
                    buffering.set(true);
                    this.currentlyBuffered = 0;
                    return null;
                } else { // there's more to come...so wait here...
                    // don't let the reader block on a closed session
                    checkClosedSession();

                    boolean interrupted;
                    do {
                        interrupted = false;
                        try {
                            condition.await();

                            checkClosedSession();
                        } catch (InterruptedException e) {
                            interrupted = true;
                        }
                    } while (interrupted);
                }
            }

            int size = number > buffer.length() ? buffer.length() : number;

            char[] result = new char[size];
            buffer.getChars(0, size, result, 0);
            buffer.delete(0, size);

            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Finish reading of the buffer.
     */
    public void finishReading() {
        buffer = new StringBuffer();
        reader = null;
    }

    /**
     * Append next message part to the buffer.
     *
     * @param message the message.
     * @param last    should be {@code true} iff this is the last part of the message, {@code false} otherwise.
     */
    public void appendMessagePart(String message, boolean last) {
        lock.lock();
        try {
            currentlyBuffered += message.length();
            if (currentlyBuffered <= bufferSize) {
                buffer.append(message);
            } else {
                if (buffering.get()) {
                    buffering.set(false);
                    final MessageTooBigException messageTooBigException =
                            new MessageTooBigException(LocalizationMessages.PARTIAL_MESSAGE_BUFFER_OVERFLOW());
                    LOGGER.log(Level.FINE, LocalizationMessages.PARTIAL_MESSAGE_BUFFER_OVERFLOW(),
                               messageTooBigException);
                    receivedLast = true;
                    throw messageTooBigException;
                }
            }

            this.receivedLast = last;
            condition.signalAll();
        } finally {
            lock.unlock();
        }

        if (this.reader == null) {
            this.reader = new BufferedStringReader(this);
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    messageHandler.onMessage(reader);
                }
            });
        }
    }

    /**
     * Set the {@link MessageHandler} that will consume the constructed {@link java.io.InputStream}.
     *
     * @param messageHandler {@link MessageHandler} that will consume the constructed {@link java.io.InputStream}.
     */
    public void setMessageHandler(MessageHandler.Whole<Reader> messageHandler) {
        this.messageHandler = messageHandler;
    }

    /**
     * Reset the buffer size.
     *
     * @param bufferSize the size to be set.
     */
    public void resetBuffer(int bufferSize) {
        this.bufferSize = bufferSize;
        buffering.set(true);
        currentlyBuffered = 0;
        buffer.delete(0, buffer.length());
    }

    void onSessionClosed() {
        sessionClosed = true;
        lock.lock();
        try {
            // wake up blocked thread
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void checkClosedSession() throws IOException {
        if (sessionClosed) {
            throw new IOException("Websocket session has been closed.");
        }
    }
}
