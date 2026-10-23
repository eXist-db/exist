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
package org.exist.http.servlets;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link ServletConfig} with explicit init parameters and a {@link ServletContext} that cannot
 * resolve anything: {@code getRealPath} and {@code getMimeType} return {@code null}, as for a
 * webapp that is not exploded on disk. Used to initialise an {@link XQueryServlet} against an
 * already running embedded database.
 */
public final class StubServletConfig {

    private StubServletConfig() {
    }

    public static ServletConfig create(final Map<String, String> initParameters) {
        final Map<String, String> params = new HashMap<>(initParameters);
        final ServletContext context = (ServletContext) Proxy.newProxyInstance(
                StubServletConfig.class.getClassLoader(),
                new Class<?>[]{ServletContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getInitParameterNames", "getAttributeNames" -> Collections.emptyEnumeration();
                    case "getContextPath" -> "/exist";
                    default -> method.getReturnType() == boolean.class ? Boolean.FALSE
                            : method.getReturnType() == int.class ? 0 : null;
                });
        return (ServletConfig) Proxy.newProxyInstance(
                StubServletConfig.class.getClassLoader(),
                new Class<?>[]{ServletConfig.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getServletContext" -> context;
                    case "getServletName" -> "XQueryServlet";
                    case "getInitParameter" -> params.get((String) args[0]);
                    case "getInitParameterNames" -> (Enumeration<String>) Collections.enumeration(params.keySet());
                    default -> null;
                });
    }

    public static ServletConfig create() {
        return create(Collections.emptyMap());
    }
}
