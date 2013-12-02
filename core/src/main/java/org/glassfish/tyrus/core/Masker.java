/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2013 Oracle and/or its affiliates. All rights reserved.
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

class Masker {
    private volatile ByteBuffer buffer;
    private byte[] mask;
    private int index = 0;

    public Masker(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    public Masker(int mask) {
        this.mask = new byte[4];
        this.mask[0] = (byte) (mask >> 24);
        this.mask[1] = (byte) (mask >> 16);
        this.mask[2] = (byte) (mask >> 8);
        this.mask[3] = (byte) mask;
    }

    byte get() {
        return buffer.get();
    }

    byte[] get(final int size) {
        byte[] bytes = new byte[size];
        buffer.get(bytes);
        return bytes;
    }

    public byte[] unmask(int count) {
        byte[] bytes = get(count);
        if (mask != null) {
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] ^= mask[index++ % ProtocolHandler.MASK_SIZE];
            }
        }

        return bytes;
    }

    public void mask(byte[] target, int location, byte[] bytes, int length) {
        if (bytes != null && target != null) {
            for (int i = 0; i < length; i++) {
                target[location + i] = mask == null
                        ? bytes[i]
                        : (byte) (bytes[i] ^ mask[index++ % ProtocolHandler.MASK_SIZE]);
            }
        }
    }

    public void setBuffer(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    public byte[] getMask() {
        return mask;
    }

    public void readMask() {
        mask = get(ProtocolHandler.MASK_SIZE);
    }
}
