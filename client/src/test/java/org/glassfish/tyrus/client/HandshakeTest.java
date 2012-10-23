package org.glassfish.tyrus.client;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.net.websocket.Session;
import javax.net.websocket.extensions.Extension;
import javax.net.websocket.extensions.FrameHandler;
import org.glassfish.tyrus.server.Server;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests whether the HandShake parameters (sub-protoxols, extensions) are sent correctly.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class HandshakeTest {

    private CountDownLatch messageLatch;

    private String receivedMessage;

    private static final String SENT_MESSAGE = "hello";

    @Test
    public void testClient() {
        Server server = new Server("org.glassfish.tyrus.client.TestBean");
        server.start();

        try {
            messageLatch = new CountDownLatch(1);

            ArrayList<String> subprotocols = new ArrayList<String>();
            subprotocols.add("asd");
            subprotocols.add("ghi");

            ArrayList<Extension> extensions = new ArrayList<Extension>();
            extensions.add(new TestExtension("ext1"));
            extensions.add(new TestExtension("ext2"));

            DefaultClientEndpointConfiguration.Builder builder = new DefaultClientEndpointConfiguration.Builder(new URI("ws://localhost:8025/websockets/tests/echo"));
//            builder.protocols(subprotocols);
            builder.extensions(extensions);
            DefaultClientEndpointConfiguration dcec = builder.build();

            ClientManager client = ClientManager.createClient();
            client.connectToServer(new AbstractTestEndpoint() {
                @Override
                public void messageReceived(String message) {
                    receivedMessage = message;
                    messageLatch.countDown();
                    System.out.println("Received message = " + message);
                }

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

    private class TestExtension implements Extension {

        private final String name;

        private TestExtension(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Map<String, String> getParameters() {
            return null;
        }

        @Override
        public FrameHandler createIncomingFrameHandler(FrameHandler downstream) {
            return null;
        }

        @Override
        public FrameHandler createOutgoingFrameHandler(FrameHandler upstream) {
            return null;
        }
    }
}
