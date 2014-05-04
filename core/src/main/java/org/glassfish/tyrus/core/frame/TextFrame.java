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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

import org.glassfish.tyrus.core.StrictUtf8;
import org.glassfish.tyrus.core.TyrusWebSocket;
import org.glassfish.tyrus.core.Utf8DecodingException;

/**
 * Text frame representation.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class TextFrame extends TyrusFrame {

    private final Charset utf8 = new StrictUtf8();
    private final CharsetDecoder currentDecoder = utf8.newDecoder();
    private final String textPayload;
    private final boolean continuation;

    private ByteBuffer remainder;

    /**
     * Constructor.
     *
     * @param frame     original (text) frame.
     * @param remainder UTF-8 decoding remainder from previously processed frame.
     */
    public TextFrame(Frame frame, ByteBuffer remainder) {
        super(frame, FrameType.TEXT);
        this.textPayload = utf8Decode(isFin(), getPayloadData(), remainder);
        this.continuation = false;
    }

    /**
     * Constructor.
     *
     * @param frame        original (text) frame.
     * @param remainder    UTF-8 decoding remainder from previously processed frame.
     * @param continuation {@code true} when this frame is continuation frame, {@code false} otherwise.
     */
    public TextFrame(Frame frame, ByteBuffer remainder, boolean continuation) {
        super(frame, continuation ? FrameType.TEXT_CONTINUATION : FrameType.TEXT);
        this.textPayload = utf8Decode(isFin(), getPayloadData(), remainder);
        this.continuation = continuation;
    }

    /**
     * Constructor.
     *
     * @param message      text message (will be encoded using strict UTF-8 encoding).
     * @param continuation {@code true} when this frame is continuation frame, {@code false} otherwise.
     * @param fin          {@code true} when this frame is last in current partial message batch. Standard (non-continuous)
     *                     frames have this bit set to {@code true}.
     */
    public TextFrame(String message, boolean continuation, boolean fin) {
        super(Frame.builder().payloadData(encode(new StrictUtf8(), message)).opcode(continuation ? (byte) 0x00 : (byte) 0x01).fin(fin).build(), continuation ? FrameType.TEXT_CONTINUATION : FrameType.TEXT);
        this.continuation = continuation;
        this.textPayload = message;
    }

    /**
     * Get text payload.
     *
     * @return text payload.
     */
    public String getTextPayload() {
        return textPayload;
    }

    /**
     * Remainder after UTF-8 decoding.
     * <p/>
     * This might be removed in the future, if encoding part will be separated from text frame impl.
     *
     * @return UTF-8 decoding remainder. Used internally to decoding next incoming frame.
     */
    public ByteBuffer getRemainder() {
        return remainder;
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

    private String utf8Decode(boolean finalFragment, byte[] data, ByteBuffer remainder) {
        final ByteBuffer b = getByteBuffer(data, remainder);
        int n = (int) (b.remaining() * currentDecoder.averageCharsPerByte());
        CharBuffer cb = CharBuffer.allocate(n);
        String res;
        while (true) {
            CoderResult result = currentDecoder.decode(b, cb, finalFragment);
            if (result.isUnderflow()) {
                if (finalFragment) {
                    currentDecoder.flush(cb);
                    if (b.hasRemaining()) {
                        throw new IllegalStateException("Final UTF-8 fragment received, but not all bytes consumed by decode process");
                    }
                    currentDecoder.reset();
                } else {
                    if (b.hasRemaining()) {
                        this.remainder = b;
                    }
                }
                cb.flip();
                res = cb.toString();
                break;
            }
            if (result.isOverflow()) {
                CharBuffer tmp = CharBuffer.allocate(2 * n + 1);
                cb.flip();
                tmp.put(cb);
                cb = tmp;
                continue;
            }
            if (result.isError() || result.isMalformed()) {
                throw new Utf8DecodingException();
            }
        }

        return res;
    }

    private ByteBuffer getByteBuffer(final byte[] data, ByteBuffer remainder) {
        if (remainder == null) {
            return ByteBuffer.wrap(data);
        } else {
            final int rem = remainder.remaining();
            final byte[] orig = remainder.array();
            byte[] b = new byte[rem + data.length];
            System.arraycopy(orig, orig.length - rem, b, 0, rem);
            System.arraycopy(data, 0, b, rem, data.length);
            return ByteBuffer.wrap(b);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(super.toString());
        sb.append(", textPayload='").append(textPayload).append('\'');
        return sb.toString();
    }

    private static byte[] encode(Charset charset, String string) {
        if (string == null || string.isEmpty()) {
            return new byte[0];
        }

        CharsetEncoder ce = charset.newEncoder();
        int en = scale(string.length(), ce.maxBytesPerChar());
        byte[] ba = new byte[en];
        if (string.length() == 0)
            return ba;

        ce.reset();
        ByteBuffer bb = ByteBuffer.wrap(ba);
        CharBuffer cb = CharBuffer.wrap(string);
        try {
            CoderResult cr = ce.encode(cb, bb, true);
            if (!cr.isUnderflow())
                cr.throwException();
            cr = ce.flush(bb);
            if (!cr.isUnderflow())
                cr.throwException();
        } catch (CharacterCodingException x) {
            // Substitution is always enabled,
            // so this shouldn't happen
            throw new Error(x);
        }
        return safeTrim(ba, bb.position());
    }

    private static int scale(int len, float expansionFactor) {
        // We need to perform double, not float, arithmetic; otherwise
        // we lose low order bits when len is larger than 2**24.
        return (int) (len * (double) expansionFactor);
    }

    // Trim the given byte array to the given length
    private static byte[] safeTrim(byte[] ba, int len) {
        if (len == ba.length
                && (System.getSecurityManager() == null)) {
            return ba;
        } else {
            return copyOf(ba, len);
        }
    }

    private static byte[] copyOf(byte[] original, int newLength) {
        byte[] copy = new byte[newLength];
        System.arraycopy(original, 0, copy, 0,
                Math.min(original.length, newLength));
        return copy;
    }
}
