package org.glassfish.tyrus.test.basic.bean;

import javax.net.websocket.Session;
import javax.net.websocket.annotations.WebSocketEndpoint;
import javax.net.websocket.annotations.WebSocketMessage;

/**
 * Together with HelloTestBean used to test invocation of methods with various order of parameters.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
@WebSocketEndpoint(path="/hello")
public class MessageParamOrderTestBean {

    @WebSocketMessage
    public String doThat(Session peer, String message) {
        return message;
    }
}
