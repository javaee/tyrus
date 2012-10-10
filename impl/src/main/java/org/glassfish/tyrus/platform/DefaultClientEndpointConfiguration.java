package org.glassfish.tyrus.platform;

import javax.net.websocket.ClientConfiguration;
import javax.net.websocket.extensions.Extension;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 *
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class DefaultClientEndpointConfiguration extends DefaultEndpointConfiguration implements ClientConfiguration{

    private URI uri;
    private List<String> preferredSubprotocols = new ArrayList<String>();
    private List<Extension> extensions = new ArrayList<Extension>();

    /**
     * Creates a client configuration that will attempt
     * to connect to the given URI.
     *
     * @param uri URI the client will connect to.
     */
    public DefaultClientEndpointConfiguration(URI uri) {
        this.uri = uri;
    }

    /**
     * Return the URI the client will connect to.
     *
     * @return URI the client will connect to.
     */
    public URI getUri() {
        return uri;
    }

    /**
     * Return the protocols, in order of preference, favorite first, that this client would
     * like to use for its sessions.
     *
     * @return prefered sub-protocols.
     */
    public List<String> getPreferredSubprotocols() {
        return preferredSubprotocols;
    }

    /**
     * Assign the List of preferred sub-protocols that this client would like to
     * use.
     *
     * @return {@link DefaultClientEndpointConfiguration} with updated sub-protocols.
     */
    public DefaultClientEndpointConfiguration setPreferredSubprotocols(List<String> preferredSubprotocols) {
        this.preferredSubprotocols = preferredSubprotocols;
        return this;
    }

    /**
     * Return the extensions, in order of preference, favorite first, that this client would
     * like to use for its sessions.
     *
     * @return extensions.
     */
    public List<Extension> getExtensions() {
        return extensions;
    }

    /**
     * Assign the List of extensions that this client would like to
     * use.
     *
     * @return {@link DefaultClientEndpointConfiguration} with updated extensions.
     */
    public DefaultClientEndpointConfiguration setExtensions(List<Extension> extensions) {
        this.extensions = extensions;
        return this;
    }
}
