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
import java.util.Arrays;

import javax.websocket.DecodeException;
import javax.websocket.Decoder;
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

        messageHandlerManager.addMessageHandler(new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String message) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Whole<PongMessage>() {
            @Override
            public void onMessage(PongMessage message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleTextHandlers() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();

        messageHandlerManager.addMessageHandler(new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String message) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Whole<Reader>() {
            @Override
            public void onMessage(Reader message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleTextHandlersCombined() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();

        messageHandlerManager.addMessageHandler(new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String message) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Partial<Reader>() {

            @Override
            public void onMessage(Reader reader, boolean b) {

            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleTextHandlersWithDecoder() {
        MessageHandlerManager messageHandlerManager = MessageHandlerManager.fromDecoderInstances(Arrays.<Decoder>asList(new CoderWrapper<Decoder>(new TestTextDecoder(), MessageHandlerManagerTest.class)));

        messageHandlerManager.addMessageHandler(new MessageHandler.Whole<MessageHandlerManagerTest>() {
            @Override
            public void onMessage(MessageHandlerManagerTest message) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Partial<Reader>() {

            @Override
            public void onMessage(Reader reader, boolean b) {

            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void noDecoderTest() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();

        messageHandlerManager.addMessageHandler(new MessageHandler.Whole<MessageHandlerManagerTest>() {
            @Override
            public void onMessage(MessageHandlerManagerTest message) {
            }
        });
    }

    public static class TestTextDecoder extends CoderAdapter implements Decoder.Text<MessageHandlerManagerTest> {

        @Override
        public MessageHandlerManagerTest decode(String s) throws DecodeException {
            return null;
        }

        @Override
        public boolean willDecode(String s) {
            return false;
        }
    }

    @Test(expected = IllegalStateException.class)
    public void multipleStringHandlers() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();

        messageHandlerManager.addMessageHandler(new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String message) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleBinaryHandlers() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();

        messageHandlerManager.addMessageHandler(new MessageHandler.Whole<InputStream>() {
            @Override
            public void onMessage(InputStream message) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleBinaryHandlersCombined() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();

        messageHandlerManager.addMessageHandler(new MessageHandler.Whole<InputStream>() {
            @Override
            public void onMessage(InputStream message) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Partial<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message, boolean last) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleBinaryHandlersWithByteArray() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();

        messageHandlerManager.addMessageHandler(new MessageHandler.Whole<byte[]>() {
            @Override
            public void onMessage(byte[] message) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleInputStreamHandlers() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();

        messageHandlerManager.addMessageHandler(new MessageHandler.Whole<InputStream>() {
            @Override
            public void onMessage(InputStream message) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Whole<InputStream>() {
            @Override
            public void onMessage(InputStream message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multiplePongHandlers() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();

        messageHandlerManager.addMessageHandler(new MessageHandler.Whole<PongMessage>() {
            @Override
            public void onMessage(PongMessage message) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Whole<PongMessage>() {
            @Override
            public void onMessage(PongMessage message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleBasicDecodable() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager(Arrays.<Class<? extends Decoder>>asList(TestTextDecoder.class));

        messageHandlerManager.addMessageHandler(new MessageHandler.Whole<MessageHandlerManagerTest>() {
            @Override
            public void onMessage(MessageHandlerManagerTest message) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Whole<MessageHandlerManagerTest>() {
            @Override
            public void onMessage(MessageHandlerManagerTest message) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleTextHandlersPartial() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();

        messageHandlerManager.addMessageHandler(new MessageHandler.Partial<String>() {
            @Override
            public void onMessage(String message, boolean last) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Partial<String>() {
            @Override
            public void onMessage(String message, boolean last) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void wrongPartialHandlerType() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();

        messageHandlerManager.addMessageHandler(new MessageHandler.Partial<MessageHandlerManagerTest>() {
            @Override
            public void onMessage(MessageHandlerManagerTest message, boolean last) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleBinaryHandlersWithByteArrayPartial() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();

        messageHandlerManager.addMessageHandler(new MessageHandler.Partial<byte[]>() {
            @Override
            public void onMessage(byte[] message, boolean last) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Partial<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message, boolean last) {
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void multipleByteBufferHandlersPartial() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();


        messageHandlerManager.addMessageHandler(new MessageHandler.Partial<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message, boolean last) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Partial<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message, boolean last) {
            }
        });
    }


    @Test(expected = IllegalStateException.class)
    public void multipleBasicDecodablePartial() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();


        messageHandlerManager.addMessageHandler(new MessageHandler.Partial<MessageHandlerManagerTest>() {
            @Override
            public void onMessage(MessageHandlerManagerTest message, boolean last) {
            }
        });

        messageHandlerManager.addMessageHandler(new MessageHandler.Partial<MessageHandlerManagerTest>() {
            @Override
            public void onMessage(MessageHandlerManagerTest message, boolean last) {
            }
        });
    }

    @Test
    public void getHandlers() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();


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

        messageHandlerManager.addMessageHandler(handler1);
        messageHandlerManager.addMessageHandler(handler2);
        messageHandlerManager.addMessageHandler(handler3);

        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler1));
        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler2));
        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler3));
    }

    @Test
    public void addRemoveAddHandlers() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager(Arrays.<Class<? extends Decoder>>asList(TestTextDecoder.class));


        final MessageHandler.Whole<String> handler1 = new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String message) {
            }
        };

        final MessageHandler.Partial<ByteBuffer> handler2 = new MessageHandler.Partial<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message, boolean last) {
            }
        };

        final MessageHandler.Whole<PongMessage> handler3 = new MessageHandler.Whole<PongMessage>() {
            @Override
            public void onMessage(PongMessage message) {
            }
        };

        final MessageHandler.Whole<MessageHandlerManagerTest> handler4 = new MessageHandler.Whole<MessageHandlerManagerTest>() {
            @Override
            public void onMessage(MessageHandlerManagerTest message) {
            }
        };

        messageHandlerManager.addMessageHandler(handler1);
        messageHandlerManager.addMessageHandler(handler2);
        messageHandlerManager.addMessageHandler(handler3);

        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler1));
        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler2));
        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler3));

        messageHandlerManager.removeMessageHandler(handler3);

        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler1));
        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler2));
        assertFalse(messageHandlerManager.getMessageHandlers().contains(handler3));

        messageHandlerManager.removeMessageHandler(handler2);

        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler1));
        assertFalse(messageHandlerManager.getMessageHandlers().contains(handler2));
        assertFalse(messageHandlerManager.getMessageHandlers().contains(handler3));

        messageHandlerManager.addMessageHandler(handler3);

        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler1));
        assertFalse(messageHandlerManager.getMessageHandlers().contains(handler2));
        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler3));

        messageHandlerManager.addMessageHandler(handler2);

        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler1));
        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler2));
        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler3));

        messageHandlerManager.removeMessageHandler(handler1);

        assertFalse(messageHandlerManager.getMessageHandlers().contains(handler1));
        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler2));
        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler3));

        messageHandlerManager.addMessageHandler(handler4);

        assertFalse(messageHandlerManager.getMessageHandlers().contains(handler1));
        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler2));
        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler3));
        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler4));
    }


    @Test
    public void removeHandlers() {
        MessageHandlerManager messageHandlerManager = new MessageHandlerManager();


        final MessageHandler.Whole<String> handler1 = new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String message) {
            }
        };
        final MessageHandler.Partial<ByteBuffer> handler2 = new MessageHandler.Partial<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message, boolean last) {
            }
        };
        final MessageHandler.Whole<PongMessage> handler3 = new MessageHandler.Whole<PongMessage>() {
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

        messageHandlerManager.removeMessageHandler(handler3);

        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler1));
        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler2));
        assertFalse(messageHandlerManager.getMessageHandlers().contains(handler3));

        messageHandlerManager.removeMessageHandler(handler2);

        assertTrue(messageHandlerManager.getMessageHandlers().contains(handler1));
        assertFalse(messageHandlerManager.getMessageHandlers().contains(handler2));
        assertFalse(messageHandlerManager.getMessageHandlers().contains(handler3));
    }
}
