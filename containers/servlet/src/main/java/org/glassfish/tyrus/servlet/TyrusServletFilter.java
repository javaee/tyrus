/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2014 Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerContainer;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import javax.servlet.http.WebConnection;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.glassfish.tyrus.core.RequestContext;
import org.glassfish.tyrus.core.TyrusUpgradeResponse;
import org.glassfish.tyrus.core.TyrusWebSocketEngine;
import org.glassfish.tyrus.core.Utils;
import org.glassfish.tyrus.core.wsadl.model.Application;
import org.glassfish.tyrus.spi.UpgradeResponse;
import org.glassfish.tyrus.spi.WebSocketEngine;
import org.glassfish.tyrus.spi.Writer;

/**
 * Filter used for Servlet integration.
 * <p/>
 * Consumes only requests with {@link HandshakeRequest#SEC_WEBSOCKET_KEY} headers present, all others are
 * passed back to {@link FilterChain}.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
class TyrusServletFilter implements Filter, HttpSessionListener {

    private final static Logger LOGGER = Logger.getLogger(TyrusServletFilter.class.getName());
    private final TyrusWebSocketEngine engine;
    private final boolean wsadlEnabled;

    // I don't like this map, but it seems like it is necessary. I am forced to handle subscriptions
    // for HttpSessionListener because the listener itself must be registered *before* ServletContext
    // initialization.
    // I could create List of listeners and send a create something like sessionDestroyed(HttpSession s)
    // but that would take more time (statistically higher number of comparisons).
    private final Map<HttpSession, TyrusHttpUpgradeHandler> sessionToHandler =
            new ConcurrentHashMap<HttpSession, TyrusHttpUpgradeHandler>();

    private org.glassfish.tyrus.server.TyrusServerContainer serverContainer = null;
    private JAXBContext wsadlJaxbContext;


    TyrusServletFilter(TyrusWebSocketEngine engine) {
        this(engine, false);
    }

    TyrusServletFilter(TyrusWebSocketEngine engine, boolean wsadlEnabled) {
        this.engine = engine;
        this.wsadlEnabled = wsadlEnabled;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

        ServletContext servletContext = filterConfig.getServletContext();
        this.serverContainer = (org.glassfish.tyrus.server.TyrusServerContainer) servletContext.getAttribute(ServerContainer.class.getName());

        try {
            // TODO? - port/contextPath .. is it really relevant here?
            serverContainer.start(servletContext.getContextPath(), 0);
        } catch (Exception e) {
            throw new ServletException("Web socket server initialization failed.", e);
        } finally {
            serverContainer.doneDeployment();
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
        public void preInit(WebSocketEngine.UpgradeInfo upgradeInfo, Writer writer, boolean authenticated) {
            handler.preInit(upgradeInfo, writer, authenticated);
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
        final String header = httpServletRequest.getHeader(HandshakeRequest.SEC_WEBSOCKET_KEY);
        if (header != null) {
            LOGGER.fine("Setting up WebSocket protocol handler");

            final TyrusHttpUpgradeHandlerProxy handler = new TyrusHttpUpgradeHandlerProxy();

            final TyrusServletWriter webSocketConnection = new TyrusServletWriter(handler);

            final RequestContext requestContext = RequestContext.Builder.create()
                    .requestURI(URI.create(httpServletRequest.getRequestURI()))
                    .queryString(httpServletRequest.getQueryString())
                    .httpSession(httpServletRequest.getSession(false))
                    .secure(httpServletRequest.isSecure())
                    .userPrincipal(httpServletRequest.getUserPrincipal())
                    .isUserInRoleDelegate(new RequestContext.Builder.IsUserInRoleDelegate() {
                        @Override
                        public boolean isUserInRole(String role) {
                            return httpServletRequest.isUserInRole(role);
                        }
                    })
                    .parameterMap(httpServletRequest.getParameterMap())
                    .remoteAddr(httpServletRequest.getRemoteAddr())
                    .build();

            Enumeration<String> headerNames = httpServletRequest.getHeaderNames();

            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();

                Enumeration<String> headerValues = httpServletRequest.getHeaders(name);

                while (headerValues.hasMoreElements()) {

                    final List<String> values = requestContext.getHeaders().get(name);
                    if (values == null) {
                        requestContext.getHeaders().put(name, Utils.parseHeaderValue(headerValues.nextElement().trim()));
                    } else {
                        values.addAll(Utils.parseHeaderValue(headerValues.nextElement().trim()));
                    }
                }
            }

            final TyrusUpgradeResponse tyrusUpgradeResponse = new TyrusUpgradeResponse();
            final WebSocketEngine.UpgradeInfo upgradeInfo = engine.upgrade(requestContext, tyrusUpgradeResponse);
            switch (upgradeInfo.getStatus()) {
                case HANDSHAKE_FAILED:
                    appendTraceHeaders(httpServletResponse, tyrusUpgradeResponse);
                    httpServletResponse.sendError(tyrusUpgradeResponse.getStatus());
                    break;
                case NOT_APPLICABLE:
                    appendTraceHeaders(httpServletResponse, tyrusUpgradeResponse);
                    filterChain.doFilter(request, response);
                    break;
                case SUCCESS:
                    LOGGER.fine("Upgrading Servlet request");

                    handler.setHandler(httpServletRequest.upgrade(TyrusHttpUpgradeHandler.class));
                    final String frameBufferSize = request.getServletContext().getInitParameter(TyrusHttpUpgradeHandler.FRAME_BUFFER_SIZE);
                    if (frameBufferSize != null) {
                        handler.setIncomingBufferSize(Integer.parseInt(frameBufferSize));
                    }

                    handler.preInit(upgradeInfo, webSocketConnection, httpServletRequest.getUserPrincipal() != null);

                    if (requestContext.getHttpSession() != null) {
                        sessionToHandler.put((HttpSession) requestContext.getHttpSession(), handler);
                    }

                    httpServletResponse.setStatus(tyrusUpgradeResponse.getStatus());
                    for (Map.Entry<String, List<String>> entry : tyrusUpgradeResponse.getHeaders().entrySet()) {
                        httpServletResponse.addHeader(entry.getKey(), Utils.getHeaderFromList(entry.getValue()));
                    }

                    response.flushBuffer();
                    LOGGER.fine("Handshake Complete");
                    break;
            }
        } else {
            if (wsadlEnabled) {// wsadl
                if (((HttpServletRequest) request).getMethod().equals("GET") &&
                        ((HttpServletRequest) request).getRequestURI().endsWith("application.wsadl")) {

                    try {
                        getWsadlJaxbContext().createMarshaller().marshal(engine.getWsadlApplication(), response.getWriter());
                    } catch (JAXBException e) {
                        throw new ServletException(e);
                    }
                    ((HttpServletResponse) response).setStatus(200);
                    response.setContentType("application/wsadl+xml");
                    response.flushBuffer();
                    return;
                }
            }

            filterChain.doFilter(request, response);
        }
    }

    private void appendTraceHeaders(HttpServletResponse httpServletResponse, TyrusUpgradeResponse tyrusUpgradeResponse) {
        for (Map.Entry<String, List<String>> entry : tyrusUpgradeResponse.getHeaders().entrySet()) {
            if (entry.getKey().contains(UpgradeResponse.TRACING_HEADER_PREFIX)) {
                httpServletResponse.addHeader(entry.getKey(), Utils.getHeaderFromList(entry.getValue()));
            }
        }
    }

    private synchronized JAXBContext getWsadlJaxbContext() throws JAXBException {
        if (wsadlJaxbContext == null) {
            wsadlJaxbContext = JAXBContext.newInstance(Application.class.getPackage().getName());
        }
        return wsadlJaxbContext;
    }

    @Override
    public void destroy() {
        serverContainer.stop();
        engine.getApplicationEventListener().onApplicationDestroyed();
    }
}
