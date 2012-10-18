package org.glassfish.tyrus.platform.encoder;

import javax.net.websocket.EncodeException;
import javax.net.websocket.Encoder;
import java.nio.ByteBuffer;

/**
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class BinaryEncoderNoOp implements Encoder.Binary{

    @Override
    public ByteBuffer encode(Object object) throws EncodeException {
        return (ByteBuffer) object;
    }
}
