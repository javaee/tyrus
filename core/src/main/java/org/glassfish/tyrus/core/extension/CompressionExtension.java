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

package org.glassfish.tyrus.core.extension;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import org.glassfish.tyrus.core.ExtendedExtension;
import org.glassfish.tyrus.core.Frame;

/**
 * Compression Extensions for WebSocket
 * draft-ietf-hybi-permessage-compression-15
 * <p/>
 * http://tools.ietf.org/html/draft-ietf-hybi-permessage-compression-15
 * <p/>
 *
 * <pre>TODO:
 * - parameters (window sizes, context takeovers).
 * - context (some utility methods to get the typed params - T getParam(Class<T>))
 * </pre>
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class CompressionExtension implements ExtendedExtension {

    private static final Pool<byte[]> BYTE_ARRAY_POOL = new Pool<byte[]>() {
        @Override
        byte[] create() {
            return new byte[8192];
        }
    };

    private static final String INFLATER = CompressionExtension.class.getName() + ".INFLATER";
    private static final String DEFLATER = CompressionExtension.class.getName() + ".DEFLATER";

    private static final Logger LOGGER = Logger.getLogger(CompressionExtension.class.getName());
    private static final boolean DEBUG = LOGGER.isLoggable(Level.FINE);

    private static final byte[] TAIL = {0x00, 0x00, (byte) 0xff, (byte) 0xff};

    @Override
    public Frame processIncoming(ExtensionContext context, Frame frame) {
        final Inflater decompresser = (Inflater) context.getProperties().get(INFLATER);

        if (DEBUG) {
            LOGGER.fine("Incoming frame: " + frame);
        }

        if (frame.isRsv1() && !frame.isControlFrame()) {
            // Decompress the bytes
            final int payloadLength = (int) frame.getPayloadLength();

            List<PartialResultWithLength<byte[]>> wholeResult = new ArrayList<PartialResultWithLength<byte[]>>();
            int wholeResultLength = 0;

            int tmp = processCompressed(decompresser, frame.getPayloadData(), payloadLength, wholeResult);
            if (tmp == -1) {
                return frame;
            } else {
                wholeResultLength += tmp;
            }

            tmp = processCompressed(decompresser, TAIL, 4, wholeResult);
            if (tmp == -1) {
                return frame;
            } else {
                wholeResultLength += tmp;
            }

            byte[] completeResult = new byte[wholeResultLength];
            wholeResultLength = 0;
            for (PartialResultWithLength<byte[]> partialResult : wholeResult) {
                tmp = partialResult.getLength();
                final byte[] result = partialResult.getResult();
                System.arraycopy(result, 0, completeResult, wholeResultLength, tmp);
                BYTE_ARRAY_POOL.recycle(result);
                wholeResultLength += tmp;
            }

            return Frame.builder(frame).payloadData(completeResult).rsv1(false).build();
        } else {
            return frame;
        }
    }

    private int processCompressed(Inflater decompresser, byte[] compressed, int length, List<PartialResultWithLength<byte[]>> partialResults) {
        decompresser.setInput(compressed, 0, length);
        int decompressedLength = 0;
        do {
            byte[] result = BYTE_ARRAY_POOL.take();
            int partialResultLength;
            try {
                partialResultLength = decompresser.inflate(result);
            } catch (DataFormatException e) {
                LOGGER.log(Level.INFO, e.getMessage(), e);
                return -1;
            }

            if (partialResultLength != 0) {
                partialResults.add(new PartialResultWithLength<byte[]>(partialResultLength, result));
                decompressedLength += partialResultLength;
            } else {
                BYTE_ARRAY_POOL.recycle(result);
            }
        } while (decompresser.getRemaining() > 0);

        return decompressedLength;
    }

    @Override
    public Frame processOutgoing(ExtensionContext context, Frame frame) {
        final Deflater compresser = (Deflater) context.getProperties().get(DEFLATER);

        if (DEBUG) {
            LOGGER.fine("Outgoing frame: " + frame);
        }

        if (!frame.isControlFrame()) {

            List<PartialResultWithLength<byte[]>> wholeResult = new ArrayList<PartialResultWithLength<byte[]>>();
            int wholeResultLength = 0;

            // Compress the bytes
            final int payloadLength = (int) frame.getPayloadLength();
            compresser.setInput(frame.getPayloadData(), 0, payloadLength);

            int compressedDataLength;
            do {
                byte[] output = BYTE_ARRAY_POOL.take();
                compressedDataLength = compresser.deflate(output, 0, output.length, Deflater.SYNC_FLUSH);

                if (compressedDataLength > 0) {
                    wholeResult.add(new PartialResultWithLength<byte[]>(compressedDataLength, output));
                    wholeResultLength += compressedDataLength;
                } else {
                    BYTE_ARRAY_POOL.recycle(output);
                }
            } while (compressedDataLength > 0);

            byte[] completeResult = new byte[wholeResultLength];
            wholeResultLength = 0;
            for (PartialResultWithLength<byte[]> partialResult : wholeResult) {
                int tmp = partialResult.getLength();
                final byte[] result = partialResult.getResult();
                System.arraycopy(result, 0, completeResult, wholeResultLength, tmp);
                BYTE_ARRAY_POOL.recycle(result);
                wholeResultLength += tmp;
            }

            boolean strip = false;
            if (completeResult[completeResult.length - 4] == TAIL[0] &&
                    completeResult[completeResult.length - 3] == TAIL[1] &&
                    completeResult[completeResult.length - 2] == TAIL[2] &&
                    completeResult[completeResult.length - 1] == TAIL[3]
                    ) {
                strip = true;
            }

            return Frame.builder(frame).payloadData(completeResult).payloadLength(strip ? completeResult.length - 4 : completeResult.length).rsv1(true).build();
        } else {
            return frame;
        }
    }

    private void init(ExtensionContext context) {
        // TODO: configurable compression level
        Deflater compresser = new Deflater(9, true);
        Inflater decompresser = new Inflater(true);

        compresser.setStrategy(Deflater.DEFAULT_STRATEGY);

        context.getProperties().put(INFLATER, decompresser);
        context.getProperties().put(DEFLATER, compresser);
    }

    @Override
    public List<Parameter> onExtensionNegotiation(ExtensionContext context, List<Parameter> requestedParameters) {
        init(context);
        return Collections.<Parameter>emptyList();
    }

    @Override
    public void onHandshakeResponse(ExtensionContext context, List<Parameter> responseParameters) {
        init(context);
    }

    @Override
    public void destroy(ExtensionContext context) {
        final Inflater decompresser = (Inflater) context.getProperties().get(INFLATER);
        final Deflater compresser = (Deflater) context.getProperties().get(DEFLATER);

        context.getProperties().remove(DEFLATER);
        context.getProperties().remove(INFLATER);

        if (decompresser != null) {
            decompresser.end();
        }

        if (compresser != null) {
            compresser.end();
        }
    }

    @Override
    public String getName() {
        return "permessage-deflate";
    }

    @Override
    public List<Parameter> getParameters() {
        return Collections.<Parameter>emptyList();
    }


    /**
     * Generic pool that instances of T which are expensive to create.
     *
     * @author Jitendra Kotamraju
     * @author Pavel Bucek (pavel.bucek at oracle.com)
     */
    private static abstract class Pool<T> {

        // volatile since multiple threads may access queue reference
        private volatile WeakReference<ConcurrentLinkedQueue<T>> queue;

        /**
         * Gets a new object from the pool.
         * <p/>
         * <p/>
         * If no object is available in the pool, this method creates a new one.
         *
         * @return always non-null.
         */
        public final T take() {
            T t = getQueue().poll();
            if (t == null)
                return create();
            return t;
        }

        /**
         * Create new instance to be added into pool.
         *
         * @return new instance.
         */
        abstract T create();

        private ConcurrentLinkedQueue<T> getQueue() {
            WeakReference<ConcurrentLinkedQueue<T>> q = queue;
            if (q != null) {
                ConcurrentLinkedQueue<T> d = q.get();
                if (d != null)
                    return d;
            }

            // overwrite the queue
            ConcurrentLinkedQueue<T> d = new ConcurrentLinkedQueue<T>();
            queue = new WeakReference<ConcurrentLinkedQueue<T>>(d);

            return d;
        }

        /**
         * Returns an object back to the pool.
         */
        public final void recycle(T t) {
            getQueue().offer(t);
        }
    }

    private static class PartialResultWithLength<T> {
        private final int length;
        private final T result;

        private PartialResultWithLength(int length, T result) {
            this.length = length;
            this.result = result;
        }

        public int getLength() {
            return length;
        }

        public T getResult() {
            return result;
        }
    }
}
