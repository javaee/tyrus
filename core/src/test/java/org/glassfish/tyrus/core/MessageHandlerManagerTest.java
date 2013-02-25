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
import javax.websocket.PongMessage;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class MessageHandlerManagerTest {
    @Test
    public void simpleTest() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();

        messageHandlerManager.addMessageHandler(new MessageHandler.Basic<String>() {
            @Override
            public void onMessage(String message) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Basic<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Basic<PongMessage>() {
            @Override
            public void onMessage(PongMessage message) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Basic<MessageHandlerManagerTest>() {
            @Override
            public void onMessage(MessageHandlerManagerTest message) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Async<String>() {
            @Override
            public void onMessage(String message, boolean isLast) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleTextHandlers() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();

        messageHandlerManager.addMessageHandler(new MessageHandler.Basic<String>() {
            @Override
            public void onMessage(String message) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Basic<Reader>() {
            @Override
            public void onMessage(Reader message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleStringHandlers() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();

        messageHandlerManager.addMessageHandler(new MessageHandler.Basic<String>() {
            @Override
            public void onMessage(String message) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Basic<String>() {
            @Override
            public void onMessage(String message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleBinaryHandlers() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();

        messageHandlerManager.addMessageHandler(new MessageHandler.Basic<InputStream>() {
            @Override
            public void onMessage(InputStream message) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Basic<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleBinaryHandlersWithByteArray() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();

        messageHandlerManager.addMessageHandler(new MessageHandler.Basic<byte[]>() {
            @Override
            public void onMessage(byte[] message) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Basic<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleInputStreamHandlers() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();

        messageHandlerManager.addMessageHandler(new MessageHandler.Basic<InputStream>() {
            @Override
            public void onMessage(InputStream message) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Basic<InputStream>() {
            @Override
            public void onMessage(InputStream message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multiplePongHandlers() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();

        messageHandlerManager.addMessageHandler(new MessageHandler.Basic<PongMessage>() {
            @Override
            public void onMessage(PongMessage message) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Basic<PongMessage>() {
            @Override
            public void onMessage(PongMessage message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleBasicDecodable() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();

        messageHandlerManager.addMessageHandler(new MessageHandler.Basic<MessageHandlerManagerTest>() {
            @Override
            public void onMessage(MessageHandlerManagerTest message) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Basic<MessageHandlerManagerTest>() {
            @Override
            public void onMessage(MessageHandlerManagerTest message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleTextHandlersAsync() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();

        messageHandlerManager.addMessageHandler(new MessageHandler.Async<String>() {
            @Override
            public void onMessage(String message, boolean last) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Async<Reader>() {
            @Override
            public void onMessage(Reader message, boolean last) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleStringHandlersAsync() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();

        messageHandlerManager.addMessageHandler(new MessageHandler.Async<String>() {
            @Override
            public void onMessage(String message, boolean last) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Async<String>() {
            @Override
            public void onMessage(String message, boolean last) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleBinaryHandlersAsync() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();

        messageHandlerManager.addMessageHandler(new MessageHandler.Async<InputStream>() {
            @Override
            public void onMessage(InputStream message, boolean last) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Async<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message, boolean last) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleBinaryHandlersWithByteArrayAsync() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();

        messageHandlerManager.addMessageHandler(new MessageHandler.Async<byte[]>() {
            @Override
            public void onMessage(byte[] message, boolean last) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Async<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message, boolean last) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleInputStreamHandlersAsync() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();


        messageHandlerManager.addMessageHandler(new MessageHandler.Async<InputStream>() {
            @Override
            public void onMessage(InputStream message, boolean last) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Async<InputStream>() {
            @Override
            public void onMessage(InputStream message, boolean last) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multiplePongHandlersAsync() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();


        messageHandlerManager.addMessageHandler(new MessageHandler.Async<PongMessage>() {
            @Override
            public void onMessage(PongMessage message, boolean last) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Async<PongMessage>() {
            @Override
            public void onMessage(PongMessage message, boolean last) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleBasicDecodableAsync() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();


        messageHandlerManager.addMessageHandler(new MessageHandler.Async<MessageHandlerManagerTest>() {
            @Override
            public void onMessage(MessageHandlerManagerTest message, boolean last) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Async<MessageHandlerManagerTest>() {
            @Override
            public void onMessage(MessageHandlerManagerTest message, boolean last) {
            }
        });
    }

    @Test
    public void getHandlers() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();


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

        messageHandlerManager.addMessageHandler(handler1);
        messageHandlerManager.addMessageHandler(handler2);
        messageHandlerManager.addMessageHandler(handler3);

        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler1));
        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler2));
        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler3));
    }

    @Test
    public void removeHandlers() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();


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

        messageHandlerManager.addMessageHandler(handler1);
        messageHandlerManager.addMessageHandler(handler2);
        messageHandlerManager.addMessageHandler(handler3);
        messageHandlerManager.addMessageHandler(handler4);

        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler1));
        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler2));
        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler3));
        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler4));

        messageHandlerManager.removeMessageHandler(handler3);

        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler1));
        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler2));
        assertFalse(messageHandlerManager.getMessageHandlers().contains(handler3));
        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler4));

        messageHandlerManager.removeMessageHandler(handler2);

        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler1));
        assertFalse(messageHandlerManager.getMessageHandlers().contains(handler2));
        assertFalse(messageHandlerManager.getMessageHandlers().contains(handler3));
        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler4));
    }
}
