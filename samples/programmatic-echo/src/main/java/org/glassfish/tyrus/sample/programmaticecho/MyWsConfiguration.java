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
package org.glassfish.tyrus.sample.programmaticecho;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.Extension;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import org.glassfish.tyrus.ErrorCollector;
import org.glassfish.tyrus.server.TyrusServerConfiguration;

/**
 * Custom server configuration.
 *
 * @author Martin Matula (martin.matula at oracle.com)
 */
public class MyWsConfiguration extends TyrusServerConfiguration {

    /**
     * Default constructor, reg
     */
    public MyWsConfiguration() {
        super(new HashSet<Class<?>>(Arrays.asList(EchoEndpointConfiguration.class)), new ErrorCollector());
    }

    public static class EchoEndpointConfiguration extends javax.websocket.server.DefaultServerConfiguration {
        public EchoEndpointConfiguration() {
            super(EchoEndpoint.class, "test");
        }

        @Override
        public String getNegotiatedSubprotocol(List<String> requestedSubprotocols) {
            return null;
        }

        @Override
        public List<Extension> getNegotiatedExtensions(List<Extension> requestedExtensions) {
            return requestedExtensions;
        }

        @Override
        public boolean checkOrigin(String originHeaderValue) {
            return true;
        }

        // TODO http://java.net/jira/browse/WEBSOCKET_SPEC-126
//        @Override
//        public boolean matchesURI(URI uri) {
//            return uri.toString().equals("/sample-programmatic-echo/test");
//        }
    }

    public static class EchoEndpoint extends Endpoint {
        @Override
        public void onOpen(final Session session, final EndpointConfiguration endpointConfiguration) {
            session.addMessageHandler(new MessageHandler.Basic<String>() {
                @Override
                public void onMessage(String message) {
                    System.out.println("##################### Message received");
                    try {
                        session.getRemote().sendString(message + " (from your server)");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }
}
