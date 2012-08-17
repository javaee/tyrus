/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.glassfish.websocket.api;

import java.util.Set;

/**
 * A ClientContainer is an implementation provided object that allows the developer to
 * initiate a web socket handshake from the provided endpoint.
 * @author dannycoward
 * @since DRAFT 001
 */
public interface ClientContainer {
    
    
    /** Connect the supplied endpoint to its server using the supplied handshake
     * parameters
     * @param endpoint
     * @param olc 
     */
    public void connectToServer(Endpoint endpoint, ClientConfiguration olc);
    /** Return an unordered collection of the currently active web socket sessions.
     * @return 
     */
    public Set<Session> getActiveSessions();
    
    /** Return the maximum time in seconds that a web socket session may be idle before
     * the container may close it.
     * @return 
     */
    public long getMaxSessionIdleTimeout();
    /** Sets the maximum time in seconds that a web socket session may be idle before
     * the container may close it.
     * @return 
     */
    public void setMaxSessionIdleTimeout(long timeout);
     /** Returns the maximum size of binary message in number of bytes that this container 
      * will buffer. 
      * @return 
      */
    public long getMaxBinaryMessageBufferSize();
    /** Sets the maximum size of binary message in number of bytes that this container 
      * will buffer. 
      * @return 
      */
    public void setMaxBinaryMessageBufferSize(long max);
    /** Sets the maximum size of text message in number of bytes that this container 
     * will buffer. 
     * @return 
     */
    public long getMaxTextMessageBufferSize();
     /** Returns the maximum size of text message in number of bytes that this container 
      * will buffer. 
      * @return 
      */
    public void setMaxTextMessageBufferSize(long max);
}



