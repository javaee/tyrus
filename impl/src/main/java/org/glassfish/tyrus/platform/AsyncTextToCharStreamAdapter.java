/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.glassfish.tyrus.platform;

import javax.net.websocket.MessageHandler;
import java.io.Reader;
import java.util.*;

/**
 * 
 * @author dannycoward
 */
public class AsyncTextToCharStreamAdapter implements BufferedStringSource, MessageHandler.AsyncText {
    private List<String> bufferedFragments = new ArrayList<String>();
    private boolean receivedLast = false;
    private BufferedStringSourceReader reader = null;
    private boolean readerThreadStopped = false;
    private static long MAX_BUFFER_SIZE = 8 * 1024;
    private MessageHandler.CharacterStream mh;
    
    public AsyncTextToCharStreamAdapter(MessageHandler.CharacterStream mh) {
        this.mh = mh;
    }
    
    private void blockOnReaderThread() {
        readerThreadStopped = true;
        try {
            while (readerThreadStopped) {
                Thread.sleep(100);
            }
        } catch (Exception e) {}
    }
    
    public char[] getNextChars(int numberOfChars) {

        if (this.bufferedFragments.isEmpty()) {
            if (receivedLast) {
                this.reader = null;
                return null;
            } else { // there's more to come...
              blockOnReaderThread();
            }
        }
        char[] chrs = new char[1];
        String nextFragment = this.bufferedFragments.get(0);
        chrs[0] = nextFragment.charAt(0);
        this.bufferedFragments.remove(0);
        if (nextFragment.length() > 1) {
            String newFragment = nextFragment.substring(1, nextFragment.length());
            this.bufferedFragments.add(0, newFragment);
        }
        return chrs;
    }
    
    @Override
    public void finishedReading() {
        this.bufferedFragments = new ArrayList();
        this.reader = null;
    }
    
    private void checkForBufferOverflow(String part) {
        int numberOfBytes = 0;
        for (String fragment : this.bufferedFragments) {
            numberOfBytes = numberOfBytes + fragment.length();   
        }
        if (MAX_BUFFER_SIZE < numberOfBytes + part.length()) {            
            throw new IllegalStateException("Buffer overflow");
        }
    }
    
    
    @Override
    public void onMessagePart(String part, boolean last) {
        this.receivedLast = last;
        this.checkForBufferOverflow(part);
        bufferedFragments.add(part);
        this.readerThreadStopped = false;
        
        if (this.reader == null) {
            this.reader = new BufferedStringSourceReader(this);
            Thread t = new Thread() {
                public void run() {
                    mh.onMessage(reader);
                }
            };
            t.start();
        }
 
    }
}


