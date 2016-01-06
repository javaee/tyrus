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

package org.glassfish.tyrus.core.frame;

/**
 * WebSocket frame representation.
 * <pre>TODO:
 * - masking (isMask is currently ignored)
 * - validation
 * - payloadLength is limited to int</pre>
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class Frame {

    private final boolean fin;
    private final boolean rsv1;
    private final boolean rsv2;
    private final boolean rsv3;
    private final boolean mask;

    private final byte opcode;
    private final long payloadLength;
    private final Integer maskingKey;

    private final byte[] payloadData;

    private final boolean controlFrame;

    /**
     * Copy constructor.
     * <p>
     * Note: this is shallow copy. Payload is *not* copied to new array.
     *
     * @param frame copied frame.
     */
    protected Frame(Frame frame) {
        this.fin = frame.fin;
        this.rsv1 = frame.rsv1;
        this.rsv2 = frame.rsv2;
        this.rsv3 = frame.rsv3;
        this.mask = frame.mask;
        this.opcode = frame.opcode;
        this.payloadLength = frame.payloadLength;
        this.maskingKey = frame.maskingKey;
        this.payloadData = frame.payloadData;

        this.controlFrame = (opcode & 0x08) == 0x08;
    }

    private Frame(boolean fin, boolean rsv1, boolean rsv2, boolean rsv3, boolean mask, byte opcode, long payloadLength,
                  Integer maskingKey, byte[] payloadData) {
        this.fin = fin;
        this.rsv1 = rsv1;
        this.rsv2 = rsv2;
        this.rsv3 = rsv3;
        this.mask = mask;
        this.opcode = opcode;
        this.payloadLength = payloadLength;
        this.maskingKey = maskingKey;
        this.payloadData = payloadData;

        this.controlFrame = (opcode & 0x08) == 0x08;
    }

    /**
     * Get FIN value.
     *
     * @return {@code true} when FIN flag is set, {@code false} otherwise.
     */
    public boolean isFin() {
        return fin;
    }

    /**
     * GET RSV1 value.
     *
     * @return {@code true} when RSV1 flag is set, {@code false} otherwise.
     */
    public boolean isRsv1() {
        return rsv1;
    }

    /**
     * GET RSV2 value.
     *
     * @return {@code true} when RSV2 flag is set, {@code false} otherwise.
     */
    public boolean isRsv2() {
        return rsv2;
    }

    /**
     * GET RSV3 value.
     *
     * @return {@code true} when RSV3 flag is set, {@code false} otherwise.
     */
    public boolean isRsv3() {
        return rsv3;
    }

    /**
     * Currently not used.
     *
     * @return not used.
     */
    public boolean isMask() {
        return mask;
    }

    /**
     * Get opcode.
     *
     * @return opcode (4 bit value).
     */
    public byte getOpcode() {
        return opcode;
    }

    /**
     * Get payload length.
     *
     * @return payload length.
     */
    public long getPayloadLength() {
        return payloadLength;
    }

    /**
     * Get masking key.
     *
     * @return masking key (32 bit value) or {@code null} when the frame should not be masked.
     */
    public Integer getMaskingKey() {
        return maskingKey;
    }

    /**
     * Get payload data.
     * <p>
     * Changes done to returned array won't be propagated to current {@link Frame} instance. If you need to modify
     * payload, you have to create new instance, see {@code Builder#Frame(Frame)}. Length of returned array will
     * be always same as {@link #getPayloadLength()}.
     *
     * @return payload data.
     */
    public byte[] getPayloadData() {
        byte[] tmp = new byte[(int) payloadLength];
        System.arraycopy(payloadData, 0, tmp, 0, (int) payloadLength);
        return tmp;
    }

    /**
     * Get information about frame type.
     *
     * @return {@code true} when this frame is control (close, ping, pong) frame, {@code false} otherwise.
     */
    public boolean isControlFrame() {
        return controlFrame;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Frame{");
        sb.append("fin=").append(fin);
        sb.append(", rsv1=").append(rsv1);
        sb.append(", rsv2=").append(rsv2);
        sb.append(", rsv3=").append(rsv3);
        sb.append(", mask=").append(mask);
        sb.append(", opcode=").append(opcode);
        sb.append(", payloadLength=").append(payloadLength);
        sb.append(", maskingKey=").append(maskingKey);
        sb.append('}');
        return sb.toString();
    }

    /**
     * Create new {@link Builder}.
     *
     * @return new builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create new {@link Builder} based on provided frame.
     *
     * @param frame frame used as a base for building new frame.
     * @return new builder instance.
     */
    public static Builder builder(Frame frame) {
        return new Builder(frame);
    }

    /**
     * Frame builder.
     */
    public static final class Builder {

        private boolean fin;
        private boolean rsv1;
        private boolean rsv2;
        private boolean rsv3;
        private boolean mask;

        private byte opcode;
        private long payloadLength;
        private Integer maskingKey = null;

        private byte[] payloadData;

        /**
         * Constructor.
         */
        public Builder() {
        }

        /**
         * Constructor.
         *
         * @param frame frame used as a base for building new frame.
         */
        public Builder(Frame frame) {
            this.fin = frame.fin;
            this.rsv1 = frame.rsv1;
            this.rsv2 = frame.rsv2;
            this.rsv3 = frame.rsv3;
            this.mask = frame.mask;
            this.opcode = frame.opcode;
            this.payloadLength = frame.payloadLength;
            this.maskingKey = frame.maskingKey;
            this.payloadData = frame.payloadData;
        }

        /**
         * Build new frame.
         *
         * @return built frame.
         */
        public Frame build() {
            return new Frame(fin, rsv1, rsv2, rsv3, mask, opcode, payloadLength, maskingKey, payloadData);
        }

        /**
         * Set FIN flag.
         *
         * @param fin value to be set as FIN.
         * @return updated {@link Builder} instance.
         */
        public Builder fin(boolean fin) {
            this.fin = fin;
            return this;
        }

        /**
         * Set RSV1 flag.
         *
         * @param rsv1 value to be set as RSV1.
         * @return updated {@link Builder} instance.
         */
        public Builder rsv1(boolean rsv1) {
            this.rsv1 = rsv1;
            return this;
        }

        /**
         * Set RSV2 flag.
         *
         * @param rsv2 value to be set as RSV2.
         * @return updated {@link Builder} instance.
         */
        public Builder rsv2(boolean rsv2) {
            this.rsv2 = rsv2;
            return this;
        }

        /**
         * Set RSV3 flag.
         *
         * @param rsv3 value to be set as RSV3.
         * @return updated {@link Builder} instance.
         */
        public Builder rsv3(boolean rsv3) {
            this.rsv3 = rsv3;
            return this;
        }

        /**
         * Currently not used.
         *
         * @param mask not used.
         * @return updated {@link Builder} instance.
         */
        public Builder mask(boolean mask) {
            this.mask = mask;
            return this;
        }

        /**
         * Set opcode.
         *
         * @param opcode opcode to be set. (4 bits).
         * @return updated {@link Builder} instance.
         */
        public Builder opcode(byte opcode) {
            this.opcode = (byte) (opcode & 0x0f);
            return this;
        }

        /**
         * Set payload length.
         * <p>
         * Payload length is automatically set to payloadData length when {@link #payloadData(byte[])} is called. This
         * method can limit the data used for this frame by setting smaller value than payloadData.length.
         *
         * @param payloadLength payload length. Must not be greater than payloadData.length.
         * @return updated {@link Builder} instance.
         * @see #payloadData(byte[])
         */
        public Builder payloadLength(long payloadLength) {
            this.payloadLength = payloadLength;
            return this;
        }

        /**
         * Set masking key. Default value is {@code null}.
         *
         * @param maskingKey masking key.
         * @return updated {@link Builder} instance.
         */
        public Builder maskingKey(Integer maskingKey) {
            this.maskingKey = maskingKey;
            return this;
        }

        /**
         * Set payload data. {@link #payloadLength(long)} is also updated with payloadData.length.
         *
         * @param payloadData data to be set.
         * @return updated {@link Builder} instance.
         * @see #payloadLength(long)
         */
        public Builder payloadData(byte[] payloadData) {
            this.payloadData = payloadData;
            this.payloadLength = payloadData.length;
            return this;
        }
    }
}
