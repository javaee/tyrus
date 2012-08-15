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
package org.glassfish.websocket.api.extension;

/** A FrameHandler is a link in the chain of handlers associated with the web socket extensions
 * configured for an endpoint. Each framehandler belongs to an extension, either as the handler for
 * all the incoming web socket frames, of as the handler for all the outgoing web socket frames.
 * on per connection.
 * @since DRAFT 003
 * @author dannycoward
 */
public abstract class FrameHandler {
    private FrameHandler nextHandler;
    
    /** Constructor that creates a FrameHandler with the given framehandler
     * as the next frame handler in the chain. 
     * @param nextHandler 
     */
    public FrameHandler(FrameHandler nextHandler) {
        this.nextHandler = nextHandler;
    }
    /** The next handler in the handler chain. */
    public FrameHandler getNextHandler() {
        return this.nextHandler;
    }
    
    /** This method is invoked whenever the implementation is ready to invoke this framehandler
     as part of the framehandler chain. The defauly implementation in this class is a no-op: i.e. it 
     simply invokes the next handler in the chain with the frame passed in. 
     */
    public void handleFrame(Frame f) {
        this.nextHandler.handleFrame(f);
    }
    
    
    
}
