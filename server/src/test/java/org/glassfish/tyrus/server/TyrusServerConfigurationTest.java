/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2015 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests correct construction of {@link TyrusServerConfiguration} from scanned classes.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class TyrusServerConfigurationTest {

    @Test
    public void testNoServerApplicationConfig() {
        Set<Class<?>> scanned = new HashSet<Class<?>>() {
            {
                add(AnnotatedA.class);
                add(AnnotatedB.class);
                add(ProgrammaticConfA.class);
                add(ProgrammaticConfB.class);
                add(Other.class);
            }
        };

        TyrusServerConfiguration configuration = new TyrusServerConfiguration(scanned, Collections
                .<ServerEndpointConfig>emptySet());

        Assert.assertEquals(2, configuration.getAnnotatedEndpointClasses(null).size());
        Assert.assertTrue(configuration.getAnnotatedEndpointClasses(null).contains(AnnotatedA.class));
        Assert.assertTrue(configuration.getAnnotatedEndpointClasses(null).contains(AnnotatedB.class));

        // TODO XXX FIXME
//        Assert.assertEquals(2, configuration.getEndpointConfigs(null).size());
//        Assert.assertTrue(configuration.getEndpointConfigs(null).contains(ProgrammaticConfA.class));
//        Assert.assertTrue(configuration.getEndpointConfigs(null).contains(ProgrammaticConfB.class));
    }

    @Test
    public void testOneServerApplicationConfig() {
        Set<Class<?>> scanned = new HashSet<Class<?>>() {
            {
                add(ApplicationConfA.class);
                add(ProgrammaticConfB.class);
                add(AnnotatedB.class);
                add(Other.class);
            }
        };

        TyrusServerConfiguration configuration = new TyrusServerConfiguration(scanned, Collections
                .<ServerEndpointConfig>emptySet());

        Assert.assertEquals(1, configuration.getAnnotatedEndpointClasses(null).size());
        Assert.assertTrue(configuration.getAnnotatedEndpointClasses(null).contains(AnnotatedA.class));

        // TODO XXX FIXME
//        Assert.assertEquals(1, configuration.getEndpointConfigs(null).size());
//        Assert.assertTrue(configuration.getEndpointConfigs(null).contains(ProgrammaticConfA.class));
    }

    @Test
    public void testTwoServerApplicationConfig() {
        Set<Class<?>> scanned = new HashSet<Class<?>>() {
            {
                add(ApplicationConfA.class);
                add(ApplicationConfB.class);
                add(AnnotatedC.class);
                add(ProgrammaticC.class);
                add(ProgrammaticConfC.class);
                add(Other.class);
            }
        };

        TyrusServerConfiguration configuration = new TyrusServerConfiguration(scanned, Collections
                .<ServerEndpointConfig>emptySet());

        Assert.assertEquals(1, configuration.getAnnotatedEndpointClasses(null).size());
        Assert.assertTrue(configuration.getAnnotatedEndpointClasses(null).contains(AnnotatedA.class));

        // TODO XXX FIXME
//        Assert.assertEquals(1, configuration.getEndpointConfigs(null).size());
//        Assert.assertTrue(configuration.getEndpointConfigs(null).contains(ProgrammaticConfA.class));
    }

    @ServerEndpoint(value = "/AA")
    public class AnnotatedA {
    }

    @ServerEndpoint(value = "/AB")
    public class AnnotatedB {
    }

    @ServerEndpoint(value = "/AC")
    public class AnnotatedC {
    }

    public class ProgrammaticA extends Endpoint {
        @Override
        public void onOpen(Session session, EndpointConfig config) {
        }
    }

    public class ProgrammaticB extends Endpoint {
        @Override
        public void onOpen(Session session, EndpointConfig config) {
        }
    }

    public class ProgrammaticC extends Endpoint {
        @Override
        public void onOpen(Session session, EndpointConfig config) {
        }
    }

    public static class ProgrammaticConfA extends ServerEndpointConfigAdapter {
        @Override
        public Class<?> getEndpointClass() {
            return ProgrammaticA.class;
        }
    }

    public static class ProgrammaticConfB extends ServerEndpointConfigAdapter {
        @Override
        public Class<?> getEndpointClass() {
            return ProgrammaticB.class;
        }
    }

    public static class ProgrammaticConfC extends ServerEndpointConfigAdapter {
        @Override
        public Class<?> getEndpointClass() {
            return ProgrammaticC.class;
        }
    }

    public class Other {
    }

    public static class ApplicationConfA implements ServerApplicationConfig {
        @Override
        public Set<ServerEndpointConfig> getEndpointConfigs(Set<Class<? extends Endpoint>> scanned) {
            return new HashSet<ServerEndpointConfig>() {
                {
                    add(new ProgrammaticConfA());
                }
            };

        }

        @Override
        public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> scanned) {
            return new HashSet<Class<?>>() {
                {
                    add(AnnotatedA.class);
                }
            };
        }
    }

    public static class ApplicationConfB implements ServerApplicationConfig {
        @Override
        public Set<ServerEndpointConfig> getEndpointConfigs(Set<Class<? extends Endpoint>> scanned) {
            return new HashSet<ServerEndpointConfig>() {
                {
                    add(new ProgrammaticConfA());
                }
            };
        }

        @Override
        public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> scanned) {
            return new HashSet<Class<?>>() {
                {
                    add(AnnotatedA.class);
                }
            };
        }
    }
}
