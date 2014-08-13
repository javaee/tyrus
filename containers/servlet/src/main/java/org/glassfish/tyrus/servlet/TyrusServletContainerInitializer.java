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

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import javax.servlet.FilterRegistration;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.HandlesTypes;

import org.glassfish.tyrus.core.DebugContext;
import org.glassfish.tyrus.core.TyrusWebSocketEngine;
import org.glassfish.tyrus.core.monitoring.ApplicationEventListener;
import org.glassfish.tyrus.server.TyrusServerContainer;
import org.glassfish.tyrus.spi.WebSocketEngine;

/**
 * Registers a filter for upgrade handshake.
 * <p/>
 * All requests will be handled by registered filter if not specified otherwise.
 *
 * @author Jitendra Kotamraju
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
@HandlesTypes({ServerEndpoint.class, ServerApplicationConfig.class, Endpoint.class})
public class TyrusServletContainerInitializer implements ServletContainerInitializer {
    private static final Logger LOGGER =
            Logger.getLogger(TyrusServletContainerInitializer.class.getName());

    /**
     * Tyrus classes scanned by container will be filtered.
     */
    private static final Set<Class<?>> FILTERED_CLASSES = new HashSet<Class<?>>() {{
        add(org.glassfish.tyrus.server.TyrusServerConfiguration.class);
    }};

    @Override
    public void onStartup(Set<Class<?>> classes, final ServletContext ctx) throws ServletException {
        if (classes == null || classes.isEmpty()) {
            return;
        }

        classes.removeAll(FILTERED_CLASSES);

        final Integer incomingBufferSize = getIntContextParam(ctx, TyrusHttpUpgradeHandler.FRAME_BUFFER_SIZE);
        final Integer maxSessionsPerApp = getIntContextParam(ctx, TyrusWebSocketEngine.MAX_SESSIONS_PER_APP);
        final Integer maxSessionsPerRemoteAddr = getIntContextParam(ctx, TyrusWebSocketEngine.MAX_SESSIONS_PER_REMOTE_ADDR);
        final DebugContext.TracingType tracingType = getEnumContextParam(ctx, TyrusWebSocketEngine.TRACING_TYPE, DebugContext.TracingType.class, DebugContext.TracingType.OFF);
        final DebugContext.TracingThreshold tracingThreshold = getEnumContextParam(ctx, TyrusWebSocketEngine.TRACING_THRESHOLD, DebugContext.TracingThreshold.class, DebugContext.TracingThreshold.TRACE);

        final ApplicationEventListener applicationEventListener = createApplicationEventListener(ctx);
        final TyrusServerContainer serverContainer = new TyrusServerContainer(classes) {

            private final WebSocketEngine engine = TyrusWebSocketEngine.builder(this)
                    .applicationEventListener(applicationEventListener)
                    .incomingBufferSize(incomingBufferSize)
                    .maxSessionsPerApp(maxSessionsPerApp)
                    .maxSessionsPerRemoteAddr(maxSessionsPerRemoteAddr)
                    .tracingType(tracingType)
                    .tracingThreshold(tracingThreshold)
                    .build();

            @Override
            public void register(Class<?> endpointClass) throws DeploymentException {
                engine.register(endpointClass, ctx.getContextPath());
            }

            @Override
            public void register(ServerEndpointConfig serverEndpointConfig) throws DeploymentException {
                engine.register(serverEndpointConfig, ctx.getContextPath());
            }

            @Override
            public WebSocketEngine getWebSocketEngine() {
                return engine;
            }
        };
        ctx.setAttribute(ServerContainer.class.getName(), serverContainer);
        String wsadlEnabledParam = ctx.getInitParameter(TyrusWebSocketEngine.WSADL_SUPPORT);
        boolean wsadlEnabled = wsadlEnabledParam != null && wsadlEnabledParam.equalsIgnoreCase("true");
        LOGGER.config("WSADL enabled: " + wsadlEnabled);

        TyrusServletFilter filter = new TyrusServletFilter((TyrusWebSocketEngine) serverContainer.getWebSocketEngine(), wsadlEnabled);

        // HttpSessionListener registration
        ctx.addListener(filter);

        // Filter registration
        final FilterRegistration.Dynamic reg = ctx.addFilter("WebSocket filter", filter);
        reg.setAsyncSupported(true);
        reg.addMappingForUrlPatterns(null, true, "/*");
        LOGGER.info("Registering WebSocket filter for url pattern /*");
        if (applicationEventListener != null) {
            applicationEventListener.onApplicationInitialized(ctx.getContextPath());
        }
    }

    /**
     * Get {@link Integer} parameter from {@link javax.servlet.ServletContext}.
     *
     * @param ctx       used to retrieve init parameter.
     * @param paramName parameter name.
     * @return parsed {@link Integer} value or {@code null} when the value is not integer or when the init parameter is
     * not present.
     */
    private Integer getIntContextParam(ServletContext ctx, String paramName) {
        String initParameter = ctx.getInitParameter(paramName);
        if (initParameter != null) {
            try {
                return Integer.parseInt(initParameter);
            } catch (NumberFormatException e) {
                LOGGER.log(Level.CONFIG, "Invalid configuration value [" + paramName + " = " + initParameter + "], integer expected");
            }
        }

        return null;
    }

    private <T extends Enum<T>> T getEnumContextParam(ServletContext ctx, String paramName, Class<T> type, T defaultValue) {
        String initParameter = ctx.getInitParameter(paramName);

        if (initParameter == null) {
            return defaultValue;
        }

        try {
            return Enum.valueOf(type, initParameter.trim().toUpperCase());
        } catch (Exception e) {
            LOGGER.log(Level.CONFIG, "Invalid configuration value [" + paramName + " = " + initParameter + "]");
        }

        return defaultValue;
    }

    private ApplicationEventListener createApplicationEventListener(final ServletContext ctx) {
        String listenerClassName = ctx.getInitParameter(ApplicationEventListener.APPLICATION_EVENT_LISTENER);
        if (listenerClassName == null) {
            return null;
        }
        try {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            Class listenerClass = Class.forName(listenerClassName, true, contextClassLoader);

            Object o = listenerClass.newInstance();
            if (o instanceof ApplicationEventListener) {
                return (ApplicationEventListener) o;
            } else {
                LOGGER.log(Level.WARNING, "Class " + listenerClassName + " does not implement ApplicationEventListener");
            }
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.WARNING, "ApplicationEventListener implementation " + listenerClassName + " not found", e);
        } catch (InstantiationException e) {
            LOGGER.log(Level.WARNING, "ApplicationEventListener implementation " + listenerClassName + " could not have been instantiated", e);
        } catch (IllegalAccessException e) {
            LOGGER.log(Level.WARNING, "ApplicationEventListener implementation " + listenerClassName + " could not have been instantiated", e);
        }
        return null;
    }
}