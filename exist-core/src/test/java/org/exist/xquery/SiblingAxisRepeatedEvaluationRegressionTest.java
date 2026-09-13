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
 * Regression test for issue #6690. An axis step with a name test returned the correct result on
 * its first evaluation and an empty sequence on every evaluation after that, within a single
 * query, whenever the context node carried a predicate context item.
 *
 * <p>{@code LocationStep} caches its structural-index lookup for the whole query execution, and
 * the sibling and preceding/following select methods stamped those cached {@link
 * org.exist.dom.persistent.NodeProxy} objects in place with the matching reference node's
 * context. On the next evaluation a duplicate-suppression guard saw the leftover stamp and
 * skipped every node.</p>
 *
 * <p>Each expression is evaluated four times over the same bound node. The first value in each
 * result is what the unfixed code got right; positions two onwards are what it dropped.</p>
 */
public class SiblingAxisRepeatedEvaluationRegressionTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer =
            new ExistXmldbEmbeddedServer(false, true, true);

    private static final String TEST_DOC = "/db/i6690.xml";

    private static final String PROLOG = "xquery version \"3.1\"; declare namespace t=\"urn:example\"; ";

    /** Bound via a predicate, so the node carries a context item — the condition that triggers the bug. */
    private static final String VIA_PREDICATE =
            "let $n := (doc('" + TEST_DOC + "')//t:key[. = 'K1']/ancestor::t:root//t:target)[1] ";

    /** Bound via a plain path, so the node carries no context item. */
    private static final String VIA_PATH =
            "let $n := (doc('" + TEST_DOC + "')/t:root//t:target)[1] ";

    @BeforeClass
    public static void storeTestDocument() throws XMLDBException {
        query("""
                xmldb:store('/db', 'i6690.xml',
                    <t:root xmlns:t="urn:example">
                        <t:key>K1</t:key>
                        <t:wrap><t:a/><t:target/></t:wrap>
                    </t:root>)
                """);
    }

    @AfterClass
    public static void removeTestDocument() throws XMLDBException {
        query("xmldb:remove('/db', 'i6690.xml')");
    }

    private static String query(final String xquery) throws XMLDBException {
        final XQueryService xqs = existEmbeddedServer.getRoot().getService(XQueryService.class);
        final ResourceSet result = xqs.query(xquery);
        return result.getSize() == 0 ? "" : result.getResource(0).getContent().toString();
    }

    /** Evaluates {@code $n/<step>} four times over the same node, returning e.g. "1,1,1,1". */
    private static String fourTimes(final String binding, final String step) throws XMLDBException {
        return query(PROLOG + binding
                + "return string-join(for $i in 1 to 4 return string(count($n" + step + ")), ',')");
    }

    @Test
    public void precedingSiblingNameTestIsStableAcrossEvaluations() throws XMLDBException {
        assertEquals("1,1,1,1", fourTimes(VIA_PREDICATE, "/preceding-sibling::t:a"));
    }

    @Test
    public void followingSiblingNameTestIsStableAcrossEvaluations() throws XMLDBException {
        assertEquals("1,1,1,1", query(PROLOG
                + "let $n := (doc('" + TEST_DOC + "')//t:key[. = 'K1']/ancestor::t:root//t:a)[1] "
                + "return string-join(for $i in 1 to 4 return string(count($n/following-sibling::t:target)), ',')"));
    }

    @Test
    public void precedingNameTestIsStableAcrossEvaluations() throws XMLDBException {
        assertEquals("1,1,1,1", fourTimes(VIA_PREDICATE, "/preceding::t:a"));
    }

    @Test
    public void followingNameTestIsStableAcrossEvaluations() throws XMLDBException {
        assertEquals("1,1,1,1", query(PROLOG
                + "let $n := (doc('" + TEST_DOC + "')//t:key[. = 'K1']/ancestor::t:root//t:a)[1] "
                + "return string-join(for $i in 1 to 4 return string(count($n/following::t:target)), ',')"));
    }

    /** A node reached without a predicate was never affected; pin it. */
    @Test
    public void precedingSiblingNameTestViaPlainPathIsStable() throws XMLDBException {
        assertEquals("1,1,1,1", fourTimes(VIA_PATH, "/preceding-sibling::t:a"));
    }

    /** The wildcard form takes a different branch and was never affected; pin it. */
    @Test
    public void precedingSiblingWildcardIsStable() throws XMLDBException {
        assertEquals("1,1,1,1", fourTimes(VIA_PREDICATE, "/preceding-sibling::*"));
    }

    /** Other axes were never affected; pin one. */
    @Test
    public void ancestorNameTestIsStable() throws XMLDBException {
        assertEquals("1,1,1,1", fourTimes(VIA_PREDICATE, "/ancestor::t:wrap"));
    }

    /** A name that matches no sibling must stay empty, not become non-empty. */
    @Test
    public void nonMatchingSiblingNameTestStaysEmpty() throws XMLDBException {
        assertEquals("0,0,0,0", fourTimes(VIA_PREDICATE, "/preceding-sibling::t:absent"));
    }
}
