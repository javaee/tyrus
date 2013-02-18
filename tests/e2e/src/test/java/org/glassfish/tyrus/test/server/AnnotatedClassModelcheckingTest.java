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

package org.glassfish.tyrus.test.server;

import java.net.URI;
import java.util.ArrayList;

import javax.websocket.DeploymentException;
import javax.websocket.PongMessage;
import javax.websocket.Session;
import javax.websocket.WebSocketClient;
import javax.websocket.WebSocketClose;
import javax.websocket.WebSocketError;
import javax.websocket.WebSocketMessage;
import javax.websocket.WebSocketOpen;
import javax.websocket.server.DefaultServerConfiguration;
import javax.websocket.server.WebSocketEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;

import org.junit.Test;

import junit.framework.Assert;

/**
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class AnnotatedClassModelcheckingTest {

    private void testServerPositive(Class<?> testedBean) {
        testServer(testedBean, false);
    }

    private void testServerNegative(Class<?> testedBean) {
        testServer(testedBean, true);
    }

    private void testServer(Class<?> testedBean, boolean shouldThrowException) {
        Server server = new Server(testedBean);
        boolean exceptionThrown = false;

        try {
            server.start();
        } catch (DeploymentException e) {
            exceptionThrown = true;
        } finally {
            server.stop();
            Assert.assertEquals(shouldThrowException, exceptionThrown);
        }
    }

    @Test
    public void testEndpointWithTwoSessionMessageParameters() {
        testServerNegative(TwoSessionParametersErrorBean.class);
    }

    @WebSocketEndpoint(value = "/hello", configuration = DefaultServerConfiguration.class)
    public static class TwoSessionParametersErrorBean {

        @WebSocketMessage
        public void twoSessions(PongMessage message, Session peer1, Session peer2) {

        }
    }

    @Test
    public void testEndpointWithTwoStringMessageParameters() {
        testServerNegative(TwoStringParametersErrorBean.class);
    }

    @WebSocketEndpoint(value = "/hello", configuration = DefaultServerConfiguration.class)
    public static class TwoStringParametersErrorBean {

        @WebSocketMessage
        public void twoStrings(String message1, String message2, Session peer2) {

        }
    }

    @Test
    public void testEndpointWithWrongMessageReturnParameter() {
        testServerNegative(WrongMessageReturnParameter.class);
    }

    @WebSocketEndpoint(value = "/hello", configuration = DefaultServerConfiguration.class)
    public static class WrongMessageReturnParameter {

        @WebSocketMessage
        public ArrayList<String> wrongReturn(String message1, Session peer2) {
            return new ArrayList<String>();
        }
    }

    @Test
    public void testEndpointWithCorrectMessageReturnParameter1() {
        testServerPositive(CorrectMessageReturnParameter1.class);
    }

    @WebSocketEndpoint(value = "/hello", configuration = DefaultServerConfiguration.class)
    public static class CorrectMessageReturnParameter1 {

        @WebSocketMessage
        public Float wrongReturn(String message1, Session peer2) {
            return new Float(5);
        }
    }

    @Test
    public void testEndpointWithCorrectMessageReturnParameter2() {
        testServerPositive(CorrectMessageReturnParameter2.class);
    }

    @WebSocketEndpoint(value = "/hello", configuration = DefaultServerConfiguration.class)
    public static class CorrectMessageReturnParameter2 {

        @WebSocketMessage
        public float wrongReturn(String message1, Session peer2) {
            return (float) 5.23;
        }
    }

    @Test
    public void testErrorMethodWithoutThrowable() {
        testServerNegative(ErrorMethodWithoutThrowableErrorBean.class);
    }

    @WebSocketEndpoint(value = "/hello", configuration = DefaultServerConfiguration.class)
    public static class ErrorMethodWithoutThrowableErrorBean {

        @WebSocketError
        public void wrongOnError(Session peer) {

        }
    }

    @Test
    public void testErrorMethodWithWrongParameter() {
        testServerNegative(ErrorMethodWithWrongParam.class);
    }

    @WebSocketEndpoint(value = "/hello", configuration = DefaultServerConfiguration.class)
    public static class ErrorMethodWithWrongParam {

        @WebSocketError
        public void wrongOnError(Session peer, Throwable t, String s) {

        }
    }

    @Test
    public void testOpenMethodWithWrongParameter() {
        testServerNegative(OpenMethodWithWrongParam.class);
    }

    @WebSocketEndpoint(value = "/hello", configuration = DefaultServerConfiguration.class)
    public static class OpenMethodWithWrongParam {

        @WebSocketOpen
        public void wrongOnOpen(Session peer, String s) {

        }
    }

    @Test
    public void testCloseMethodWithWrongParameter() {
        testServerNegative(CloseMethodWithWrongParam.class);
    }

    @WebSocketEndpoint(value = "/hello", configuration = DefaultServerConfiguration.class)
    public static class CloseMethodWithWrongParam {

        @WebSocketClose
        public void wrongOnClose(Session peer, String s) {

        }
    }

    @Test
    public void testMultipleWrongMethods() {
        testServerNegative(MultipleWrongMethodsBean.class);
    }

    @Test
    public void testMultipleWrongMethodsOnClient() {
        boolean exceptionThrown = false;

        try {
            ClientManager client = ClientManager.createClient();
            client.connectToServer(MultipleWrongMethodsBean.class, new URI("wss://localhost:8025/websockets/tests/hello"));
        } catch (DeploymentException e) {
            //e.printStackTrace();
            exceptionThrown = true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Assert.assertEquals(true, exceptionThrown);
        }
    }

    @WebSocketClient
    @WebSocketEndpoint(value = "/hello", configuration = DefaultServerConfiguration.class)
    public static class MultipleWrongMethodsBean {

        @WebSocketClose
        public void wrongOnClose(Session peer, String s) {

        }

        @WebSocketOpen
        public void wrongOnOpen(Session peer, String s) {

        }

        @WebSocketError
        public void wrongOnError(Session peer, Throwable t, String s) {

        }

        @WebSocketMessage
        public void twoStrings(String message1, String message2, Session peer2) {

        }
    }
}
