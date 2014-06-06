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

/**
 * Listens to message events and collects session-level statistics for sent and received messages.
 * Creates and registers {@link org.glassfish.tyrus.ext.monitoring.jmx.MessageStatisticsMXBean} MXBeans for text,
 * binary control and all messages which expose these statistics.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 * @see org.glassfish.tyrus.core.monitoring.MessageEventListener
 */
class SessionMonitor extends BaseMonitor implements MessageListener {

    private final MessageStatistics sentTextMessageStatistics = new MessageStatistics();
    private final MessageStatistics sentBinaryMessageStatistics = new MessageStatistics();
    private final MessageStatistics sentControlMessageStatistics = new MessageStatistics();

    private final MessageStatistics receivedTextMessageStatistics = new MessageStatistics();
    private final MessageStatistics receivedBinaryMessageStatistics = new MessageStatistics();
    private final MessageStatistics receivedControlMessageStatistics = new MessageStatistics();

    private final String applicationName;
    private final String endpointPath;
    private final String sessionId;
    private final MessageListener messageListener;
    private final EndpointMXBeanImpl endpointMXBean;

    SessionMonitor(String applicationName, String endpointPath, String sessionId, MessageListener messageListener, EndpointMXBeanImpl endpointMXBean) {
        this.applicationName = applicationName;
        this.endpointPath = endpointPath;
        this.sessionId = sessionId;
        this.messageListener = messageListener;
        this.endpointMXBean = endpointMXBean;

        MessageStatisticsMXBean textMessagesMXBean = new MessageStatisticsMXBeanImpl(sentTextMessageStatistics, receivedTextMessageStatistics);
        MessageStatisticsMXBean binaryMessagesMXBean = new MessageStatisticsMXBeanImpl(sentBinaryMessageStatistics, receivedBinaryMessageStatistics);
        MessageStatisticsMXBean controlMessagesMXBean = new MessageStatisticsMXBeanImpl(sentControlMessageStatistics, receivedControlMessageStatistics);

        MessageStatisticsAggregator sentMessagesTotal = new MessageStatisticsAggregator(sentTextMessageStatistics, sentBinaryMessageStatistics, sentControlMessageStatistics);
        MessageStatisticsAggregator receivedMessagesTotal = new MessageStatisticsAggregator(receivedTextMessageStatistics, receivedBinaryMessageStatistics, receivedControlMessageStatistics);
        SessionMXBeanImpl sessionMXBean = new SessionMXBeanImpl(sentMessagesTotal, receivedMessagesTotal, getErrorCounts(), textMessagesMXBean, binaryMessagesMXBean, controlMessagesMXBean, sessionId);

        endpointMXBean.putSessionMXBean(sessionId, sessionMXBean);
        MBeanPublisher.registerSessionMXBeans(applicationName, endpointPath, sessionId, sessionMXBean, textMessagesMXBean, binaryMessagesMXBean, controlMessagesMXBean);
    }

    void unregister() {
        MBeanPublisher.unregisterSessionMXBeans(applicationName, endpointPath, sessionId);
        endpointMXBean.removeSessionMXBean(sessionId);
    }

    @Override
    public void onTextMessageSent(long length) {
        sentTextMessageStatistics.onMessage(length);
        messageListener.onTextMessageSent(length);
    }

    @Override
    public void onBinaryMessageSent(long length) {
        sentBinaryMessageStatistics.onMessage(length);
        messageListener.onBinaryMessageSent(length);
    }

    @Override
    public void onControlMessageSent(long length) {
        sentControlMessageStatistics.onMessage(length);
        messageListener.onControlMessageSent(length);
    }

    @Override
    public void onTextMessageReceived(long length) {
        receivedTextMessageStatistics.onMessage(length);
        messageListener.onTextMessageReceived(length);
    }

    @Override
    public void onBinaryMessageReceived(long length) {
        receivedBinaryMessageStatistics.onMessage(length);
        messageListener.onBinaryMessageReceived(length);
    }

    @Override
    public void onControlMessageReceived(long length) {
        receivedControlMessageStatistics.onMessage(length);
        messageListener.onControlMessageReceived(length);
    }

    private static class MessageStatistics implements MessageStatisticsSource {

        /*
        volatile is enough in this case, because only one thread can sent or receive a message in a session
         */
        private volatile long messagesCount = 0;
        private volatile long messagesSize = 0;
        private volatile long minimalMessageSize = Long.MAX_VALUE;
        private volatile long maximalMessageSize = 0;

        void onMessage(long size) {
            messagesCount++;
            messagesSize += size;
            if (minimalMessageSize > size) {
                minimalMessageSize = size;
            }
            if (maximalMessageSize < size) {
                maximalMessageSize = size;
            }
        }

        @Override
        public long getMessagesCount() {
            return messagesCount;
        }

        @Override
        public long getMessagesSize() {
            return messagesSize;
        }

        @Override
        public long getMinMessageSize() {
            if (minimalMessageSize == Long.MAX_VALUE) {
                return 0;
            }
            return minimalMessageSize;
        }

        @Override
        public long getMaxMessageSize() {
            return maximalMessageSize;
        }
    }
}
