/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2014 Oracle and/or its affiliates. All rights reserved.
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

import java.net.URI;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.tyrus.core.l10n.LocalizationMessages;

/**
 * Utility methods shared among Tyrus modules.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class Utils {

    private static final Logger LOGGER = Logger.getLogger(Utils.class.getName());

    /**
     * Define to {@link String} conversion for various types.
     *
     * @param <T> type for which is conversion defined.
     */
    public static abstract class Stringifier<T> {

        /**
         * Convert object to {@link String}.
         *
         * @param t object to be converted.
         * @return {@link String} representation of given object.
         */
        abstract String toString(T t);
    }

    /**
     * Parse header value - splits multiple values (quoted, unquoted) separated by
     * comma.
     *
     * @param headerValue string containing header values.
     * @return split list of values.
     */
    public static List<String> parseHeaderValue(String headerValue) {
        List<String> values = new ArrayList<String>();

        // 0 - start of new header value
        // 1 - non-quoted value
        // 2 - quoted value
        // 3 - end of quoted value (after '\"', before ',')
        int state = 0;
        StringBuilder sb = new StringBuilder();

        for (char c : headerValue.toCharArray()) {
            switch (state) {
                case 0:
                    // ignore trailing whitespace
                    if (Character.isWhitespace(c)) {
                        break;
                    }
                    if (c == '\"') {
                        state = 2;
                        sb.append(c);
                        break;
                    }
                    sb.append(c);
                    state = 1;
                    break;
                case 1:
                    if (c != ',') {
                        sb.append(c);
                    } else {
                        values.add(sb.toString());
                        sb = new StringBuilder();
                        state = 0;
                    }
                    break;
                case 2:
                    if (c != '\"') {
                        sb.append(c);
                    } else {
                        sb.append(c);
                        values.add(sb.toString());
                        sb = new StringBuilder();
                        state = 3;
                    }
                    break;
                case 3:
                    if (Character.isWhitespace(c)) {
                        break;
                    }
                    if (c == ',') {
                        state = 0;
                    }

                    // error - ignore for now.
                    break;
                default:
                    // should not happen
                    break;
            }
        }

        if (sb.length() > 0) {
            values.add(sb.toString());
        }

        return values;
    }

    /**
     * Creates the array of bytes containing the bytes from the position to the limit of the {@link ByteBuffer}.
     *
     * @param buffer where the bytes are taken from.
     * @return array of bytes containing the bytes from the position to the limit of the {@link ByteBuffer}.
     */
    public static byte[] getRemainingArray(ByteBuffer buffer) {
        if (buffer == null) {
            return new byte[0];
        }

        byte[] ret = new byte[buffer.remaining()];

        if (buffer.hasArray()) {
            byte[] array = buffer.array();
            System.arraycopy(array, buffer.arrayOffset() + buffer.position(), ret, 0, ret.length);
        } else {
            buffer.get(ret);
        }

        return ret;
    }

    /**
     * Creates single {@link String} value from provided List by calling {@link Object#toString()} on each item
     * and separating existing ones with {@code ", "}.
     *
     * @param list to be serialized.
     * @param <T>  item type.
     * @return single {@link String} containing all items from provided list.
     */
    public static <T> String getHeaderFromList(List<T> list) {
        StringBuilder sb = new StringBuilder();
        Iterator<T> it = list.iterator();
        while (it.hasNext()) {
            sb.append(it.next());
            if (it.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    /**
     * Get list of strings from List&lt;T&gt;.
     *
     * @param list        list to be converted.
     * @param stringifier strignifier used for conversion. When {@code null}, {@link Object#toString()} method will be used.
     * @return converted list.
     */
    public static <T> List<String> getStringList(List<T> list, Stringifier<T> stringifier) {
        List<String> result = new ArrayList<String>();
        for (T item : list) {
            if (stringifier != null) {
                result.add(stringifier.toString(item));
            } else {
                result.add(item.toString());
            }
        }
        return result;
    }

    /**
     * Convert list of values to singe {@link String} usable as HTTP header value.
     *
     * @param list        list of values.
     * @param stringifier strignifier used for conversion. When {@code null}, {@link Object#toString()} method will be used.
     * @return serialized list.
     */
    public static <T> String getHeaderFromList(List<T> list, Stringifier<T> stringifier) {
        StringBuilder sb = new StringBuilder();
        Iterator<T> it = list.iterator();
        while (it.hasNext()) {
            if (stringifier != null) {
                sb.append(stringifier.toString(it.next()));
            } else {
                sb.append(it.next());
            }
            if (it.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    /**
     * Check for null. Throws {@link IllegalArgumentException} if provided value is null.
     *
     * @param reference    object to check.
     * @param errorMessage message to be set to thrown {@link IllegalArgumentException}.
     * @param <T>          object type.
     */
    public static <T> void checkNotNull(T reference, String errorMessage) {
        if (reference == null) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    /**
     * Convert {@code long} to {@code byte[]}.
     *
     * @param value to be converted.
     * @return converted value.
     */
    public static byte[] toArray(long value) {
        byte[] b = new byte[8];
        for (int i = 7; i >= 0 && value > 0; i--) {
            b[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return b;
    }

    /**
     * Convert {@code byte[]} to {@code long}.
     *
     * @param bytes to be converted.
     * @param start start index.
     * @param end   end index.
     * @return converted value.
     */
    public static long toLong(byte[] bytes, int start, int end) {
        long value = 0;
        for (int i = start; i < end; i++) {
            value <<= 8;
            value ^= (long) bytes[i] & 0xFF;
        }
        return value;
    }

    public static List<String> toString(byte[] bytes) {
        return toString(bytes, 0, bytes.length);
    }

    public static List<String> toString(byte[] bytes, int start, int end) {
        List<String> list = new ArrayList<String>();
        for (int i = start; i < end; i++) {
            list.add(Integer.toHexString(bytes[i] & 0xFF).toUpperCase(Locale.US));
        }
        return list;
    }

    /**
     * Concatenates two buffers into one. If buffer given as first argument has enough space for putting
     * the other one, it will be done and the original buffer will be returned. Otherwise new buffer will
     * be created.
     *
     * @param buffer  first buffer.
     * @param buffer1 second buffer.
     * @return concatenation.
     */
    public static ByteBuffer appendBuffers(ByteBuffer buffer, ByteBuffer buffer1, int incomingBufferSize, int BUFFER_STEP_SIZE) {

        final int limit = buffer.limit();
        final int capacity = buffer.capacity();
        final int remaining = buffer.remaining();
        final int len = buffer1.remaining();

        // buffer1 will be appended to buffer
        if (len < (capacity - limit)) {

            buffer.mark();
            buffer.position(limit);
            buffer.limit(capacity);
            buffer.put(buffer1);
            buffer.limit(limit + len);
            buffer.reset();
            return buffer;
            // Remaining data is moved to left. Then new data is appended
        } else if (remaining + len < capacity) {
            buffer.compact();
            buffer.put(buffer1);
            buffer.flip();
            return buffer;
            // create new buffer
        } else {
            int newSize = remaining + len;
            if (newSize > incomingBufferSize) {
                throw new IllegalArgumentException(LocalizationMessages.BUFFER_OVERFLOW());
            } else {
                final int roundedSize = (newSize % BUFFER_STEP_SIZE) > 0 ? ((newSize / BUFFER_STEP_SIZE) + 1) * BUFFER_STEP_SIZE : newSize;
                final ByteBuffer result = ByteBuffer.allocate(roundedSize > incomingBufferSize ? newSize : roundedSize);
                result.put(buffer);
                result.put(buffer1);
                result.flip();
                return result;
            }
        }
    }

    /**
     * Get typed property from generic property map.
     *
     * @param properties property map.
     * @param key        key of value to be retrieved.
     * @param type       type of value to be retrieved.
     * @return typed value or {@code null} if property is not set or value is not assignable.
     */
    public static <T> T getProperty(Map<String, Object> properties, String key, Class<T> type) {
        return getProperty(properties, key, type, null);
    }

    /**
     * Get typed property from generic property map.
     *
     * @param properties   property map.
     * @param key          key of value to be retrieved.
     * @param type         type of value to be retrieved.
     * @param defaultValue value returned when record does not exist in supplied map.
     * @return typed value or {@code null} if property is not set or value is not assignable.
     */
    public static <T> T getProperty(final Map<String, Object> properties, final String key, final Class<T> type, final T defaultValue) {
        if (properties != null) {
            final Object o = properties.get(key);
            if (o != null) {
                try {
                    if (type.isAssignableFrom(o.getClass())) {
                        //noinspection unchecked
                        return (T) o;
                    } else if (type.equals(Integer.class)) {
                        //noinspection unchecked
                        return (T) Integer.valueOf(o.toString());
                    } else if (type.equals(Long.class)) {
                        //noinspection unchecked
                        return (T) Long.valueOf(o.toString());
                    } else if (type.equals(Boolean.class)) {
                        //noinspection unchecked
                        return (T) (Boolean) (o.toString().equals("1") || Boolean.valueOf(o.toString()));
                    } else {
                        return null;
                    }
                } catch (final Throwable t) {
                    LOGGER.log(Level.CONFIG,
                            String.format("Invalid type of configuration property of %s (%s), %s cannot be cast to %s",
                                    key, o.toString(), o.getClass().toString(), type.toString())
                    );
                    return null;
                }
            }
        }

        return defaultValue;
    }

    /**
     * Get port from provided {@link URI}.
     * <p/>
     * Expected schemes are {@code "ws"} and {@code "wss"} and this method will return {@code 80} or
     * {@code 443} when the port is not explicitly set in the provided {@link URI}.
     *
     * @param uri provided uri.
     * @return port number which should be used for creating connections/etc.
     */
    public static int getWsPort(URI uri) {
        return getWsPort(uri, uri.getScheme());
    }

    /**
     * Get port from provided {@link URI}.
     * <p/>
     * Expected schemes are {@code "ws"} and {@code "wss"} and this method will return {@code 80} or
     * {@code 443} when the port is not explicitly set in the provided {@link URI}.
     *
     * @param uri    provided uri.
     * @param scheme scheme to be used when checking for {@code "ws"} and {@code "wss"}.
     * @return port number which should be used for creating connections/etc.
     */
    public static int getWsPort(URI uri, String scheme) {
        if (uri.getPort() == -1) {
            if ("wss".equals(scheme)) {
                return 443;
            } else if ("ws".equals(scheme)) {
                return 80;
            }
        } else {
            return uri.getPort();
        }

        return -1;
    }

    /**
     * Parse HTTP date.
     * <p/>
     * HTTP applications have historically allowed three different formats for the representation of date/time stamps:
     * <ul>
     * <li>{@code Sun, 06 Nov 1994 08:49:37 GMT} (RFC 822, updated by RFC 1123)</li>
     * <li>{@code Sunday, 06-Nov-94 08:49:37 GMT} (RFC 850, obsoleted by RFC 1036)</li>
     * <li>{@code Sun Nov  6 08:49:37 1994} (ANSI C's asctime() format)</li>
     * </ul>
     *
     * @param stringValue String value to be parsed.
     * @return A {@link Date} parsed from the string.
     * @throws ParseException if the specified string cannot be parsed in neither of all three HTTP date formats.
     */
    public static Date parseHttpDate(String stringValue) throws ParseException {
        SimpleDateFormat formatRfc1123 = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
        try {
            return formatRfc1123.parse(stringValue);
        } catch (ParseException e) {
            SimpleDateFormat formatRfc1036 = new SimpleDateFormat("EEE, dd-MMM-yy HH:mm:ss zzz");
            try {
                return formatRfc1036.parse(stringValue);
            } catch (ParseException e1) {
                SimpleDateFormat formatAnsiCAsc = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy");
                return formatAnsiCAsc.parse(stringValue);
            }
        }
    }
}
