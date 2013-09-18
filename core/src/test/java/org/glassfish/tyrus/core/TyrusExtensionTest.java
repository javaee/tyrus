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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.websocket.Extension;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class TyrusExtensionTest {

    @Test
    public void simple() {
        final TyrusExtension test = new TyrusExtension("test");
        assertEquals("test", test.getName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidName1() {
        new TyrusExtension("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidName2() {
        new TyrusExtension(null);
    }

    @Test
    public void params() {
        final List<Extension.Parameter> parameters = new ArrayList<Extension.Parameter>() {{
            add(new Extension.Parameter() {
                @Override
                public String getName() {
                    return "Quote";
                }

                @Override
                public String getValue() {
                    return "Mmm. Lost a planet, Master Obi-Wan has. How embarrassing. How embarrassing.";
                }
            }
            );
        }};
        final TyrusExtension test = new TyrusExtension("test", parameters);

        assertNotNull(test.getParameters());

        assertEquals("Quote", test.getParameters().get(0).getName());
        assertEquals("Mmm. Lost a planet, Master Obi-Wan has. How embarrassing. How embarrassing.", test.getParameters().get(0).getValue());
    }

    @Test
    public void error1() {
        final List<Extension> extensions = TyrusExtension.fromHeaders(Arrays.asList("ext1;param=val\"ue"));

        assertEquals(0, extensions.size());
    }

    @Test
    public void error2() {
        final List<Extension> extensions = TyrusExtension.fromHeaders(Arrays.asList("ext1;param=value=value"));

        assertEquals(0, extensions.size());
    }

    @Test
    public void error3() {
        final List<Extension> extensions = TyrusExtension.fromHeaders(Arrays.asList("ext1,param=value"));

        assertEquals(1, extensions.size());
    }

    @Test
    public void error4() {
        final List<Extension> extensions = TyrusExtension.fromHeaders(Arrays.asList("ext1,param=value,ext2;param=value"));

        assertEquals(2, extensions.size());
        assertEquals("ext1", extensions.get(0).getName());
        assertEquals("ext2", extensions.get(1).getName());
        assertTrue(extensions.get(1).getParameters().size() == 1);
        assertEquals("param", extensions.get(1).getParameters().get(0).getName());
        assertEquals("value", extensions.get(1).getParameters().get(0).getValue());
    }

    @Test
    public void testParseHeaders1() {
        final List<Extension> extensions = TyrusExtension.fromHeaders(Arrays.asList("ext1;param=value"));

        assertEquals(1, extensions.size());
        assertEquals("ext1", extensions.get(0).getName());
        assertTrue(extensions.get(0).getParameters().size() == 1);
        assertEquals("param", extensions.get(0).getParameters().get(0).getName());
        assertEquals("value", extensions.get(0).getParameters().get(0).getValue());
    }

    @Test
    public void testParseHeaders2() {
        final List<Extension> extensions = TyrusExtension.fromHeaders(Arrays.asList("ext1;param=value,ext2;param=value"));

        assertEquals(2, extensions.size());
        assertEquals("ext1", extensions.get(0).getName());
        assertTrue(extensions.get(0).getParameters().size() == 1);
        assertEquals("param", extensions.get(0).getParameters().get(0).getName());
        assertEquals("value", extensions.get(0).getParameters().get(0).getValue());
        assertEquals("ext2", extensions.get(1).getName());
        assertTrue(extensions.get(1).getParameters().size() == 1);
        assertEquals("param", extensions.get(1).getParameters().get(0).getName());
        assertEquals("value", extensions.get(1).getParameters().get(0).getValue());

    }

    @Test
    public void testParseHeaders3() {
        final List<Extension> extensions = TyrusExtension.fromHeaders(Arrays.asList("ext1;param=value", "ext2;param=value"));

        assertEquals(2, extensions.size());
        assertEquals("ext1", extensions.get(0).getName());
        assertTrue(extensions.get(0).getParameters().size() == 1);
        assertEquals("param", extensions.get(0).getParameters().get(0).getName());
        assertEquals("value", extensions.get(0).getParameters().get(0).getValue());
        assertEquals("ext2", extensions.get(1).getName());
        assertTrue(extensions.get(1).getParameters().size() == 1);
        assertEquals("param", extensions.get(1).getParameters().get(0).getName());
        assertEquals("value", extensions.get(1).getParameters().get(0).getValue());
    }

    @Test
    public void testParseHeadersQuoted1() {
        final List<Extension> extensions = TyrusExtension.fromHeaders(Arrays.asList("ext1;param=\"  value  \""));

        assertEquals(1, extensions.size());
        assertEquals("ext1", extensions.get(0).getName());
        assertTrue(extensions.get(0).getParameters().size() == 1);
        assertEquals("param", extensions.get(0).getParameters().get(0).getName());
        assertEquals("  value  ", extensions.get(0).getParameters().get(0).getValue());
    }

    @Test
    public void testParseHeadersQuoted2() {
        final List<Extension> extensions = TyrusExtension.fromHeaders(Arrays.asList("ext1;param=\"  value  \",ext2;param=\"  value \\\" \""));

        assertEquals(2, extensions.size());
        assertEquals("ext1", extensions.get(0).getName());
        assertTrue(extensions.get(0).getParameters().size() == 1);
        assertEquals("param", extensions.get(0).getParameters().get(0).getName());
        assertEquals("  value  ", extensions.get(0).getParameters().get(0).getValue());
        assertEquals("ext2", extensions.get(1).getName());
        assertTrue(extensions.get(1).getParameters().size() == 1);
        assertEquals("param", extensions.get(1).getParameters().get(0).getName());
        assertEquals("  value \" ", extensions.get(1).getParameters().get(0).getValue());
    }

    @Test
    public void testParseHeadersQuoted3() {
        final List<Extension> extensions = TyrusExtension.fromHeaders(Arrays.asList("ext1;param=\"  value  \"", "ext2;param=\"  value \\\\ \""));

        assertEquals(2, extensions.size());
        assertEquals("ext1", extensions.get(0).getName());
        assertTrue(extensions.get(0).getParameters().size() == 1);
        assertEquals("param", extensions.get(0).getParameters().get(0).getName());
        assertEquals("  value  ", extensions.get(0).getParameters().get(0).getValue());
        assertEquals("ext2", extensions.get(1).getName());
        assertTrue(extensions.get(1).getParameters().size() == 1);
        assertEquals("param", extensions.get(1).getParameters().get(0).getName());
        assertEquals("  value \\ ", extensions.get(1).getParameters().get(0).getValue());
    }
}
