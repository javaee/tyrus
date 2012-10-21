package org.glassfish.tyrus;

import javax.net.websocket.EncodeException;
import javax.net.websocket.Encoder;

/**
 * Fall-back encoder - encoders any object to string using {@link Object#toString()} method.
 *
 * @author Martin Matula (martin.matula at oracle.com)
 */
public class ToStringEncoder implements Encoder.Text<Object> {
    public static ToStringEncoder INSTANCE = new ToStringEncoder();

    @Override
    public String encode(Object object) throws EncodeException {
        return object.toString();
    }
}
