/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.glassfish.tyrus.client;

import javax.net.websocket.Endpoint;
import javax.net.websocket.Session;
import javax.net.websocket.MessageHandler;
import javax.net.websocket.annotations.*;
import java.io.*;
/**
 *
 * @author dannycoward
 */
    @WebSocketEndpoint("/blockingstreaming")
public class BlockingStreamingTextServer extends Endpoint {
        

    
    class MyCharacterStreamHandler implements MessageHandler.CharacterStream {
        Session session;
        StringBuilder sb = new StringBuilder();
        
        MyCharacterStreamHandler(Session session) {
            this.session = session;
        }
        @Override
        public void onMessage(Reader r) {
            System.out.println("BLOCKINGSTREAMSERVER: on message reader called");
            
            try {
                int i = 0;
                while ((i=r.read()) != -1) {
                    sb.append((char) i);
                    System.out.println("BLOCKINGSTREAMSERVER:" + (char) i);

                }
                System.out.println("BLOCKINGSTREAMSERVER - fully processed message: " + sb.toString());
                
                
                Writer w = session.getRemote().getSendWriter();
                w.write("hi there");
                pause();
                w.write("you");
                pause();
                w.write("how are things ?");
                pause();
                w.close();


                
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }   
    
    private void pause() {
        try {
            Thread.sleep(100);
        } catch (Exception e) {}
    }
        
    @WebSocketOpen
    public void onOpen(Session session) {
        
        System.out.println("BLOCKINGSERVER opened !");
        session.addMessageHandler(new MyCharacterStreamHandler(session));
        
        
        
    }
    
}

