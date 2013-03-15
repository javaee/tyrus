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
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import junit.framework.Assert;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.tests.qa.config.AppConfig;
import org.glassfish.tyrus.tests.qa.lifecycle.LifeCycleDeployment;
import org.glassfish.tyrus.tests.qa.lifecycle.config.CustomConfigurationProtocolsProgrammatic;
import org.glassfish.tyrus.tests.qa.lifecycle.config.MyExtension;
import org.glassfish.tyrus.tests.qa.lifecycle.config.ProgrammaticPartialMessageByteBufferSessionConfig;
import org.glassfish.tyrus.tests.qa.lifecycle.config.ProgrammaticPartialMessageByteSessionConfig;
import org.glassfish.tyrus.tests.qa.lifecycle.config.ProgrammaticPartialMessageStringSessionConfig;
import org.glassfish.tyrus.tests.qa.lifecycle.config.ProgrammaticWholeMessageBufferedReaderSessionConfig;
import org.glassfish.tyrus.tests.qa.lifecycle.config.ProgrammaticWholeMessageByteBufferSessionConfig;
import org.glassfish.tyrus.tests.qa.lifecycle.config.ProgrammaticWholeMessageByteSessionConfig;
import org.glassfish.tyrus.tests.qa.lifecycle.config.ProgrammaticWholeMessageObjectInputStreamSessionConfig;
import org.glassfish.tyrus.tests.qa.lifecycle.config.ProgrammaticWholeMessageStringSessionConfig;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.annotations.AnnotatedSubprotocols;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.annotations.ExtensionsViaCustomConfigurator;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.binary.AnnotatedPartialMessageByteBufferSession;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.binary.AnnotatedPartialMessageByteSession;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.binary.AnnotatedWholeMessageByteBufferSession;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.binary.AnnotatedWholeMessageByteSession;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.binary.AnnotatedWholeMessageObjectInputStreamSession;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.binary.ProgrammaticPartialMessageByteBufferSession;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.binary.ProgrammaticPartialMessageByteSession;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.binary.ProgrammaticWholeMessageByteBufferSession;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.binary.ProgrammaticWholeMessageByteSession;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.binary.ProgrammaticWholeMessageObjectInputStreamSession;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.text.AnnotatedPartialMessageStringSession;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.text.AnnotatedWholeMessageBufferedReaderSession;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.text.AnnotatedWholeMessageStringSession;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.annotations.MaxMessageSizeOnClient;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.annotations.MaxMessageSizeOnServer;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.annotations.SubprotocolsViaCustomConfigurator;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.deployment.ClientOnCloseDuplication;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.deployment.ClientOnErrorDuplication;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.deployment.ClientOnMessageDuplication;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.deployment.ClientOnOpenDuplication;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.deployment.ServerOnCloseDuplication;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.deployment.ServerOnErrorDuplication;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.deployment.ServerOnMessageDuplication;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.deployment.ServerOnOpenDuplication;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.text.ProgrammaticPartialMessageStringSession;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.text.ProgrammaticWholeMessageBufferedReaderSession;
import org.glassfish.tyrus.tests.qa.lifecycle.handlers.text.ProgrammaticWholeMessageStringSession;
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
        //server = channel.new Server();
        //server.start();
        SessionController.resetState();

    }

    @After
    public void stopServer() throws Exception {
        //server.destroy();
    }

    private Session deployClient(Class client, URI connectURI) throws DeploymentException, IOException {
        return deployClient(client, connectURI, ClientEndpointConfig.Builder.create().build());
    }

    private Session deployClient(Class client, URI connectURI, ClientEndpointConfig cec) throws DeploymentException, IOException {
        WebSocketContainer wsc = ContainerProvider.getWebSocketContainer();
        logger.log(Level.INFO, "deployClient: registering client: {0}", client);
        logger.log(Level.INFO, "deployClient: connectTo: {0}", connectURI);
        logger.log(Level.INFO, "deployClient: subProtocols: {0}", cec.getPreferredSubprotocols());
        Session clientSession;
        if (Endpoint.class.isAssignableFrom(client)) {
            clientSession = wsc.connectToServer(
                    client,
                    cec,
                    connectURI);
        } else {
            clientSession = wsc.connectToServer(
                    client,
                    connectURI);
        }

        logger.log(Level.INFO, "deployClient: client session: {0}", clientSession);
        logger.log(Level.INFO, "deployClient: Negotiated subprotocol: {0}", clientSession.getNegotiatedSubprotocol());
        return clientSession;
    }

    private Server deployServer(Class config) throws DeploymentException {
        logger.log(Level.INFO, "registering server: {0}", config);
        tyrus.registerEndpoint(config);
        final Server tyrusServer = tyrus.startServer();
        return tyrusServer;
    }

    private void lifeCycle(Class serverHandler, Class clientHandler) throws DeploymentException, IOException {
        lifeCycle(serverHandler, clientHandler, SessionController.SessionState.FINISHED_SERVER.getMessage(), testConf.getURI(), null);
    }

    private void lifeCycle(Class serverHandler, Class clientHandler, ClientEndpointConfig cec) throws DeploymentException, IOException {
        lifeCycle(serverHandler, clientHandler, SessionController.SessionState.FINISHED_SERVER.getMessage(), testConf.getURI(), cec);
    }

    private void lifeCycle(Class serverHandler, Class clientHandler, String state, URI clientUri, ClientEndpointConfig cec) throws DeploymentException, IOException {
        final CountDownLatch stopConversation = new CountDownLatch(1);
        final Server tyrusServer = deployServer(serverHandler);
        if (cec == null) {
            cec = ClientEndpointConfig.Builder.create().build();
        }
        Session clientSession = deployClient(clientHandler, clientUri, cec);
        // FIXME TC: clientSession.equals(lcSession)
        // FIXME TC: clientSession.addMessageHandler .. .throw excetpion
        try {
            if (System.getProperty("DEBUG_ON") != null) {
                stopConversation.await(LifeCycleDeployment.DEBUG_TIMEOUT, TimeUnit.SECONDS);
            } else {
                stopConversation.await(LifeCycleDeployment.NORMAL_TIMEOUT, TimeUnit.SECONDS);
            }
        } catch (InterruptedException ex) {
            // this is fine
        }
        /*
         if (stopConversation.getCount() != 0) {
         fail();
         }
         */

        tyrus.stopServer(tyrusServer);
        if (state != null) {
            logger.log(Level.INFO, "Asserting: {0} {1}", new Object[]{state, SessionController.getState()});
            Assert.assertEquals("session lifecycle finished", state, SessionController.getState());
        }
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
        lifeCycle(ProgrammaticWholeMessageStringSessionConfig.class, ProgrammaticWholeMessageStringSession.class);
    }

    @Test
    public void testLifeCycleProgrammaticObjects() throws DeploymentException, IOException {
        Issue.disableAll();
        lifeCycle(ProgrammaticWholeMessageObjectInputStreamSessionConfig.class, ProgrammaticWholeMessageObjectInputStreamSession.class);
    }

    @Test
    public void testLifeCycleProgrammaticByteArray() throws DeploymentException, IOException {
        Issue.disableAll();
        lifeCycle(ProgrammaticWholeMessageByteSessionConfig.class, ProgrammaticWholeMessageByteSession.class);
    }

    @Test
    public void testLifeCycleProgrammaticByteBuffer() throws DeploymentException, IOException {
        Issue.disableAll();
        lifeCycle(ProgrammaticWholeMessageByteBufferSessionConfig.class, ProgrammaticWholeMessageByteBufferSession.class);
    }

    @Test
    public void testLifeCycleProgrammaticBufferedReader() throws DeploymentException, IOException {
        Issue.disableAll();
        lifeCycle(ProgrammaticWholeMessageBufferedReaderSessionConfig.class, ProgrammaticWholeMessageBufferedReaderSession.class);
    }

    @Test
    public void testLifeCycleProgrammaticStingPartialMessage() throws DeploymentException, IOException {
        Issue.disableAll();
        lifeCycle(ProgrammaticPartialMessageStringSessionConfig.class, ProgrammaticPartialMessageStringSession.class);
    }

    @Test
    public void testLifeCycleProgrammaticByteArrayPartialMessage() throws DeploymentException, IOException {
        Issue.disableAll();
        lifeCycle(ProgrammaticPartialMessageByteSessionConfig.class, ProgrammaticPartialMessageByteSession.class);
    }

    @Test
    public void testLifeCycleProgrammaticByteBufferPartialMessage() throws DeploymentException, IOException {
        Issue.disableAll();
        lifeCycle(ProgrammaticPartialMessageByteBufferSessionConfig.class, ProgrammaticPartialMessageByteBufferSession.class);
    }

    @Test
    public void tyrus93_Programmatic() throws DeploymentException, IOException {
        Issue.TYRUS_93.disableAllButThisOne();
        lifeCycle(ProgrammaticWholeMessageStringSessionConfig.class, ProgrammaticWholeMessageStringSession.class);
    }

    @Test
    public void tyrus94_Programmatic() throws DeploymentException, IOException {
        Issue.TYRUS_94.disableAllButThisOne();
        lifeCycle(ProgrammaticWholeMessageStringSessionConfig.class, ProgrammaticWholeMessageStringSession.class);
    }

    @Test
    public void tyrus101_Programmatic() throws DeploymentException, IOException {
        Issue.TYRUS_101.disableAllButThisOne();
        lifeCycle(ProgrammaticWholeMessageStringSessionConfig.class, ProgrammaticWholeMessageStringSession.class);
    }

    @Test
    public void tyrus104_Programmatic() throws DeploymentException, IOException {
        Issue.TYRUS_104.disableAllButThisOne();
        lifeCycle(ProgrammaticWholeMessageStringSessionConfig.class, ProgrammaticWholeMessageStringSession.class);
    }

    @Test
    public void testLifeCycleAnnotated() throws DeploymentException, InterruptedException, IOException {
        Issue.disableAll();
        lifeCycle(AnnotatedWholeMessageStringSession.Server.class, AnnotatedWholeMessageStringSession.Client.class);
    }

    @Test
    public void testLifeCycleAnnotatedObjectInputStream() throws DeploymentException, InterruptedException, IOException {
        Issue.disableAll();
        lifeCycle(AnnotatedWholeMessageObjectInputStreamSession.Server.class, AnnotatedWholeMessageObjectInputStreamSession.Client.class);
    }

    @Test
    public void testLifeCycleAnnotatedByteArray() throws DeploymentException, InterruptedException, IOException {
        Issue.disableAll();
        lifeCycle(AnnotatedWholeMessageByteSession.Server.class, AnnotatedWholeMessageByteSession.Client.class);
    }

    @Test
    public void testLifeCycleAnnotatedByteBuffer() throws DeploymentException, InterruptedException, IOException {
        Issue.disableAll();
        lifeCycle(AnnotatedWholeMessageByteBufferSession.Server.class, AnnotatedWholeMessageByteBufferSession.Client.class);
    }

    @Test
    public void testLifeCycleAnnotatedBufferedReader() throws DeploymentException, InterruptedException, IOException {
        Issue.disableAll();
        lifeCycle(AnnotatedWholeMessageBufferedReaderSession.Server.class, AnnotatedWholeMessageBufferedReaderSession.Client.class);
    }

    @Test
    public void testLifeCycleAnnotatedStringPartialMessage() throws DeploymentException, InterruptedException, IOException {
        Issue.disableAll();
        lifeCycle(AnnotatedPartialMessageStringSession.Server.class, AnnotatedPartialMessageStringSession.Client.class);
    }

    @Test
    public void testLifeCycleAnnotatedByteArrayPartialMessage() throws DeploymentException, InterruptedException, IOException {
        Issue.disableAll();
        lifeCycle(AnnotatedPartialMessageByteSession.Server.class, AnnotatedPartialMessageByteSession.Client.class);
    }

    @Test
    public void testLifeCycleAnnotatedByteBufferPartialMessage() throws DeploymentException, InterruptedException, IOException {
        Issue.disableAll();
        lifeCycle(AnnotatedPartialMessageByteBufferSession.Server.class, AnnotatedPartialMessageByteBufferSession.Client.class);
    }

    @Test
    public void tyrus93_Annotated() throws DeploymentException, InterruptedException, IOException {
        Issue.TYRUS_93.disableAllButThisOne();
        lifeCycle(AnnotatedWholeMessageStringSession.Server.class, AnnotatedWholeMessageStringSession.Client.class);
    }

    @Test
    public void tyrus94_Annotated() throws DeploymentException, InterruptedException, IOException {
        Issue.TYRUS_94.disableAllButThisOne();
        lifeCycle(AnnotatedWholeMessageStringSession.Server.class, AnnotatedWholeMessageStringSession.Client.class);

    }

    @Test
    public void tyrus101_Annotated() throws DeploymentException, InterruptedException, IOException {
        Issue.TYRUS_101.disableAllButThisOne();
        lifeCycle(AnnotatedWholeMessageStringSession.Server.class, AnnotatedWholeMessageStringSession.Client.class);
    }

    @Test
    public void tyrus104_Annotated() throws DeploymentException, InterruptedException, IOException {
        Issue.TYRUS_104.disableAllButThisOne();
        lifeCycle(AnnotatedWholeMessageStringSession.Server.class, AnnotatedWholeMessageStringSession.Client.class);
    }

    @Test
    //TODO
    public void addMessageHandlerPossibleOnlyOnce() throws DeploymentException, IOException {
        Issue.disableAll();


    }

    @Test
    public void testURIMatchAnnotated() throws DeploymentException, URISyntaxException, IOException {
        boolean exThrown = false;
        try {
            Issue.disableAll();
            lifeCycle(AnnotatedWholeMessageStringSession.Server.class, AnnotatedWholeMessageStringSession.Client.class, null, new URI("ws://localhost/aaaaa"), null);
        } catch (Exception ex) {
            exThrown = true;
        }

        Assert.assertEquals("URI don't match and Hnadshake  exception is not thrown", true, exThrown);
    }

    @Test
    public void testURIMatchProgrammatic() throws DeploymentException, URISyntaxException, IOException {
        boolean exThrown = false;
        try {
            Issue.disableAll();
            lifeCycle(ProgrammaticWholeMessageStringSessionConfig.class, ProgrammaticWholeMessageStringSession.class, null, new URI("ws://localhost/aaaaa"), null);
        } catch (Exception ex) {
            exThrown = true;
        }

        Assert.assertEquals("URI don't match and Hnadshake  exception is not thrown", true, exThrown);
    }

    private void isMultipleAnnotationEx(Exception ex, String what) {
        if (ex == null || ex.getMessage() == null) {
            Assert.fail("isMultipleAnnotationEx: ex==null or ex.getMessage()==null");
        }
        if (!ex.getMessage().startsWith(what)) {
            Assert.fail(ex.getMessage());
        }
    }

    private void multipleDeployment(Class server, Class client, String whichOne) {
        Exception exThrown = null;
        try {
            lifeCycle(server, client, null, testConf.getURI(), null);
        } catch (Exception e) {
            exThrown = e;
            e.printStackTrace();
        }
        isMultipleAnnotationEx(exThrown, whichOne);
    }

    @Test
    public void testMaxMessageSizeOnClient() throws DeploymentException, IOException {
        Issue.disableAll();
        lifeCycle(MaxMessageSizeOnClient.Server.class, MaxMessageSizeOnClient.Client.class, SessionController.SessionState.MESSAGE.getMessage(), testConf.getURI(), null);
    }

    @Test
    public void testMaxMessageSizeOnServer() throws DeploymentException, IOException {
        Issue.disableAll();
        lifeCycle(MaxMessageSizeOnServer.Server.class, MaxMessageSizeOnServer.Client.class, SessionController.SessionState.OPEN_CLIENT.getMessage(), testConf.getURI(), null);
    }

    @Test
    public void testClientOnErrorDuplication() throws DeploymentException, IOException {
        Issue.disableAll();
        multipleDeployment(ClientOnErrorDuplication.Server.class, ClientOnErrorDuplication.Client.class, "Multiple methods using @OnError annotation");
    }

    @Test
    public void testServerOnErrorDuplication() throws DeploymentException, IOException {
        Issue.disableAll();
        multipleDeployment(ServerOnErrorDuplication.Server.class, ServerOnErrorDuplication.Client.class, "Multiple methods using @OnError annotation");
    }

    @Test
    public void testClientOnMessageDuplication() throws DeploymentException, IOException {
        Issue.disableAll();
        multipleDeployment(ClientOnMessageDuplication.Server.class, ClientOnMessageDuplication.Client.class, "Binary MessageHandler already registered");
    }

    @Test
    public void testServerOnMessageDuplication() throws DeploymentException, IOException {
        Issue.disableAll();
        multipleDeployment(ServerOnMessageDuplication.Server.class, ServerOnMessageDuplication.Client.class, "Binary MessageHandler already registered");
    }

    @Test
    public void testClientOnOpenDuplication() throws DeploymentException, IOException {
        Issue.disableAll();
        multipleDeployment(ClientOnOpenDuplication.Server.class, ClientOnOpenDuplication.Client.class, "Multiple methods using @OnOpen annotation");
    }

    @Test
    public void testServerOnOpenDuplication() throws DeploymentException, IOException {
        Issue.disableAll();
        multipleDeployment(ServerOnOpenDuplication.Server.class, ServerOnOpenDuplication.Client.class, "Multiple methods using @OnOpen annotation");
    }

    @Test
    public void testClientOnCloseDuplication() throws DeploymentException, IOException {
        Issue.disableAll();
        multipleDeployment(ClientOnCloseDuplication.Server.class, ClientOnCloseDuplication.Client.class, "Multiple methods using @OnClose annotation");
    }

    @Test
    public void testServerOnCloseDuplication() throws DeploymentException, IOException {
        Issue.disableAll();
        multipleDeployment(ServerOnCloseDuplication.Server.class, ServerOnCloseDuplication.Client.class, "Multiple methods using @OnClose annotation");
    }

    @Test
    public void testLifeCycleAnnotatedSubProtocols() throws DeploymentException, InterruptedException, IOException {
        Issue.disableAll();
        List<String> subProtocols = Arrays.asList(LifeCycleDeployment.clientProtoOrder);
        lifeCycle(
                AnnotatedSubprotocols.Server.class,
                AnnotatedSubprotocols.Client.class,
                ClientEndpointConfig.Builder.create().preferredSubprotocols(subProtocols)
                .build());
    }

    @Test
    public void testLifeCycleSubProtocolsViaCustomConfigurator() throws DeploymentException, InterruptedException, IOException {
        Issue.disableAll();
        lifeCycle(
                SubprotocolsViaCustomConfigurator.Server.class,
                SubprotocolsViaCustomConfigurator.Client.class);

    }

    @Test
    public void testLifeCycleExtensionsViaCustomConfigurator() throws DeploymentException, InterruptedException, IOException {
        Issue.disableAll();
        lifeCycle(
                ExtensionsViaCustomConfigurator.Server.class,
                ExtensionsViaCustomConfigurator.Client.class);
    }

    @Test
    public void testLifeCycleProgrammaticSubProtocols() throws DeploymentException, InterruptedException, IOException {
        Issue.disableAll();
        List<String> subProtocols = Arrays.asList(LifeCycleDeployment.clientProtoOrder);
        lifeCycle(
                CustomConfigurationProtocolsProgrammatic.class,
                ProgrammaticWholeMessageStringSession.class,
                ClientEndpointConfig.Builder.create().preferredSubprotocols(subProtocols)
                .build());
    }

    @Test
    public void testLifeCycleProgrammaticExtensions() throws DeploymentException, InterruptedException, IOException {
        Issue.disableAll();

        lifeCycle(
                CustomConfigurationProtocolsProgrammatic.class,
                ProgrammaticWholeMessageStringSession.class,
                ClientEndpointConfig.Builder.create().extensions(MyExtension.initExtensions())
                .build());
    }
}
