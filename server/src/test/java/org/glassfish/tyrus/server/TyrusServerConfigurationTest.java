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

import java.util.HashSet;
import java.util.Set;

import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.Session;
import javax.websocket.server.DefaultServerConfiguration;
import javax.websocket.server.ServerApplicationConfiguration;
import javax.websocket.server.ServerEndpointConfiguration;
import javax.websocket.server.WebSocketEndpoint;

import org.glassfish.tyrus.ErrorCollector;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests correct construction of {@link TyrusServerConfiguration} from scanned classes.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class TyrusServerConfigurationTest {

    private static ErrorCollector errorCollector = new ErrorCollector();

    @Test
    public void testNoServerApplicationConfiguration(){
        Set<Class<?>> scanned = new HashSet<Class<?>>(){{
            add(AnnotatedA.class);
            add(AnnotatedB.class);
            add(ProgrammaticConfA.class);
            add(ProgrammaticConfB.class);
            add(ProgrammaticA.class);
            add(ProgrammaticB.class);
            add(Other.class);
        }};

        TyrusServerConfiguration configuration = new TyrusServerConfiguration(scanned, errorCollector);

        Assert.assertEquals(2, configuration.getAnnotatedEndpointClasses(null).size());
        Assert.assertTrue(configuration.getAnnotatedEndpointClasses(null).contains(AnnotatedA.class));
        Assert.assertTrue(configuration.getAnnotatedEndpointClasses(null).contains(AnnotatedB.class));

        Assert.assertEquals(2, configuration.getEndpointConfigurationClasses(null).size());
        Assert.assertTrue(configuration.getEndpointConfigurationClasses(null).contains(ProgrammaticConfA.class));
        Assert.assertTrue(configuration.getEndpointConfigurationClasses(null).contains(ProgrammaticConfB.class));
    }

    @Test
    public void testOneServerApplicationConfiguration(){
        Set<Class<?>> scanned = new HashSet<Class<?>>(){{
            add(ApplicationConfA.class);
            add(ProgrammaticConfB.class);
            add(AnnotatedB.class);
            add(ProgrammaticB.class);
            add(Other.class);
        }};

        TyrusServerConfiguration configuration = new TyrusServerConfiguration(scanned, errorCollector);

        Assert.assertEquals(1, configuration.getAnnotatedEndpointClasses(null).size());
        Assert.assertTrue(configuration.getAnnotatedEndpointClasses(null).contains(AnnotatedA.class));

        Assert.assertEquals(1, configuration.getEndpointConfigurationClasses(null).size());
        Assert.assertTrue(configuration.getEndpointConfigurationClasses(null).contains(ProgrammaticConfA.class));
    }

    @Test
    public void testTwoServerApplicationConfiguration(){
        Set<Class<?>> scanned = new HashSet<Class<?>>(){{
            add(ApplicationConfA.class);
            add(ApplicationConfB.class);
            add(AnnotatedC.class);
            add(ProgrammaticC.class);
            add(ProgrammaticConfC.class);
            add(Other.class);
        }};

        TyrusServerConfiguration configuration = new TyrusServerConfiguration(scanned, errorCollector);

        Assert.assertEquals(1, configuration.getAnnotatedEndpointClasses(null).size());
        Assert.assertTrue(configuration.getAnnotatedEndpointClasses(null).contains(AnnotatedA.class));

        Assert.assertEquals(1, configuration.getEndpointConfigurationClasses(null).size());
        Assert.assertTrue(configuration.getEndpointConfigurationClasses(null).contains(ProgrammaticConfA.class));
    }

    @WebSocketEndpoint(value = "/AA", configuration = DefaultServerConfiguration.class)
    public class AnnotatedA{}

    @WebSocketEndpoint(value = "/AB", configuration = DefaultServerConfiguration.class)
    public class AnnotatedB{}

    @WebSocketEndpoint(value = "/AC", configuration = DefaultServerConfiguration.class)
    public class AnnotatedC{}

    public class ProgrammaticA extends Endpoint{
        @Override
        public void onOpen(Session session, EndpointConfiguration config) {}
    }

    public class ProgrammaticB extends Endpoint{
        @Override
        public void onOpen(Session session, EndpointConfiguration config) {}
    }

    public class ProgrammaticC extends Endpoint{
        @Override
        public void onOpen(Session session, EndpointConfiguration config) {}
    }

    public class ProgrammaticConfA extends ServerEndpointConfigurationAdapter{
        @Override
        public Class<? extends Endpoint> getEndpointClass() {
            return ProgrammaticA.class;
        }
    }

    public class ProgrammaticConfB extends ServerEndpointConfigurationAdapter{
        @Override
        public Class<? extends Endpoint> getEndpointClass() {
            return ProgrammaticB.class;
        }
    }

    public class ProgrammaticConfC extends ServerEndpointConfigurationAdapter{
        @Override
        public Class<? extends Endpoint> getEndpointClass() {
            return ProgrammaticC.class;
        }
    }

    public class Other{}

    public static class ApplicationConfA implements ServerApplicationConfiguration {
        @Override
        public Set<Class<? extends ServerEndpointConfiguration>> getEndpointConfigurationClasses(Set<Class<? extends ServerEndpointConfiguration>> scanned) {
            return new HashSet<Class<? extends ServerEndpointConfiguration>>(){{
                add(ProgrammaticConfA.class);
            }};

        }

        @Override
        public Set<Class> getAnnotatedEndpointClasses(Set<Class> scanned) {
            return new HashSet<Class>(){{
                add(AnnotatedA.class);
            }};
        }
    }

    public static class ApplicationConfB implements ServerApplicationConfiguration {
        @Override
        public Set<Class<? extends ServerEndpointConfiguration>> getEndpointConfigurationClasses(Set<Class<? extends ServerEndpointConfiguration>> scanned) {
            return new HashSet<Class<? extends ServerEndpointConfiguration>>(){{
                add(ProgrammaticConfA.class);
            }};

        }

        @Override
        public Set<Class> getAnnotatedEndpointClasses(Set<Class> scanned) {
            return new HashSet<Class>(){{
                add(AnnotatedA.class);
            }};
        }
    }
}
