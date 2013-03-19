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

import javax.websocket.ClientEndpointConfig;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.tests.qa.config.AppConfig;
import org.glassfish.tyrus.tests.qa.lifecycle.LifeCycleDeployment;
import org.glassfish.tyrus.tests.qa.tools.CommChannel;
import org.glassfish.tyrus.tests.qa.tools.SessionController;
import org.glassfish.tyrus.tests.qa.tools.TyrusToolkit;

import org.junit.After;
import org.junit.Before;

import junit.framework.Assert;
import org.glassfish.tyrus.tests.qa.tools.ServerToolkit;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 * @author Michal Conos (michal.conos at oracle.com)
 */
public abstract class AbstractLifeCycleTestBase {

    protected static final Logger logger = Logger.getLogger(AbstractLifeCycleTestBase.class.getCanonicalName());

    AppConfig testConf = new AppConfig(
            LifeCycleDeployment.CONTEXT_PATH,
            LifeCycleDeployment.LIFECYCLE_ENDPOINT_PATH,
            LifeCycleDeployment.COMMCHANNEL_SCHEME,
            LifeCycleDeployment.COMMCHANNEL_HOST,
            LifeCycleDeployment.COMMCHANNEL_PORT,
            LifeCycleDeployment.INSTALL_ROOT);
    ServerToolkit tyrus;


    //private Server tyrusServer;

    @Before
    public void setupServer() throws Exception {
        tyrus = new TyrusToolkit(testConf);
        SessionController.resetState();

    }

    @After
    public void stopServer() {
        tyrus.stopServer();
    }

    protected Session deployClient(Class client, URI connectURI) throws DeploymentException, IOException {
        return deployClient(client, connectURI, ClientEndpointConfig.Builder.create().build());
    }

    protected Session deployClient(Class client, URI connectURI, ClientEndpointConfig cec) throws DeploymentException, IOException {
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

    protected void deployServer(Class config) throws DeploymentException {
        logger.log(Level.INFO, "registering server: {0}", config);
        tyrus.registerEndpoint(config);
        tyrus.startServer();
    }

    protected void lifeCycle(Class serverHandler, Class clientHandler) throws DeploymentException, IOException {
        lifeCycle(serverHandler, clientHandler, SessionController.SessionState.FINISHED_SERVER.getMessage(), testConf.getURI(), null);
    }

    protected void lifeCycle(Class serverHandler, Class clientHandler, ClientEndpointConfig cec) throws DeploymentException, IOException {
        lifeCycle(serverHandler, clientHandler, SessionController.SessionState.FINISHED_SERVER.getMessage(), testConf.getURI(), cec);
    }

    protected void lifeCycle(Class serverHandler, Class clientHandler, String state, URI clientUri, ClientEndpointConfig cec) throws DeploymentException, IOException {
        final CountDownLatch stopConversation = new CountDownLatch(1);
        try {
            deployServer(serverHandler);
        } catch (DeploymentException e) {
            stopServer();
            throw e;
        }
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

        tyrus.stopServer();

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

    protected void isMultipleAnnotationEx(Exception ex, String what) {
        if (ex == null || ex.getMessage() == null) {
            Assert.fail("isMultipleAnnotationEx: ex==null or ex.getMessage()==null");
        }
        if (!ex.getMessage().startsWith(what)) {
            Assert.fail(ex.getMessage());
        }
    }

    protected void multipleDeployment(Class server, Class client, String whichOne) {
        Exception exThrown = null;
        try {
            lifeCycle(server, client, null, testConf.getURI(), null);
        } catch (Exception e) {
            exThrown = e;
            e.printStackTrace();
        }
        isMultipleAnnotationEx(exThrown, whichOne);
    }


}
