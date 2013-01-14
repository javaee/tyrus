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

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import javax.websocket.Extension;

/**
 * WebSocket {@link Extension} implementation.
 *
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class TyrusExtension implements Extension {

    private final String name;
    private final Map<String, String> parameters;

    /**
     * Create {@link Extension} with specific name.
     *
     * @param name extension name.
     * @throws IllegalArgumentException when name is null or empty string.
     */
    public TyrusExtension(String name) {
        this(name, null);
    }

    /**
     * Create {@link Extension} with name and parameters.
     *
     * @param name       extension name.
     * @param parameters extension parameters.
     */
    public TyrusExtension(String name, Map<String, String> parameters) {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException();
        }

        this.name = name;
        if (parameters != null) {
            final TreeMap<String, String> m = new TreeMap<String, String>(new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    return o1.compareTo(o2);
                }
            });
            m.putAll(parameters);
            this.parameters = Collections.unmodifiableMap(m);
        } else {
            this.parameters = Collections.unmodifiableMap(Collections.<String, String>emptyMap());
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Map<String, String> getParameters() {
        return parameters;
    }

    @Override
    public String toString() {
        return "TyrusExtension{" +
                "name='" + name + '\'' +
                ", parameters=" + parameters +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TyrusExtension that = (TyrusExtension) o;

        final Comparator<String> stringComparator = new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return o1.compareTo(o2);
            }
        };

        TreeMap<String, String> params1 = new TreeMap<String, String>(stringComparator);
        TreeMap<String, String> params2 = new TreeMap<String, String>(stringComparator);
        params1.putAll(parameters);
        params2.putAll(that.parameters);

        return name.equals(that.name) && params1.equals(params2);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + parameters.hashCode();
        return result;
    }

    /**
     * Naive parsing of one {@link Extension}.
     *
     * @param s {@link String} containing {@link Extension}.
     * @return extension represented as {@link TyrusExtension}.
     */
    public static TyrusExtension fromString(String s) {
        if (s == null || s.length() == 0) {
            return null;
        }

        final String[] split1 = s.split(";");
        if (split1.length == 1) {
            // just a name
            return new TyrusExtension(s);
        } else {
            String name = split1[0];
            Map<String, String> params = new HashMap<String, String>();
            for (int i = 1; i < split1.length; i++) {
                final String[] property = split1[i].split("=");
                if (property.length == 2) {
                    params.put(property[0], property[1]);
                }
            }
            return new TyrusExtension(name, params);
        }
    }
}
