package org.glassfish.websocket.platform;

import javax.net.websocket.Endpoint;
import javax.net.websocket.MessageHandler;
import javax.net.websocket.RemoteEndpoint;

/**
 *
 * @author dannycoward
 */
public class MessageHandlerTextImpl implements MessageHandler.Text {
    private Endpoint endpoint;
    private RemoteEndpoint peer;

    MessageHandlerTextImpl(Endpoint endpoint, RemoteEndpoint peer) {
        this.endpoint = endpoint;
        this.peer = peer;
    }
    public void onMessage(String text) {
        throw new RuntimeException("not yet");
    }
}
