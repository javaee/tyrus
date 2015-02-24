/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.core.coder;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.DecodeException;
import javax.websocket.Decoder;

import org.glassfish.tyrus.core.ReflectionHelper;

/**
 * Collection of decoders for all primitive types.
 *
 * @author Martin Matula (martin.matula at oracle.com)
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public abstract class PrimitiveDecoders<T> extends CoderAdapter implements Decoder.Text<T> {
    public static final List<Class<? extends Decoder>> ALL;
    public static final Map<Class<?>, Decoder.Text<?>> ALL_INSTANCES;

    static {
        ALL = Collections.unmodifiableList(Arrays.<Class<? extends Decoder>>asList(
                BooleanDecoder.class,
                ByteDecoder.class,
                CharacterDecoder.class,
                DoubleDecoder.class,
                FloatDecoder.class,
                IntegerDecoder.class,
                LongDecoder.class,
                ShortDecoder.class
        ));

        ALL_INSTANCES = getAllInstances();
    }

    @Override
    public boolean willDecode(String s) {
        return true;
    }

    public static class BooleanDecoder extends PrimitiveDecoders<Boolean> {
        @Override
        public Boolean decode(String s) throws DecodeException {
            Boolean result;

            try {
                result = Boolean.valueOf(s);
            } catch (Exception e) {
                throw new DecodeException(s, "Decoding failed", e);
            }

            return result;
        }
    }

    public static class ByteDecoder extends PrimitiveDecoders<Byte> {
        @Override
        public Byte decode(String s) throws DecodeException {
            Byte result;

            try {
                result = Byte.valueOf(s);
            } catch (Exception e) {
                throw new DecodeException(s, "Decoding failed", e);
            }

            return result;
        }
    }

    public static class CharacterDecoder extends PrimitiveDecoders<Character> {
        @Override
        public Character decode(String s) throws DecodeException {
            Character result;

            try {
                result = s.charAt(0);
            } catch (Exception e) {
                throw new DecodeException(s, "Decoding failed", e);
            }

            return result;
        }
    }

    public static class DoubleDecoder extends PrimitiveDecoders<Double> {
        @Override
        public Double decode(String s) throws DecodeException {
            Double result;

            try {
                result = Double.valueOf(s);
            } catch (Exception e) {
                throw new DecodeException(s, "Decoding failed", e);
            }

            return result;
        }
    }

    public static class FloatDecoder extends PrimitiveDecoders<Float> {
        @Override
        public Float decode(String s) throws DecodeException {
            Float result;

            try {
                result = Float.valueOf(s);
            } catch (Exception e) {
                throw new DecodeException(s, "Decoding failed", e);
            }

            return result;
        }
    }

    public static class IntegerDecoder extends PrimitiveDecoders<Integer> {
        @Override
        public Integer decode(String s) throws DecodeException {
            Integer result;

            try {
                result = Integer.valueOf(s);
            } catch (Exception e) {
                throw new DecodeException(s, "Decoding failed", e);
            }

            return result;
        }
    }

    public static class LongDecoder extends PrimitiveDecoders<Long> {
        @Override
        public Long decode(String s) throws DecodeException {
            Long result;

            try {
                result = Long.valueOf(s);
            } catch (Exception e) {
                throw new DecodeException(s, "Decoding failed", e);
            }

            return result;
        }
    }

    public static class ShortDecoder extends PrimitiveDecoders<Short> {
        @Override
        public Short decode(String s) throws DecodeException {
            Short result;

            try {
                result = Short.valueOf(s);
            } catch (Exception e) {
                throw new DecodeException(s, "Decoding failed", e);
            }

            return result;
        }
    }

    private static Map<Class<?>, Decoder.Text<?>> getAllInstances() {
        Map<Class<?>, Decoder.Text<?>> map = new HashMap<Class<?>, Decoder.Text<?>>();

        for (Class<? extends Decoder> dec : ALL) {
            Class<?> type = ReflectionHelper.getClassType(dec, Decoder.Text.class);
            try {
                map.put(type, (Decoder.Text<?>) dec.newInstance());
            } catch (Exception e) {
                Logger.getLogger(PrimitiveDecoders.class.getName())
                      .log(Level.WARNING, String.format("Decoder %s could not have been instantiated.", dec));
            }
        }

        return map;
    }

}
