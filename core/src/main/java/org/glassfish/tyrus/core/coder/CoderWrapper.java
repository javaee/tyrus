/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.core.coder;

import javax.websocket.Decoder;
import javax.websocket.Encoder;

/**
 * Wrapper of coders storing the coder coder class (and optionally coder instance), return type of the encode / decode
 * method and coder class.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class CoderWrapper<T> extends CoderAdapter implements Decoder, Encoder {

    private final Class<? extends T> coderClass;
    private final T coder;

    /**
     * Return type of the encode / decode method.
     */
    private final Class<?> type;

    /**
     * Construct new coder wrapper.
     *
     * @param coderClass coder class.
     * @param type       return type provided by the encode / decode method. Cannot be {@code null}.
     */
    public CoderWrapper(Class<? extends T> coderClass, Class<?> type) {
        this.coderClass = coderClass;
        this.coder = null;
        this.type = type;
    }

    /**
     * Construct new coder wrapper.
     *
     * @param coder cannot be {@code null}.
     * @param type  return type provided by the encode / decode method. Cannot be {@code null}.
     */
    public CoderWrapper(T coder, Class<?> type) {
        this.coder = coder;
        this.coderClass = (Class<T>) coder.getClass();
        this.type = type;
    }

    /**
     * Get the return type of the encode / decode method.
     *
     * @return return type of the encode / decode method.
     */
    public Class<?> getType() {
        return type;
    }

    /**
     * Get coder class.
     *
     * @return coder class.
     */
    public Class<? extends T> getCoderClass() {
        return coderClass;
    }

    /**
     * Get coder instance.
     *
     * @return coder instance. {@code null} if registered using coder class.
     */
    public T getCoder() {
        return coder;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("CoderWrapper");
        sb.append("{coderClass=").append(coderClass);
        sb.append(", coder=").append(coder);
        sb.append(", type=").append(type);
        sb.append('}');
        return sb.toString();
    }
}
