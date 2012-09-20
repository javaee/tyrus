/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 - 2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package wstestbeans;


import javax.net.websocket.Endpoint;
import javax.net.websocket.MessageHandler;
import javax.net.websocket.Session;
import javax.net.websocket.RemoteEndpoint;
import javax.net.websocket.annotations.WebSocketEndpoint;


@WebSocketEndpoint(
        path = "/hellodeployhello"
)
/**
 *
 * @author dannycoward
 */
public class HelloDeployHello {
//    @WebSocketMessage
//        public String onMessage(String path, RemoteEndpoint p) {
//            String myPath = p.getContext().getPath();
//            String pathToDeployIfc = myPath + path + "_ifc";
//            String pathToDeployAdapt = myPath + path + "_adapt";
//            System.out.println("Deploying new end points to: " + pathToDeployIfc + " and " + pathToDeployAdapt);
//
//            Endpoint endpoint = new CustomEndpointUsingInterface();
//            ServerContainer containerContext = p.getContext().getContainerContext();
//            containerContext.Xdeploy(endpoint, pathToDeployIfc);
//            endpoint = new CustomEndpointUsingAdapter();
//            containerContext.Xdeploy(endpoint, pathToDeployAdapt);
//
//            return "Deployed to : " + pathToDeployIfc + " and " + pathToDeployAdapt;
//        }
}

class CustomEndpointUsingInterface extends Endpoint {
//    public void initialize(XEndpointContext epcontext){
//        System.out.println("Initializing dynamically deployed end point implementing interface");
//    }

    /**
     * Called whenever a peer first connects to this end point.
     */
    @Override
    public void onOpen(Session session) {
        System.out.println("Opened dynamically deployed end point which implements the interface");
        session.addMessageHandler(new MyMessageHandler(session, "interface implementing dynamically deployed end point"));
    }


    /**
     * Called when a peer disconnects from this end point.
     */
    @Override
    public void onClose(Session session) {
        System.out.println("Closing dynamically deployed end point implementing interface");

    }

    /**
     * Called when there us an error on the connection from the supplied peer
     * to this end point.
     *
     * @param p
     * @param e
     */
    public void onError(RemoteEndpoint p, Exception e) {
        System.out.println("Error");
    }

    /**
     * Called by the container when this end point is about to be taken out of
     * service.
     */
    public void remove() {
        System.out.println("Removed dynamically deployed interface implementing end point");
    }
}

class MyMessageHandler implements MessageHandler.Text {
    private Session session;
    private String msg;

    public MyMessageHandler(Session session, String msg) {
        this.session = session;
        this.msg = msg;
    }

    public void onMessage(String message) {
        System.out.println(msg + " got message " + message);
        try {
            session.getRemote().sendString("PASS (" + msg + "): Thanks for your message: " + message);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}

class CustomEndpointUsingAdapter extends Endpoint {
//    public void initialize(XEndpointContext epcontext){
//        System.out.println("Initializing dynamically deployed end point extending adapter");
//    }

    /**
     * Called whenever a peer first connects to this end point.
     */
    public void onOpen(Session session) {
        System.out.println("Opened dynamically deployed end point which extending adapter");
        session.addMessageHandler(new MyMessageHandler(session, "dynamically deployed end point which extending adapter"));
    }

    /**
     * Called when a peer sends a binary message to this end point.
     */
    public void onMessage(RemoteEndpoint p, byte[] data) {

    }

    /**
     * Called when a peer disconnects from this end point.
     */
    @Override
    public void onClose(Session session) {
        System.out.println("Closing dynamically deployed end point extending adapter");
    }

    /**
     * Called when there us an error on the connection from the supplied peer
     * to this end point.
     *
     * @param p
     * @param e
     */
    public void onError(RemoteEndpoint p, Exception e) {
        System.out.println("Error");
    }

    /**
     * Called by the container when this end point is about to be taken out of
     * service.
     */
    public void remove() {
        System.out.println("Removed dynamically deployed interface extending adapter");
    }

}
