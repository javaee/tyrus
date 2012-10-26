/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus;

import java.lang.reflect.Method;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.websocket.ClientContainer;
import javax.net.websocket.Decoder;
import javax.net.websocket.EncodeException;
import javax.net.websocket.Encoder;
import javax.net.websocket.Endpoint;
import javax.net.websocket.EndpointConfiguration;
import javax.net.websocket.MessageHandler;
import javax.net.websocket.RemoteEndpoint;
import javax.net.websocket.ServerEndpointConfiguration;
import org.glassfish.tyrus.internal.PathPattern;
import org.glassfish.tyrus.spi.SPIEndpoint;
import org.glassfish.tyrus.spi.SPIHandshakeRequest;

/**
 * Wraps the registered application class.
 * There is one {@link EndpointWrapper} for each application class, which handles all the methods.
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Martin Matula (martin.matula at oracle.com)
 */
public class EndpointWrapper extends SPIEndpoint {
    /**
     * The container for this session.
     */
    private final ClientContainer container;

    private final List<Decoder> decoders = new ArrayList<Decoder>();
    private final List<Encoder> encoders = new ArrayList<Encoder>();

    private final EndpointConfiguration configuration;
    private final Endpoint endpoint;
    private final Map<RemoteEndpoint, SessionImpl> remoteEndpointToSession =
            new ConcurrentHashMap<RemoteEndpoint, SessionImpl>();

    // the following is set during the handshake
    private String uri;
    private final Map<String, String> templateValues = new HashMap<String, String>();
    private boolean isSecure;
    private String queryString;

    public EndpointWrapper(Endpoint endpoint, EndpointConfiguration configuration, ClientContainer container) {
        this.endpoint = endpoint;
        this.configuration = configuration;
        this.container = container;

        decoders.addAll(configuration.getDecoders());
        decoders.addAll(PrimitiveDecoders.ALL);
        decoders.add(NoOpTextCoder.INSTANCE);
        decoders.add(NoOpBinaryCoder.INSTANCE);

        encoders.addAll(configuration.getEncoders());
        encoders.add(NoOpTextCoder.INSTANCE);
        encoders.add(NoOpBinaryCoder.INSTANCE);
        encoders.add(ToStringEncoder.INSTANCE);
    }

    @Override
    public boolean checkHandshake(SPIHandshakeRequest hr) {
        ServerEndpointConfiguration sec;

        if (configuration instanceof ServerEndpointConfiguration) {
            sec = (ServerEndpointConfiguration) configuration;
        } else {
            return false;
        }

        uri = hr.getRequestUri();
        queryString = hr.getQueryString();
        isSecure = hr.isSecure();

        final PathPattern pathPattern = new PathPattern(sec.getPath());

        final boolean match = pathPattern.match(uri, pathPattern.getTemplate().getTemplateVariables(), templateValues);
        return match && sec.checkOrigin(hr.getHeader("Origin"));
    }

    Object decodeMessage(Object message, Class<?> type, boolean isString) {

        for (Decoder dec : decoders) {
            try {
                if (isString && (dec instanceof Decoder.Text)) {
                    Method m = dec.getClass().getDeclaredMethod("decode", String.class);
                    if (type != null && type.isAssignableFrom(m.getReturnType())) {
                        if (((Decoder.Text) dec).willDecode((String) message)) {
                            return ((Decoder.Text) dec).decode((String) message);
                        }
                    }
                } else if (!isString && (dec instanceof Decoder.Binary)) {
                    Method m = dec.getClass().getDeclaredMethod("decode", ByteBuffer.class);
                    if (type != null && type.equals(m.getReturnType())) {
                        if (((Decoder.Binary) dec).willDecode((ByteBuffer) message)) {
                            return ((Decoder.Binary) dec).decode((ByteBuffer) message);
                        }
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    String doEncode(Object o) throws EncodeException {
        for (Encoder enc : encoders) {
            if (enc instanceof Encoder.Text) {
                try {
                    // TODO: implement utility to find type param and use that
                    Method m = enc.getClass().getMethod("encode", o.getClass());
                    if (m != null) {
                        //noinspection unchecked
                        return ((Encoder.Text) enc).encode(o);
                    }
                } catch (java.lang.NoSuchMethodException nsme) {
                    // just continue looping
                } catch (EncodeException ee) {
                    ee.printStackTrace();
                    throw ee;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        throw new EncodeException("Unable to encode ", o);
    }

    @Override
    public List<String> getNegotiatedExtensions(List<String> clientExtensions) {
        ServerEndpointConfiguration sec;

        if (configuration instanceof ServerEndpointConfiguration) {
            sec = (ServerEndpointConfiguration) configuration;
        } else {
            return null;
        }

        return sec.getNegotiatedExtensions(clientExtensions);
    }

    @Override
    public String getNegotiatedProtocol(List<String> clientProtocols) {
        ServerEndpointConfiguration sec;

        if (configuration instanceof ServerEndpointConfiguration) {
            sec = (ServerEndpointConfiguration) configuration;
        } else {
            return null;
        }

        return sec.getNegotiatedSubprotocol(clientProtocols);
    }

    @Override
    public void remove() {
        // TODO: disconnect the endpoint?
    }

    @Override
    public void onConnect(RemoteEndpoint gs, String subprotocol, List<String> extensions) {
        // create a new session
        SessionImpl session = new SessionImpl(container, gs, this, subprotocol, extensions, isSecure,
                uri == null ? null : URI.create(uri), queryString, templateValues);
        remoteEndpointToSession.put(gs, session);
        endpoint.onOpen(session);
    }

    @Override
    public void onMessage(RemoteEndpoint gs, ByteBuffer messageBytes) {
        SessionImpl session = remoteEndpointToSession.get(gs);
        session.notifyMessageHandlers(messageBytes);
    }

    @Override
    public void onMessage(RemoteEndpoint gs, String messageString) {
        SessionImpl session = remoteEndpointToSession.get(gs);
        session.notifyMessageHandlers(messageString);
    }

    /*
     * Initial implementation policy:
     * - if there is a streaming message handler invoke it
     * - if there is a blocking handler, use an adapter to invoke it
     */
    @Override
    public void onPartialMessage(RemoteEndpoint gs, String partialString, boolean last) {
        SessionImpl session = remoteEndpointToSession.get(gs);
        boolean handled = false;
        for (MessageHandler handler : (Set<MessageHandler>) session.getInvokableMessageHandlers()) {
            MessageHandler.AsyncText baseHandler;
            if (handler instanceof MessageHandler.AsyncText) {
                baseHandler = (MessageHandler.AsyncText) handler;
                baseHandler.onMessagePart(partialString, last);
                if (last) {
                    handled = true;
                }
                break;
            }
        }
        if (last && !handled) {
            System.out.println("Unhandled text message in EndpointWrapper");
        }
    }

    @Override
    public void onPartialMessage(RemoteEndpoint gs, ByteBuffer partialBytes, boolean last) {
        SessionImpl session = remoteEndpointToSession.get(gs);
        //System.out.println("EndpointWrapper----" + ((SessionImpl) peer.getSession()).getInvokableMessageHandlers());
        boolean handled = false;
        for (MessageHandler handler : (Set<MessageHandler>) session.getInvokableMessageHandlers()) {
            if (handler instanceof MessageHandler.AsyncBinary) {
                //System.out.println("async binary");
                ((MessageHandler.AsyncBinary) handler).onMessagePart(partialBytes, last);
                handled = true;
                break;
            }
        }
        if (!handled) {
            System.out.println("Unhandled partial binary message in EndpointWrapper");
        }

    }


    @Override
    public void onPong(RemoteEndpoint gs, ByteBuffer bytes) {
        SessionImpl session = remoteEndpointToSession.get(gs);
        //System.out.println("EndpointWrapper----" + ((SessionImpl) peer.getSession()).getInvokableMessageHandlers());
        boolean handled = false;
        for (MessageHandler handler : (Set<MessageHandler>) session.getMessageHandlers()) {
            if (handler instanceof MessageHandler.Pong) {
                //System.out.println("async binary");
                ((MessageHandler.Pong) handler).onPong(bytes);
                handled = true;
            }
        }
        if (!handled) {
            System.out.println("Unhandled pong message in EndpointWrapper");
        }

    }

    // the endpoint needs to respond as soon as possible (see the websocket RFC)
    // no involvement from application layer, there is no ping listener
    @Override
    public void onPing(RemoteEndpoint gs, ByteBuffer bytes) {
        SessionImpl session = remoteEndpointToSession.get(gs);
        session.getRemote().sendPong(bytes);
    }

    @Override
    public void onClose(RemoteEndpoint gs) {
        SessionImpl session = remoteEndpointToSession.get(gs);
        // TODO: where should I get the CloseReason from?
        endpoint.onClose(session, null);
        remoteEndpointToSession.remove(gs);
    }

    boolean isActive(SessionImpl session) {
        return remoteEndpointToSession.values().contains(session);
    }
}
