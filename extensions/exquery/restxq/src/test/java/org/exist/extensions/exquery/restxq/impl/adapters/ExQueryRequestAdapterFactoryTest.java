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
package org.exist.extensions.exquery.restxq.impl.adapters;

import jakarta.servlet.http.HttpServletRequest;
import org.exist.http.ExQueryRequestAdapterFactory;
import org.exquery.http.HttpRequest;
import org.exquery.http.HttpMethod;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the {@link ExQueryRequestAdapterFactory} SPI wiring that
 * {@code org.exist.http.RESTServer} relies on to expose the EXQuery request module.
 *
 * <p>{@code RESTServer} swallows a failed or empty lookup (it only logs at debug
 * level), so a misnamed {@code META-INF/services} file would silently disable the
 * request module without failing any other test. This test fails instead.
 */
class ExQueryRequestAdapterFactoryTest {

    @Test
    void factoryIsDiscoveredViaServiceLoader() {
        final boolean found = ServiceLoader.load(ExQueryRequestAdapterFactory.class,
                        ExQueryRequestAdapterFactory.class.getClassLoader())
                .stream()
                .anyMatch(provider -> provider.type() == HttpServletRequestAdapter.Factory.class);

        assertTrue(found, "HttpServletRequestAdapter.Factory must be registered in "
                + "META-INF/services/org.exist.http.ExQueryRequestAdapterFactory");
    }

    @Test
    void factoryAdaptsServletRequest() {
        final ExQueryRequestAdapterFactory factory = ServiceLoader.load(ExQueryRequestAdapterFactory.class,
                        ExQueryRequestAdapterFactory.class.getClassLoader())
                .findFirst()
                .orElseThrow();

        final HttpRequest adapted = factory.adapt(servletRequest("GET", "https"), () -> null);

        assertInstanceOf(HttpServletRequestAdapter.class, adapted);
        assertEquals(HttpMethod.GET, adapted.getMethod());
        assertEquals("https", adapted.getScheme());
    }

    private static HttpServletRequest servletRequest(final String method, final String scheme) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "getMethod" -> method;
                    case "getScheme" -> scheme;
                    default -> throw new UnsupportedOperationException(m.getName());
                });
    }
}
