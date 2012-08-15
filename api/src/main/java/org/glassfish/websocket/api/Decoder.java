/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.glassfish.websocket.api;

/**
 *
 * @author dannycoward
 */
public interface Decoder {
    public interface Binary<T> {
        /** The method the runtime will call to make the conversion.*/
        public T decode(byte[] bytes) throws DecodeException;
        /** Decide whether you will decode the incoming byte array message.*/
        public boolean willDecode(byte[] bytes);

    }
    
    public interface Text<T> {
        /** Decode the incoming String parameter into an instance of the custom type.*/
        public T decode(String s) throws DecodeException;
        /** Decide whether you will decode the incoming String message.*/
        public boolean willDecode(String s);
    }
}
