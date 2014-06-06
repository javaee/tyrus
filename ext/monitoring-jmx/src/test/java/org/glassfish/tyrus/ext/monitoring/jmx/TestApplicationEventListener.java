/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.ext.monitoring.jmx;

import java.util.concurrent.CountDownLatch;

import javax.websocket.Session;

import org.glassfish.tyrus.core.frame.TyrusFrame;
import org.glassfish.tyrus.core.monitoring.ApplicationEventListener;
import org.glassfish.tyrus.core.monitoring.EndpointEventListener;
import org.glassfish.tyrus.core.monitoring.MessageEventListener;

/**
 * {@link org.glassfish.tyrus.core.monitoring.ApplicationEventListener} wrapper that accepts five latches that are
 * decremented when message is sent, received, session opened, session closed or an error occurred.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
class TestApplicationEventListener implements ApplicationEventListener {

    private final ApplicationEventListener applicationEventListener;
    private final CountDownLatch sessionOpenedLatch;
    private final CountDownLatch sessionClosedLatch;
    private final CountDownLatch messageSentLatch;
    private final CountDownLatch messageReceivedLatch;
    private final CountDownLatch errorLatch;

    /**
     * Constructor.
     *
     * @param applicationEventListener wrapped application event listener.
     * @param sessionOpenedLatch       latch that is decreased when a session is opened.
     * @param sessionClosedLatch       latch that is decreased when a session is closed.
     * @param messageSentLatch         latch that is decreased when a message is sent.
     * @param messageReceivedLatch     latch that is decreased when a message is received.
     * @param errorLatch               latch that is decreased when an error has occurred.
     */
    TestApplicationEventListener(ApplicationEventListener applicationEventListener, CountDownLatch sessionOpenedLatch, CountDownLatch sessionClosedLatch, CountDownLatch messageSentLatch, CountDownLatch messageReceivedLatch, CountDownLatch errorLatch) {
        this.applicationEventListener = applicationEventListener;
        this.sessionOpenedLatch = sessionOpenedLatch;
        this.sessionClosedLatch = sessionClosedLatch;
        this.messageSentLatch = messageSentLatch;
        this.messageReceivedLatch = messageReceivedLatch;
        this.errorLatch = errorLatch;
    }

    @Override
    public void onApplicationInitialized(String applicationName) {
        applicationEventListener.onApplicationInitialized(applicationName);
    }

    @Override
    public void onApplicationDestroyed() {
        applicationEventListener.onApplicationDestroyed();
    }

    @Override
    public EndpointEventListener onEndpointRegistered(String endpointPath, Class<?> endpointClass) {
        return new TestEndpointEventListener(applicationEventListener.onEndpointRegistered(endpointPath, endpointClass), sessionOpenedLatch, sessionClosedLatch, messageSentLatch, messageReceivedLatch, errorLatch);
    }

    @Override
    public void onEndpointUnregistered(String endpointPath) {
        applicationEventListener.onEndpointUnregistered(endpointPath);
    }

    private class TestEndpointEventListener implements EndpointEventListener {

        private final EndpointEventListener endpointEventListener;
        private final CountDownLatch sessionOpenedLatch;
        private final CountDownLatch sessionClosedLatch;
        private final CountDownLatch messageSentLatch;
        private final CountDownLatch messageReceivedLatch;
        private final CountDownLatch errorLatch;

        TestEndpointEventListener(EndpointEventListener endpointEventListener, CountDownLatch sessionOpenedLatch, CountDownLatch sessionClosedLatch, CountDownLatch messageSentLatch, CountDownLatch messageReceivedLatch, CountDownLatch errorLatch) {
            this.endpointEventListener = endpointEventListener;
            this.sessionOpenedLatch = sessionOpenedLatch;
            this.sessionClosedLatch = sessionClosedLatch;
            this.messageSentLatch = messageSentLatch;
            this.messageReceivedLatch = messageReceivedLatch;
            this.errorLatch = errorLatch;
        }

        @Override
        public MessageEventListener onSessionOpened(String sessionId) {
            MessageEventListener messageEventListener = new TestMessageEventListener(endpointEventListener.onSessionOpened(sessionId), messageSentLatch, messageReceivedLatch);
            if (sessionOpenedLatch != null) {
                sessionOpenedLatch.countDown();
            }
            return messageEventListener;
        }

        @Override
        public void onSessionClosed(String sessionId) {
            endpointEventListener.onSessionClosed(sessionId);
            if (sessionClosedLatch != null) {
                sessionClosedLatch.countDown();
            }
        }

        @Override
        public void onError(Session session, Throwable t) {
            endpointEventListener.onError(session, t);
            if (errorLatch != null) {
                errorLatch.countDown();
            }
        }
    }

    private class TestMessageEventListener implements MessageEventListener {

        private final MessageEventListener messageEventListener;
        private final CountDownLatch messageSentLatch;
        private final CountDownLatch messageReceivedLatch;

        TestMessageEventListener(MessageEventListener messageEventListener, CountDownLatch messageSentLatch, CountDownLatch messageReceivedLatch) {
            this.messageEventListener = messageEventListener;
            this.messageSentLatch = messageSentLatch;
            this.messageReceivedLatch = messageReceivedLatch;
        }

        @Override
        public void onFrameSent(TyrusFrame.FrameType frameType, long payloadLength) {
            messageEventListener.onFrameSent(frameType, payloadLength);
            if (messageSentLatch != null) {
                messageSentLatch.countDown();
            }
        }

        @Override
        public void onFrameReceived(TyrusFrame.FrameType frameType, long payloadLength) {
            messageEventListener.onFrameReceived(frameType, payloadLength);
            if (messageReceivedLatch != null) {
                messageReceivedLatch.countDown();
            }
        }
    }
}
