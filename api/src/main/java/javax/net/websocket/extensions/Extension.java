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
package javax.net.websocket.extensions;

import java.util.*;
/**
 * The Extension interface represents a web socket extension. Extensions are added to a web socket endpoint by
 * adding them to its {@link EndpointConfiguration}. The extension consists of a name, a collection of
 * extension parameters and a pair of ExtensionHandlers, one that handles all the frames the web socket implementation
 * uses for representing incoming web socket events and messages, and the other that handles all the frames the web socket
 * implementation uses for representing outgoing web socket events and messages.
 * @since DRAFT 003
 * @author dannycoward
 */
public interface Extension {

    /** The name of this extension. */
    public String getName();
    /** The map name value pairs that are the web socket extension parameters for this extension. */
    public Map<String, String> getParameters();
    /** The FrameHandler that is invoked for any incoming Frames. */
    public FrameHandler createIncomingFrameHandler(FrameHandler downstream);
    /** The FrameHandler that is invoked for any outgoing Frames. */
    public FrameHandler createOutgoingFrameHandler(FrameHandler upstream);

}
