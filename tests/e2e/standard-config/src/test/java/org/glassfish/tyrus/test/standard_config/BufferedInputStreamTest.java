package org.glassfish.tyrus.test.standard_config;

import java.io.*;
import junit.framework.Assert;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;
import org.junit.Test;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;

import static org.junit.Assert.assertTrue;

/**
 * Tests the BufferedInputStream and bug fix TYRUS-274
 *
 * Client opens DataOutputStream to write int and server uses DataInputStream
 * to read int and verify the message
 *
 * @author Raghuram Krishnamchari (raghuramcbz at gmail.com) jira/github user: raghucbz
 */
public class BufferedInputStreamTest extends TestContainer {
    public static int MESSAGE = 1234;

    @Test
    public void testClient() throws DeploymentException {
        final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();
        Server server = startServer(BufferedInputStreamEndpoint.class);

        try {
            BufferedInputStreamClient bisc = new BufferedInputStreamClient();
            ClientManager client = ClientManager.createClient();
            client.connectToServer(bisc, cec, getURI(BufferedInputStreamEndpoint.class));

            Thread.currentThread().sleep(3000); // just sleep while server gets the message and prints result
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    /**
     * BufferedInputStream Server Endpoint
     * @author Raghuram Krishnamchari (raghuramcbz at gmail.com) jira/github user: raghucbz
     */

    @ServerEndpoint(value = "/bufferedinputstreamserver")
    public static class BufferedInputStreamEndpoint {

        @OnOpen
        public void init(Session session) {
            System.out.println("BufferedInputStreamServer opened");
            session.addMessageHandler(new MyMessageHandler(session));
        }

        class MyMessageHandler implements MessageHandler.Whole<InputStream> {
            private Session session;

            MyMessageHandler(Session session) {
                this.session = session;
            }

            @Override
            public void onMessage(InputStream inputStream) {
                System.out.println("BufferedInputStreamServer got message: " + inputStream);
                try {
                    DataInputStream dataInputStream = new DataInputStream(inputStream);
                    int messageReceived = dataInputStream.readInt();

                    assertTrue("Server did not get the right message: " + messageReceived, messageReceived == BufferedInputStreamTest.MESSAGE);
                    System.out.println("Server successfully got message: " + messageReceived);
                } catch (Exception e) {
                    System.out.println("BufferedInputStreamServer exception: " + e);
                    e.printStackTrace();
                    Assert.fail();
                }
            }
        }

    }

    /**
     * BufferedInputStream Client Endpoint
     * @author Raghuram Krishnamchari (raghuramcbz at gmail.com) jira/github user: raghucbz
     */
    public class BufferedInputStreamClient extends Endpoint {

        @Override
        public void onOpen(Session session, EndpointConfig EndpointConfig) {
            System.out.println("BufferedInputStreamClient opened !!");
            try {
                OutputStream outputStream = session.getBasicRemote().getSendStream();
                DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
                dataOutputStream.writeInt(MESSAGE);
                dataOutputStream.close();
                System.out.println("## BufferedInputStreamClient - binary message sent");

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onError(Session session, Throwable thr) {
            thr.printStackTrace();
        }
    }
}