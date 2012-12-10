package org.glassfish.tyrus.server;

import javax.websocket.EndpointFactory;

/**
 * Default factory used for creating Endpoints, currently with no functionality.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class DefaultEndpointFactory implements EndpointFactory {
    @Override
    public Object createEndpoint() {
        return null;
    }
}
