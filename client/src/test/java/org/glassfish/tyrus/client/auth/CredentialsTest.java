/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.client.auth;

import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class CredentialsTest {

    private final static String USERNAME = "username";
    private final static String PASSWORD_STRING = "password";
    private final static byte[] PASSWORD_BYTE_ARRAY = {0x01, 0x02, 0x03, 0x04};

    @Test
    public void testGetUsername() throws Exception {
        Credentials credentials = new Credentials(USERNAME, PASSWORD_STRING);

        assertEquals(USERNAME, credentials.getUsername());
    }

    @Test
    public void testGetPasswordString() throws Exception {
        Credentials credentials = new Credentials(USERNAME, PASSWORD_STRING);

        assertArrayEquals(PASSWORD_STRING.getBytes(AuthConfig.CHARACTER_SET), credentials.getPassword());
    }

    @Test
    public void testGetPasswordByteArray() throws Exception {
        Credentials credentials = new Credentials(USERNAME, PASSWORD_BYTE_ARRAY);

        assertEquals(PASSWORD_BYTE_ARRAY, credentials.getPassword());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullUsername1() {
        //noinspection ResultOfObjectAllocationIgnored
        new Credentials(null, PASSWORD_STRING);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullUsername2() {
        //noinspection ResultOfObjectAllocationIgnored
        new Credentials(null, PASSWORD_BYTE_ARRAY);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullPassword1() {
        //noinspection ResultOfObjectAllocationIgnored
        new Credentials(USERNAME, (String) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullPassword2() {
        //noinspection ResultOfObjectAllocationIgnored
        new Credentials(USERNAME, (byte[]) null);
    }
}