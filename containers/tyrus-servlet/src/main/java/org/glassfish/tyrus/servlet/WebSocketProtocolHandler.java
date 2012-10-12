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
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
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

package org.glassfish.tyrus.servlet;

import java.io.IOException;
import java.lang.Exception;

import java.nio.ByteBuffer;
import java.util.logging.Logger;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.ProtocolHandler;
import javax.servlet.http.WebConnection;

/**
 * @author Jitendra Kotamraju
 */
public class WebSocketProtocolHandler implements ProtocolHandler, ReadListener {
    private ServletInputStream is;
    private ServletOutputStream os;
    private ByteBuffer buf;
    private ParsingState state = new ParsingState();
    private static final Logger LOGGER = Logger.getLogger(WebSocketProtocolHandler.class.getName());

    public static final int MASK_SIZE = 4;

    @Override
    public void init(WebConnection wc) {
        LOGGER.info("Servlet 3.1 Upgrade");
        // TODO check HTTP headers for websocket
        try {
            is = wc.getInputStream();
            os = wc.getOutputStream();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        is.setReadListener(this);
    }

    @Override
    public void onDataAvailable() {
        LOGGER.info("OnDataAvailable() is called");
        try {
            do {
                fillBuf();
                DataFrame frame = parse();
                if (frame != null) {
                    LOGGER.info("Got a DataFrame");
                }
                state = new ParsingState();
            } while(is.available() > 0);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    private void fillBuf() throws IOException {
        byte[] data = new byte[4096];
        int len = is.read(data);
        if (len == 0) {
            throw new RuntimeException("No data available.");
        }
        if (buf == null) {
            buf = ByteBuffer.wrap(data);
        } else {
            int rem = buf.remaining();
            byte[] orig = buf.array();
            byte[] b = new byte[rem+data.length];
            System.arraycopy(orig, orig.length-rem, b, 0, rem);
            System.arraycopy(data, 0, b, rem, data.length);
            buf = ByteBuffer.wrap(b);
        }
    }

    @Override
    public void onAllDataRead() {
    }

    @Override
    public void onError(Throwable t) {
    }

    public DataFrame parse() {
        DataFrame dataFrame = null;
        try {
            switch (state.state) {
                case 0:
                    if (buf.remaining() < 2) {
                        // Don't have enough bytes to read opcode and lengthCode
                        return null;
                    }

                    byte opcode = buf.get();
                    boolean rsvBitSet = isBitSet(opcode, 6)
                            || isBitSet(opcode, 5)
                            || isBitSet(opcode, 4);
                    if (rsvBitSet) {
                        throw new WebSocketProtocolException("RSV bit(s) incorrectly set.");
                    }
                    state.finalFragment = isBitSet(opcode, 7);
                    state.controlFrame = isControlFrame(opcode);
                    state.opcode = (byte) (opcode & 0x7f);
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
                    byte lengthCode = buf.get();

                    state.masked = (lengthCode & 0x80) == 0x80;
                    if (state.masked) {
                        lengthCode ^= 0x80;
                    }
                    state.lengthCode = lengthCode;

                    state.state++;
                    // fall through
                case 1:
                    if (state.lengthCode <= 125) {
                        state.length = state.lengthCode;
                    } else {
                        if (state.controlFrame) {
                            throw new WebSocketProtocolException("Control frame payloads must be no greater than 125 bytes.");
                        }

                        final int lengthBytes = state.lengthCode == 126 ? 2 : 8;
                        if (buf.remaining() < lengthBytes) {
                            // Don't have enough bytes to read length
                            return null;
                        }
                        state.length = (lengthBytes == 2) ? buf.getShort() : buf.getLong();
                    }
                    state.state++;
                case 2:
                    if (state.masked) {
                        if (buf.remaining() < MASK_SIZE) {
                            // Don't have enough bytes to read mask
                            return null;
                        }
                        state.mask = buf.get(new byte[MASK_SIZE]).array();
                    }
                    state.state++;
                case 3:
                    if (buf.remaining() < state.length) {
                        return null;
                    }
                    byte[] data = buf.get(new byte[(int)state.length]).array();
                    unmask(state.mask, data, 0, data.length);
                    dataFrame = new DataFrame(DataFrame.Type.TEXT, data);

//                    if (data.length != state.length) {
//                        throw new WebSocketProtocolException(String.format("Data read (%s) is not the expected" +
//                                " size (%s)", data.length, state.length));
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
                    buf = null;
                    state = null;

            }
        } catch (Exception e) {
            state = null;
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(e);
            }
        }

        return dataFrame;

    }

    protected boolean isControlFrame(byte opcode) {
        return (opcode & 0x08) == 0x08;
    }

    private boolean isBitSet(final byte b, int bit) {
        return ((b >> bit & 1) != 0);
    }

    private boolean isContinuationFrame(byte opcode) {
        return opcode == 0;
    }

    private boolean isTextFrame(byte opcode) {
        return opcode == 1;
    }

//    private byte getOpcode(FrameType type) {
//        if (type instanceof TextFrameType) {
//            return 0x01;
//        } else if (type instanceof BinaryFrameType) {
//            return 0x02;
//        } else if (type instanceof ClosingFrameType) {
//            return 0x08;
//        } else if (type instanceof PingFrameType) {
//            return 0x09;
//        } else if (type instanceof PongFrameType) {
//            return 0x0A;
//        }
//
//        throw new WebSocketProtocolException("Unknown frame type: " + type.getClass().getName());
//    }

//    private FrameType valueOf(byte fragmentType, byte value) {
//        final int opcode = value & 0xF;
//        switch (opcode) {
//            case 0x00:
//                return new ContinuationFrameType((fragmentType & 0x01) == 0x01);
//            case 0x01:
//                return new TextFrameType();
//            case 0x02:
//                return new BinaryFrameType();
//            case 0x08:
//                return new ClosingFrameType();
//            case 0x09:
//                return new PingFrameType();
//            case 0x0A:
//                return new PongFrameType();
//            default:
//                throw new WebSocketProtocolException(String.format("Unknown frame type: %s",
//                        Integer.toHexString(opcode & 0xFF).toUpperCase()));
//        }
//    }

    private static class ParsingState {
        int state = 0;
        byte opcode = (byte)-1;
        long length = -1;
        //FrameType frameType;
        boolean masked;
        byte[] mask;
        boolean finalFragment;
        boolean controlFrame;
        private byte lengthCode = -1;
    }


    static void unmask(byte[] mask, byte[] data, int offset, int length) {
        for (int i = offset, index=0; i < length; i++) {
            data[i] ^= mask[index++ % MASK_SIZE];
        }
    }

}
