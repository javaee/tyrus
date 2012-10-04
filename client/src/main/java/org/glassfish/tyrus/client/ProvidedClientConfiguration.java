package org.glassfish.tyrus.client;

import javax.net.websocket.ClientConfiguration;
import javax.net.websocket.Decoder;
import javax.net.websocket.Encoder;
import javax.net.websocket.extensions.Extension;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Default client configuration. Will be replaced by {@link javax.net.websocket.DefaultClientConfiguration} once ported to new API version.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class ProvidedClientConfiguration implements ClientConfiguration{
    private URI uri;
    private List<String> preferredSubprotocols = new ArrayList<String>();
    private List<Extension> extensions = new ArrayList<Extension>();
    private List<Encoder> encoders = new ArrayList<Encoder>();
    private List<Decoder> decoders = new ArrayList<Decoder>();

    /**
     * Creates a client configuration that will attempt
     * to connect to the given URI.
     *
     * @param uri URI the client will connect to.
     */
    public ProvidedClientConfiguration(URI uri) {
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
     * @return {@link ProvidedClientConfiguration} with updated sub-protocols.
     */
    public ProvidedClientConfiguration setPreferredSubprotocols(List<String> preferredSubprotocols) {
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
     * @return {@link ProvidedClientConfiguration} with updated extensions.
     */
    public ProvidedClientConfiguration setExtensions(List<Extension> extensions) {
        this.extensions = extensions;
        return this;
    }

    /**
     * Assign the list of encoders this client will use.
     *
     * @return encoders.
     */
    public List<Encoder> getEncoders() {
        return encoders;
    }

    /**
     * Assign the list of encoders this client will use.
     *
     * @return {@link ProvidedClientConfiguration} with updated encoders.
     */
    public ProvidedClientConfiguration setEncoders(List<Encoder> encoders) {
        this.encoders = encoders;
        return this;
    }

    /**
     * Assign the list of decoders this client will use.
     *
     * @return decoders.
     */
    public List<Decoder> getDecoders() {
        return this.decoders;
    }

    /**
     * Assign the list of decoders this client will use.
     */
    public ProvidedClientConfiguration setDecoders(List<Decoder> decoders) {
        this.decoders = decoders;
        return this;
    }

}
