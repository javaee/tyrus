/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.sample.shared.collection;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.DeploymentException;

import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
public class TestSharedCollectionEndpoint extends TestContainer {

    public TestSharedCollectionEndpoint() {
        setContextPath("/sample-shared-collection");
    }

    @Test
    public void testInit() throws DeploymentException, IOException, InterruptedException {
        final Server server = startServer(SharedCollectionEndpoint.class);

        try {
            final CountDownLatch updateLatch = new CountDownLatch(1);

            final SharedMap sharedMap1 = new SharedMap(createClient(), getURI(SharedCollectionEndpoint.class),
                                                       new SharedMap.UpdateListener() {
                                                           @Override
                                                           public void onUpdate() {
                                                               updateLatch.countDown();
                                                           }
                                                       });

            assertTrue(updateLatch.await(5, TimeUnit.SECONDS));

            // initial content = [{Red Leader}, {Red Two}, {Red Three}, ...]
            assertEquals(5, sharedMap1.size());

        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testClear() throws DeploymentException, IOException, InterruptedException {
        final Server server = startServer(SharedCollectionEndpoint.class);

        try {
            // init
            final CountDownLatch updateLatch1 = new CountDownLatch(2);
            // init, put
            final CountDownLatch updateLatch2 = new CountDownLatch(2);

            final SharedMap sharedMap1 = new SharedMap(
                    createClient(), getURI(SharedCollectionEndpoint.class),
                    new SharedMap.UpdateListener() {
                        @Override
                        public void onUpdate() {
                            updateLatch1.countDown();
                        }
                    });

            final SharedMap sharedMap2 = new SharedMap(
                    createClient(), getURI(SharedCollectionEndpoint.class),
                    new SharedMap.UpdateListener() {
                        @Override
                        public void onUpdate() {
                            updateLatch1.countDown();
                            updateLatch2.countDown();
                        }
                    });

            // waiting for init - connect + initial values
            assertTrue(updateLatch1.await(5, TimeUnit.SECONDS));

            sharedMap1.clear();

            // init + values + clear from sharedMap1
            assertTrue(updateLatch2.await(5, TimeUnit.SECONDS));

            assertEquals(0, sharedMap2.size());
        } finally {
            stopServer(server);
        }
    }

    @Test
    public void testPut() throws DeploymentException, IOException, InterruptedException {
        final Server server = startServer(SharedCollectionEndpoint.class);

        try {
            // init
            final CountDownLatch updateLatch1 = new CountDownLatch(2);
            // init, put
            final CountDownLatch updateLatch2 = new CountDownLatch(2);

            final SharedMap sharedMap1 = new SharedMap(
                    createClient(), getURI(SharedCollectionEndpoint.class),
                    new SharedMap.UpdateListener() {
                        @Override
                        public void onUpdate() {
                            updateLatch1.countDown();
                        }
                    });

            final SharedMap sharedMap2 = new SharedMap(
                    createClient(), getURI(SharedCollectionEndpoint.class),
                    new SharedMap.UpdateListener() {
                        @Override
                        public void onUpdate() {
                            updateLatch1.countDown();
                            updateLatch2.countDown();
                        }
                    });

            // waiting for init - connect + initial values
            assertTrue(updateLatch1.await(5, TimeUnit.SECONDS));

            sharedMap1.put(TestSharedCollectionEndpoint.class.getName(), "test");

            // init + values + put from sharedMap1
            assertTrue(updateLatch2.await(5, TimeUnit.SECONDS));

            final String value = sharedMap2.get(TestSharedCollectionEndpoint.class.getName());
            assertNotNull(value);
            assertEquals("test", value);

        } finally {
            stopServer(server);
        }
    }
}
