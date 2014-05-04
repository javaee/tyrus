/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.core.frame;

import org.glassfish.tyrus.core.TyrusWebSocket;

/**
 * Binary frame representation.
 */
public class BinaryFrame extends TyrusFrame {

    private final boolean continuation;

    /**
     * Constructor.
     *
     * @param frame original (binary) frame.
     */
    public BinaryFrame(Frame frame) {
        super(frame, FrameType.BINARY);
        this.continuation = false;
    }

    /**
     * Constructor.
     *
     * @param frame        original (binary) frame.
     * @param continuation {@code true} when this frame is continuation frame, {@code false} otherwise.
     */
    public BinaryFrame(Frame frame, boolean continuation) {
        super(frame, continuation ? FrameType.BINARY_CONTINUATION : FrameType.BINARY);
        this.continuation = continuation;
    }

    /**
     * Constructor.
     *
     * @param payload      frame payload.
     * @param continuation {@code true} {@code true} when this frame is continuation frame, {@code false} otherwise.
     * @param fin          {@code true} when this frame is last in current partial message batch. Standard (non-continuous)
     *                     frames have this bit set to {@code true}.
     */
    public BinaryFrame(byte[] payload, boolean continuation, boolean fin) {
        super(Frame.builder().payloadData(payload).opcode(continuation ? (byte) 0x00 : (byte) 0x02).fin(fin).build(), continuation ? FrameType.BINARY_CONTINUATION : FrameType.BINARY);
        this.continuation = continuation;
    }

    @Override
    public void respond(TyrusWebSocket socket) {

        if (continuation) {
            socket.onFragment(this, isFin());
        } else {
            if (isFin()) {
                socket.onMessage(this);
            } else {
                socket.onFragment(this, false);
            }
        }
    }
}
