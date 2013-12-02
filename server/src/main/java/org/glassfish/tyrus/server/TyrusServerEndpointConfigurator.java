/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.server;

import java.util.ArrayList;
import java.util.List;

import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;

import org.glassfish.tyrus.core.ComponentProviderService;
import org.glassfish.tyrus.core.ExtendedExtension;
import org.glassfish.tyrus.core.Frame;

/**
 * Tyrus implementation of {@link ServerEndpointConfig.Configurator}.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class TyrusServerEndpointConfigurator extends ServerEndpointConfig.Configurator {

    private final ComponentProviderService componentProviderService;

    public TyrusServerEndpointConfigurator() {
        this.componentProviderService = ComponentProviderService.create();
    }

    @Override
    public String getNegotiatedSubprotocol(List<String> supported, List<String> requested) {
        if (requested != null) {
            for (String clientProtocol : requested) {
                if (supported.contains(clientProtocol)) {
                    return clientProtocol;
                }
            }
        }

        return "";
    }

    @Override
    public List<Extension> getNegotiatedExtensions(List<Extension> installed, List<Extension> requested) {
        installed = new ArrayList<Extension>(installed);

        List<Extension> result = new ArrayList<Extension>();

        if (requested != null) {
            for (final Extension requestedExtension : requested) {
                for (Extension extension : installed) {
                    final String name = extension.getName();
                    // exception have the same name = are equal. Params should not be taken into account.
                    if (name != null && name.equals(requestedExtension.getName())) {
                        if (extension instanceof ExtendedExtension) {
                            final ExtendedExtension extendedExtension = (ExtendedExtension) extension;
                            result.add(new ExtendedExtension() {
                                @Override
                                public Frame processIncoming(ExtensionContext context, Frame frame) {
                                    return extendedExtension.processIncoming(context, frame);
                                }

                                @Override
                                public Frame processOutgoing(ExtensionContext context, Frame frame) {
                                    return extendedExtension.processOutgoing(context, frame);
                                }

                                /**
                                 * TODO.
                                 *
                                 * @param context TODO
                                 * @param requestedParameters TODO
                                 * @return TODO
                                 */
                                @Override
                                public List<Parameter> onExtensionNegotiation(ExtensionContext context, List<Parameter> requestedParameters) {
                                    return extendedExtension.onExtensionNegotiation(context, requestedExtension.getParameters());
                                }

                                @Override
                                public void onHandshakeResponse(ExtensionContext context, List<Parameter> responseParameters) {
                                    extendedExtension.onHandshakeResponse(context, responseParameters);
                                }

                                @Override
                                public void destroy(ExtensionContext context) {
                                    extendedExtension.destroy(context);
                                }

                                @Override
                                public String getName() {
                                    return name;
                                }

                                @Override
                                public List<Parameter> getParameters() {
                                    return extendedExtension.getParameters();
                                }
                            });
                        } else {
                            result.add(requestedExtension);
                        }
                    }
                }
            }
        }

        return result;
    }

    @Override
    public boolean checkOrigin(String originHeaderValue) {
        return true;
    }

    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {

    }

    @Override
    public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
        //noinspection unchecked
        return (T) componentProviderService.getEndpointInstance(endpointClass);
    }
}
