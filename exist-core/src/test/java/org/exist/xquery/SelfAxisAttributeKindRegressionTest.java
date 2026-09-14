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
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertEquals;

/**
 * Regression test for issue #6689. A named attribute kind test on the self axis,
 * such as {@code self::attribute(id)}, was always false on a persistent node.
 *
 * <p>{@code LocationStep.getSelf} looked every non-wildcard test up in the element half of the
 * structural index, so {@code self::attribute(id)} searched for an <em>element</em> named
 * {@code id}, found nothing, and returned the empty sequence without ever consulting the node
 * test. The unnamed and wildcard forms take a different branch, and the attribute axis uses
 * {@code getAttributes}, which selects the correct half — which is why only this one shape
 * failed.</p>
 *
 * <p>Each check is paired with the in-memory result for the same expression, since the in-memory
 * path was always correct and defines the expected answer.</p>
 */
public class SelfAxisAttributeKindRegressionTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer =
            new ExistXmldbEmbeddedServer(false, true, true);

    private static final String TEST_DOC = "/db/i6689.xml";

    private static final String PROLOG = "xquery version \"3.1\"; declare namespace t=\"urn:example\"; ";
    private static final String PERSISTENT = "let $n := doc('" + TEST_DOC + "')/t:root/t:target ";
    private static final String IN_MEMORY =
            "let $n := (<t:root xmlns:t=\"urn:example\"><t:target id=\"1\" t:qualified=\"2\"/></t:root>)/t:target ";

    @BeforeClass
    public static void storeTestDocument() throws XMLDBException {
        query("""
                xmldb:store('/db', 'i6689.xml',
                    <t:root xmlns:t="urn:example"><t:target id="1" t:qualified="2"/></t:root>)
                """);
    }

    @AfterClass
    public static void removeTestDocument() throws XMLDBException {
        query("xmldb:remove('/db', 'i6689.xml')");
    }

    private static String query(final String xquery) throws XMLDBException {
        final XQueryService xqs = existEmbeddedServer.getRoot().getService(XQueryService.class);
        final ResourceSet result = xqs.query(xquery);
        return result.getSize() == 0 ? "" : result.getResource(0).getContent().toString();
    }

    private static String count(final String binding, final String expr) throws XMLDBException {
        return query(PROLOG + binding + "return count(" + expr + ")");
    }

    /** The reported failure: a named attribute kind test on the self axis. */
    @Test
    public void namedAttributeKindTestOnSelfAxis() throws XMLDBException {
        assertEquals("1", count(IN_MEMORY, "$n/@*[self::attribute(id)]"));
        assertEquals("1", count(PERSISTENT, "$n/@*[self::attribute(id)]"));
    }

    /** The same test on an attribute in a namespace. */
    @Test
    public void namespacedAttributeKindTestOnSelfAxis() throws XMLDBException {
        assertEquals("1", count(IN_MEMORY, "$n/@*[self::attribute(t:qualified)]"));
        assertEquals("1", count(PERSISTENT, "$n/@*[self::attribute(t:qualified)]"));
    }

    /** A name that matches no attribute must still return nothing. */
    @Test
    public void nonMatchingAttributeKindTestOnSelfAxis() throws XMLDBException {
        assertEquals("0", count(IN_MEMORY, "$n/@*[self::attribute(absent)]"));
        assertEquals("0", count(PERSISTENT, "$n/@*[self::attribute(absent)]"));
    }

    /** The context step being a named attribute step rather than {@code @*} must not matter. */
    @Test
    public void namedAttributeKindTestUnderNamedContextStep() throws XMLDBException {
        assertEquals("1", count(PERSISTENT, "$n/@id[self::attribute(id)]"));
    }

    /** Nor must binding the attributes to a variable first. */
    @Test
    public void namedAttributeKindTestOnBoundVariable() throws XMLDBException {
        assertEquals("1", query(PROLOG + PERSISTENT
                + "let $a := $n/@* return count($a[self::attribute(id)])"));
    }

    /** Unnamed and wildcard attribute kind tests were never broken; pin them. */
    @Test
    public void unnamedAndWildcardAttributeKindTestsOnSelfAxis() throws XMLDBException {
        assertEquals("2", count(PERSISTENT, "$n/@*[self::attribute()]"));
        assertEquals("2", count(PERSISTENT, "$n/@*[self::attribute(*)]"));
    }

    /** The attribute axis selects the correct index half already; pin that too. */
    @Test
    public void namedAttributeKindTestOnAttributeAxis() throws XMLDBException {
        assertEquals("1", count(PERSISTENT, "$n/attribute::attribute(id)"));
    }

    /** Named element kind and name tests on the self axis must be unaffected. */
    @Test
    public void namedElementTestsOnSelfAxisAreUnaffected() throws XMLDBException {
        assertEquals("1", count(PERSISTENT, "$n/self::t:target"));
        assertEquals("1", count(PERSISTENT, "$n/self::element(t:target)"));
        assertEquals("0", count(PERSISTENT, "$n/self::element(t:absent)"));
    }
}
