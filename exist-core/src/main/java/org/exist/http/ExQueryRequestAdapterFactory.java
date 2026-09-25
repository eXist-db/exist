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
package org.exist.http;

import jakarta.servlet.http.HttpServletRequest;
import org.exist.util.io.FilterInputStreamCacheFactory.FilterInputStreamCacheConfiguration;
import org.exquery.http.HttpRequest;

/**
 * SPI for the optional EXQuery RestXQ extension ({@code extensions/exquery/restxq}) to plug
 * its {@code HttpServletRequest} -&gt; {@link HttpRequest} adapter into {@link RESTServer},
 * without exist-core taking a compile-time dependency on that extension module's
 * implementation class (exist-core already depends on the {@code org.exquery} API jar for
 * the {@link HttpRequest}/{@link FilterInputStreamCacheConfiguration} types themselves, just
 * not on any concrete adapter).
 *
 * <p>Register an implementation's fully-qualified class name in
 * {@code META-INF/services/org.exist.http.ExQueryRequestAdapterFactory}. At construction,
 * {@link RESTServer} discovers it via {@link java.util.ServiceLoader}; if the RestXQ
 * extension is not on the classpath, no implementation is found and EXQuery request-module
 * support is simply skipped — mirroring how
 * org.exist.storage.vector.VectorExtensionHook lets the optional vector extension
 * hook into broker pool lifecycle the same way, and how
 * org.exist.xquery.ModuleFactory and org.exist.indexing.IndexFactory let optional
 * modules/indexes self-register.
 */
public interface ExQueryRequestAdapterFactory {

    /**
     * Wraps a servlet request as an {@link HttpRequest}, so it can be exposed to XQuery under
     * the {@code exquery-request} context attribute (see
     * {@code org.exist.extensions.exquery.modules.request.RequestModule#EXQ_REQUEST_ATTR} —
     * a fixed string, not read reflectively; {@link RESTServer} duplicates the literal rather
     * than depending on that module too).
     *
     * @param request the incoming servlet request
     * @param cacheConfiguration binary cache configuration for buffering the request body
     * @return the adapted request
     */
    HttpRequest adapt(HttpServletRequest request, FilterInputStreamCacheConfiguration cacheConfiguration);
}
