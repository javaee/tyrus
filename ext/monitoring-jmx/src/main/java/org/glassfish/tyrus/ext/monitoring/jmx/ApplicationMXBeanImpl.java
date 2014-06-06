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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
class ApplicationMXBeanImpl extends BaseMXBeanImpl implements ApplicationMXBean, Serializable {

    private static final long serialVersionUID = 5417841460238333390L;

    private final Callable<List<EndpointClassNamePathPair>> endpoints;
    private final Callable<List<String>> endpointPaths;
    private final Map<String, EndpointMXBean> endpointMXBeans = new ConcurrentHashMap<String, EndpointMXBean>();
    private final Callable<Integer> openSessionsCount;
    private final Callable<Integer> maxOpenSessionsCount;

    public ApplicationMXBeanImpl(MessageStatisticsSource sentMessageStatistics, MessageStatisticsSource receivedMessageStatistics, Callable<List<EndpointClassNamePathPair>> endpoints, Callable<List<String>> endpointPaths, Callable<Integer> openSessionsCount, Callable<Integer> maxOpenSessionsCount, Callable<List<ErrorCount>> errorCounts, MessageStatisticsMXBean textMessageStatisticsMXBean, MessageStatisticsMXBean binaryMessageStatisticsMXBean, MessageStatisticsMXBean controlMessageStatisticsMXBean) {
        super(sentMessageStatistics, receivedMessageStatistics, errorCounts, textMessageStatisticsMXBean, binaryMessageStatisticsMXBean, controlMessageStatisticsMXBean);
        this.endpoints = endpoints;
        this.endpointPaths = endpointPaths;
        this.openSessionsCount = openSessionsCount;
        this.maxOpenSessionsCount = maxOpenSessionsCount;
    }

    @Override
    public List<EndpointClassNamePathPair> getEndpoints() {
        return endpoints.call();
    }

    @Override
    public List<String> getEndpointPaths() {
        return endpointPaths.call();
    }

    @Override
    public List<EndpointMXBean> getEndpointMXBeans() {
        return new ArrayList<EndpointMXBean>(endpointMXBeans.values());
    }

    @Override
    public int getOpenSessionsCount() {
        return openSessionsCount.call();
    }

    @Override
    public int getMaximalOpenSessionsCount() {
        return maxOpenSessionsCount.call();
    }

    void putEndpointMXBean(String endpointPath, EndpointMXBean endpointMXBean) {
        endpointMXBeans.put(endpointPath, endpointMXBean);
    }

    void removeEndpointMXBean(String path) {
        endpointMXBeans.remove(path);
    }
}
