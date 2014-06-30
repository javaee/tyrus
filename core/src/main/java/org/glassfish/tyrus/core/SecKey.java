/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2014 Oracle and/or its affiliates. All rights reserved.
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

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;

import org.glassfish.tyrus.core.l10n.LocalizationMessages;
import org.glassfish.tyrus.spi.UpgradeRequest;

/**
 * Class represents security key, used during the handshake phase.
 *
 * @author Alexey Stashok
 */
class SecKey {
    private static final Random random = new SecureRandom();

    private static final int KEY_SIZE = 16;

    /**
     * Security key string representation, which includes chars and spaces.
     */
    private final String secKey;

    public SecKey() {
        secKey = create();
    }

    private String create() {
        byte[] bytes = new byte[KEY_SIZE];
        random.nextBytes(bytes);
        return Base64Utils.encodeToString(bytes, false);
    }

    /**
     * Constructor.
     *
     * @param base64 sec key to be represented as {@link SecKey} instance.
     * @throws HandshakeException when the provided key is {@code null}.
     */
    public SecKey(String base64) throws HandshakeException {
        if (base64 == null) {
            throw new HandshakeException(LocalizationMessages.SEC_KEY_NULL_NOT_ALLOWED());
        }
        secKey = base64;
    }

    /**
     * Generate server-side security key, which gets passed to the client during
     * the handshake phase as part of message payload.
     *
     * @param clientKey client's Sec-WebSocket-Key
     * @return server key.
     */
    public static SecKey generateServerKey(SecKey clientKey) throws HandshakeException {
        String key = clientKey.getSecKey() + UpgradeRequest.SERVER_KEY_HASH;
        final MessageDigest instance;
        try {
            instance = MessageDigest.getInstance("SHA-1");
            instance.update(key.getBytes("UTF-8"));
            final byte[] digest = instance.digest();
            if (digest.length != 20) {
                throw new HandshakeException(LocalizationMessages.SEC_KEY_INVALID_LENGTH(digest.length));
            }

            return new SecKey(Base64Utils.encodeToString(digest, false));
        } catch (NoSuchAlgorithmException e) {
            throw new HandshakeException(e.getMessage());
        } catch (UnsupportedEncodingException e) {
            throw new HandshakeException(e.getMessage());
        }
    }

    /**
     * Gets security key string representation, which includes chars and spaces.
     *
     * @return Security key string representation, which includes chars and spaces.
     */
    public String getSecKey() {
        return secKey;
    }

    @Override
    public String toString() {
        return secKey;
    }

    /**
     * Validate provided server key.
     *
     * @param serverKey server key to be validated.
     * @throws HandshakeException when the server key is invalid.
     */
    public void validateServerKey(String serverKey) throws HandshakeException {
        final SecKey key = generateServerKey(this);
        if (!key.getSecKey().equals(serverKey)) {
            throw new HandshakeException(LocalizationMessages.SEC_KEY_INVALID_SERVER());
        }
    }
}
