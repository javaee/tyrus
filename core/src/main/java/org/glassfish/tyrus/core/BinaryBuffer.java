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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.tyrus.core.l10n.LocalizationMessages;

/**
 * Save received partial messages to a list and concatenate them.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
class BinaryBuffer {
    private final List<ByteBuffer> list = new ArrayList<ByteBuffer>();
    private int bufferSize;
    private int currentlyBuffered = 0;
    private static final Logger LOGGER = Logger.getLogger(BinaryBuffer.class.getName());

    /**
     * Append buffer.
     * <p>
     * Actual implementation just stores the buffer instance in list.
     *
     * @param message to be buffered.
     */
    void appendMessagePart(ByteBuffer message) {

        if ((currentlyBuffered + message.remaining()) <= bufferSize) {
            currentlyBuffered += message.remaining();
            list.add(message);
        } else {
            final MessageTooBigException messageTooBigException = new MessageTooBigException(
                    LocalizationMessages.PARTIAL_MESSAGE_BUFFER_OVERFLOW());
            LOGGER.log(Level.FINE, LocalizationMessages.PARTIAL_MESSAGE_BUFFER_OVERFLOW(), messageTooBigException);
            throw messageTooBigException;
        }
    }

    /**
     * Return concatenated list of buffers and reset internal state.
     *
     * @return concatenated buffer.
     */
    ByteBuffer getBufferedContent() {
        ByteBuffer b = ByteBuffer.allocate(currentlyBuffered);

        for (ByteBuffer buffered : list) {
            b.put(buffered);
        }

        b.flip();
        resetBuffer(0);
        return b;
    }

    /**
     * Reset buffer with setting maximal buffer size.
     *
     * @param bufferSize max buffer size.
     */
    void resetBuffer(int bufferSize) {
        this.bufferSize = bufferSize;
        this.list.clear();
        currentlyBuffered = 0;
    }
}

