/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2013 Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.websocket.CloseReason;
import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.DeploymentException;
import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.Extension;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import javax.websocket.server.ServerEndpointConfiguration;

import org.glassfish.tyrus.internal.PathPattern;
import org.glassfish.tyrus.spi.SPIEndpoint;
import org.glassfish.tyrus.spi.SPIHandshakeRequest;
import org.glassfish.tyrus.spi.SPIRemoteEndpoint;

/**
 * Wraps the registered application class.
 * There is one {@link EndpointWrapper} for each application class, which handles all the methods.
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Martin Matula (martin.matula at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class EndpointWrapper extends SPIEndpoint {
    /**
     * The container for this session.
     */
    private final WebSocketContainer container;
    private final String contextPath;

    private final List<DecoderWrapper> decoders = new ArrayList<DecoderWrapper>();
    private final List<Encoder> encoders = new ArrayList<Encoder>();

    private final EndpointConfiguration configuration;
    private final Class<?> endpointClass;
    private final Endpoint endpoint;
    private final Map<SPIRemoteEndpoint, SessionImpl> remoteEndpointToSession =
            new ConcurrentHashMap<SPIRemoteEndpoint, SessionImpl>();
    private final ErrorCollector collector;
    private final ComponentProviderService componentProvider;

    // the following is set during the handshake
    private String uri;
    private final Map<String, String> templateValues = new HashMap<String, String>();
    private boolean isSecure;
    private String queryString;

    /**
     * Create {@link EndpointWrapper} for class that extends {@link Endpoint}.
     *
     * @param endpointClass     endpoint class for which the wrapper is created.
     * @param configuration     endpoint configuration.
     * @param componentProvider component provider.
     * @param container         container where the wrapper is running.
     * @param contextPath       context path.
     * @param collector         error collector.
     */
    public EndpointWrapper(Class<? extends Endpoint> endpointClass, EndpointConfiguration configuration,
                           ComponentProviderService componentProvider, WebSocketContainer container,
                           String contextPath, ErrorCollector collector) {
        this(null, endpointClass, configuration, componentProvider, container, contextPath, collector);
    }

    /**
     * Create {@link EndpointWrapper} for {@link Endpoint} instance or {@link AnnotatedEndpoint} instance.
     *
     * @param endpoint          endpoint instance for which the wrapper is created.
     * @param configuration     endpoint configuration.
     * @param componentProvider component provider.
     * @param container         container where the wrapper is running.
     * @param contextPath       context path.
     * @param collector         error collector.
     */
    public EndpointWrapper(Endpoint endpoint, EndpointConfiguration configuration, ComponentProviderService componentProvider, WebSocketContainer container,
                           String contextPath, ErrorCollector collector) {
        this(endpoint, null, configuration, componentProvider, container, contextPath, collector);
    }

    private EndpointWrapper(Endpoint endpoint, Class<? extends Endpoint> endpointClass, EndpointConfiguration configuration,
                            ComponentProviderService componentProvider, WebSocketContainer container,
                            String contextPath, ErrorCollector collector) {
        this.endpointClass = endpointClass;
        this.endpoint = endpoint;
        this.container = container;
        this.contextPath = contextPath;
        this.collector = collector;
        this.componentProvider = componentProvider;

        this.configuration = configuration == null ? new EndpointConfiguration() {
            @Override
            public List<Encoder> getEncoders() {
                return Collections.emptyList();
            }

            @Override
            public List<Decoder> getDecoders() {
                return Collections.emptyList();
            }
        } : configuration;

        for (Decoder dec : this.configuration.getDecoders()) {
            if (dec instanceof DecoderWrapper) {
                decoders.add((DecoderWrapper) dec);
            } else {
                Class<?> type = getDecoderClassType(dec.getClass());
                decoders.add(new DecoderWrapper(dec, type, dec.getClass()));
            }
        }

        decoders.addAll(PrimitiveDecoders.ALL_WRAPPED);
        decoders.add(new DecoderWrapper(NoOpTextCoder.INSTANCE, String.class, NoOpTextCoder.class));
        decoders.add(new DecoderWrapper(NoOpByteBufferCoder.INSTANCE, ByteBuffer.class, NoOpByteBufferCoder.class));
        decoders.add(new DecoderWrapper(NoOpByteArrayCoder.INSTANCE, byte[].class, NoOpByteArrayCoder.class));

        encoders.addAll(this.configuration.getEncoders());
        encoders.add(NoOpTextCoder.INSTANCE);
        encoders.add(NoOpByteBufferCoder.INSTANCE);
        encoders.add(NoOpByteArrayCoder.INSTANCE);
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

        final PathPattern pathPattern = new PathPattern(getEndpointPath(sec.getPath()));

        final boolean match = pathPattern.match(uri, pathPattern.getTemplate().getTemplateVariables(), templateValues);


        // TODO: http://java.net/jira/browse/WEBSOCKET_SPEC-126
//        final boolean match;
//        try {
//            match = sec.matchesURI(new URI(hr.getRequestUri()));
//        } catch (URISyntaxException e) {
//            return false;
//        }

        // TODO: check origin should be called after http://java.net/jira/browse/WEBSOCKET_SPEC-128 is resolved
        return match; // && sec.checkOrigin(hr.getHeader("Origin"));
    }

    private String getEndpointPath(String relativePath) {
        return (contextPath.endsWith("/") ? contextPath.substring(0, contextPath.length() - 1) : contextPath)
                + "/" + (relativePath.startsWith("/") ? relativePath.substring(1) : relativePath);
    }

    public Object decodeCompleteMessage(Object message, Class<?> type) {
        for (DecoderWrapper dec : decoders) {
            try {
                if (dec.getDecoder() instanceof Decoder.Text) {
                    if (type != null && type.isAssignableFrom(dec.getType())) {
                        if (((Decoder.Text) dec.getDecoder()).willDecode((String) message)) {
                            return ((Decoder.Text) dec.getDecoder()).decode((String) message);
                        }
                    }
                } else if (dec.getDecoder() instanceof Decoder.Binary) {
                    if (type != null && type.isAssignableFrom(dec.getType())) {
                        if (((Decoder.Binary) dec.getDecoder()).willDecode((ByteBuffer) message)) {
                            return ((Decoder.Binary) dec.getDecoder()).decode((ByteBuffer) message);
                        }
                    }
                } else if (dec.getDecoder() instanceof Decoder.TextStream) {
                    if (type != null && type.isAssignableFrom(dec.getType())) {
                        return ((Decoder.TextStream) dec.getDecoder()).decode(new StringReader((String) message));
                    }
                } else if (dec.getDecoder() instanceof Decoder.BinaryStream) {
                    if (type != null && type.isAssignableFrom(dec.getType())) {
                        byte[] array = ((ByteBuffer) message).array();
                        return ((Decoder.BinaryStream) dec.getDecoder()).decode(new ByteArrayInputStream(array));
                    }
                }
            } catch (DecodeException de) {
                de.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private ArrayList<DecoderWrapper> findApplicableDecoders(Object message, boolean isString) {
        ArrayList<DecoderWrapper> result = new ArrayList<DecoderWrapper>();

        for (DecoderWrapper dec : decoders) {
            if (isString && (Decoder.Text.class.isAssignableFrom(dec.getOriginalClass()))) {
                if (((Decoder.Text) dec.getDecoder()).willDecode((String) message)) {
                    result.add(dec);
                }
            } else if (!isString && (Decoder.Binary.class.isAssignableFrom(dec.getOriginalClass()))) {
                if (((Decoder.Binary) dec.getDecoder()).willDecode((ByteBuffer) message)) {
                    result.add(dec);
                }
            } else if (isString && (Decoder.TextStream.class.isAssignableFrom(dec.getOriginalClass()))) {
                result.add(dec);
            } else if (!isString && (Decoder.BinaryStream.class.isAssignableFrom(dec.getOriginalClass()))) {
                result.add(dec);
            }
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    Object doEncode(Object message) throws EncodeException {
        for (Encoder enc : encoders) {
            try {
                if (enc instanceof Encoder.Binary) {
                    Class<?> type = ReflectionHelper.getClassType(enc.getClass(), Encoder.Binary.class);
                    if (type.isAssignableFrom(message.getClass())) {
                        return ((Encoder.Binary) enc).encode(message);
                    }
                } else if (enc instanceof Encoder.Text) {
                    Class<?> type = ReflectionHelper.getClassType(enc.getClass(), Encoder.Text.class);
                    if (type.isAssignableFrom(message.getClass())) {
                        return ((Encoder.Text) enc).encode(message);
                    }
                } else if (enc instanceof Encoder.BinaryStream) {
                    Class<?> type = ReflectionHelper.getClassType(enc.getClass(), Encoder.BinaryStream.class);
                    if (type.isAssignableFrom(message.getClass())) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ((Encoder.BinaryStream) enc).encode(message, new ByteArrayOutputStream());
                        return baos;
                    }
                } else if (enc instanceof Encoder.TextStream) {
                    Class<?> type = ReflectionHelper.getClassType(enc.getClass(), Encoder.TextStream.class);
                    if (type.isAssignableFrom(message.getClass())) {
                        Writer writer = new StringWriter();
                        ((Encoder.TextStream) enc).encode(message, writer);
                        return writer;
                    }
                }
            } catch (EncodeException ee) {
                throw ee;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        throw new EncodeException(message, "Unable to encode ");
    }

    @Override
    public List<Extension> getNegotiatedExtensions(List<Extension> clientExtensions) {

        ServerEndpointConfiguration sec;

        if (configuration instanceof ServerEndpointConfiguration) {
            sec = (ServerEndpointConfiguration) configuration;
        } else {
            return Collections.emptyList();
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
    public Set<Session> getOpenSessions() {
        Set<Session> result = new HashSet<Session>();

        for (Session session : remoteEndpointToSession.values()) {
            if (session.isOpen()) {
                result.add(session);
            }
        }

        return Collections.unmodifiableSet(result);
    }

    @Override
    public Session createSessionForRemoteEndpoint(SPIRemoteEndpoint re, String subprotocol, List<Extension> extensions) {

        return new SessionImpl(container, re, this, subprotocol, extensions, isSecure,
                uri == null ? null : URI.create(uri), queryString, templateValues);
    }

    @Override
    public void remove() {
        // TODO: disconnect the endpoint?
    }

    @Override
    public void onConnect(SPIRemoteEndpoint gs, String subprotocol, List<Extension> extensions) {
        // create a new session
        SessionImpl session = new SessionImpl(container, gs, this, subprotocol, extensions, isSecure,
                uri == null ? null : URI.create(uri), queryString, templateValues);
        remoteEndpointToSession.put(gs, session);

        final Endpoint toCall = endpoint != null ? endpoint :
                (Endpoint) componentProvider.getInstance(endpointClass, session, collector);
        try {
            toCall.onOpen(session, configuration);
        } catch (Throwable t) {
            if (toCall != null) {
                toCall.onError(session, t);
            } else {
                collector.addException(new DeploymentException(t.getMessage(), t));
            }
        }
    }

    @Override
    public void onMessage(SPIRemoteEndpoint gs, ByteBuffer messageBytes) {
        SessionImpl session = remoteEndpointToSession.get(gs);
        try {
            session.notifyMessageHandlers(messageBytes, findApplicableDecoders(messageBytes, false));
        } catch (Throwable t) {
            final Endpoint toCall = endpoint != null ? endpoint :
                    (Endpoint) componentProvider.getInstance(endpointClass, session, collector);
            toCall.onError(session, t);
        }
    }

    @Override
    public void onMessage(SPIRemoteEndpoint gs, String messageString) {
        SessionImpl session = remoteEndpointToSession.get(gs);
        try {
            session.notifyMessageHandlers(messageString, findApplicableDecoders(messageString, true));
        } catch (Throwable t) {
            final Endpoint toCall = endpoint != null ? endpoint :
                    (Endpoint) componentProvider.getInstance(endpointClass, session, collector);
            toCall.onError(session, t);
        }
    }

    @Override
    public void onPartialMessage(SPIRemoteEndpoint gs, String partialString, boolean last) {
        SessionImpl session = remoteEndpointToSession.get(gs);
        try {
            session.notifyMessageHandlers(partialString, last);
        } catch (Throwable t) {
            final Endpoint toCall = endpoint != null ? endpoint :
                    (Endpoint) componentProvider.getInstance(endpointClass, session, collector);
            toCall.onError(session, t);
        }
    }

    @Override
    public void onPartialMessage(SPIRemoteEndpoint gs, ByteBuffer partialBytes, boolean last) {
        SessionImpl session = remoteEndpointToSession.get(gs);
        try {
            session.notifyMessageHandlers(partialBytes, last);
        } catch (Throwable t) {
            final Endpoint toCall = endpoint != null ? endpoint :
                    (Endpoint) componentProvider.getInstance(endpointClass, session, collector);
            toCall.onError(session, t);
        }
    }


    @Override
    public void onPong(SPIRemoteEndpoint gs, ByteBuffer bytes) {
        //TODO What should I call?
    }

    // the endpoint needs to respond as soon as possible (see the websocket RFC)
    // no involvement from application layer, there is no ping listener
    @Override
    public void onPing(SPIRemoteEndpoint gs, ByteBuffer bytes) {
        SessionImpl session = remoteEndpointToSession.get(gs);
        session.getRemote().sendPong(bytes);
    }

    @Override
    public void onClose(SPIRemoteEndpoint gs, CloseReason closeReason) {
        Session session = remoteEndpointToSession.get(gs);

        final Endpoint toCall = endpoint != null ? endpoint :
                (Endpoint) componentProvider.getInstance(endpointClass, session, collector);

        try {
            toCall.onClose(session, closeReason);
        } catch (Throwable t) {
            if (toCall != null) {
                toCall.onError(session, t);
            } else {
                collector.addException(new DeploymentException(t.getMessage(), t));
            }
        }

        remoteEndpointToSession.remove(gs);
        componentProvider.removeSession(session);
    }

    @Override
    public EndpointConfiguration getEndpointConfiguration() {
        return configuration;
    }

    boolean isOpen(SessionImpl session) {
        return remoteEndpointToSession.values().contains(session);
    }

    private Class<?> getDecoderClassType(Class<?> decoderClass) {
        if (Decoder.Binary.class.isAssignableFrom(decoderClass)) {
            return ReflectionHelper.getClassType(decoderClass, Decoder.Binary.class);
        } else if (Decoder.Text.class.isAssignableFrom(decoderClass)) {
            return ReflectionHelper.getClassType(decoderClass, Decoder.Text.class);
        } else if (Decoder.BinaryStream.class.isAssignableFrom(decoderClass)) {
            return ReflectionHelper.getClassType(decoderClass, Decoder.BinaryStream.class);
        } else if (Decoder.TextStream.class.isAssignableFrom(decoderClass)) {
            return ReflectionHelper.getClassType(decoderClass, Decoder.TextStream.class);
        } else {
            return null;
        }
    }
}
