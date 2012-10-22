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
public class HelloTextClient extends Endpoint {
    boolean gotSomethingBack = false;
    
    public void onOpen(Session session) {
        System.out.println("HELLOCLIENT opened !!");
        try {
            session.addMessageHandler(new MessageHandler.Text() {
                public void onMessage(String text) {
                    System.out.println("HELLOCLIENT received: " + text);
                    gotSomethingBack = true;
                }
            });
            session.getRemote().sendString("Client says hello");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    
}
