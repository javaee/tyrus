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

package org.glassfish.websocket.api.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.glassfish.websocket.api.RemoteEndpoint;

/**
 *  This is the annotation you need to use at the class level of your end point to declare
 * that this class will have methods that accept web socket messages.
 * This is the annotation you use to specify the URL path of the web socket.
 * Note that containers may use multiple instances of these POJOs to support
 * an endpoint, particularly in distributed web servers. So developers are cautioned
 * when using member variables to hold application state.
 * <br>For example: <br>
 * <code><p>&nbsp&nbsp&nbsp@WebSocketEndpoint(path="/hello") <br>
 * public class HelloSocket { <br>
 * &nbsp&nbsp&nbsp...<br>
 * }
 * </code>
 * @author dannycoward
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface WebSocketEndpoint {
    /** The relative URL to this end point. The path is relative to the
     * root of the URL-space of the host on which the end point is deployed. All
     * paths begin with "/".
     * @return
     */
    String path();
    /**
     * An array of the subprotocols supported by this web socket end point, in descending
     * order of importance.
     * @return
     */
    String[] subprotocols() default {};
    /** The Java type of the custom Xremote implementation used by this web socket end point. The default Xremote
     * implementation is a platform provided implementation of the RemoteEndpoint interface.
     *
     * @return
     */
    Class Xremote() default RemoteEndpoint.class;

        /**  The list of Classes that this endpoint may use to decode incoming messages. For endpoints
         * that have methods with parameter types that are not primitive types or String will need
         * to provide a decoder to transform the message off the wire into that type.
     * @return
     */
    Class[] decoders() default {};
    /**
     * The list of Classes that this endpoint may use to encode outgoing messages. For endpoints
         * that have methods with return types that are not primitive types or String will need
         * to provide a decoder to transform the return type into a String or byte[].
     * @return
     * @return
     */
    Class[] encoders() default {};
}
