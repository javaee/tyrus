package org.glassfish.tyrus.grizzly;

import java.util.ArrayList;
import java.util.List;
import javax.net.websocket.ClientEndpointConfiguration;
import javax.net.websocket.extensions.Extension;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.websockets.HandShake;
import org.glassfish.grizzly.websockets.draft17.Draft17Handler;
import org.glassfish.grizzly.websockets.draft17.HandShake17;

/**
 * {@link org.glassfish.grizzly.websockets.ProtocolHandler} that supports sub-protocol and {@link javax.net.websocket.extensions.Extension} insertion.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class GrizzlyProtocolHandler extends Draft17Handler {

    private ClientEndpointConfiguration clc;

    private String subprotocol;

    private List<Extension> extensions;

    /**
     * Construct new {@link GrizzlyProtocolHandler}.
     *
     * @param mask        data mask, see {@link org.glassfish.grizzly.websockets.ProtocolHandler}.
     * @param subprotocol selected by server.
     * @param extensions  supported by server.
     */
    public GrizzlyProtocolHandler(boolean mask, String subprotocol, List<Extension> extensions) {
        super(mask);
        this.subprotocol = subprotocol;
        this.extensions = extensions;
    }

    @Override
    public HandShake createHandShake(HttpContent requestContent) {
        HandShake result = new HandShake17((HttpRequestPacket) requestContent.getHttpHeader());

        ArrayList<String> subprotocols = new ArrayList<String>();
        subprotocols.add(subprotocol);
        result.setSubProtocol(subprotocols);

        ArrayList<String> extString = new ArrayList<String>();
        for (Extension extension : extensions) {
            extString.add(extension.getName());
        }
        result.setExtensions(extString);
        return result;
    }
}
