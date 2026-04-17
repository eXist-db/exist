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
package org.exist.http.restxq;

/**
 * Namespace constants for RESTXQ annotations and related namespaces.
 */
public final class RestXqNamespaces {

    /** The RESTXQ annotation namespace. */
    public static final String REST_NS = "http://exquery.org/ns/restxq";

    /** The RESTXQ annotation namespace prefix. */
    public static final String REST_PREFIX = "rest";

    /** The W3C serialization namespace for %output:* annotations. */
    public static final String OUTPUT_NS = "http://www.w3.org/2010/xslt-xquery-serialization";

    /** The output namespace prefix. */
    public static final String OUTPUT_PREFIX = "output";

    /** The HTTP client namespace for response elements. */
    public static final String HTTP_NS = "http://expath.org/ns/http-client";

    /** The HTTP client namespace prefix. */
    public static final String HTTP_PREFIX = "http";

    /** The input processing namespace for %input:* annotations. */
    public static final String INPUT_NS = "http://exquery.org/ns/restxq/input";

    /** The input namespace prefix. */
    public static final String INPUT_PREFIX = "input";

    /** The eXist-db auth annotation namespace. */
    public static final String AUTH_NS = "http://exist-db.org/ns/auth";

    /** The auth namespace prefix. */
    public static final String AUTH_PREFIX = "auth";

    /** The web module namespace (BaseX extension). */
    public static final String WEB_NS = "http://basex.org/modules/web";

    /** The web module namespace prefix. */
    public static final String WEB_PREFIX = "web";

    private RestXqNamespaces() {
        // utility class
    }
}
