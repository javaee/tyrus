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

package org.glassfish.tyrus.protocol.core;

import java.security.SecureRandom;
import java.util.logging.Logger;


/**
 * @author Jitendra Kotamraju
 */
public class WebSocketProtocolEncoder {
    private static final Logger LOGGER = Logger.getLogger(
            WebSocketProtocolEncoder.class.getName());
    public static final int MASK_SIZE = 4;
    private final boolean maskData;

    public WebSocketProtocolEncoder(boolean maskData) {
        this.maskData = maskData;
    }

    public byte[] encode(WebSocketFrame frame) {
        byte b1 = 0;
        if (frame.isFinalFragment()) {
            b1 |= 0x80;
        }
        b1 |= frame.getRsv() % 8 << 4;
        b1 |= frame.getFrameType().getOpcode();

        final byte[] bytes = frame.getPayload().array();
        final byte[] lengthBytes = encodeLength(bytes.length);

        int length = 1 + lengthBytes.length + bytes.length + (maskData ? MASK_SIZE : 0);
        int payloadStart = 1 + lengthBytes.length + (maskData ? MASK_SIZE : 0);
        final byte[] packet = new byte[length];
        packet[0] = b1;
        System.arraycopy(lengthBytes, 0, packet, 1, lengthBytes.length);
        if (maskData) {
            byte[] mask = new byte[MASK_SIZE];
            new SecureRandom().nextBytes(mask);

            packet[1] |= 0x80;
            mask(mask, packet, payloadStart, bytes);
            System.arraycopy(mask, 0, packet, payloadStart - MASK_SIZE,
                    MASK_SIZE);
        } else {
            System.arraycopy(bytes, 0, packet, payloadStart, bytes.length);
        }
        return packet;
    }

    /**
     * Converts the length given to the appropriate framing data: <ol> <li>0-125 one element that is the payload length.
     * <li>up to 0xFFFF, 3 element array starting with 126 with the following 2 bytes interpreted as a 16 bit unsigned
     * integer showing the payload length. <li>else 9 element array starting with 127 with the following 8 bytes
     * interpreted as a 64-bit unsigned integer (the high bit must be 0) showing the payload length. </ol>
     *
     * @param length the payload size
     *
     * @return the array
     */
    public byte[] encodeLength(final long length) {
        byte[] lengthBytes;
        if (length <= 125) {
            lengthBytes = new byte[1];
            lengthBytes[0] = (byte) length;
        } else {
            byte[] b = toArray(length);
            if (length <= 0xFFFF) {
                lengthBytes = new byte[3];
                lengthBytes[0] = 126;
                System.arraycopy(b, 6, lengthBytes, 1, 2);
            } else {
                lengthBytes = new byte[9];
                lengthBytes[0] = 127;
                System.arraycopy(b, 0, lengthBytes, 1, 8);
            }
        }
        return lengthBytes;
    }

    static byte[] toArray(long length) {
        long value = length;
        byte[] b = new byte[8];
        for (int i = 7; i >= 0 && value > 0; i--) {
            b[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return b;
    }

    static void mask(byte[] mask, byte[] dst, int offset, byte[] data) {
        for (int i = 0, index = 0; i < data.length; i++) {
            dst[offset + i] =
                 (byte)(data[i] ^ mask[index++ % MASK_SIZE]);
        }
    }

}
