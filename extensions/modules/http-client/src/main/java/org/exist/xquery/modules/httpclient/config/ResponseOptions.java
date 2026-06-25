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

/**
 * Immutable response-handling options parsed from an {@code <http:request>} element.
 *
 * <p>These fields control how the HTTP response is interpreted and returned,
 * as defined by the EXPath HTTP Client specification.</p>
 *
 * @param statusOnly        whether to return only the status code and headers, omitting the body
 * @param overrideMediaType an optional media type to use when parsing the response body
 */
public record ResponseOptions(
        boolean statusOnly,
        String overrideMediaType
) {
    /**
     * Default response options: return full body, no media-type override.
     */
    public static final ResponseOptions DEFAULTS = new ResponseOptions(false, null);
}
