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

package org.glassfish.tyrus.gf.ejb;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ejb.Singleton;
import javax.ejb.Stateful;
import javax.ejb.Stateless;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.glassfish.tyrus.core.ComponentProvider;

/**
 * Provides the instance for the supported EJB classes.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 */
public class EjbComponentProvider extends ComponentProvider {

    private static final Logger LOGGER = Logger.getLogger(EjbComponentProvider.class.getName());

    @SuppressWarnings("unchecked")
    @Override
    public <T> T create(Class<T> c) {
        String name = getName(c);
        T result = null;
        if (name == null) {
            return null;
        }

        try {
            InitialContext ic = new InitialContext();
            result =  (T) lookup(ic, c, name);
        } catch (NamingException ex) {
            String message =  "An instance of EJB class " + c.getName() +
                    " could not be looked up using simple form name or the fully-qualified form name.";
            LOGGER.log(Level.SEVERE, message, ex);
        }

        return result;
    }

    @Override
    public boolean isApplicable(Class<?> c){
        return (c.isAnnotationPresent(Singleton.class) ||
                c.isAnnotationPresent(Stateful.class) ||
                c.isAnnotationPresent(Stateless.class));
    }

    @Override
    public boolean destroy(Object o) {
        return false;
    }

    private String getName(Class<?> c) {
        String name;

        if (c.isAnnotationPresent(Singleton.class)) {
            name = c.getAnnotation(Singleton.class).name();
        } else if (c.isAnnotationPresent(Stateful.class)) {
            name = c.getAnnotation(Stateful.class).name();
        } else if (c.isAnnotationPresent(Stateless.class)) {
            name = c.getAnnotation(Stateless.class).name();
        } else {
            return null;
        }

        if (name == null || name.length() == 0) {
            name = c.getSimpleName();
        }
        return name;
    }

    private Object lookup(InitialContext ic, Class<?> c, String name) throws NamingException {
        try {
            return lookupSimpleForm(ic, c, name);
        } catch (NamingException ex) {
            LOGGER.log(Level.WARNING, "An instance of EJB class " + c.getName() +
                    " could not be looked up using simple form name. " +
                    "Attempting to look up using the fully-qualified form name.", ex);

            return lookupFullyQualfiedForm(ic, c, name);
        }
    }

    private Object lookupSimpleForm(InitialContext ic, Class<?> c, String name) throws NamingException {
        String jndiName = "java:module/" + name;
        return ic.lookup(jndiName);
    }

    private Object lookupFullyQualfiedForm(InitialContext ic, Class<?> c, String name) throws NamingException {
        String jndiName = "java:module/" + name + "!" + c.getName();
        return ic.lookup(jndiName);
    }

}
