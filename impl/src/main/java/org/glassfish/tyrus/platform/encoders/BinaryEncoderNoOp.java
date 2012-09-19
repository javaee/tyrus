package org.glassfish.tyrus.platform.encoders;

import javax.net.websocket.EncodeException;
import javax.net.websocket.Encoder;

/**
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class BinaryEncoderNoOp implements Encoder.Binary{

    @Override
    public byte[] encode(Object object) throws EncodeException {
        return (byte[]) object;
    }
}
