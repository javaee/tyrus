/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.tyrus.gf.cdi;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.glassfish.tyrus.spi.ComponentProvider;

/**
 * Provides the instance for CDI class.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class CdiComponentProvider extends ComponentProvider {

    private final BeanManager beanManager;

    /**
     * Constructor.
     * </p>
     * Looks up the {@link BeanManager} which is later used to provide the instance.
     *
     * @throws javax.naming.NamingException
     */
    public CdiComponentProvider() throws NamingException {
        InitialContext ic = new InitialContext();
        beanManager = (BeanManager) ic.lookup("java:comp/BeanManager");
    }

    @Override
    public boolean isApplicable(Class<?> c) {
        return true;
    }

    @Override
    public <T> T provideInstance(Class<T> c) {
        T managedObject;
        AnnotatedType annotatedType;
        InjectionTarget it;
        CreationalContext cc;

        synchronized (beanManager) {
            annotatedType = beanManager.createAnnotatedType(c);
            it = beanManager.createInjectionTarget(annotatedType);
            cc = beanManager.createCreationalContext(null);
        }
        managedObject = (T) it.produce(cc);
        it.inject(managedObject, cc);
        it.postConstruct(managedObject);

        return managedObject;
    }
}
