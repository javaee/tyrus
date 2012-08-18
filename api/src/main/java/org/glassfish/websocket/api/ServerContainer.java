/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 - 2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.websocket.api;

import org.glassfish.websocket.api.refactor.XEndpointContext;
import java.util.*;
/**
 * There is one instance of this object available to all the web sockets
 * in the runtime. For example, if the runtime is embedded in a web container,
 * there is one container context per web container. This object enables some basic querying of where the runtime
 * is located in the URI space, and also contains a rudimentary place to
 * store data that can be shared between different web socket end points
 * in the runtime.
 * @author dannycoward
 */
public interface ServerContainer extends ClientContainer {
    /** Basic transient storage of application data visible across the
     * runtime */
    public Map<String, Object> XgetProperties();


    /** A list of the endpoint contexts in this container. */
    public List<XEndpointContext> XgetEndpointContexts();

    
    
    /** Turn into publishServer.*/
    public void Xdeploy(Endpoint endpoint, String path);
    
    /** Publish the given endpoint with the provided configuration
     * information. 
     * @param endpoint
     * @param ilc 
     */
    public void publishServer(Endpoint endpoint, ServerConfiguration ilc);
    


}
