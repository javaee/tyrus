package org.glassfish.tyrus.test.basic;

import org.glassfish.tyrus.client.WebSocketClient;
import org.glassfish.tyrus.platform.EndpointAdapter;
import org.glassfish.tyrus.platform.main.Server;
import org.glassfish.tyrus.spi.SPIRemoteEndpoint;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests the correct behavior of various orders of parameters of methods annotated with {@link javax.net.websocket.annotations.WebSocketMessage}
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class MessageParamOrderTest {

    private CountDownLatch messageLatch;

    private String receivedMessage;

    private static final String SENT_MESSAGE = "Hello World";

    @Test
    public void testHello() {
        Server server = new Server(org.glassfish.tyrus.test.basic.bean.HelloTestBean.class);
        server.start();
        try {
            messageLatch = new CountDownLatch(1);

            WebSocketClient client = WebSocketClient.createClient();
            client.openSocket("ws://localhost:8025/websockets/tests/hello", 10000, new EndpointAdapter() {

                @Override
                public void onConnect(SPIRemoteEndpoint p) {
                    try {
                        p.send(SENT_MESSAGE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onMessage(SPIRemoteEndpoint p, String message) {
                    receivedMessage = message;
                    messageLatch.countDown();
                }
            });
            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertTrue("The received message is the same as the sent one", receivedMessage.equals(SENT_MESSAGE));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    @Test
    public void testOther() {
        Server server = new Server(org.glassfish.tyrus.test.basic.bean.MessageParamOrderTestBean.class);
        server.start();
        try {
            messageLatch = new CountDownLatch(1);

            WebSocketClient client = WebSocketClient.createClient();
            client.openSocket("ws://localhost:8025/websockets/tests/hello", 10000, new EndpointAdapter() {

                @Override
                public void onConnect(SPIRemoteEndpoint p) {
                    try {
                        p.send(SENT_MESSAGE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onMessage(SPIRemoteEndpoint p, String message) {
                    receivedMessage = message;
                    messageLatch.countDown();
                }
            });
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
