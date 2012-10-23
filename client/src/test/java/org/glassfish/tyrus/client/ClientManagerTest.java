package org.glassfish.tyrus.client;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.net.websocket.Session;
import org.glassfish.tyrus.server.Server;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests the implementation of {@link javax.net.websocket.ClientContainer}.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class ClientManagerTest {

    private String receivedMessage;

    private static final String SENT_MESSAGE = "hello";

    @Ignore
    @Test
    public void testClient() {
        Server server = new Server("org.glassfish.tyrus.client.TestBean");
        server.start();

        CountDownLatch messageLatch = new CountDownLatch(1);

        try {
            DefaultClientEndpointConfiguration.Builder builder = new DefaultClientEndpointConfiguration.Builder(new URI("ws://localhost:8025/websockets/tests/echo"));
            DefaultClientEndpointConfiguration dcec = builder.build();

            ClientManager client = ClientManager.createClient();
            client.connectToServer(new AbstractTestEndpoint() {

                @Override
                public void onOpen(Session session) {
                    try {
                        session.addMessageHandler(new TestTextMessageHandler(this));
                        session.getRemote().sendString(SENT_MESSAGE);
                        System.out.println("Sent message: " + SENT_MESSAGE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void messageReceived(String message) {
                    receivedMessage = message;
                    System.out.println("Received message = " + message);
                }
            }, dcec);

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
