/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.config;

import java.lang.reflect.Method;

/**
 * Forward reference resolver universal implementation.
 *
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public class ReferenceImpl<R, O extends Configurable> implements Reference<R, O>, Configurable {

    private R resolver;
    private String methodName;
    private String name;
    private O cached = null;

    public ReferenceImpl(R resolver, String methodName, String name) {
        this.resolver = resolver;
        this.methodName = methodName;
        this.name = name;
    }

    public ReferenceImpl(R resolver, O cached, String name) {
        this.resolver = resolver;
        this.methodName = null;
        this.name = name;
        this.cached = cached;
    }

    @Override
    public String getName() {
        return name;
    }

    @SuppressWarnings("unchecked")
    @Override
    public O resolve() {
        if (cached == null) {
            final Class<? extends Object> clazz = resolver.getClass();

            for (final Method method : clazz.getMethods()) {
                if (method.getName().equals(methodName)
                    && method.getParameterTypes().length == 1
                    && "java.lang.String".equals(method.getParameterTypes()[0].getName())
                    ) {
                    try {
                        cached = (O) method.invoke(resolver, name);
                        break;
                    } catch (final Exception e) {
                        cached = null;
                    }
                }
            }
        }
        return cached;
    }

    @Override
    public R resolver() {
        return resolver;
    }

    @Override
    public boolean isConfigured() {
        final O obj = resolve();
        return obj != null && obj.isConfigured();

    }

    @Override
    public Configuration getConfiguration() {
        final O obj = resolve();
        if (obj == null) return null;

        return obj.getConfiguration();
    }
}
