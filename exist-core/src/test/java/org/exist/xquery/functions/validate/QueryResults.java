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
package org.exist.xquery.functions.validate;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What the validation tests share: run a query that is expected to return one
 * item and hand back its serialized content for the test to assert on.
 */
final class QueryResults {

    private QueryResults() {
    }

    static String single(final ExistXmldbEmbeddedServer server, final String query) throws XMLDBException {
        final ResourceSet results = server.executeQuery(query);
        assertEquals(1, results.getSize());

        return (String) results.getResource(0).getContent();
    }
}
