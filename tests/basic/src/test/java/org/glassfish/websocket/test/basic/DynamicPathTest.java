package org.glassfish.websocket.test.basic;

import org.glassfish.websocket.client.WebSocketClient;
import org.glassfish.websocket.platform.EndpointAdapter;
import org.glassfish.websocket.platform.main.Server;
import org.glassfish.websocket.spi.SPIRemoteEndpoint;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests the dynamic path including the * path pattern
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class DynamicPathTest {

    private CountDownLatch messageLatch;

    private String receivedMessage;

    private static final String BASIC_MESSAGE = "Hello World";

    private static final String FOO_BAR_SEGMENT = "/foo/bar";

    private static final String DYNAMIC_SEGMENT = "/dynamo";

    @Ignore
    @Test
    public void testDynamicPath(){
        Server server = new Server(org.glassfish.websocket.test.basic.bean.DynamicPathTestBean.class);
        server.start();

        try{
            this.testPath("",BASIC_MESSAGE,"A");
            this.testPath(FOO_BAR_SEGMENT,BASIC_MESSAGE,"FB");
            this.testPath(DYNAMIC_SEGMENT,BASIC_MESSAGE,"*");
        }catch (Exception e){
            e.printStackTrace();
        }finally{
            server.stop();
        }
    }

    private void testPath(String segmentPath, final String message,final String response) {
        messageLatch = new CountDownLatch(1);
        try {
            WebSocketClient client = WebSocketClient.createClient();
            client.openSocket("ws://localhost:8025/websockets/tests/dynamicpath" + segmentPath, 10000,new EndpointAdapter() {
                @Override
                public void onConnect(SPIRemoteEndpoint p) {
                    try {
                        p.send(message);
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
            System.out.println("Expected response:"+response+".");
            System.out.println("Real response:"+receivedMessage+".");
            Assert.assertTrue("The received message does not equal the required response", receivedMessage.equals(response));
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
