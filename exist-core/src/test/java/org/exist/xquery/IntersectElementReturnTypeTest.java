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
package org.exist.xquery;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import static org.junit.Assert.assertEquals;

/**
 * Regression tests for issue #4255 — DynamicTypeCheck was comparing a DOM
 * {@code short} node type (e.g. {@code Node.ELEMENT_NODE = 1}) against an
 * eXist {@link org.exist.xquery.value.Type} int ({@code Type.ELEMENT = 60})
 * for persistent {@code NodeProxy} items with an {@code UNKNOWN_NODE_TYPE}.
 * That surfaced through {@code intersect} on persistent node sets when the
 * result was assigned to a function declared {@code as element()}.
 */
public class IntersectElementReturnTypeTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer embedded =
            new ExistXmldbEmbeddedServer(false, true, true);

    @BeforeClass
    public static void store() throws XMLDBException {
        embedded.executeQuery(
                "xmldb:store('/db', 'issue4255.xml', <root><x/><y/><z/></root>)");
    }

    @AfterClass
    public static void cleanup() throws XMLDBException {
        try {
            embedded.executeQuery("xmldb:remove('/db', 'issue4255.xml')");
        } catch (final XMLDBException ignored) {
        }
    }

    @Test
    public void persistentIntersectAgainstElementReturnType() throws XMLDBException {
        final String query = """
                declare function local:f() as element() {
                    let $root := doc('/db/issue4255.xml')/root
                    let $seq1 := ($root/x, $root/y)
                    let $seq2 := ($root/y, $root/z)
                    return ($seq1 intersect $seq2)
                };
                local:f()
                """;
        final ResourceSet rs = embedded.executeQuery(query);
        assertEquals("<y/>", rs.getResource(0).getContent());
    }

    @Test
    public void inMemoryIntersectAgainstElementReturnType() throws XMLDBException {
        final String query = """
                declare function local:f() as element() {
                    let $root := <root><x/><y/><z/></root>
                    let $seq1 := ($root/x, $root/y)
                    let $seq2 := ($root/y, $root/z)
                    return ($seq1 intersect $seq2)
                };
                local:f()
                """;
        final ResourceSet rs = embedded.executeQuery(query);
        assertEquals("<y/>", rs.getResource(0).getContent());
    }

    @Test
    public void persistentUnionAgainstElementReturnType() throws XMLDBException {
        // Sanity: same fix path should keep union working
        final String query = """
                declare function local:f() as element()+ {
                    let $root := doc('/db/issue4255.xml')/root
                    let $seq1 := ($root/x, $root/y)
                    let $seq2 := ($root/y, $root/z)
                    return ($seq1 union $seq2)
                };
                string-join(local:f() ! name(), ',')
                """;
        final ResourceSet rs = embedded.executeQuery(query);
        assertEquals("x,y,z", rs.getResource(0).getContent());
    }

    @Test
    public void persistentExceptAgainstElementReturnType() throws XMLDBException {
        // Sanity: same fix path should keep except working
        final String query = """
                declare function local:f() as element() {
                    let $root := doc('/db/issue4255.xml')/root
                    let $seq1 := ($root/x, $root/y)
                    let $seq2 := ($root/y, $root/z)
                    return ($seq1 except $seq2)
                };
                local:f()
                """;
        final ResourceSet rs = embedded.executeQuery(query);
        assertEquals("<x/>", rs.getResource(0).getContent());
    }
}
