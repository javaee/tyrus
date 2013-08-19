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
package org.glassfish.tyrus.servlet;

import java.io.IOException;
import java.net.URI;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import javax.servlet.http.WebConnection;

import org.glassfish.tyrus.core.RequestContext;
import org.glassfish.tyrus.core.Utils;
import org.glassfish.tyrus.server.ServerContainerFactory;
import org.glassfish.tyrus.spi.SPIWebSocketEngine;
import org.glassfish.tyrus.spi.SPIWriter;
import org.glassfish.tyrus.websockets.HandshakeException;
import org.glassfish.tyrus.websockets.WebSocketEngine;

/**
 * Filter used for Servlet integration.
 * <p/>
 * Consumes only requests with {@link WebSocketEngine#SEC_WS_KEY_HEADER} headers present, all others are
 * passed back to {@link FilterChain}.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class TyrusServletFilter implements Filter, HttpSessionListener {

    private static final int INFORMATIONAL_FIXED_PORT = 8080;
    private final static Logger LOGGER = Logger.getLogger(TyrusServletFilter.class.getName());
    private final WebSocketEngine engine = new WebSocketEngine();
    private org.glassfish.tyrus.server.TyrusServerContainer serverContainer = null;

    // @ServerEndpoint annotated classes and classes extending ServerApplicationConfig
    private Set<Class<?>> classes = null;
    private final Set<Class<?>> dynamicallyDeployedClasses = new HashSet<Class<?>>();
    private final Set<ServerEndpointConfig> dynamicallyDeployedServerEndpointConfigs = new HashSet<ServerEndpointConfig>();

    // I don't like this map, but it seems like it is necessary. I am forced to handle subscriptions
    // for HttpSessionListener because the listener itself must be registered *before* ServletContext
    // initialization.
    // I could create List of listeners and send a create something like sessionDestroyed(HttpSession s)
    // but that would take more time (statistically higher number of comparisons).
    private final Map<HttpSession, TyrusHttpUpgradeHandler> sessionToHandler =
            new ConcurrentHashMap<HttpSession, TyrusHttpUpgradeHandler>();

    public TyrusServletFilter() {
    }

    void addClass(Class<?> clazz) {
        if (this.serverContainer != null) {
            throw new IllegalStateException("Filter already initiated.");
        }
        this.dynamicallyDeployedClasses.add(clazz);
    }

    void addServerEndpointConfig(ServerEndpointConfig serverEndpointConfig) {
        if (this.serverContainer != null) {
            throw new IllegalStateException("Filter already initiated.");
        }
        this.dynamicallyDeployedServerEndpointConfigs.add(serverEndpointConfig);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String contextRoot = filterConfig.getServletContext().getContextPath();
        this.serverContainer = ServerContainerFactory.create(new ServletServerFactory(engine), contextRoot, INFORMATIONAL_FIXED_PORT, classes, dynamicallyDeployedClasses, dynamicallyDeployedServerEndpointConfigs);
        try {
            serverContainer.start();
        } catch (Exception e) {
            throw new ServletException("Web socket server initialization failed.", e);
        } finally {

            // remove reference to filter.
            final ServerContainer container = (ServerContainer) filterConfig.getServletContext().getAttribute(TyrusServletServerContainer.SERVER_CONTAINER_ATTRIBUTE);
            if (container instanceof TyrusServletServerContainer) {
                ((TyrusServletServerContainer) container).doneDeployment();
            }
        }
    }

    @Override
    public void sessionCreated(HttpSessionEvent se) {
        // do nothing.
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        final TyrusHttpUpgradeHandler upgradeHandler = sessionToHandler.get(se.getSession());
        if (upgradeHandler != null) {
            sessionToHandler.remove(se.getSession());
            upgradeHandler.sessionDestroyed();
        }
    }

    private static class TyrusHttpUpgradeHandlerProxy extends TyrusHttpUpgradeHandler {

        private TyrusHttpUpgradeHandler handler;

        @Override
        public void init(WebConnection wc) {
            handler.init(wc);
        }

        @Override
        public void onDataAvailable() {
            handler.onDataAvailable();
        }

        @Override
        public void onAllDataRead() {
            handler.onAllDataRead();
        }

        @Override
        public void onError(Throwable t) {
            handler.onError(t);
        }

        @Override
        public void destroy() {
            handler.destroy();
        }

        @Override
        public void sessionDestroyed() {
            handler.sessionDestroyed();
        }

        @Override
        public void postInit(SPIWebSocketEngine engine, SPIWriter writer, boolean authenticated) {
            handler.postInit(engine, writer, authenticated);
        }

        @Override
        public void setIncomingBufferSize(int incomingBufferSize) {
            handler.setIncomingBufferSize(incomingBufferSize);
        }

        @Override
        WebConnection getWebConnection() {
            return handler.getWebConnection();
        }

        void setHandler(TyrusHttpUpgradeHandler handler) {
            this.handler = handler;
        }
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        final HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;

        // check for mandatory websocket header
        final String header = httpServletRequest.getHeader(WebSocketEngine.SEC_WS_KEY_HEADER);
        if (header != null) {
            LOGGER.fine("Setting up WebSocket protocol handler");

            final TyrusHttpUpgradeHandlerProxy handler = new TyrusHttpUpgradeHandlerProxy();

            final TyrusServletWriter webSocketConnection = new TyrusServletWriter(handler, httpServletResponse);

            final RequestContext requestContext = RequestContext.Builder.create()
                    .requestURI(URI.create(httpServletRequest.getRequestURI()))
                    .queryString(httpServletRequest.getQueryString())
                    .connection(webSocketConnection)
                    .requestPath(httpServletRequest.getServletPath())
                    .httpSession(httpServletRequest.getSession())
                    .secure(httpServletRequest.isSecure())
                    .userPrincipal(httpServletRequest.getUserPrincipal())
                    .isUserInRoleDelegate(new RequestContext.Builder.IsUserInRoleDelegate() {
                        @Override
                        public boolean isUserInRole(String role) {
                            return httpServletRequest.isUserInRole(role);
                        }
                    })
                    .parameterMap(httpServletRequest.getParameterMap())
                    .build();

            Enumeration<String> headerNames = httpServletRequest.getHeaderNames();

            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();

                final List<String> values = requestContext.getHeaders().get(name);
                if (values == null) {
                    requestContext.getHeaders().put(name, Utils.parseHeaderValue(httpServletRequest.getHeader(name).trim()));
                } else {
                    values.addAll(Utils.parseHeaderValue(httpServletRequest.getHeader(name).trim()));
                }
            }

            try {
                final SPIWebSocketEngine.UpgradeListener upgradeListener = new SPIWebSocketEngine.UpgradeListener() {
                    @Override
                    public void onUpgradeFinished() throws HandshakeException {
                        LOGGER.fine("Upgrading Servlet request");
                        try {
                            handler.setHandler(httpServletRequest.upgrade(TyrusHttpUpgradeHandler.class));
                            final String frameBufferSize = request.getServletContext().getInitParameter(TyrusHttpUpgradeHandler.FRAME_BUFFER_SIZE);
                            if (frameBufferSize != null) {
                                handler.setIncomingBufferSize(Integer.parseInt(frameBufferSize));
                            }
                        } catch (Exception e) {
                            throw new HandshakeException(500, "Handshake error.", e);
                        }

                        // calls engine.onConnect()
                        handler.postInit(engine, webSocketConnection, httpServletRequest.getUserPrincipal() != null);
                        sessionToHandler.put(httpServletRequest.getSession(), handler);
                    }
                };

                if (!engine.upgrade(webSocketConnection, requestContext, webSocketConnection, upgradeListener)) {
                    filterChain.doFilter(request, response);
                    return;
                }

            } catch (HandshakeException e) {
                LOGGER.log(Level.CONFIG, e.getMessage(), e);
                httpServletResponse.sendError(e.getCode(), e.getMessage());
            }

            // Servlet bug ?? Not sure why we need to flush the headers
            response.flushBuffer();
            LOGGER.fine("Handshake Complete");
        } else {
            filterChain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
        serverContainer.stop();
    }

    /**
     * Set the scanned classes.
     *
     * @param classes scanned classes.
     */
    public void setClasses(Set<Class<?>> classes) {
        this.classes = classes;
    }
}
