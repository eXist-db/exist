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
package org.exist.xquery.modules.httpclient.config;

import java.net.http.HttpClient;

/**
 * Immutable options parsed from an {@code <http:request>} element.
 *
 * <p>All fields correspond directly to attributes on the {@code http:request} element
 * as defined by the EXPath HTTP Client specification.</p>
 */
public record HttpClientOptions(
        boolean followRedirect,
        int timeout,
        HttpClient.Version httpVersion,
        boolean autoAcceptEncoding
) {
    /** Default options: follow redirects, no timeout, HTTP/1.1, auto-accept encoding. */
    public static final HttpClientOptions DEFAULTS = new HttpClientOptions(true, 0, HttpClient.Version.HTTP_1_1, true);
}
