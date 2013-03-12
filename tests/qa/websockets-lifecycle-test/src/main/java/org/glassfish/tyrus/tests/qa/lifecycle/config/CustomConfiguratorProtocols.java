/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.tests.qa.lifecycle.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.websocket.Extension;
import javax.websocket.Extension.Parameter;
import javax.websocket.server.ServerEndpointConfig;
import org.glassfish.tyrus.tests.qa.lifecycle.SessionLifeCycle;

/**
 *
 * @author michal.conos at oracle.com
 */
public class CustomConfiguratorProtocols extends ServerEndpointConfig.Configurator {
    
    protected static final Logger logger = Logger.getLogger(SessionLifeCycle.class.getCanonicalName());

    public class MyExtension implements Extension {

        String name;
        Map<String, String> map;

        public MyExtension(String name, Map map) {
            this.name = name;
            this.map = map;
        }
        
        public MyExtension(String name, String param, String value) {
            Map params = new HashMap();
            params.put(param, value);
            this.map = params;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public List<Parameter> getParameters() {
            List<Parameter> params = new ArrayList<>();

            for (final String name : map.keySet()) {
                final String val = map.get(name);

                params.add(new Parameter() {
                    @Override
                    public String getName() {
                        return name;
                    }

                    @Override
                    public String getValue() {
                        return val;
                    }
                });
            }

            return params;
        }
    }

    @Override
    public String getNegotiatedSubprotocol(List<String> supported, List<String> requested) {
        return "mikc10";
    }

    @Override
    public List<Extension> getNegotiatedExtensions(List<Extension> installed, List<Extension> requested) {
        List<Extension> extensions = new ArrayList<>();
        
        for(int i=0;i<100;i++) {
            extensions.add(
                    new MyExtension(
                    "mikcext"+i, 
                    "mikcparam"+i, 
                    "mikcval"+i)
                    );
        }
        
        return extensions;
    }
    
    @Override
    public boolean checkOrigin(String originHeaderValue) {
        logger.log(Level.INFO, "Orogon:{0}", originHeaderValue);
        return true;
    }
}
