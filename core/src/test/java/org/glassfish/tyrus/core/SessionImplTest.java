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
package org.glassfish.tyrus.core;

import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;

import javax.websocket.MessageHandler;
import javax.websocket.OnMessage;
import javax.websocket.PongMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class SessionImplTest {
    EndpointWrapper ew = new EndpointWrapper(EchoEndpoint.class, null, null, null, null, null, null);

    @Test
    public void simpleTest() {
        Session session = new SessionImpl(null, null, ew, null, null, false, null, null, null);

        session.addMessageHandler(new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String message) {
            }
        });

        session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message) {
            }
        });

        session.addMessageHandler(new MessageHandler.Whole<PongMessage>() {
            @Override
            public void onMessage(PongMessage message) {
            }
        });
    }



    @Test(expected = IllegalStateException.class)
    public void multipleTextHandlers() {
        Session session = new SessionImpl(null, null, ew, null, null, false, null, null, null);

        session.addMessageHandler(new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String message) {
            }
        });

        session.addMessageHandler(new MessageHandler.Whole<Reader>() {
            @Override
            public void onMessage(Reader message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleStringHandlers() {
        Session session = new SessionImpl(null, null, ew, null, null, false, null, null, null);

        session.addMessageHandler(new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String message) {
            }
        });

        session.addMessageHandler(new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleBinaryHandlers() {
        Session session = new SessionImpl(null, null, ew, null, null, false, null, null, null);

        session.addMessageHandler(new MessageHandler.Whole<InputStream>() {
            @Override
            public void onMessage(InputStream message) {
            }
        });

        session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleBinaryHandlersWithByteArray() {
        Session session = new SessionImpl(null, null, ew, null, null, false, null, null, null);

        session.addMessageHandler(new MessageHandler.Whole<byte[]>() {
            @Override
            public void onMessage(byte[] message) {
            }
        });

        session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleInputStreamHandlers() {
        Session session = new SessionImpl(null, null, ew, null, null, false, null, null, null);

        session.addMessageHandler(new MessageHandler.Whole<InputStream>() {
            @Override
            public void onMessage(InputStream message) {
            }
        });

        session.addMessageHandler(new MessageHandler.Whole<InputStream>() {
            @Override
            public void onMessage(InputStream message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multiplePongHandlers() {
        Session session = new SessionImpl(null, null, ew, null, null, false, null, null, null);

        session.addMessageHandler(new MessageHandler.Whole<PongMessage>() {
            @Override
            public void onMessage(PongMessage message) {
            }
        });

        session.addMessageHandler(new MessageHandler.Whole<PongMessage>() {
            @Override
            public void onMessage(PongMessage message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleBasicDecodable() {
        Session session = new SessionImpl(null, null, ew, null, null, false, null, null, null);

        session.addMessageHandler(new MessageHandler.Whole<SessionImplTest>() {
            @Override
            public void onMessage(SessionImplTest message) {
            }
        });

        session.addMessageHandler(new MessageHandler.Whole<SessionImplTest>() {
            @Override
            public void onMessage(SessionImplTest message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleTextHandlersAsync() {
        Session session = new SessionImpl(null, null, ew, null, null, false, null, null, null);

        session.addMessageHandler(new MessageHandler.Partial<String>() {
            @Override
            public void onMessage(String message, boolean last) {
            }
        });

        session.addMessageHandler(new MessageHandler.Partial<Reader>() {
            @Override
            public void onMessage(Reader message, boolean last) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleStringHandlersAsync() {
        Session session = new SessionImpl(null, null, ew, null, null, false, null, null, null);

        session.addMessageHandler(new MessageHandler.Partial<String>() {
            @Override
            public void onMessage(String message, boolean last) {
            }
        });

        session.addMessageHandler(new MessageHandler.Partial<String>() {
            @Override
            public void onMessage(String message, boolean last) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleBinaryHandlersAsync() {
        Session session = new SessionImpl(null, null, ew, null, null, false, null, null, null);

        session.addMessageHandler(new MessageHandler.Partial<InputStream>() {
            @Override
            public void onMessage(InputStream message, boolean last) {
            }
        });

        session.addMessageHandler(new MessageHandler.Partial<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message, boolean last) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleBinaryHandlersWithByteArrayAsync() {
        Session session = new SessionImpl(null, null, ew, null, null, false, null, null, null);

        session.addMessageHandler(new MessageHandler.Partial<byte[]>() {
            @Override
            public void onMessage(byte[] message, boolean last) {
            }
        });

        session.addMessageHandler(new MessageHandler.Partial<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message, boolean last) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleInputStreamHandlersAsync() {
        Session session = new SessionImpl(null, null, ew, null, null, false, null, null, null);


        session.addMessageHandler(new MessageHandler.Partial<InputStream>() {
            @Override
            public void onMessage(InputStream message, boolean last) {
            }
        });

        session.addMessageHandler(new MessageHandler.Partial<InputStream>() {
            @Override
            public void onMessage(InputStream message, boolean last) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multiplePongHandlersAsync() {
        Session session = new SessionImpl(null, null, ew, null, null, false, null, null, null);


        session.addMessageHandler(new MessageHandler.Partial<PongMessage>() {
            @Override
            public void onMessage(PongMessage message, boolean last) {
            }
        });

        session.addMessageHandler(new MessageHandler.Partial<PongMessage>() {
            @Override
            public void onMessage(PongMessage message, boolean last) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleBasicDecodableAsync() {
        Session session = new SessionImpl(null, null, ew, null, null, false, null, null, null);


        session.addMessageHandler(new MessageHandler.Partial<SessionImplTest>() {
            @Override
            public void onMessage(SessionImplTest message, boolean last) {
            }
        });

        session.addMessageHandler(new MessageHandler.Partial<SessionImplTest>() {
            @Override
            public void onMessage(SessionImplTest message, boolean last) {
            }
        });
    }

    @Test
    public void getHandlers() {
        Session session = new SessionImpl(null, null, ew, null, null, false, null, null, null);

        final MessageHandler.Whole<String> handler1 = new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String message) {
            }
        };
        final MessageHandler.Whole<ByteBuffer> handler2 = new MessageHandler.Whole<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message) {
            }
        };
        final MessageHandler.Whole<PongMessage> handler3 = new MessageHandler.Whole<PongMessage>() {
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
        Session session = new SessionImpl(null, null, ew, null, null, false, null, null, null);


        final MessageHandler.Partial<String> handler1 = new MessageHandler.Partial<String>() {
            @Override
            public void onMessage(String message, boolean last) {
            }
        };
        final MessageHandler.Whole<ByteBuffer> handler2 = new MessageHandler.Whole<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message) {
            }
        };
        final MessageHandler.Whole<PongMessage> handler3 = new MessageHandler.Whole<PongMessage>() {
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

        session.removeMessageHandler(handler3);

        assertTrue(session.getMessageHandlers().contains(handler1));
        assertTrue(session.getMessageHandlers().contains(handler2));
        assertFalse(session.getMessageHandlers().contains(handler3));

        session.removeMessageHandler(handler2);

        assertTrue(session.getMessageHandlers().contains(handler1));
        assertFalse(session.getMessageHandlers().contains(handler2));
        assertFalse(session.getMessageHandlers().contains(handler3));
    }

    @Test
    public void idTest() {
        Session session1 = new SessionImpl(null, null, ew, null, null, false, null, null, null);
        Session session2 = new SessionImpl(null, null, ew, null, null, false, null, null, null);
        Session session3 = new SessionImpl(null, null, ew, null, null, false, null, null, null);

        assertFalse(session1.getId().equals(session2.getId()));
        assertFalse(session1.getId().equals(session3.getId()));
        assertFalse(session2.getId().equals(session3.getId()));
    }


    @ServerEndpoint(value = "/echo")
    private static class EchoEndpoint {

        @OnMessage
        public String doThat(String message, Session peer) {
            return message;
        }
    }

    @Test
    public void userPropertiesTest(){
        Session session1 = new SessionImpl(null, null, ew, null, null, false, null, null, null);
        Session session2 = new SessionImpl(null, null, ew, null, null, false, null, null, null);

        final String test1 = "test1";
        final String test2 = "test2";

        session1.getUserProperties().put(test1, test1);
        session2.getUserProperties().put(test2, test2);

        assertNull(session1.getUserProperties().get(test2));
        assertNull(session2.getUserProperties().get(test1));

        assertNotNull(session1.getUserProperties().get(test1));
        assertNotNull(session2.getUserProperties().get(test2));
    }
}