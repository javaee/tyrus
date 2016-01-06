/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2016 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation may be used to annotate server endpoints as a optional annotation
 * to {@link javax.websocket.server.ServerEndpoint}. When number of maximal open
 * sessions is exceeded every new attempt to open session is closed with
 * {@link javax.websocket.CloseReason.CloseCodes#TRY_AGAIN_LATER}.
 * If value less then 1 is specified, no limit will be applied.
 * Annotation example:
 * <pre><code>
 * &#64;MaxSessions(100)
 * &#64;ServerEndpoint("/limited-resources")
 * public class LimitedEndpoint {
 * }
 * </code></pre>
 * <p>
 * Maximal number of open sessions can be also specified programmatically
 * using {@link org.glassfish.tyrus.core.TyrusServerEndpointConfig.Builder#maxSessions(int)}.
 * <p>
 *
 * @author Ondrej Kosatka (ondrej.kosatka at oracle.com)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MaxSessions {

    /**
     * Maximal number of open sessions.
     *
     * @return maximal number of open sessions.
     */
    public int value();

}
