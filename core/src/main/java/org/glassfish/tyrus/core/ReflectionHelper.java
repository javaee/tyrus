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

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Map;

import javax.websocket.DeploymentException;

import org.glassfish.tyrus.core.l10n.LocalizationMessages;

/**
 * Utility methods for Java reflection.
 *
 * @author Paul.Sandoz@Sun.Com
 */
public class ReflectionHelper {

    /**
     * Get declaring class of provided field, method or constructor.
     *
     * @param ao object for which the declared class will be returned.
     * @return declaring class of provided object.
     */
    public static Class getDeclaringClass(AccessibleObject ao) {
        if (ao instanceof Method) {
            return ((Method) ao).getDeclaringClass();
        } else if (ao instanceof Field) {
            return ((Field) ao).getDeclaringClass();
        } else if (ao instanceof Constructor) {
            return ((Constructor) ao).getDeclaringClass();
        } else {
            throw new RuntimeException();
        }
    }

    /**
     * Create a string representation of an object.
     * <p>
     * Returns a string consisting of the name of the class of which the
     * object is an instance, the at-sign character '<code>@</code>', and
     * the unsigned hexadecimal representation of the hash code of the
     * object. In other words, this method returns a string equal to the
     * value of:
     * <blockquote>
     * <pre>
     * o.getClass().getName() + '@' + Integer.toHexString(o.hashCode())
     * </pre></blockquote>
     *
     * @param o the object.
     * @return the string representation of the object.
     */
    public static String objectToString(Object o) {
        if (o == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(o.getClass().getName()).append('@').append(Integer.toHexString(o.hashCode()));
        return sb.toString();
    }

    /**
     * Create a string representation of a method and an instance whose
     * class implements the method.
     * <p>
     * Returns a string consisting of the name of the class of which the object
     * is an instance, the at-sign character '<code>@</code>',
     * the unsigned hexadecimal representation of the hash code of the
     * object, the character '<code>.</code>', the name of the method,
     * the character '<code>(</code>', the list of method parameters, and
     * the character '<code>)</code>'. In other words, thos method returns a
     * string equal to the value of:
     * <blockquote>
     * <pre>
     * o.getClass().getName() + '@' + Integer.toHexString(o.hashCode()) +
     * '.' + m.getName() + '(' + &lt;parameters&gt; + ')'.
     * </pre></blockquote>
     *
     * @param o the object whose class implements <code>m</code>.
     * @param m the method.
     * @return the string representation of the method and instance.
     */
    public static String methodInstanceToString(Object o, Method m) {
        StringBuilder sb = new StringBuilder();
        sb.append(o.getClass().getName()).append('@').append(Integer.toHexString(o.hashCode())).append('.')
          .append(m.getName()).append('(');

        Class[] params = m.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            sb.append(getTypeName(params[i]));
            if (i < (params.length - 1)) {
                sb.append(",");
            }
        }

        sb.append(')');

        return sb.toString();
    }

    /**
     * @param type
     * @return
     */
    private static String getTypeName(Class type) {
        if (type.isArray()) {
            try {
                Class cl = type;
                int dimensions = 0;
                while (cl.isArray()) {
                    dimensions++;
                    cl = cl.getComponentType();
                }
                StringBuilder sb = new StringBuilder();
                sb.append(cl.getName());
                for (int i = 0; i < dimensions; i++) {
                    sb.append("[]");
                }
                return sb.toString();
            } catch (Throwable e) { /*FALLTHRU*/ }
        }
        return type.getName();
    }

    /**
     * Get the Class from the class name.
     * <p>
     * The context class loader will be utilized if accessible and non-null.
     * Otherwise the defining class loader of this class will
     * be utilized.
     *
     * @param name the class name.
     * @return the Class, otherwise null if the class cannot be found.
     */
    public static Class classForName(String name) {
        return classForName(name, getContextClassLoader());
    }

    /**
     * Get the Class from the class name.
     *
     * @param name the class name.
     * @param cl   the class loader to use, if null then the defining class loader
     *             of this class will be utilized.
     * @return the Class, otherwise null if the class cannot be found.
     */
    public static Class classForName(String name, ClassLoader cl) {
        if (cl != null) {
            try {
                return Class.forName(name, false, cl);
            } catch (ClassNotFoundException ex) {
            }
        }
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ex) {
        }

        return null;
    }

    /**
     * Get the Class from the class name.
     * <p>
     * The context class loader will be utilized if accessible and non-null.
     * Otherwise the defining class loader of this class will
     * be utilized.
     *
     * @param name the class name.
     * @return the Class, otherwise null if the class cannot be found.
     * @throws ClassNotFoundException if the class cannot be found.
     */
    public static Class classForNameWithException(String name) throws ClassNotFoundException {
        return classForNameWithException(name, getContextClassLoader());
    }

    /**
     * Get the Class from the class name.
     *
     * @param name the class name.
     * @param cl   the class loader to use, if null then the defining class loader
     *             of this class will be utilized.
     * @return the Class, otherwise null if the class cannot be found.
     * @throws ClassNotFoundException if the class cannot be found.
     */
    public static Class classForNameWithException(String name, ClassLoader cl) throws ClassNotFoundException {
        if (cl != null) {
            try {
                return Class.forName(name, false, cl);
            } catch (ClassNotFoundException ex) {
            }
        }
        return Class.forName(name);
    }

    /**
     * Get privileged exception action to obtain Class from given class name.
     * If run using security manager, the returned privileged exception action
     * must be invoked within a doPrivileged block.
     * <p>
     * The actual context class loader will be utilized if accessible and non-null.
     * Otherwise the defining class loader of the calling class will be utilized.
     *
     * @param <T>  class type.
     * @param name class name.
     * @return privileged exception action to obtain the Class.
     * The action could throw {@link ClassNotFoundException} or return {@code null} if the class cannot be found.
     * @throws ClassNotFoundException when provided string contains classname of unknown class.
     * @see AccessController#doPrivileged(java.security.PrivilegedExceptionAction)
     */
    public static <T> PrivilegedExceptionAction<Class<T>> classForNameWithExceptionPEA(final String name) throws
            ClassNotFoundException {
        return classForNameWithExceptionPEA(name, getContextClassLoader());
    }

    /**
     * Get privileged exception action to obtain Class from given class name.
     * If run using security manager, the returned privileged exception action
     * must be invoked within a doPrivileged block.
     *
     * @param <T>  class type.
     * @param name class name.
     * @param cl   class loader to use, if {@code null} then the defining class loader
     *             of the calling class will be utilized.
     * @return privileged exception action to obtain the Class.
     * The action throws {@link ClassNotFoundException}
     * or returns {@code null} if the class cannot be found.
     * @throws ClassNotFoundException when provided string contains classname of unknown class.
     * @see AccessController#doPrivileged(java.security.PrivilegedExceptionAction)
     */
    @SuppressWarnings("unchecked")
    public static <T> PrivilegedExceptionAction<Class<T>> classForNameWithExceptionPEA(final String name, final
    ClassLoader cl) throws ClassNotFoundException {
        return new PrivilegedExceptionAction<Class<T>>() {
            @Override
            public Class<T> run() throws ClassNotFoundException {
                if (cl != null) {
                    try {
                        return (Class<T>) Class.forName(name, false, cl);
                    } catch (ClassNotFoundException ex) {
                        // ignored on purpose
                    }
                }
                return (Class<T>) Class.forName(name);
            }
        };
    }

    /**
     * Get the context class loader.
     *
     * @return the context class loader, otherwise {@code null} if not set.
     */
    private static ClassLoader getContextClassLoader() {
        return AccessController.doPrivileged(getContextClassLoaderPA());
    }

    /**
     * Get privileged action to obtain context class loader.
     * If run using security manager, the returned privileged action
     * must be invoked within a doPrivileged block.
     *
     * @return privileged action to obtain the actual context class loader.
     * The action could return {@code null} if context class loader has not been set.
     * @see AccessController#doPrivileged(java.security.PrivilegedAction)
     */
    public static PrivilegedAction<ClassLoader> getContextClassLoaderPA() {
        return new PrivilegedAction<ClassLoader>() {
            @Override
            public ClassLoader run() {
                return Thread.currentThread().getContextClassLoader();
            }
        };
    }

    /**
     * Set a method to be accessible.
     *
     * @param m the method to be set as accessible
     */
    public static void setAccessibleMethod(final Method m) {
        if (Modifier.isPublic(m.getModifiers())) {
            return;
        }

        AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                if (!m.isAccessible()) {
                    m.setAccessible(true);
                }
                return m;
            }
        });
    }

    /**
     * Get the class that is the type argument of a parameterized type.
     *
     * @param parameterizedType must be an instance of ParameterizedType
     *                          and have exactly one type argument.
     * @return the class of the actual type argument. If the type argument
     * is a class then the class is returned. If the type argument
     * is a generic array type and the generic component type is a
     * class then class of the array is returned. if the type argument
     * is a parameterized type and it's raw type is a class then
     * that class is returned.
     * If the parameterizedType is not an instance of ParameterizedType
     * or contains more than one type argument null is returned.
     * @throws IllegalArgumentException if the single type argument is not of
     *                                  a class, or a generic array type, or the generic component type
     *                                  of the generic array type is not class, or not a parameterized
     *                                  type with a raw type that is not a class.
     */
    public static Class getGenericClass(Type parameterizedType) throws IllegalArgumentException {
        final Type t = getTypeArgumentOfParameterizedType(parameterizedType);
        if (t == null) {
            return null;
        }

        final Class c = getClassOfType(t);
        if (c == null) {
            throw new IllegalArgumentException("Type not supported");
        }
        return c;
    }

    public static final class TypeClassPair {
        public final Type t;
        public final Class c;

        public TypeClassPair(Type t, Class c) {
            this.t = t;
            this.c = c;
        }
    }

    public static TypeClassPair getTypeArgumentAndClass(Type parameterizedType) throws IllegalArgumentException {
        final Type t = getTypeArgumentOfParameterizedType(parameterizedType);
        if (t == null) {
            return null;
        }

        final Class c = getClassOfType(t);
        if (c == null) {
            throw new IllegalArgumentException("Generic type not supported");
        }

        return new TypeClassPair(t, c);
    }

    private static Type getTypeArgumentOfParameterizedType(Type parameterizedType) {
        if (!(parameterizedType instanceof ParameterizedType)) {
            return null;
        }

        ParameterizedType type = (ParameterizedType) parameterizedType;
        Type[] genericTypes = type.getActualTypeArguments();
        if (genericTypes.length != 1) {
            return null;
        }

        return genericTypes[0];
    }

    private static Class getClassOfType(Type type) {
        if (type instanceof Class) {
            return (Class) type;
        } else if (type instanceof GenericArrayType) {
            GenericArrayType arrayType = (GenericArrayType) type;
            Type t = arrayType.getGenericComponentType();
            if (t instanceof Class) {
                return getArrayClass((Class) t);
            }
        } else if (type instanceof ParameterizedType) {
            ParameterizedType subType = (ParameterizedType) type;
            Type t = subType.getRawType();
            if (t instanceof Class) {
                return (Class) t;
            }
        }
        return null;
    }

    /**
     * Get Array class of component class.
     *
     * @param c the component class of the array
     * @return the array class.
     */
    public static Class getArrayClass(Class c) {
        try {
            Object o = Array.newInstance(c, 0);
            return o.getClass();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Get the static valueOf(String ) method.
     *
     * @param c The class to obtain the method.
     * @return the method, otherwise null if the method is not present.
     */
    @SuppressWarnings("unchecked")
    public static Method getValueOfStringMethod(Class c) {
        try {
            Method m = c.getDeclaredMethod("valueOf", String.class);
            if (!Modifier.isStatic(m.getModifiers()) && m.getReturnType() == c) {
                return null;
            }
            return m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Get the static fromString(String ) method.
     *
     * @param c The class to obtain the method.
     * @return the method, otherwise null if the method is not present.
     */
    @SuppressWarnings("unchecked")
    public static Method getFromStringStringMethod(Class c) {
        try {
            Method m = c.getDeclaredMethod("fromString", String.class);
            if (!Modifier.isStatic(m.getModifiers()) && m.getReturnType() == c) {
                return null;
            }
            return m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Get the constructor that has a single parameter of String.
     *
     * @param c The class to obtain the constructor.
     * @return the constructor, otherwise null if the constructor is not present.
     */
    @SuppressWarnings("unchecked")
    public static Constructor getStringConstructor(Class c) {
        try {
            return c.getConstructor(String.class);
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * A tuple consisting of a concrete class, declaring class that declares a generic interface type.
     */
    public static class DeclaringClassInterfacePair {
        public final Class concreteClass;

        public final Class declaringClass;

        public final Type genericInterface;

        private DeclaringClassInterfacePair(Class concreteClass, Class declaringClass, Type genericInteface) {
            this.concreteClass = concreteClass;
            this.declaringClass = declaringClass;
            this.genericInterface = genericInteface;
        }
    }

    /**
     * Get the parameterized class arguments for a declaring class that declares a generic interface type.
     *
     * @param p the declaring class
     * @return the parameterized class arguments, or null if the generic interface type is not a parameterized type.
     */
    public static Class[] getParameterizedClassArguments(DeclaringClassInterfacePair p) {
        if (p.genericInterface instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) p.genericInterface;
            Type[] as = pt.getActualTypeArguments();
            Class[] cas = new Class[as.length];

            for (int i = 0; i < as.length; i++) {
                Type a = as[i];
                if (a instanceof Class) {
                    cas[i] = (Class) a;
                } else if (a instanceof ParameterizedType) {
                    pt = (ParameterizedType) a;
                    cas[i] = (Class) pt.getRawType();
                } else if (a instanceof TypeVariable) {
                    ClassTypePair ctp = resolveTypeVariable(p.concreteClass, p.declaringClass, (TypeVariable) a);
                    cas[i] = (ctp != null) ? ctp.c : Object.class;
                } else if (a instanceof GenericArrayType) {
                    cas[i] = getClassOfType(a);
                }
            }
            return cas;
        } else {
            return null;
        }
    }

    /**
     * Get the parameterized type arguments for a declaring class that declares a generic interface type.
     *
     * @param p the declaring class
     * @return the parameterized type arguments, or null if the generic interface type is not a parameterized type.
     */
    public static Type[] getParameterizedTypeArguments(DeclaringClassInterfacePair p) {
        if (p.genericInterface instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) p.genericInterface;
            Type[] as = pt.getActualTypeArguments();
            Type[] ras = new Type[as.length];

            for (int i = 0; i < as.length; i++) {
                Type a = as[i];
                if (a instanceof Class) {
                    ras[i] = a;
                } else if (a instanceof ParameterizedType) {
                    pt = (ParameterizedType) a;
                    ras[i] = a;
                } else if (a instanceof TypeVariable) {
                    ClassTypePair ctp = resolveTypeVariable(p.concreteClass, p.declaringClass, (TypeVariable) a);
                    ras[i] = ctp.t;
                }
            }
            return ras;
        } else {
            return null;
        }
    }

    /**
     * Find the declaring class that implements or extends an interface.
     *
     * @param concrete the concrete class than directly or indirectly implements or extends an interface class.
     * @param iface    the interface class.
     * @return the tuple of the declaring class and the generic interface type.
     */
    public static DeclaringClassInterfacePair getClass(Class concrete, Class iface) {
        return getClass(concrete, iface, concrete);
    }

    private static DeclaringClassInterfacePair getClass(Class concrete, Class iface, Class c) {
        Type[] gis = c.getGenericInterfaces();
        DeclaringClassInterfacePair p = getType(concrete, iface, c, gis);
        if (p != null) {
            return p;
        }

        c = c.getSuperclass();
        if (c == null || c == Object.class) {
            return null;
        }

        return getClass(concrete, iface, c);
    }

    private static DeclaringClassInterfacePair getType(Class concrete, Class iface, Class c, Type[] ts) {
        for (Type t : ts) {
            DeclaringClassInterfacePair p = getType(concrete, iface, c, t);
            if (p != null) {
                return p;
            }
        }
        return null;
    }

    private static DeclaringClassInterfacePair getType(Class concrete, Class iface, Class c, Type t) {
        if (t instanceof Class) {
            if (t == iface) {
                return new DeclaringClassInterfacePair(concrete, c, t);
            } else {
                return getClass(concrete, iface, (Class) t);
            }
        } else if (t instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) t;
            if (pt.getRawType() == iface) {
                return new DeclaringClassInterfacePair(concrete, c, t);
            } else {
                return getClass(concrete, iface, (Class) pt.getRawType());
            }
        }
        return null;
    }

    /**
     * A tuple consisting of a class and type of the class.
     */
    public static class ClassTypePair {
        /**
         * The class.
         */
        public final Class c;

        /**
         * The type of the class.
         */
        public final Type t;

        public ClassTypePair(Class c) {
            this(c, c);
        }

        public ClassTypePair(Class c, Type t) {
            this.c = c;
            this.t = t;
        }
    }

    /**
     * Given a type variable resolve the Java class of that variable.
     *
     * @param c  the concrete class from which all type variables are resolved
     * @param dc the declaring class where the type variable was defined
     * @param tv the type variable
     * @return the resolved Java class and type, otherwise null if the type variable could not be resolved
     */
    public static ClassTypePair resolveTypeVariable(Class c, Class dc, TypeVariable tv) {
        return resolveTypeVariable(c, dc, tv, new HashMap<TypeVariable, Type>());
    }

    private static ClassTypePair resolveTypeVariable(Class c, Class dc, TypeVariable tv, Map<TypeVariable, Type> map) {
        Type[] gis = c.getGenericInterfaces();
        for (Type gi : gis) {
            if (gi instanceof ParameterizedType) {
                // process pt of interface
                ParameterizedType pt = (ParameterizedType) gi;
                ClassTypePair ctp = resolveTypeVariable(pt, (Class) pt.getRawType(), dc, tv, map);
                if (ctp != null) {
                    return ctp;
                }
            }
        }

        Type gsc = c.getGenericSuperclass();
        if (gsc instanceof ParameterizedType) {
            // process pt of class
            ParameterizedType pt = (ParameterizedType) gsc;
            return resolveTypeVariable(pt, c.getSuperclass(), dc, tv, map);
        } else if (gsc instanceof Class) {
            return resolveTypeVariable(c.getSuperclass(), dc, tv, map);
        }
        return null;
    }

    private static ClassTypePair resolveTypeVariable(ParameterizedType pt, Class c, Class dc, TypeVariable tv,
                                                     Map<TypeVariable, Type> map) {
        Type[] typeArguments = pt.getActualTypeArguments();

        TypeVariable[] typeParameters = c.getTypeParameters();

        Map<TypeVariable, Type> submap = new HashMap<TypeVariable, Type>();
        for (int i = 0; i < typeArguments.length; i++) {
            // Substitute a type variable with the Java class
            if (typeArguments[i] instanceof TypeVariable) {
                Type t = map.get(typeArguments[i]);
                submap.put(typeParameters[i], t);
            } else {
                submap.put(typeParameters[i], typeArguments[i]);
            }
        }

        if (c == dc) {
            Type t = submap.get(tv);
            if (t instanceof Class) {
                return new ClassTypePair((Class) t);
            } else if (t instanceof GenericArrayType) {
                t = ((GenericArrayType) t).getGenericComponentType();
                if (t instanceof Class) {
                    c = (Class) t;
                    try {
                        return new ClassTypePair(getArrayClass(c));
                    } catch (Exception e) {
                    }
                    return null;
                } else if (t instanceof ParameterizedType) {
                    Type rt = ((ParameterizedType) t).getRawType();
                    if (rt instanceof Class) {
                        c = (Class) rt;
                    } else {
                        return null;
                    }
                    try {
                        return new ClassTypePair(getArrayClass(c), t);
                    } catch (Exception e) {
                        return null;
                    }
                } else {
                    return null;
                }
            } else if (t instanceof ParameterizedType) {
                pt = (ParameterizedType) t;
                if (pt.getRawType() instanceof Class) {
                    return new ClassTypePair((Class) pt.getRawType(), pt);
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } else {
            return resolveTypeVariable(c, dc, tv, submap);
        }
    }

    /**
     * Find a method on a class given an existing method.
     * <p>
     * If there exists a public method on the class that has the same name
     * and parameters as the existing method then that public method is
     * returned.
     * <p>
     * Otherwise, if there exists a public method on the class that has
     * the same name and the same number of parameters as the existing method,
     * and each generic parameter type, in order, of the public method is equal
     * to the generic parameter type, in the same order, of the existing method
     * or is an instance of {@link TypeVariable} then that public method is
     * returned.
     *
     * @param c the class to search for a public method
     * @param m the method to find
     * @return the found public method.
     */
    public static Method findMethodOnClass(Class c, Method m) {
        try {
            return c.getMethod(m.getName(), m.getParameterTypes());
        } catch (NoSuchMethodException ex) {
            for (Method _m : c.getMethods()) {
                if (_m.getName().equals(m.getName()) && _m.getParameterTypes().length == m.getParameterTypes().length) {
                    if (compareParameterTypes(m.getGenericParameterTypes(), _m.getGenericParameterTypes())) {
                        return _m;
                    }
                }
            }
        }
        return null;
    }

    private static boolean compareParameterTypes(Type[] ts, Type[] _ts) {
        for (int i = 0; i < ts.length; i++) {
            if (!ts[i].equals(_ts[i])) {
                if (!(_ts[i] instanceof TypeVariable)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Find a type of the class given it's Superclass.
     *
     * @param inspectedClass Class whose type is searched for.
     * @param superClass     Class relatively to which the search is performed.
     * @return type of the class.
     */
    public static Class<?> getClassType(Class<?> inspectedClass, Class<?> superClass) {
        ReflectionHelper.DeclaringClassInterfacePair p = ReflectionHelper.getClass(inspectedClass, superClass);
        Class[] as = ReflectionHelper.getParameterizedClassArguments(p);
        if (as == null) {
            return null;
        } else {
            return as[0];
        }
    }

    /**
     * Returns an {@link OsgiRegistry} instance.
     *
     * @return an {@link OsgiRegistry} instance or {@code null} if the class cannot be instantiated (not in OSGi
     * environment).
     */
    public static OsgiRegistry getOsgiRegistryInstance() {
        try {
            final Class<?> bundleReferenceClass = Class.forName("org.osgi.framework.BundleReference");

            if (bundleReferenceClass != null) {
                return OsgiRegistry.getInstance();
            }
        } catch (Exception e) {
            // Do nothing - instance is null.
        }

        return null;
    }

    /**
     * Creates an instance of {@link Class} c using {@link Class#newInstance()}. Exceptions are logged to {@link
     * ErrorCollector}.
     *
     * @param c         {@link Class} whose instance is going to be created
     * @param collector {@link ErrorCollector} which collects the {@link Exception}s.
     * @param <T>       type.
     * @return new instance of {@link Class}.
     */
    public static <T> T getInstance(Class<T> c, ErrorCollector collector) {
        T instance = null;

        try {
            instance = getInstance(c);
        } catch (Exception e) {
            collector.addException(new DeploymentException(LocalizationMessages.CLASS_NOT_INSTANTIATED(c.getName()),
                                                           e));
        }

        return instance;
    }

    /**
     * Creates an instance of {@link Class} c using {@link Class#newInstance()}.
     *
     * @param c   {@link Class} whose instance is going to be created
     * @param <T> type.
     * @return new instance of {@link Class}.
     * @throws IllegalAccessException if the class or its nullary
     *                                constructor is not accessible.
     * @throws InstantiationException if this {@code Class} represents an abstract class,
     *                                an interface, an array class, a primitive type, or void;
     *                                or if the class has no nullary constructor;
     *                                or if the instantiation fails for some other reason.
     */
    public static <T> T getInstance(Class<T> c) throws IllegalAccessException, InstantiationException {
        return c.newInstance();
    }
}
