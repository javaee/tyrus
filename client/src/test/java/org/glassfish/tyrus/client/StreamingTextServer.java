/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.glassfish.tyrus.client;

import javax.net.websocket.Endpoint;
import javax.net.websocket.Session;
import javax.net.websocket.MessageHandler;
import javax.net.websocket.annotations.*;
/**
 *
 * @author dannycoward
 */
    @WebSocketEndpoint("/streamingtext")
public class StreamingTextServer extends Endpoint {
    
    @WebSocketOpen
    public void onOpen(Session session) {
        System.out.println("STREAMINGSERVER opened !");
        
        session.addMessageHandler(new MessageHandler.AsyncText() {
            StringBuilder sb = new StringBuilder();
            @Override
            public void onMessagePart(String text, boolean last) {
                System.out.println("STREAMINGSERVER piece came: " + text);
                sb.append(text);
                if (last) {
                    System.out.println("STREAMINGSERVER whole message: " + sb.toString());
                    sb = new StringBuilder();
                }
            }
            
        });
        
        try {
            //session.getRemote().sendString("send me something !");
            System.out.println(session.getRemote());
            session.getRemote().sendPartialString("thank ", false);
            session.getRemote().sendPartialString("you ", false);
            session.getRemote().sendPartialString("very ", false);
            session.getRemote().sendPartialString("much ", false);
            session.getRemote().sendPartialString("!", true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        
        
    }
    
}

