package org.glassfish.tyrus.client;

import org.glassfish.tyrus.platform.EndpointAdapter;
import org.glassfish.tyrus.platform.main.Server;
import org.junit.Assert;
import org.junit.Test;

import javax.net.websocket.RemoteEndpoint;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests the implementation of {@link javax.net.websocket.ClientContainer}.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class ClientManagerTest {

    private CountDownLatch messageLatch;

    private String receivedMessage;

    private static final String SENT_MESSAGE = "hello";

    @Test
    public void testClient() {
        Server server = new Server("org.glassfish.tyrus.client.TestBean");
        server.start();

        messageLatch = new CountDownLatch(1);

        try {

            ClientManager client = ClientManager.createClient();
            client.connectToServer(new EndpointAdapter(){
//            client.openSocket("ws://localhost:8025/websockets/tests/echo", 100000, new EndpointAdapter() {

                @Override
                public void onConnect(RemoteEndpoint gs) {
                    try {
                        gs.sendString(SENT_MESSAGE);
                        System.out.println("Sent message: " + SENT_MESSAGE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onMessage(RemoteEndpoint gs, String message) {
                    receivedMessage = message;
                    messageLatch.countDown();
                    System.out.println("Received message = " + message);
                }
            }, new ProvidedClientConfiguration(new URI("ws://localhost:8025/websockets/tests/echo")));
            messageLatch.await(5, TimeUnit.SECONDS);

            Assert.assertTrue("The received message is the same as the sent one", receivedMessage.equals(SENT_MESSAGE));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }
}
