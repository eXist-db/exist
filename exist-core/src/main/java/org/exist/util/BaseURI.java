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
package org.exist.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.AnyURIValue;

import java.net.URI;

public class BaseURI {

    private final static Logger LOG = LogManager.getLogger(BaseURI.class);

    /**
     * Convert the location of a resource function into an XML database URI
     * @param uri the location of the resource function
     * @return the input uri with an xmldb:// prefix (if it had no scheme before)
     * if uri has a scheme (is absolute) the original uri is wrapped, unaltered
     */
    public static AnyURIValue dbBaseURIFromLocation(final URI uri) {
        if (uri.getScheme() == null) {
            try {
                return new AnyURIValue(XmldbURI.XMLDB_SCHEME + "://" + uri);
            } catch (XPathException e) {
                LOG.warn("Could not create {} URI from {}", XmldbURI.XMLDB_URI_PREFIX, uri);
                throw new RuntimeException(e);
            }
        }
        // already absolute
        return new AnyURIValue(uri);
    }
}
