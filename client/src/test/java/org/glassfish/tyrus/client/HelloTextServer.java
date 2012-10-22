/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.glassfish.tyrus.client;

import javax.net.websocket.annotations.*;
import javax.net.websocket.*;
import java.net.*;
/**
 *
 * @author dannycoward
 */

    @WebSocketEndpoint("/hellotext")
public class HelloTextServer {
        
        @WebSocketOpen
        public void init(Session session) {
            System.out.println("HELLOSERVER opened");
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
            System.out.println("HELLOSERVER got  message: " + message + " from session " + session);
            try {
                session.getRemote().sendString("server hello");
            } catch (Exception e) {
                e.printStackTrace();
            }
            //return "got the message";
        }
}
