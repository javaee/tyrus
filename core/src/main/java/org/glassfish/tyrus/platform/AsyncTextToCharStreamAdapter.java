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
package org.glassfish.tyrus.platform;

import java.util.ArrayList;
import java.util.List;
import javax.net.websocket.MessageHandler;

/**
 * A simple adapter that acts as a listens for text message fragments on one thread and offers
 * a (blocking) Reader that can be read on another thread, buffering content along the way.
 * @author dannycoward
 */
class AsyncTextToCharStreamAdapter implements BufferedStringSource, MessageHandler.AsyncText {
    /* The text message pieces currently being buffered. */
    private List<String> bufferedFragments = new ArrayList<String>();
    /* Has this adapter received the last in a sequence of fragments. */
    private boolean receivedLast = false;
    /* The reader implementation this adapter will offer. */
    private BufferedStringSourceReader reader = null;
    /* Configurable buffer size. */
    private static long MAX_BUFFER_SIZE = 8 * 1024;
    /* The MessageHandler that will be invoked when a new message starts. */
    private MessageHandler.CharacterStream mh;
    /* Statelock to mediate between the notification thread for message fragments and the
     * thread reading the Reader data.
     */
    private Object stateLock;
    
    public AsyncTextToCharStreamAdapter(MessageHandler.CharacterStream mh) {
        this.mh = mh;
        this.stateLock = new Object();
    }
    
    private void blockOnReaderThread() {
        synchronized (stateLock) {
            try {
                this.stateLock.wait();
            } catch (InterruptedException e) {
                // thread unblocked
            }
        }
    }

    
    public char[] getNextChars(int numberOfChars) {

        if (this.bufferedFragments.isEmpty()) {
            if (receivedLast) {
                this.reader = null;
                return null;
            } else { // there's more to come...so wait here...
              blockOnReaderThread();
            }
        }
        char[] chrs = new char[1];
        String nextFragment = this.bufferedFragments.get(0);
        chrs[0] = nextFragment.charAt(0);
        this.bufferedFragments.remove(0);
        if (nextFragment.length() > 1) {
            String newFragment = nextFragment.substring(1, nextFragment.length());
            this.bufferedFragments.add(0, newFragment);
        }
        return chrs;
    }
    
    @Override
    public void finishedReading() {
        this.bufferedFragments = new ArrayList();
        this.reader = null;
    }
    
    private void checkForBufferOverflow(String part) {
        int numberOfBytes = 0;
        for (String fragment : this.bufferedFragments) {
            numberOfBytes = numberOfBytes + fragment.length();   
        }
        if (MAX_BUFFER_SIZE < numberOfBytes + part.length()) {            
            throw new IllegalStateException("Buffer overflow");
        }
    }
    
    
    @Override
    public void onMessagePart(String part, boolean last) {
        this.receivedLast = last;
        this.checkForBufferOverflow(part);
        bufferedFragments.add(part);
        
        synchronized (stateLock) {
            try {
                this.stateLock.notifyAll();
            } catch (Exception e) {
                
            }
        }
         
        if (this.reader == null) {
            this.reader = new BufferedStringSourceReader(this);
            Thread t = new Thread() {
                public void run() {
                    mh.onMessage(reader);
                }
            };
            t.start();
        }
 
    }
}


