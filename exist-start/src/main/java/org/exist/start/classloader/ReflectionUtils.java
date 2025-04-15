/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.start.classloader;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectionUtils {
    /**
     * Invokes the main method of a specified class using the provided class loader and arguments.
     * <p>
     * This method dynamically loads a class by its name, retrieves its {@code main} method,
     * and then executes it with the given arguments. It is commonly used to launch a Java
     * application from another Java program.
     *
     * @param classloader the {@link ClassLoader} to use for loading the class
     * @param classname   the fully qualified name of the class containing the main method
     * @param args        an array of {@code String} arguments to pass to the main method
     * @throws IllegalAccessException    if the main method is inaccessible
     * @throws InvocationTargetException if an error occurs during the invocation of the main method
     * @throws NoSuchMethodException     if the main method cannot be found in the specified class
     * @throws ClassNotFoundException    if the specified class cannot be located using the given class loader
     */
    public static void invokeMain(final ClassLoader classloader, final String classname, final String[] args)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException, ClassNotFoundException {

        final Class<?> invoked_class = classloader.loadClass(classname);

        final Class<?>[] method_param_types = new Class[1];
        method_param_types[0] = args.getClass();

        final Method main = invoked_class.getDeclaredMethod("main", method_param_types);

        final Object[] method_params = new Object[1];
        method_params[0] = args;
        main.invoke(null, method_params);
    }

    /**
     * Obtains an instance of {@link EXistClassLoader}, sets it as the context class loader
     * of the current thread, and returns it.
     * <p>
     * This method creates a new instance of the {@link Classpath} class, retrieves an
     * {@link EXistClassLoader} using the classpath, and assigns it as the thread's context
     * class loader to ensure proper class loading behavior.
     *
     * @return an initialized {@link EXistClassLoader} instance that becomes the context
     * class loader of the current thread
     */
    public static EXistClassLoader getEXistClassLoader() {
        final Classpath _classpath = new Classpath();
        final EXistClassLoader eXistClassLoader = _classpath.getClassLoader(null);
        Thread.currentThread().setContextClassLoader(eXistClassLoader);
        return eXistClassLoader;
    }
}
