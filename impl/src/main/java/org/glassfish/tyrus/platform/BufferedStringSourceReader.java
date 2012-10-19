/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.glassfish.tyrus.platform;

import java.io.Reader;

/**
 *
 * @author dannycoward
 */
public class BufferedStringSourceReader extends Reader {
    BufferedStringSource bss;
    
    public BufferedStringSourceReader(BufferedStringSource bss) {
        this.bss = bss;
    }
    
    @Override
    public int read(char[] destination, int offsetToStart, int numberOfChars) {
        char[] got = bss.getNextChars(numberOfChars);
        if (got != null) {
            System.arraycopy(got, 0, destination, offsetToStart, got.length);
            return got.length;
        } else {
            return -1;
        }
    }
    
    @Override
    public void close() {
        this.bss.finishedReading();
    }
}
