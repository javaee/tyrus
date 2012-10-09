package org.glassfish.tyrus.platform;

import javax.net.websocket.Endpoint;
import javax.net.websocket.HandshakeRequest;
import javax.net.websocket.HandshakeResponse;
import javax.net.websocket.ServerConfiguration;
import javax.net.websocket.extensions.Extension;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides the default {@link ServerConfiguration} used by the {@link BeanServer}.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class DefaultServerEndpointConfiguration extends DefaultEndpointConfiguration implements ServerConfiguration {

    /**
     * {@link Endpoint} extensions (user provided).
     */
    private Set<Extension> extensions = Collections.newSetFromMap(new ConcurrentHashMap<Extension, Boolean>());

    /**
     * {@link Endpoint} sub-protocols (user provided).
     */
    private Set<String> subprotocols = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    /**
     * Creates new configuration for {@link Endpoint} which is used on the server side.
     *
     * @param model Model of the {@link Endpoint}.
     */
    public DefaultServerEndpointConfiguration(Model model){
        super(model);
        this.model = model;
    }

    @Override
    public String getNegotiatedSubprotocol(List<String> requestedSubprotocols) {
        return null;
    }

    @Override
    public List<Extension> getNegotiatedExtensions(List<Extension> requestedExtensions) {
        return null;
    }

    @Override
    public boolean checkOrigin(String originHeaderValue) {
        return false;
    }

    @Override
    public boolean matchesURI(URI uri) {
        return false;
    }

    @Override
    public void modifyHandshake(HandshakeRequest request, HandshakeResponse response) {

    }
}
