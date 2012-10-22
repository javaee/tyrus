/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.glassfish.tyrus.client;

import javax.net.websocket.*;
/**
 *
 * @author dannycoward
 */
public class StreamingTextClient extends Endpoint {
     boolean gotSomethingBack = false;
    
    public void onOpen(Session session) {
        System.out.println("STREAMINGCLIENT opened !");
        
        try {
            //session.getRemote().sendString("hello");
            session.getRemote().sendPartialString("here ", false);
            session.getRemote().sendPartialString("is ", false);
            session.getRemote().sendPartialString("a ", false);
            session.getRemote().sendPartialString("stream.", true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        final Session theSession = session;
        session.addMessageHandler(new MessageHandler.AsyncText() {
            StringBuilder sb = new StringBuilder();
            public void onMessagePart(String text, boolean last) {
                System.out.println("STREAMINGCLIENT piece came: " + text);
                sb.append(text);
                if (last) {
                    System.out.println("STREAMINGCLIENT received whole message: " + sb.toString());
                    sb = new StringBuilder();
                    gotSomethingBack = true;
                }
                
                
            }
   
        });
            
        
    }
    
    
}
