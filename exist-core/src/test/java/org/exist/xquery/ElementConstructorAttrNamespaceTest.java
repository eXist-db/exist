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
import org.junit.jupiter.api.Test;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for QT4 / XQTS 3.1 prod-DirElemContent.namespace
 * Constr-inscope-{1..4}: when a namespaced attribute is copied into a
 * direct element constructor via an enclosed expression, and the prefix
 * the source attribute uses is already bound to a different namespace URI
 * on the new constructor, eXist must rebind the attribute to a freshly
 * generated prefix rather than silently dropping it.
 *
 * <p>XQuery 3.1 §3.9.3.4 (Direct Element Constructors) and §4.16
 * (Copy-Namespaces Declaration). The default copy-namespaces mode is
 * {@code preserve, inherit}: an attribute's own in-scope namespace MUST
 * be preserved on the constructed element.</p>
 */
public class ElementConstructorAttrNamespaceTest {

    @RegisterExtension
    public static final ExistXmldbEmbeddedServer existEmbeddedServer =
            new ExistXmldbEmbeddedServer(false, true, true);

    /** Constr-inscope-3: prefix on the new constructor conflicts with the copied attribute's prefix. */
    @Test
    public void copiedAttributeWithConflictingPrefixIsPreserved() throws XMLDBException {
        final String xquery = """
                for $x in <parent1 xmlns:foo="http://www.example.com/parent1" foo:attr1="attr1"/>
                return <new xmlns:foo="http://www.example.com">{$x//@*:attr1}</new>""";
        final ResourceSet result = existEmbeddedServer.executeQuery(xquery);
        assertEquals(1, result.getSize());
        final String out = result.getResource(0).getContent().toString();
        // The attribute must survive the copy: its namespace URI is
        // http://www.example.com/parent1 and its local name is attr1.
        assertTrue(out.contains(":attr1=\"attr1\""),
                "attribute attr1 must be present in: " + out);
        assertTrue(out.contains("http://www.example.com/parent1"),
                "source namespace must be declared on the new element: " + out);
    }

    /** Constr-inscope-4: two attributes with conflicting prefixes copied via enclosed expr. */
    @Test
    public void multipleCopiedAttributesWithConflictingPrefixesArePreserved() throws XMLDBException {
        final String xquery = """
                for $x in <inscope>
                            <parent1 xmlns:foo="http://www.example.com/parent1" foo:attr1="attr1"/>
                            <parent2 xmlns:foo="http://www.example.com/parent2" foo:attr2="attr2"/>
                          </inscope>
                return <new>{$x//@*:attr1, $x//@*:attr2}</new>""";
        final ResourceSet result = existEmbeddedServer.executeQuery(xquery);
        assertEquals(1, result.getSize());
        final String out = result.getResource(0).getContent().toString();
        assertTrue(out.contains(":attr1=\"attr1\""), "attr1 must be present: " + out);
        assertTrue(out.contains(":attr2=\"attr2\""), "attr2 must be present: " + out);
        assertTrue(out.contains("http://www.example.com/parent1"),
                "parent1 namespace must be declared: " + out);
        assertTrue(out.contains("http://www.example.com/parent2"),
                "parent2 namespace must be declared: " + out);
    }

    /** Simpler reproducer: rename in-scope namespace using a single copied attribute. */
    @Test
    public void copiedAttributeNamespaceRebindsPrefix() throws XMLDBException {
        final String xquery = """
                let $src := <s xmlns:foo="http://example.com/A" foo:k="v"/>
                return <out xmlns:foo="http://example.com/B">{$src/@*}</out>""";
        final ResourceSet result = existEmbeddedServer.executeQuery(xquery);
        assertEquals(1, result.getSize());
        final String out = result.getResource(0).getContent().toString();
        assertTrue(out.contains(":k=\"v\""), "attribute k must be present: " + out);
        assertTrue(out.contains("http://example.com/A"),
                "source URI A must be retained on the constructed element: " + out);
    }
}
