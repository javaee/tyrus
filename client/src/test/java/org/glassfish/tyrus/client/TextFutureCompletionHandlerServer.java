/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.glassfish.tyrus.client;

import javax.net.websocket.annotations.*;
import javax.net.websocket.*;
import java.net.*;
import java.util.concurrent.*;
/**
 *
 * @author dannycoward
 */

    @WebSocketEndpoint("/hellocompletionhandlerfuture")
public class TextFutureCompletionHandlerServer {
        
        @WebSocketOpen
        public void init(Session session) {
            System.out.println("HELLOCFSERVER opened");
            //System.out.println(" session container is " + session.getContainer());
            
            //MyStreamingEndpoint mse = new MyStreamingEndpoint();
            try {
                //URI uri = new URI("/streaming");
                //DefaultServerConfiguration dsc = new DefaultServerConfiguration(uri);
            
                //((ServerContainer) session.getContainer()).publishServer(mse, dsc);
                //System.out.println("Deployed at " + uri);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        @WebSocketMessage
        public void sayHello(String message, Session session) {
            System.out.println("HELLOCFSERVER got  message: " + message + " from session " + session);
            System.out.println("HELLOCFSERVER lets send one back in async mode with a future and completion handler");

            try {
                SendHandler sh = new SendHandler() {
                    public void setResult(SendResult sr) {
                        System.out.println("HELLOCFSERVER-HANDLER: Result was ok ? " + sr.isOK());
                    }
                };
                
                Future<SendResult> fsr = session.getRemote().sendString("server hello", sh);
                System.out.println("HELLOCFSERVER: Waiting on get...");
                SendResult sr = fsr.get();
                System.out.println("HELLOCFSERVER: .get returned " + sr);
            } catch (Exception e) {
                e.printStackTrace();
            }
            //return "got the message";
        }
}
