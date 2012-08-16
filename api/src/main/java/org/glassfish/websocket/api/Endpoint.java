/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.glassfish.websocket.api;

/**
 *
 * @author dannycoward
 */
public abstract class Endpoint {
        /** Called whenever a peer first connects to this end point.*/
    public abstract void onOpen(Session s);
   
    /** Called when a peer disconnects from this end point.*/
    public void onClose(Session session) {}

    public void onError(Throwable t, Session session) {}
}
