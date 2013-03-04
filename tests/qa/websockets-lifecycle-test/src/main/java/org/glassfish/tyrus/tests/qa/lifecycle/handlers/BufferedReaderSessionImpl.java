/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.tests.qa.lifecycle.handlers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.logging.Level;
import javax.websocket.Session;
import org.glassfish.tyrus.tests.qa.lifecycle.SessionConversation;
import org.glassfish.tyrus.tests.qa.lifecycle.SessionLifeCycle;

/**
 *
 * @author michal.conos at oracle.com
 */
public class BufferedReaderSessionImpl extends SessionLifeCycle<Reader, InputStream> implements SessionConversation {

    private int messageSize;
    private String messageToSend = "";
    private String gotMessage = "";
    private static final String oneLine = "abcdefghijklm\n";
    
    private String readMessage(Reader rd) throws IOException {
        String wholeMessage="";
        BufferedReader br = new BufferedReader(rd);
        for (;;) {
            String line = br.readLine();
            if (line == null) {
                break;
            }
            wholeMessage += line + "\n";
        }
        return wholeMessage;
    }

    public BufferedReaderSessionImpl(int messageSize) {
        this.messageSize = messageSize;
        initSendMessage();

    }

    private void initSendMessage() {
        for (int idx = 0; idx < messageSize / oneLine.length(); idx++) {
            messageToSend += oneLine;
        }
        logger.log(Level.INFO, "XXX:initSendMessage:{0}", messageToSend);
    }

    @Override
    public void startTalk(Session s) throws IOException {
        logger.log(Level.INFO, "XXX: Send message:{0}", messageToSend);
        javax.websocket.RemoteEndpoint.Basic basic = s.getBasicRemote();
        Writer wr = basic.getSendWriter();
        wr.write(messageToSend);
        wr.close();
    }

    @Override
    public void onServerMessageHandler(Reader reader, Session session) throws IOException {
        logger.log(Level.INFO, "XXX: HERE I AM!!!");
        String  message = readMessage(reader);
        logger.log(Level.INFO, "XXX: bounce message:{0}", message);
        Writer wr = session.getBasicRemote().getSendWriter();
        wr.write(message);
        wr.close();
    }

    @Override
    public void onClientMessageHandler(Reader reader, Session session) throws IOException {
        String message = readMessage(reader);
        if (message.equals(messageToSend)) {
            closeTheSessionFromClient(session);
        }
    }

    @Override
    public SessionLifeCycle getSessionConversation() {
        return new BufferedReaderSessionImpl(1024);
    }

    @Override
    public void onServerMessageHandler(InputStream message, Session session, boolean last) throws IOException {
        
    }

    @Override
    public void onClientMessageHandler(InputStream message, Session session, boolean last) throws IOException {
        
    }
}
