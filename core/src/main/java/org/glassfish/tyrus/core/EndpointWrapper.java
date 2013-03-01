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

package org.glassfish.tyrus.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
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
import javax.websocket.server.ServerEndpointConfigurator;

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

    private final List<CoderWrapper<Decoder>> decoders = new ArrayList<CoderWrapper<Decoder>>();
    private final List<CoderWrapper<Encoder>> encoders = new ArrayList<CoderWrapper<Encoder>>();

    private final EndpointConfiguration configuration;
    private final Class<?> endpointClass;
    private final Endpoint endpoint;
    private final Map<SPIRemoteEndpoint, SessionImpl> remoteEndpointToSession =
            new ConcurrentHashMap<SPIRemoteEndpoint, SessionImpl>();
    private final ErrorCollector collector;
    private final ComponentProviderService componentProvider;

    private final ServerEndpointConfigurator serverEndpointConfigurator;

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
    public EndpointWrapper(Class<?> endpointClass, EndpointConfiguration configuration,
                           ComponentProviderService componentProvider, WebSocketContainer container,
                           String contextPath, ErrorCollector collector, ServerEndpointConfigurator serverEndpointConfigurator) {
        this(null, endpointClass, configuration, componentProvider, container, contextPath, collector, serverEndpointConfigurator);
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
                           String contextPath, ErrorCollector collector, ServerEndpointConfigurator serverEndpointConfigurator) {
        this(endpoint, null, configuration, componentProvider, container, contextPath, collector, serverEndpointConfigurator);
    }

    private EndpointWrapper(Endpoint endpoint, Class<?> endpointClass, EndpointConfiguration configuration,
                            ComponentProviderService componentProvider, WebSocketContainer container,
                            String contextPath, ErrorCollector collector, ServerEndpointConfigurator serverEndpointConfigurator) {
        this.endpointClass = endpointClass;
        this.endpoint = endpoint;
        this.container = container;
        this.contextPath = contextPath;
        // Uri is re-set in checkHandshake method; this value will be used only in scenarios
        // when checkHandshake is not called, like using EndpointWrapper on the client side.
        // this.uri is then used for creating SessionImpl and used as a return value in Session.getRequestURI() method.
        this.uri = contextPath;
        this.collector = collector;
        this.componentProvider = componentProvider;
        this.serverEndpointConfigurator = serverEndpointConfigurator;

        this.configuration = configuration == null ? new EndpointConfiguration() {

            private final Map<String, Object> properties = new HashMap<String, Object>();

            @Override
            public List<Encoder> getEncoders() {
                return Collections.emptyList();
            }

            @Override
            public List<Decoder> getDecoders() {
                return Collections.emptyList();
            }

            @Override
            public Map<String, Object> getUserProperties() {
                return properties;
            }
        } : configuration;

        for (Decoder dec : this.configuration.getDecoders()) {
            if (dec instanceof CoderWrapper) {
                decoders.add((CoderWrapper) dec);
            } else {
                Class<?> type = getDecoderClassType(dec.getClass());
                decoders.add(new CoderWrapper(dec, type));
            }
        }

        decoders.addAll(PrimitiveDecoders.ALL_WRAPPED);
        decoders.add(new CoderWrapper<Decoder>(NoOpTextCoder.class, String.class));
        decoders.add(new CoderWrapper<Decoder>(NoOpByteBufferCoder.class, ByteBuffer.class));
        decoders.add(new CoderWrapper<Decoder>(NoOpByteArrayCoder.class, byte[].class));
        decoders.add(new CoderWrapper<Decoder>(ReaderDecoder.class, Reader.class));
        decoders.add(new CoderWrapper<Decoder>(InputStreamDecoder.class, InputStream.class));

        for (Encoder encoder : this.configuration.getEncoders()) {
            if (encoder instanceof CoderWrapper) {
                encoders.add((CoderWrapper) encoder);
            } else {
                Class<?> type = getEncoderClassType(encoder.getClass());
                encoders.add(new CoderWrapper(encoder, type));
            }
        }

        encoders.add(new CoderWrapper<Encoder>(NoOpTextCoder.class, String.class));
        encoders.add(new CoderWrapper<Encoder>(NoOpByteBufferCoder.class, ByteBuffer.class));
        encoders.add(new CoderWrapper<Encoder>(NoOpByteArrayCoder.class, byte[].class));
        encoders.add(new CoderWrapper<Encoder>(ToStringEncoder.class, Object.class));
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

        return serverEndpointConfigurator.checkOrigin(hr.getHeader("Origin")) &&
                serverEndpointConfigurator.matchesURI(getEndpointPath(sec.getPath()), URI.create(uri), templateValues);
    }

    private String getEndpointPath(String relativePath) {
        return (contextPath.endsWith("/") ? contextPath.substring(0, contextPath.length() - 1) : contextPath)
                + "/" + (relativePath.startsWith("/") ? relativePath.substring(1) : relativePath);
    }

    private <T> T getCoderInstance(Session session, CoderWrapper<T> wrapper) {
        final T coder = wrapper.getCoder();
        if (coder == null) {
            return this.componentProvider.getInstance(wrapper.getCoderClass(), session, collector);
        }

        return coder;
    }

    public Object decodeCompleteMessage(Session session, Object message, Class<?> type) {
        for (CoderWrapper<Decoder> dec : decoders) {
            try {
                final Class<? extends Decoder> decoderClass = dec.getCoderClass();

                if (Decoder.Text.class.isAssignableFrom(decoderClass)) {
                    if (type != null && type.isAssignableFrom(dec.getType())) {
                        final Decoder.Text decoder = (Decoder.Text) getCoderInstance(session, dec);

                        if (decoder.willDecode((String) message)) {
                            return decoder.decode((String) message);
                        }
                    }
                } else if (Decoder.Binary.class.isAssignableFrom(decoderClass)) {
                    if (type != null && type.isAssignableFrom(dec.getType())) {
                        final Decoder.Binary decoder = (Decoder.Binary) getCoderInstance(session, dec);

                        if (decoder.willDecode((ByteBuffer) message)) {
                            return decoder.decode((ByteBuffer) message);
                        }
                    }
                } else if (Decoder.TextStream.class.isAssignableFrom(decoderClass)) {
                    if (type != null && type.isAssignableFrom(dec.getType())) {
                        return ((Decoder.TextStream) getCoderInstance(session, dec)).decode(new StringReader((String) message));
                    }
                } else if (Decoder.BinaryStream.class.isAssignableFrom(decoderClass)) {
                    if (type != null && type.isAssignableFrom(dec.getType())) {
                        byte[] array = ((ByteBuffer) message).array();
                        return ((Decoder.BinaryStream) getCoderInstance(session, dec)).decode(new ByteArrayInputStream(array));
                    }
                }
            } catch (Exception e) {
                collector.addException(e);
            }
        }
        return null;
    }

    private ArrayList<CoderWrapper<Decoder>> findApplicableDecoders(Session session, Object message, boolean isString) {
        ArrayList<CoderWrapper<Decoder>> result = new ArrayList<CoderWrapper<Decoder>>();

        for (CoderWrapper<Decoder> dec : decoders) {
            if (isString && (Decoder.Text.class.isAssignableFrom(dec.getCoderClass()))) {
                final Decoder.Text decoder = (Decoder.Text) getCoderInstance(session, dec);

                if (decoder.willDecode((String) message)) {
                    result.add(dec);
                }
            } else if (!isString && (Decoder.Binary.class.isAssignableFrom(dec.getCoderClass()))) {
                final Decoder.Binary decoder = (Decoder.Binary) getCoderInstance(session, dec);

                if (decoder.willDecode((ByteBuffer) message)) {
                    result.add(dec);
                }
            } else if (isString && (Decoder.TextStream.class.isAssignableFrom(dec.getCoderClass()))) {
                result.add(dec);
            } else if (!isString && (Decoder.BinaryStream.class.isAssignableFrom(dec.getCoderClass()))) {
                result.add(dec);
            }
        }

        return result;
    }

    Object doEncode(Session session, Object message) throws EncodeException {
        for (CoderWrapper<Encoder> enc : encoders) {
            try {
                final Class<? extends Encoder> encoderClass = enc.getCoderClass();

                if (Encoder.Binary.class.isAssignableFrom(encoderClass)) {
                    if (enc.getType().isAssignableFrom(message.getClass())) {
                        final Encoder.Binary encoder = (Encoder.Binary) getCoderInstance(session, enc);

                        return encoder.encode(message);
                    }
                } else if (Encoder.Text.class.isAssignableFrom(encoderClass)) {
                    if (enc.getType().isAssignableFrom(message.getClass())) {
                        final Encoder.Text encoder = (Encoder.Text) getCoderInstance(session, enc);

                        return encoder.encode(message);
                    }
                } else if (Encoder.BinaryStream.class.isAssignableFrom(encoderClass)) {
                    if (enc.getType().isAssignableFrom(message.getClass())) {
                        final ByteArrayOutputStream stream = new ByteArrayOutputStream();
                        final Encoder.BinaryStream encoder = (Encoder.BinaryStream) getCoderInstance(session, enc);

                        encoder.encode(message, stream);
                        return stream;
                    }
                } else if (Encoder.TextStream.class.isAssignableFrom(encoderClass)) {
                    if (enc.getType().isAssignableFrom(message.getClass())) {
                        final Writer writer = new StringWriter();
                        final Encoder.TextStream encoder = (Encoder.TextStream) getCoderInstance(session, enc);

                        encoder.encode(message, writer);
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
        if (configuration instanceof ServerEndpointConfiguration) {
            return serverEndpointConfigurator.getNegotiatedExtensions(((ServerEndpointConfiguration) configuration).getExtensions(), clientExtensions);
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public String getNegotiatedProtocol(List<String> clientProtocols) {
        if (configuration instanceof ServerEndpointConfiguration) {
            return serverEndpointConfigurator.getNegotiatedSubprotocol(((ServerEndpointConfiguration) configuration).getSubprotocols(), clientProtocols);
        } else {
            return null;
        }
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
        final SessionImpl session = new SessionImpl(container, re, this, subprotocol, extensions, isSecure,
                uri == null ? null : URI.create(uri), queryString, templateValues);
        remoteEndpointToSession.put(re, session);
        return session;
    }

    @Override
    public void remove() {
        // TODO: disconnect the endpoint?
    }

    @Override
    public void onConnect(SPIRemoteEndpoint gs, String subprotocol, List<Extension> extensions) {
        SessionImpl session = remoteEndpointToSession.get(gs);
        if (session == null) {
            // create a new session
            session = new SessionImpl(container, gs, this, subprotocol, extensions, isSecure,
                    uri == null ? null : URI.create(uri), queryString, templateValues);
        } else {
            // Session was already created in WebSocketContainer#connectToServer call
            // we need to update extensions and subprotocols
            session.setNegotiatedExtensions(extensions);
            session.setNegotiatedSubprotocol(subprotocol);
        }

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
            session.notifyMessageHandlers(messageBytes, findApplicableDecoders(session, messageBytes, false));
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
            session.notifyMessageHandlers(messageString, findApplicableDecoders(session, messageString, true));
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
        try {
            session.getBasicRemote().sendPong(bytes);
        } catch (IOException e) {
            // TODO XXX FIXME
        }
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

    private Class<?> getEncoderClassType(Class<?> encoderClass) {
        if (Encoder.Binary.class.isAssignableFrom(encoderClass)) {
            return ReflectionHelper.getClassType(encoderClass, Encoder.Binary.class);
        } else if (Encoder.Text.class.isAssignableFrom(encoderClass)) {
            return ReflectionHelper.getClassType(encoderClass, Encoder.Text.class);
        } else if (Encoder.BinaryStream.class.isAssignableFrom(encoderClass)) {
            return ReflectionHelper.getClassType(encoderClass, Encoder.BinaryStream.class);
        } else if (Encoder.TextStream.class.isAssignableFrom(encoderClass)) {
            return ReflectionHelper.getClassType(encoderClass, Encoder.TextStream.class);
        } else {
            return null;
        }
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
