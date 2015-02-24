/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2015 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.tests.qa.lifecycle;

/**
 * @author Michal Čonos (michal.conos at oracle.com)
 */
public interface LifeCycleDeployment {
    public static final String INSTALL_ROOT = System.getenv("HOME") + "/glassfish4/glassfish";
    public static final String CONTEXT_PATH = "/websockets-lifecycle-test";
    public static final String LIFECYCLE_ENDPOINT_PATH = "/life";
    public static final int COMMCHANNEL_PORT = 9339;
    public static final String COMMCHANNEL_SCHEME = "http";
    public static final String COMMCHANNEL_HOST = "localhost";
    public static final long DEBUG_TIMEOUT = 6000;
    public static final long NORMAL_TIMEOUT = 5;
    public static final String[] serverProtoOrder = {
            "mikc21", "mikc22", "mikc23", "mikc24.111", "mikc25/0", "mikc26-0", "mikc27+0", "mikc28*9", "mikc291",
            "mikc210", "mikc21", "mikc22", "mikc23", "mikc24.111", "mikc25/0", "mikc26-0", "mikc27+0", "mikc28*9",
            "mikc291", "mikc210", "mikc21", "mikc22", "mikc23", "mikc24.111", "mikc25/0", "mikc26-0", "mikc27+0",
            "mikc28*9", "mikc291", "mikc210", "mikc21", "mikc22", "mikc23", "mikc24.111", "mikc25/0", "mikc26-0",
            "mikc27+0", "mikc28*9", "mikc291", "mikc210", "mikc21", "mikc22", "mikc23", "mikc24.111", "mikc25/0",
            "mikc26-0", "mikc27+0", "mikc28*9", "mikc291", "mikc10",
    };
    public static final String[] clientProtoOrder = {
            "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
            "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
            "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
            "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
            "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
            "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
            "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
            "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
            "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
            "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
            "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
            "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
            "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
            "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
            "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
            "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
            "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
            "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
            "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
            "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
            "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
            "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
            "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
            "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10",
            "mikc1", "mikc2", "mikc3", "mikc4.111", "mikc5/0", "mikc6-0", "mikc7+0", "mikc8*9", "mikc91", "mikc10"
    };
}
