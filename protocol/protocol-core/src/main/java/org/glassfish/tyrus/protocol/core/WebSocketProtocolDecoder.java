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

import java.nio.ByteBuffer;
import java.util.logging.Logger;


/**
 * @author Jitendra Kotamraju
 */
public class WebSocketProtocolDecoder {
    private static final Logger LOGGER = Logger.getLogger(
            WebSocketProtocolDecoder.class.getName());
    public static final int MASK_SIZE = 4;

    private enum State {
        FRAME_START, LENGTH, MASKING_KEY, PAYLOAD
    }
    private State state;
    private WebSocketFrame.Type frameType;
    private int rsv;
    private byte[] payload;
    private int remaining;
    private final byte[] mask = new byte[MASK_SIZE];
    private boolean finalFragment;
    private boolean masked;
    private int lengthCode;
    private final boolean inFragmentation;

    public WebSocketProtocolDecoder(boolean inFragmentation) {
        this.inFragmentation = inFragmentation;
    }

    /**
    * Returns a websocket frame if the entire frame is available.
    * The specified ByteBuffer will have the remaining data for
    * the next frame.
    *
    * @param buf websocket protocol data
    * @return null if a frame cannot be decoded
    * else an entire frame
    */
    public WebSocketFrame decode(ByteBuffer buf) {
        try {
            switch (state) {
                case FRAME_START:
                    if (buf.remaining() < 2) {
                        // Don't have enough bytes to read opcode and lengthCode
                        return null;
                    }

                    // byte1: final fragment, rsv, opcode
                    byte b1 = buf.get();
                    finalFragment = (b1 & 0x80) != 0;
                    rsv = (b1 & 0x70) >> 4;
                    frameType = getFrameType(b1 & 0x0F);

                    // byte2: masked, frameLength
                    byte b2 = buf.get();
                    masked = (b2 & 0x80) != 0;
                    lengthCode = b2 & 0x7F;

//                    if (rsv != 0) {   // TODO check for extensions
//                        throw new WebSocketProtocolException("RSV bit(s) incorrectly set.");
//                    }
//

//                    state.frameType = valueOf(inFragmentedType, state.opcode);
//                    if (!state.finalFragment && state.controlFrame) {
//                        throw new WebSocketProtocolException("Fragmented control frame");
//                    }
//
//                    if (!state.controlFrame) {
//                        if (isContinuationFrame(state.opcode) && !processingFragment) {
//                            throw new WebSocketProtocolException("End fragment sent, but wasn't processing any previous fragments");
//                        }
//                        if (processingFragment && !isContinuationFrame(state.opcode)) {
//                            throw new WebSocketProtocolException("Fragment sent but opcode was not 0");
//                        }
//                        if (!state.finalFragment && !isContinuationFrame(state.opcode)) {
//                            processingFragment = true;
//                        }
//                        if (!state.finalFragment) {
//                            if (inFragmentedType == 0) {
//                                inFragmentedType = state.opcode;
//                            }
//                        }
//                    }


                    state = State.LENGTH;
                    // fall through

                case LENGTH:
                    long frameLength;
                    if (lengthCode <= 125) {
                        frameLength = lengthCode;
                    } else {
                        if (frameType.isControlFrame()) {
                            throw new WebSocketProtocolException("Control frame payloads must be no greater than 125 bytes.");
                        }

                        final int lengthBytes = lengthCode == 126 ? 2 : 8;
                        if (buf.remaining() < lengthBytes) {
                            // Don't have enough bytes to read frameLength
                            return null;
                        }
                        frameLength = (lengthBytes == 2) ? buf.getShort() : buf.getLong();
                    }
                    if (frameLength > Integer.MAX_VALUE) {
                        throw new WebSocketProtocolException("Too large frame, frameLength = "+ frameLength);
                    }
                    payload = new byte[(int) frameLength];
                    remaining = payload.length;
                    state = State.MASKING_KEY;
                    // fallthrough

                case MASKING_KEY:
                    if (masked) {
                        if (buf.remaining() < MASK_SIZE) {
                            // Don't have enough bytes to read mask
                            return null;
                        }
                        buf.get(mask);
                    }
                    state = State.PAYLOAD;
                    // fallthrough

                case PAYLOAD:
                    if (!buf.hasRemaining()) {
                        return null;                // No data to read
                    }
                    // accumulate available payload
                    int available = buf.remaining();
                    int readLen = (available < remaining)
                            ? available : remaining;
                    buf.get(payload, payload.length-remaining, readLen);
                    remaining -= readLen;
                    if (remaining != 0) {
                        return null;
                    } else {
                        unmask(mask, payload, 0, payload.length);
                        return new WebSocketFrame(finalFragment, rsv, frameType,
                            ByteBuffer.wrap(payload));
                    }

//                    if (data.frameLength != state.frameLength) {
//                        throw new WebSocketProtocolException(String.format("Data read (%s) is not the expected" +
//                                " size (%s)", data.frameLength, state.frameLength));
//                    }
//                    dataFrame = state.frameType.create(state.finalFragment, data);

//                    if (!state.controlFrame && (isTextFrame(state.opcode) || inFragmentedType == 1)) {
//                        utf8Decode(state.finalFragment, data, dataFrame);
//                    }
//
//                    if (!state.controlFrame && state.finalFragment) {
//                        inFragmentedType = 0;
//                        processingFragment = false;
//                    }

            }
        } catch (Exception e) {
            state = null;
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    WebSocketFrame.Type getFrameType(int opcode) {
        switch (opcode) {
            case 0x00:
                return WebSocketFrame.Type.CONTINUATION;
            case 0x01:
                return WebSocketFrame.Type.TEXT;
            case 0x02:
                return WebSocketFrame.Type.BINARY;
            case 0x08:
                return WebSocketFrame.Type.CLOSE;
            case 0x09:
                return WebSocketFrame.Type.PING;
            case 0x0A:
                return WebSocketFrame.Type.PONG;
            default:
                throw new WebSocketProtocolException(String.format("Unknown frame type: %s",
                        Integer.toHexString(opcode & 0xFF).toUpperCase()));
        }
    }

    static void unmask(byte[] mask, byte[] data, int offset, int length) {
        for (int i = offset, index = 0; i < length; i++) {
            data[i] ^= mask[index++ % MASK_SIZE];
        }
    }

}
