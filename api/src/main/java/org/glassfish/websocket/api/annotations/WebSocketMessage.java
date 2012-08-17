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

import java.lang.annotation.*;

/**
 * Method level annotation indicating that this Java method will be called on an end point class
 * when the end point receives a message. <br>
 * The Java method may have either one or two parameters or three. The first
 * parameter will always encapsulate the incoming message, and may be of type String, byte[], any Java primitive
 * or associated class-type. If the type of the parameter is not any of these types, the developer must provide
 * an encoder class, noted at the WebSocket annotation of the enclosing class, so the runtime can encode the incoming String or byte[] into the supplied type.
 * <br>The optional
 * second parameter must be of type Peer or a type provided by the developer. If the second parameter is a developer
 * provided type, it must implement Peer and additionally be annotated using the WebSocketRemote annotation.<br>
 * The Java method may also have a non-void return type. If so, the runtime interprets this as a synchronous
 * reply to the incoming message. The return type may be of type String, byte[], any Java primitive type or
 * associated Java class, or a custom Java class provided a decoder class is provided, as noted on the WebSocket annotation
 * on the enclosing class.
 * <br>The optional third parameter is of type String and annotated with the @PathSegment annoation, If present
 * the path relative to the web socket context root will be passed as this parameter in order to provide the developer
 * with the path that was used to invoke the method.
 * <br>
 * For example:<br>
 * <p>
 * <code>
 * &nbsp&nbsp&nbsp@WebSocketMessage<br>
    public void handleStringMessage(String message) {<br>
    &nbsp&nbsp&nbsp// process message
    }
 * </code><br><br>
 * or...<br><br>
 * <code>
 * &nbsp&nbsp&nbsp@WebSocketMessage<br>
    public void handleNumber(Integer count) {<br>
    &nbsp&nbsp&nbsp// process count <br>
    }
 * </code>
 * <br><br>or...<br><br>
 *  <code>
 * &nbsp&nbsp&nbsp@WebSocketMessage<br>
    public String handleNumber(Integer count) {<br>
    &nbsp&nbsp&nbsp// process count <br>
 *  &nbsp&nbsp&nbspreturn "thanks for sending that wonderful number to me !";<br>
    }<br>
 * </code>
 * <br>or...<br><br>
 * <p>
 * <code>
 * &nbsp&nbsp&nbsp@WebSocketMessage<br>
    public void handleSimpleMessage(String message, Peer client) {<br>
    &nbsp&nbsp&nbsp// process message object<br>
 *  &nbsp&nbsp&nbsp// log which client it's from<br>
    }<br><br>
 * </code>
 * or...
 *  * <code>
 * &nbsp&nbsp&nbsp@WebSocketMessage<br>
    public void handleMessage(String message, @PathSegment String path) {<br>
    &nbsp&nbsp&nbsp// process message object<br>
 *  &nbsp&nbsp&nbsp// log the actual path used<br>
    }<br><br>
 * </code>
 * or...
 * <p><code>
 * &nbsp&nbsp&nbsp@WebSocketMessage(<br>
   &nbsp&nbsp&nbsp&nbspencoder="enc.JSONEncoder",<br>
   &nbsp&nbsp&nbsp&nbspdecoder="enc.JSONDecoder")<br>
    public JSONObject helloWorld(JSONObject message) {<br>
    &nbsp&nbsp&nbsp// process message object<br>
 *  &nbsp&nbsp&nbsp// formulate reply object<br>
 *  &nbsp&nbsp&nbspreturn reply;<br>
    }<br>
 * </code>
 * @author dannycoward
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface WebSocketMessage {
    /** default is "" (exact match for incoming URIs to the root path of this endpoint
     * or you can specify a relative path like /foo in which case the method will
     * catch requests with URI <root uri>/foo
     * or you can specify '*' as a dynamic path in which case you'll get anything that
     * starts with <root uri>
     * @return
     */
    public String XdynamicPath() default "";

}
