package org.glassfish.tyrus.test.basic;

import javax.net.websocket.MessageHandler;

/**
 *
 * @author dannycoward
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
