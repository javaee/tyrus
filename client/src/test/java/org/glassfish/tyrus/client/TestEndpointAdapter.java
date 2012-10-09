package org.glassfish.tyrus.client;


import javax.net.websocket.Endpoint;
import javax.net.websocket.Session;

public class TestEndpointAdapter extends Endpoint {

    public void messageReceived(String message){};

    public void onMessage(byte[] message){};

    public void onOpen(Session session){};
}
