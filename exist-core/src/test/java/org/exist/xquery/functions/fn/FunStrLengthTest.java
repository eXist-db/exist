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
package org.exist.xquery.functions.fn;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XPathQueryService;

import static org.junit.Assert.assertEquals;

public class FunStrLengthTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer server = new ExistXmldbEmbeddedServer(true, true, true);

    @Test
    public void contextItemWithinPredicate() throws XMLDBException {
        final XPathQueryService queryService = (XPathQueryService) server.getRoot().getService("XQueryService", "1.0");

        ResourceSet results = null;

        // upon empty sequence
        results = queryService.query("()[fn:string-length(.) gt 0]");
        assertEquals(0, results.getSize());

        // upon computed empty sequence
        results = queryService.query("fn:tokenize('', '/')[fn:string-length(.) gt 0]");
        assertEquals(0, results.getSize());

        // upon non-empty sequence
        results = queryService.query("('a', '', 'bc', '', 'def')[fn:string-length(.) gt 0]");
        assertEquals(3, results.getSize());
        assertEquals("a", results.getResource(0).getContent());
        assertEquals("bc", results.getResource(1).getContent());
        assertEquals("def", results.getResource(2).getContent());

        // upon computed non-empty sequence
        results = queryService.query("fn:tokenize('ab/c//def//g//', '/')[fn:string-length(.) gt 0]");
        assertEquals(4, results.getSize());
        assertEquals("ab", results.getResource(0).getContent());
        assertEquals("c", results.getResource(1).getContent());
        assertEquals("def", results.getResource(2).getContent());
        assertEquals("g", results.getResource(3).getContent());
    }


}
