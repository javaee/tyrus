package org.glassfish.tyrus.test.basic;

import javax.net.websocket.MessageHandler;

/**
 * @author Danny Coward (danny.coward at oracle.com)
 */
public class TestTextMessageHandler implements MessageHandler.Text {

    private TestEndpointAdapter endpointAdapter;

    TestTextMessageHandler(TestEndpointAdapter endpointAdapter) {
        this.endpointAdapter = endpointAdapter;
    }

    @Override
    public void onMessage(String text) {
        endpointAdapter.onMessage(text);
    }
}
