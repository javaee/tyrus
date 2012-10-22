/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.glassfish.tyrus.client;

import javax.net.websocket.*;
import java.io.*;
/**
 *
 * @author dannycoward
 */
public class BlockingStreamingTextClient extends Endpoint {
    boolean gotSomethingBack = false;
    
    public void onOpen(Session session) {
        System.out.println("BLOCKINGCLIENT opened !");
        
        send(session);
        
        final Session theSession = session;
        
        session.addMessageHandler(new MessageHandler.CharacterStream() {
            StringBuilder sb = new StringBuilder();
            public void onMessage(Reader r) {
                try {
                    int i = 0;
                    while  ((i = r.read()) != -1) {
                        System.out.println("BLOCKINGCLIENT: " + (char) i);
                        sb.append((char) i);
                    }
                    System.out.println("BLOCKINGCLIENT WHOLE MESSAGE = " + sb.toString());
                    gotSomethingBack = true;
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            
                
            } 
            
            
            
        });
        
        
            //session.getRemote().sendString("SClient says stream");
            
        
    }
    
    public void send(Session session) {
        try {
            for (int i = 0; i < 10; i++) {
                session.getRemote().sendPartialString("blk" + i + "", false);
                //Thread.sleep(500);
            }
            session.getRemote().sendPartialString("END", true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }
    
    
}
