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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpSession;

import javax.annotation.Nullable;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * A minimal, mutable {@link HttpServletRequest} for driving {@link XQueryServlet#process} without
 * a servlet container: URI, method, attributes and parameters are plain fields; every other method
 * answers with an empty default ({@code null}, {@code false}, {@code 0}, an empty enumeration or map).
 * <p>
 * Deliberately not a mock: it is cheap enough to be created per invocation in a JMH benchmark
 * ({@code XQueryServletBenchmark} in exist-core-jmh) without the mocking framework dominating the
 * measurement.
 */
public class StubHttpServletRequest extends HttpServletRequestWrapper {

    private static final HttpServletRequest DEFAULTS = (HttpServletRequest) Proxy.newProxyInstance(
            StubHttpServletRequest.class.getClassLoader(),
            new Class<?>[]{HttpServletRequest.class},
            (proxy, method, args) -> defaultValue(method.getReturnType()));

    private final String contextPath;
    private final String requestURI;
    private final Map<String, Object> attributes = new HashMap<>();
    private final Map<String, String[]> parameters = new HashMap<>();
    private String method = "GET";
    @Nullable private String pathTranslated;

    /**
     * @param contextPath the context path, e.g. {@code "/exist"}
     * @param requestURI the full request URI including the context path, e.g. {@code "/exist/apps/x/y.xql"}
     */
    public StubHttpServletRequest(final String contextPath, final String requestURI) {
        super(DEFAULTS);
        this.contextPath = contextPath;
        this.requestURI = requestURI;
    }

    public StubHttpServletRequest method(final String method) {
        this.method = method;
        return this;
    }

    public StubHttpServletRequest pathTranslated(@Nullable final String pathTranslated) {
        this.pathTranslated = pathTranslated;
        return this;
    }

    public StubHttpServletRequest attribute(final String name, final Object value) {
        attributes.put(name, value);
        return this;
    }

    public StubHttpServletRequest parameter(final String name, final String value) {
        parameters.put(name, new String[]{value});
        return this;
    }

    @Override
    public String getContextPath() {
        return contextPath;
    }

    @Override
    public String getRequestURI() {
        return requestURI;
    }

    @Override
    public String getMethod() {
        return method;
    }

    @Override
    @Nullable
    public String getPathTranslated() {
        return pathTranslated;
    }

    @Override
    @Nullable
    public Object getAttribute(final String name) {
        return attributes.get(name);
    }

    @Override
    public void setAttribute(final String name, final Object o) {
        attributes.put(name, o);
    }

    @Override
    public void removeAttribute(final String name) {
        attributes.remove(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(attributes.keySet());
    }

    @Override
    @Nullable
    public String getParameter(final String name) {
        final String[] values = parameters.get(name);
        return values == null ? null : values[0];
    }

    @Override
    @Nullable
    public String[] getParameterValues(final String name) {
        return parameters.get(name);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return parameters;
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(parameters.keySet());
    }

    @Override
    @Nullable
    public HttpSession getSession(final boolean create) {
        return null;
    }

    @Override
    @Nullable
    public HttpSession getSession() {
        return null;
    }

    @Nullable
    private static Object defaultValue(final Class<?> type) {
        if (type == boolean.class) {
            return false;
        } else if (type == int.class) {
            return 0;
        } else if (type == long.class) {
            return 0L;
        } else if (type == Enumeration.class) {
            return Collections.emptyEnumeration();
        } else if (type == Map.class) {
            return Collections.emptyMap();
        }
        return null;
    }
}
