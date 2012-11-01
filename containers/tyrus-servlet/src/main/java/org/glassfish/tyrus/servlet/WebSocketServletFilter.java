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
package org.glassfish.tyrus.servlet;

import java.io.IOException;
import java.util.Set;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.ProtocolHandler;

import org.glassfish.tyrus.protocol.core.WebSocketHandShake;

/**
 * Filter used for Servlet integration.
 *
 * Consumes only requests with {@link WebSocketHandShake#SEC_WS_PROTOCOL_HEADER} headers present, all others are
 * passed back to {@link FilterChain}.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class WebSocketServletFilter implements Filter {

    private final static Logger LOGGER = Logger.getLogger(WebSocketServletFilter.class.getName());

    // @WebSocketEndpoint annotated classes
    private final Set<Class<?>> classes;

    public WebSocketServletFilter(Set<Class<?>> classes) {
        this.classes = classes;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // TODO
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        final HttpServletRequest httpServletRequest = (HttpServletRequest) request;

        // check for mandatory websocket header
        final String header = httpServletRequest.getHeader(WebSocketHandShake.SEC_WS_KEY_HEADER);
        if(header != null) {
            LOGGER.info("Setting up WebSocket protocol handler");
            ProtocolHandler handler = new WebSocketProtocolHandler();
            httpServletRequest.upgrade(handler);
            new ServletHandShake().doUpgrade(httpServletRequest, (HttpServletResponse)response);

            // Servlet bug ?? Not sure why we need to flush the headers
            ((HttpServletResponse)response).flushBuffer();
            LOGGER.info("Handshake Complete");
        } else {
            filterChain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
        // TODO
    }
}
