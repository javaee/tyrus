/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus;

import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;

import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;
import javax.websocket.Session;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class SessionImplTest {

    @Test
    public void simpleTest() {
        Session session = new SessionImpl(null, null, null, null, null, false, null, null, null);

        session.addMessageHandler(new MessageHandler.Basic<String>() {
            @Override
            public void onMessage(String message) {
            }
        });

        session.addMessageHandler(new MessageHandler.Basic<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message) {
            }
        });

        session.addMessageHandler(new MessageHandler.Basic<PongMessage>() {
            @Override
            public void onMessage(PongMessage message) {
            }
        });

        session.addMessageHandler(new MessageHandler.Basic<SessionImplTest>() {
            @Override
            public void onMessage(SessionImplTest message) {
            }
        });

        session.addMessageHandler(new MessageHandler.Async<String>() {
            @Override
            public void onMessage(String message, boolean isLast) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleTextHandlers() {
        Session session = new SessionImpl(null, null, null, null, null, false, null, null, null);

        session.addMessageHandler(new MessageHandler.Basic<String>() {
            @Override
            public void onMessage(String message) {
            }
        });

        session.addMessageHandler(new MessageHandler.Basic<Reader>() {
            @Override
            public void onMessage(Reader message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleBinaryHandlers() {
        Session session = new SessionImpl(null, null, null, null, null, false, null, null, null);

        session.addMessageHandler(new MessageHandler.Basic<InputStream>() {
            @Override
            public void onMessage(InputStream message) {
            }
        });

        session.addMessageHandler(new MessageHandler.Basic<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multiplePongHandlers() {
        Session session = new SessionImpl(null, null, null, null, null, false, null, null, null);

        session.addMessageHandler(new MessageHandler.Basic<PongMessage>() {
            @Override
            public void onMessage(PongMessage message) {
            }
        });

        session.addMessageHandler(new MessageHandler.Basic<PongMessage>() {
            @Override
            public void onMessage(PongMessage message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleBasicDecodable() {
        Session session = new SessionImpl(null, null, null, null, null, false, null, null, null);

        session.addMessageHandler(new MessageHandler.Basic<SessionImplTest>() {
            @Override
            public void onMessage(SessionImplTest message) {
            }
        });

        session.addMessageHandler(new MessageHandler.Basic<SessionImplTest>() {
            @Override
            public void onMessage(SessionImplTest message) {
            }
        });
    }

    @Test
    public void getHandlers() {
        Session session = new SessionImpl(null, null, null, null, null, false, null, null, null);

        final MessageHandler.Basic<String> handler1 = new MessageHandler.Basic<String>() {
            @Override
            public void onMessage(String message) {
            }
        };
        final MessageHandler.Basic<ByteBuffer> handler2 = new MessageHandler.Basic<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message) {
            }
        };
        final MessageHandler.Basic<PongMessage> handler3 = new MessageHandler.Basic<PongMessage>() {
            @Override
            public void onMessage(PongMessage message) {
            }
        };

        session.addMessageHandler(handler1);
        session.addMessageHandler(handler2);
        session.addMessageHandler(handler3);

        assertTrue(session.getMessageHandlers().contains(handler1));
        assertTrue(session.getMessageHandlers().contains(handler2));
        assertTrue(session.getMessageHandlers().contains(handler3));
    }

    @Test
    public void removeHandlers() {
        Session session = new SessionImpl(null, null, null, null, null, false, null, null, null);

        final MessageHandler.Basic<String> handler1 = new MessageHandler.Basic<String>() {
            @Override
            public void onMessage(String message) {
            }
        };
        final MessageHandler.Basic<ByteBuffer> handler2 = new MessageHandler.Basic<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message) {
            }
        };
        final MessageHandler.Basic<PongMessage> handler3 = new MessageHandler.Basic<PongMessage>() {
            @Override
            public void onMessage(PongMessage message) {
            }
        };
        final MessageHandler.Async<String> handler4 = new MessageHandler.Async<String>() {
            @Override
            public void onMessage(String message, boolean isLast) {
            }
        };

        session.addMessageHandler(handler1);
        session.addMessageHandler(handler2);
        session.addMessageHandler(handler3);
        session.addMessageHandler(handler4);

        assertTrue(session.getMessageHandlers().contains(handler1));
        assertTrue(session.getMessageHandlers().contains(handler2));
        assertTrue(session.getMessageHandlers().contains(handler3));
        assertTrue(session.getMessageHandlers().contains(handler4));

        session.removeMessageHandler(handler3);

        assertTrue(session.getMessageHandlers().contains(handler1));
        assertTrue(session.getMessageHandlers().contains(handler2));
        assertFalse(session.getMessageHandlers().contains(handler3));
        assertTrue(session.getMessageHandlers().contains(handler4));

        session.removeMessageHandler(handler2);

        assertTrue(session.getMessageHandlers().contains(handler1));
        assertFalse(session.getMessageHandlers().contains(handler2));
        assertFalse(session.getMessageHandlers().contains(handler3));
        assertTrue(session.getMessageHandlers().contains(handler4));
    }
}
