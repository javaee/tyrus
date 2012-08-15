/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.glassfish.websocket.api;

/**
 *
 * @author dannycoward
 */
public interface Encoder {
    public interface Binary<T> {
        /** The method the runtime will call to make the conversion.*/
        public byte[] encode(T data) throws EncodeException;
    }
    
    public interface Text<T> {
        public String encode(T data) throws EncodeException;
    }
}
