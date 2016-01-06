/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015-2016 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.ext.client.java8;

import java.io.IOException;
import java.net.URI;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.function.BiConsumer;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.core.Beta;
import org.glassfish.tyrus.core.MessageHandlerManager;

/**
 * Session builder removed the need for having client endpoint.
 * <p>
 * Client endpoint is replaced by references to onOpen, onError and onClose methods plus message handlers. Same rules
 * which do apply or limit message handlers are forced here as well. None of the methods is required, so {@link
 * Session}
 * can be opened even without it and used only for sending messages.
 * <p>
 * {@link javax.websocket.Encoder Encoders} and {@link javax.websocket.Decoder decoders} can be registered by creating
 * {@link javax.websocket.ClientEndpointConfig} and registering it to SessionBuilder via
 * {@link org.glassfish.tyrus.ext.client.java8.SessionBuilder#clientEndpointConfig} method call.
 * <p>
 * Code example:
 * <pre>Session session = new SessionBuilder()
 *      .uri(getURI(SessionBuilderEncDecTestEndpoint.class))
 *      .clientEndpointConfig(clientEndpointConfig)
 *      .messageHandler(AClass.class,aClass -&gt; messageLatch.countDown())
 *      .onOpen((session1, endpointConfig) -&gt; onOpenLatch.countDown())
 *      .onError((session1, throwable) -&gt; onErrorLatch.countDown())
 *      .onClose((session1, closeReason) -&gt; onCloseLatch.countDown())
 *      .connect();</pre>
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
@Beta
public class SessionBuilder {

    private static final BiConsumer NO_OP_BI_CONSUMER = (o, o2) -> {
        // no-op
    };

    private final WebSocketContainer container;
    private final List<Map.Entry<Class<?>, MessageHandler.Whole<?>>> wholeMessageHandlers = new ArrayList<>();
    private final List<Map.Entry<Class<?>, MessageHandler.Partial<?>>> partialMessageHandlers = new ArrayList<>();

    private URI uri;
    private ClientEndpointConfig clientEndpointConfig;

    private BiConsumer<Session, EndpointConfig> onOpen;
    private BiConsumer<Session, Throwable> onError;
    private BiConsumer<Session, CloseReason> onClose;

    /**
     * Create SessionBuilder with provided {@link javax.websocket.WebSocketContainer}.
     *
     * @param container provided websocket container.
     */
    public SessionBuilder(WebSocketContainer container) {
        this.container = container;
    }

    /**
     * Create SessionBuilder with provided container provider class name.
     * <p>
     * Generally, this is used only when you want to have fine-grained control about used container.
     *
     * @param containerProviderClassName container provider class name.
     */
    public SessionBuilder(String containerProviderClassName) {
        this(ClientManager.createClient(containerProviderClassName));
    }

    /**
     * Create new SessionBuilder instance.
     */
    public SessionBuilder() {
        this(ClientManager.createClient());
    }

    /**
     * Set {@link javax.websocket.ClientEndpointConfig}.
     *
     * @param clientEndpointConfig {@link javax.websocket.ClientEndpointConfig} to be set.
     * @return updated SessionBuilder instance.
     */
    public SessionBuilder clientEndpointConfig(ClientEndpointConfig clientEndpointConfig) {
        this.clientEndpointConfig = clientEndpointConfig;
        return this;
    }

    /**
     * Set {@link java.net.URI} of the server endpoint.
     *
     * @param uri server endpoint address.
     * @return updated SessionBuilder instance.
     */
    public SessionBuilder uri(URI uri) {
        this.uri = uri;
        return this;
    }

    /**
     * Add whole message handler.
     *
     * @param clazz          handled message type class.
     * @param messageHandler message handler.
     * @param <T>            handled message type.
     * @return updated SessionBuilder instance.
     */
    public <T> SessionBuilder messageHandler(Class<T> clazz, MessageHandler.Whole<T> messageHandler) {
        wholeMessageHandlers.add(new AbstractMap.SimpleEntry<>(clazz, messageHandler));
        return this;
    }

    /**
     * Add partial message handler.
     *
     * @param clazz          handled message type class.
     * @param messageHandler message handler.
     * @param <T>            handled message type.
     * @return updated SessionBuilder instance.
     */
    public <T> SessionBuilder messageHandlerPartial(Class<T> clazz, MessageHandler.Partial<T> messageHandler) {
        partialMessageHandlers.add(new AbstractMap.SimpleEntry<>(clazz, messageHandler));
        return this;
    }

    /**
     * Set method reference which will be invoked when a {@link Session} is opened.
     *
     * @param onOpen method invoked when a {@link Session} is opened.
     * @return updated SessionBuilder instance.
     * @see javax.websocket.OnOpen
     */
    public SessionBuilder onOpen(BiConsumer<Session, EndpointConfig> onOpen) {
        this.onOpen = onOpen;
        return this;
    }

    /**
     * Set method reference which will be invoked when {@link javax.websocket.OnError} method is invoked.
     *
     * @param onError method invoked when {@link javax.websocket.OnError} method is invoked.
     * @return updated SessionBuilder instance.
     * @see javax.websocket.OnError
     */
    public SessionBuilder onError(BiConsumer<Session, Throwable> onError) {
        this.onError = onError;
        return this;
    }

    /**
     * Set method reference which will be invoked when a {@link Session} is closed.
     *
     * @param onClose method invoked when a {@link Session} is closed.
     * @return updated SessionBuilder instance.
     * @see javax.websocket.OnClose
     */
    public SessionBuilder onClose(BiConsumer<Session, CloseReason> onClose) {
        this.onClose = onClose;
        return this;
    }

    /**
     * Connect to the remote (server) endpoint.
     * <p>
     * This method can be called multiple times, each invocation will result in new {@link Session} (new TCP connection
     * to the server).
     *
     * @return created session.
     * @throws IOException         when there is a problem with connecting to the server endpoint.
     * @throws DeploymentException when there is a problem with provided settings or there is other, non IO connection
     *                             issue.
     */
    public Session connect() throws IOException, DeploymentException {

        // default values
        if (clientEndpointConfig == null) {
            clientEndpointConfig = ClientEndpointConfig.Builder.create().build();
        }

        //noinspection unchecked
        onOpen = onOpen != null ? onOpen : (BiConsumer<Session, EndpointConfig>) NO_OP_BI_CONSUMER;
        //noinspection unchecked
        onClose = onClose != null ? onClose : (BiConsumer<Session, CloseReason>) NO_OP_BI_CONSUMER;
        //noinspection unchecked
        onError = onError != null ? onError : (BiConsumer<Session, Throwable>) NO_OP_BI_CONSUMER;

        // validation
        MessageHandlerManager messageHandlerManager =
                MessageHandlerManager.fromDecoderClasses(clientEndpointConfig.getDecoders());

        try {
            for (Map.Entry<Class<?>, MessageHandler.Whole<?>> entry : wholeMessageHandlers) {
                messageHandlerManager
                        .addMessageHandler((Class) entry.getKey(), (MessageHandler.Whole) entry.getValue());
            }

            for (Map.Entry<Class<?>, MessageHandler.Partial<?>> entry : partialMessageHandlers) {
                messageHandlerManager
                        .addMessageHandler((Class) entry.getKey(), (MessageHandler.Partial) entry.getValue());
            }
        } catch (IllegalStateException ise) {
            throw new DeploymentException(ise.getMessage(), ise);
        }
        // validation end


        final URI path = this.uri;
        final ClientEndpointConfig clientEndpointConfig = this.clientEndpointConfig;

        final BiConsumer<Session, EndpointConfig> onOpen = this.onOpen;
        final BiConsumer<Session, Throwable> onError = this.onError;
        final BiConsumer<Session, CloseReason> onClose = this.onClose;

        final Endpoint endpoint = new Endpoint() {
            @Override
            public void onOpen(Session session, EndpointConfig config) {
                for (Map.Entry<Class<?>, MessageHandler.Whole<?>> entry : wholeMessageHandlers) {
                    session.addMessageHandler((Class) entry.getKey(), (MessageHandler.Whole) entry.getValue());
                }

                for (Map.Entry<Class<?>, MessageHandler.Partial<?>> entry : partialMessageHandlers) {
                    session.addMessageHandler((Class) entry.getKey(), (MessageHandler.Partial) entry.getValue());
                }

                onOpen.accept(session, config);
            }

            @Override
            public void onClose(Session session, CloseReason closeReason) {
                onClose.accept(session, closeReason);
            }

            @Override
            public void onError(Session session, Throwable thr) {
                onError.accept(session, thr);
            }
        };

        return container.connectToServer(endpoint, clientEndpointConfig, path);
    }

    /**
     * Connect to the remote (server) endpoint asynchronously.
     * <p>
     * Same statements as at {@link SessionBuilder#connect()} do apply here, only the returned value and possible
     * exceptions are returned as {@link java.util.concurrent.CompletableFuture}.
     * <p>
     * {@link ForkJoinPool#commonPool()} is used for executing the connection phase.
     *
     * @return completable future returning {@link Session} when created.
     */
    public CompletableFuture<Session> connectAsync() {

        final CompletableFuture<Session> completableFuture = new CompletableFuture<>();

        final ForkJoinTask<Void> forkJoinTask = new ForkJoinTask<Void>() {

            @Override
            public final Void getRawResult() {
                return null;
            }

            @Override
            public final void setRawResult(Void v) {
            }

            @Override
            protected boolean exec() {
                try {
                    completableFuture.complete(connect());
                    return true;
                } catch (Exception e) {
                    completableFuture.completeExceptionally(e);
                }
                return false;
            }
        };

        // TODO: Can we use ForkJoinPool#commonPool?
        ForkJoinPool.commonPool().execute(forkJoinTask);

        //noinspection unchecked
        return completableFuture;
    }

    /**
     * Connect to the remote (server) endpoint asynchronously.
     * <p>
     * Same statements as at {@link SessionBuilder#connect()} do apply here, only the returned value and possible
     * exceptions are returned as {@link java.util.concurrent.CompletableFuture}.
     * <p>
     * Provided {@link java.util.concurrent.ExecutorService} is used for executing the connection phase.
     *
     * @param executorService executor service used for executing the {@link #connect()} method.
     * @return completable future returning {@link Session} when created.
     */
    public CompletableFuture<Session> connectAsync(ExecutorService executorService) {

        final CompletableFuture<Session> completableFuture = new CompletableFuture<>();

        Runnable runnable = () -> {
            try {
                completableFuture.complete(connect());
            } catch (Exception e) {
                completableFuture.completeExceptionally(e);
            }
        };

        executorService.execute(runnable);

        return completableFuture;
    }
}
