/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2016 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Method;

/**
 * Provides an instance.
 * <p>
 * Method {@link #isApplicable(Class)} is called first to check whether the provider is able to provide the given
 * {@link Class}.  Method {@link #create(Class)} is called to get the instance.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Martin Matula (martin.matula at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public abstract class ComponentProvider {
    /**
     * Checks whether this component provider is able to provide an instance of given {@link Class}.
     *
     * @param c {@link Class} to be checked.
     * @return {@code true} iff this {@link ComponentProvider} is able to create an instance of the given {@link Class}.
     */
    public abstract boolean isApplicable(Class<?> c);

    /**
     * Create new instance.
     *
     * @param c   {@link Class} to be created.
     * @param <T> type of the created object.
     * @return instance, iff found, {@code null} otherwise.
     */
    public abstract <T> Object create(Class<T> c);

    /**
     * Get the method which should be invoked instead provided one.
     * <p>
     * Useful mainly for EJB container support, where methods from endpoint class cannot be invoked directly - Tyrus
     * needs
     * to use method declared on remote interface.
     * <p>
     * Default implementation returns method provided as parameter.
     *
     * @param method method from endpoint class.
     * @return method which should be invoked.
     */
    public Method getInvocableMethod(Method method) {
        return method;
    }

    /**
     * Destroys the given managed instance.
     *
     * @param o instance to be destroyed.
     * @return <code>true</code> iff the instance was coupled to this {@link ComponentProvider}, false otherwise.
     */
    public abstract boolean destroy(Object o);
}
