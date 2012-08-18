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

package org.glassfish.websocket.api.refactor;

import java.util.*;
import org.glassfish.websocket.api.ServerContainer;
import org.glassfish.websocket.api.Session;

/**
 * Each web socket end point has one and only one XEndpointContext object. From this object
 * the end point can obtain all the active conversations this end point is part of. The
 * XEndpointContext can be used to store data that is sharable across all conversations.
 * @author dannycoward
 */
public interface XEndpointContext {
    /** Obtain a collection of all the active conversations
     * the end point to which this XEndpointContext is associated. For example, in
     * a chat application, this call will return a collection of conversation
     * objects representing all the active web socket connections to the
     * end point.
     * @return
     */
    public Set<Session> getConversations();
    /** Get a reference to the container context to which this context belongs. */
    public ServerContainer getContainerContext();
     /** A read/write map of properties. Applications may use this to share
     * application data throughout the lifetime of this web socket end point.
     * @return
     */
    public Map<String, Object> XgetProperties();


    /** Obtain the URI relative to the root of the web socket runtime for the end point to which this context belongs. For example,
     if the web socket is at ws:/example.com/hello, this call returns "/hello". */
    public String getPath();

}
