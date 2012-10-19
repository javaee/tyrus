/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.glassfish.tyrus.platform;

/**
 *
 * @author dannycoward
 */
public interface BufferedStringSource {
    public char[] getNextChars(int numberOfChars);
    public void finishedReading();
}
