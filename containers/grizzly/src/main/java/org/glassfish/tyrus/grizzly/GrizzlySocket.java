package org.glassfish.tyrus.grizzly;

import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.websockets.DefaultWebSocket;
import org.glassfish.grizzly.websockets.ProtocolHandler;
import org.glassfish.grizzly.websockets.WebSocketListener;

/**
 * Socket used for the Grizzly SPI implementation.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class GrizzlySocket extends DefaultWebSocket {

    private HttpRequestPacket request;

    public GrizzlySocket(ProtocolHandler protocolHandler, HttpRequestPacket request, WebSocketListener... listeners) {
        super(protocolHandler, request, listeners);
        this.request = request;
    }

    /**
     * Returns the http request used for the handshake before opening this socket.
     *
     * @return http request used for the handshake.
     */
    public HttpRequestPacket getRequest() {
        return request;
    }
}
