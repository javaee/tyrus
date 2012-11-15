/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 - 2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus;

import javax.websocket.DecodeException;
import javax.websocket.Decoder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Collection of decoders for all primitive types.
 *
 * @author Martin Matula (martin.matula at oracle.com)
 * @author Danny Coward (danny.coward at oracle.com)
 */
public abstract class PrimitiveDecoders<T> implements Decoder.Text<T> {
    public static final List<Decoder.Text<?>> ALL;

    public static final List<DecoderWrapper> ALL_WRAPPED;

    static {
        Decoder.Text<?>[] decoders = new Decoder.Text[]{
                new PrimitiveDecoders<Boolean>() {
                    @Override
                    public Boolean decode(String s) throws DecodeException {
                        return Boolean.valueOf(s);
                    }
                },
                new PrimitiveDecoders<Byte>() {
                    @Override
                    public Byte decode(String s) throws DecodeException {
                        return Byte.valueOf(s);
                    }
                },
                new PrimitiveDecoders<Character>() {
                    @Override
                    public Character decode(String s) throws DecodeException {
                        return s.charAt(0);
                    }
                },
                new PrimitiveDecoders<Double>() {
                    @Override
                    public Double decode(String s) throws DecodeException {
                        return Double.valueOf(s);
                    }
                },
                new PrimitiveDecoders<Float>() {
                    @Override
                    public Float decode(String s) throws DecodeException {
                        return Float.valueOf(s);
                    }
                },
                new PrimitiveDecoders<Integer>() {
                    @Override
                    public Integer decode(String s) throws DecodeException {
                        return Integer.valueOf(s);
                    }
                },
                new PrimitiveDecoders<Long>() {
                    @Override
                    public Long decode(String s) throws DecodeException {
                        return Long.valueOf(s);
                    }
                },
                new PrimitiveDecoders<Short>() {
                    @Override
                    public Short decode(String s) throws DecodeException {
                        return Short.valueOf(s);
                    }
                },
        };

        ALL = Collections.unmodifiableList(Arrays.asList(decoders));

        ALL_WRAPPED = getWrappedAll();
    }

    @Override
    public boolean willDecode(String s) {
        return true;
    }

    private static List<DecoderWrapper> getWrappedAll(){
        List<DecoderWrapper> result = new ArrayList<DecoderWrapper>();

        for (Decoder dec : ALL) {
            Class<?> type = ReflectionHelper.getClassType(dec.getClass(), Decoder.Text.class);
            result.add(new DecoderWrapper(dec,type, dec.getClass()));
        }

        return result;
    }
}
