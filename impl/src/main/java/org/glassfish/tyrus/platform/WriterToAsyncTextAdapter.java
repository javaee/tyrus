/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.glassfish.tyrus.platform;

import java.io.*;

import javax.net.websocket.*;
/**
 *
 * @author dannycoward
 */
public class WriterToAsyncTextAdapter extends Writer {
    private RemoteEndpoint re;
    String buffer = null;
    
    public WriterToAsyncTextAdapter(RemoteEndpoint re) {
        this.re = re;
    }
    
    private void sendBuffer(boolean last) throws IOException {
        re.sendPartialString(buffer, last);
    }
    
    @Override
    public void write(char[] chars, int index, int len) throws IOException {
        if (buffer != null) {
            this.sendBuffer(false);
        }
        buffer = new String(chars, index, len -1);
    }
    
    @Override
    public void flush() throws IOException {
        this.sendBuffer(true);
        buffer = null;
    }
    
    @Override
    public void close() throws IOException {
        this.sendBuffer(true);
    }
}
