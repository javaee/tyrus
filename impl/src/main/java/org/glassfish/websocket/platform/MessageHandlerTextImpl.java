package org.glassfish.websocket.platform;

import org.glassfish.websocket.api.*;
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

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
