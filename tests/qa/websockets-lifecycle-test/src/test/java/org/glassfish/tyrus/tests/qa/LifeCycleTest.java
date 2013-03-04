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
package org.glassfish.tyrus.tests.qa;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.websocket.ClientEndpointConfig.Builder;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import junit.framework.Assert;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.tests.qa.config.AppConfig;
import org.glassfish.tyrus.tests.qa.lifecycle.LifeCycleDeployment;
import org.glassfish.tyrus.tests.qa.lifecycle.config.AnnotatedBufferedReaderSessionConfig;
import org.glassfish.tyrus.tests.qa.lifecycle.config.AnnotatedByteBufferSessionConfig;
import org.glassfish.tyrus.tests.qa.lifecycle.config.AnnotatedByteSessionConfig;
import org.glassfish.tyrus.tests.qa.lifecycle.config.AnnotatedObjectInputStreamSessionConfig;
import org.glassfish.tyrus.tests.qa.lifecycle.config.AnnotatedStringSessionConfig;
import org.glassfish.tyrus.tests.qa.lifecycle.config.ProgrammaticBufferedReaderSessionConfig;
import org.glassfish.tyrus.tests.qa.lifecycle.config.ProgrammaticByteBufferSessionConfig;
import org.glassfish.tyrus.tests.qa.lifecycle.config.ProgrammaticByteSessionConfig;
import org.glassfish.tyrus.tests.qa.lifecycle.config.ProgrammaticObjectInputStreamSessionConfig;
import org.glassfish.tyrus.tests.qa.lifecycle.config.ProgrammaticStringSessionConfig;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.binary.AnnotatedByteBufferSession;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.binary.AnnotatedByteSession;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.binary.AnnotatedObjectInputStreamSession;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.binary.ProgrammaticByteBufferSession;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.binary.ProgrammaticByteSession;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.binary.ProgrammaticObjectInputStreamSession;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.text.AnnotatedBufferedReaderSession;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.text.AnnotatedStringSession;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.text.ProgrammaticBufferedReaderSession;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.text.ProgrammaticStringSession;
import org.glassfish.tyrus.tests.qa.regression.Issue;
import org.glassfish.tyrus.tests.qa.tools.CommChannel;
import org.glassfish.tyrus.tests.qa.tools.SessionController;
import org.glassfish.tyrus.tests.qa.tools.TyrusToolkit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 * @author Michal Conos (michal.conos at oracle.com)
 */
public class LifeCycleTest {

    private static final Logger logger = Logger.getLogger(LifeCycleTest.class.getCanonicalName());
    AppConfig testConf = new AppConfig(
            LifeCycleDeployment.CONTEXT_PATH,
            LifeCycleDeployment.LIFECYCLE_ENDPOINT_PATH,
            LifeCycleDeployment.COMMCHANNEL_SCHEME,
            LifeCycleDeployment.COMMCHANNEL_HOST,
            LifeCycleDeployment.COMMCHANNEL_PORT);
    TyrusToolkit tyrus = new TyrusToolkit(testConf);
    CommChannel channel;
    CommChannel.Server server;

    @Before
    public void setupServer() throws Exception {
       channel = new CommChannel(testConf);
       server = channel.new Server();
       server.start();
       SessionController.resetState();

    }

    @After
    public void stopServer() throws Exception {
       server.destroy();
    }

    private Session deployClient(Class client, URI connectURI) throws DeploymentException, IOException {
        WebSocketContainer wsc = ContainerProvider.getWebSocketContainer();
        logger.log(Level.INFO, "registering client: {0}", client);
        logger.log(Level.INFO, "connectTo: {0}", connectURI);
        Session clientSession = wsc.connectToServer(
                client,
                ClientEndpointConfig.Builder.create().build(),
                connectURI);
        logger.log(Level.INFO, "client session: {0}", clientSession);
        return clientSession;
    }

    private Server deployServer(Class config) throws DeploymentException {
        logger.log(Level.INFO, "registering server: {0}", config);
        tyrus.registerEndpoint(config);
        final Server tyrusServer = tyrus.startServer();
        return tyrusServer;
    }

    private void lifeCycle(Class serverHandler, Class clientHandler) throws DeploymentException, IOException {
        final CountDownLatch stopConversation = new CountDownLatch(1);
        final Server tyrusServer = deployServer(serverHandler);
        Session clientSession = deployClient(clientHandler, testConf.getURI());
        // FIXME TC: clientSession.equals(lcSession)
        // FIXME TC: clientSession.addMessageHandler .. .throw excetpion
        try {
            stopConversation.await(4, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            // this is fine
        }
        /*
         if (stopConversation.getCount() != 0) {
         fail();
         }
         */

        tyrus.stopServer(tyrusServer);
        logger.log(Level.INFO, "Asserting: {0} {1}", new Object[] {SessionController.SessionState.FINISHED_SERVER.getMessage(), SessionController.getState()});
        Assert.assertEquals("session lifecycle finished", SessionController.SessionState.FINISHED_SERVER.getMessage(), SessionController.getState());
    }

    /*
     private void lifeCycleAnnotated(Class serverHandler, Class clientHandler) throws DeploymentException, InterruptedException, IOException {
    
    

     ServerAnnotatedConfiguration.registerServer("annotatedLifeCycle", serverHandler);
     ServerAnnotatedConfiguration.registerSessionController("annotatedSessionController", sc);
     tyrus.registerEndpoint(ServerAnnotatedConfiguration.class);
     final Server tyrusServer = tyrus.startServer();
     WebSocketContainer wsc = ContainerProvider.getWebSocketContainer();
     Session clientSession = wsc.connectToServer(
     AnnotatedClient.class,
     new ClientConfiguration(clientHandler, sc),
     testConf.getURI());
     Thread.sleep(10000);
     tyrus.stopServer(tyrusServer);
     Assert.assertEquals(sessionName + ": session lifecycle finished", SessionController.SessionState.FINISHED_SERVER.getMessage(), sc.getState());
     }
     */
    @Test
    public void testLifeCycleProgrammatic() throws DeploymentException, IOException {
        Issue.disableAll();
        lifeCycle(ProgrammaticStringSessionConfig.class, ProgrammaticStringSession.class);
    }

    @Test
    public void testLifeCycleProgrammaticObjects() throws DeploymentException, IOException {
        Issue.disableAll();
        lifeCycle(ProgrammaticObjectInputStreamSessionConfig.class, ProgrammaticObjectInputStreamSession.class);
    }

    @Test
    public void testLifeCycleProgrammaticByteArray() throws DeploymentException, IOException {
        Issue.disableAll();
        lifeCycle(ProgrammaticByteSessionConfig.class, ProgrammaticByteSession.class);
    }
    
    @Test
    public void testLifeCycleProgrammaticByteBuffer() throws DeploymentException, IOException {
        Issue.disableAll();
        lifeCycle(ProgrammaticByteBufferSessionConfig.class, ProgrammaticByteBufferSession.class);
    }
    
    @Test
    public void testLifeCycleProgrammaticBufferedReader() throws DeploymentException, IOException {
        Issue.disableAll();
        lifeCycle(ProgrammaticBufferedReaderSessionConfig.class, ProgrammaticBufferedReaderSession.class);
    }

    @Test
    public void tyrus93_Programmatic() throws DeploymentException, IOException {
        Issue.TYRUS_93.disableAllButThisOne();
        lifeCycle(ProgrammaticStringSessionConfig.class, ProgrammaticStringSession.class);
    }

    @Test
    public void tyrus94_Programmatic() throws DeploymentException, IOException {
        Issue.TYRUS_94.disableAllButThisOne();
        lifeCycle(ProgrammaticStringSessionConfig.class, ProgrammaticStringSession.class);
    }

    @Test
    public void tyrus101_Programmatic() throws DeploymentException, IOException {
        Issue.TYRUS_101.disableAllButThisOne();
        lifeCycle(ProgrammaticStringSessionConfig.class, ProgrammaticStringSession.class);
    }

    @Test
    public void tyrus104_Programmatic() throws DeploymentException, IOException {
        Issue.TYRUS_104.disableAllButThisOne();
        lifeCycle(ProgrammaticStringSessionConfig.class, ProgrammaticStringSession.class);
    }

    @Test
    public void testLifeCycleAnnotated() throws DeploymentException, InterruptedException, IOException {
        Issue.disableAll();
        lifeCycle(AnnotatedStringSessionConfig.class, AnnotatedStringSession.Client.class);
    }
    
    @Test
    public void testLifeCycleAnnotatedObjectInputStream() throws DeploymentException, InterruptedException, IOException {
        Issue.disableAll();
        lifeCycle(AnnotatedObjectInputStreamSessionConfig.class, AnnotatedObjectInputStreamSession.Client.class);
    }
    
    @Test
    public void testLifeCycleAnnotatedByteArray() throws DeploymentException, InterruptedException, IOException {
        Issue.disableAll();
        lifeCycle(AnnotatedByteSessionConfig.class, AnnotatedByteSession.Client.class);
    }
    
    @Test
    public void testLifeCycleAnnotatedByteBuffer() throws DeploymentException, InterruptedException, IOException {
        Issue.disableAll();
        lifeCycle(AnnotatedByteBufferSessionConfig.class, AnnotatedByteBufferSession.Client.class);
    }
    
    @Test
    public void testLifeCycleAnnotatedBufferedReader() throws DeploymentException, InterruptedException, IOException {
        Issue.disableAll();
        lifeCycle(AnnotatedBufferedReaderSessionConfig.class, AnnotatedBufferedReaderSession.Client.class);
    }

    @Test
    public void tyrus93_Annotated() throws DeploymentException, InterruptedException, IOException {
        Issue.TYRUS_93.disableAllButThisOne();
        lifeCycle(AnnotatedStringSessionConfig.class, AnnotatedStringSession.Client.class);
    }

    @Test
    public void tyrus94_Annotated() throws DeploymentException, InterruptedException, IOException {
        Issue.TYRUS_94.disableAllButThisOne();
        lifeCycle(AnnotatedStringSessionConfig.class, AnnotatedStringSession.Client.class);

    }

    @Test
    public void tyrus101_Annotated() throws DeploymentException, InterruptedException, IOException {
        Issue.TYRUS_101.disableAllButThisOne();
        lifeCycle(AnnotatedStringSessionConfig.class, AnnotatedStringSession.Client.class);
    }

    @Test
    public void tyrus104_Annotated() throws DeploymentException, InterruptedException, IOException {
        Issue.TYRUS_104.disableAllButThisOne();
        lifeCycle(AnnotatedStringSessionConfig.class, AnnotatedStringSession.Client.class);
    }

    @Test
    public void addMessageHandlerPossibleOnlyOnce() throws DeploymentException, IOException {
        Issue.disableAll();
        

    }
}
