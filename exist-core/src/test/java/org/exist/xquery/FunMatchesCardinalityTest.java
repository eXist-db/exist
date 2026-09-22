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
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@code fn:matches} is declared {@code matches($input as xs:string?, ...)}, so supplying more
 * than one item is a type error.
 *
 * <p>eXist used to accept a sequence and silently test only its first item, because
 * {@code Sequence.getStringValue()} returns the first item's value. That is how
 * {@code matches(('x','a'), 'a')} came to answer {@code false}: it tested {@code 'x'}. Reported as
 * issue 59 in 2013.</p>
 *
 * <p>What is fixed here is the path that evaluates the argument as a value. Inside a predicate on
 * persistent nodes, {@code fn:matches} still behaves existentially -- {@code //e[matches(v,'a')]}
 * selects rather than raising -- because there the function is an index-backed node filter. That
 * divergence is unchanged by this commit and is tracked separately; the conformant way to write
 * the existential test is {@code some $v in v satisfies matches($v,'a')}, which since the
 * preceding commit reaches the same index.</p>
 */
public class FunMatchesCardinalityTest {

    private static final String COLLECTION_NAME = "matches-cardinality-test";
    private static final String DOC_NAME = "fixture.xml";

    private static final String FIXTURE_XML = """
            <data>
              <entry><val>a</val></entry>
              <entry><val>x</val><val>a</val></entry>
            </data>
            """;

    @ClassRule
    public static final ExistXmldbEmbeddedServer server =
            new ExistXmldbEmbeddedServer(false, true, true);

    @BeforeClass
    public static void loadFixture() throws XMLDBException {
        final Collection root = server.getRoot();
        final CollectionManagementService cms = root.getService(CollectionManagementService.class);
        final Collection coll = cms.createCollection(COLLECTION_NAME);
        final XMLResource res = coll.createResource(DOC_NAME, XMLResource.class);
        res.setContent(FIXTURE_XML);
        coll.storeResource(res);
    }

    @AfterClass
    public static void cleanup() throws XMLDBException {
        final Collection root = server.getRoot();
        final CollectionManagementService cms = root.getService(CollectionManagementService.class);
        cms.removeCollection(COLLECTION_NAME);
    }

    private ResourceSet query(final String xquery) throws XMLDBException {
        return server.getRoot().getService(XQueryService.class).query(xquery);
    }

    private void assertRaisesTypeError(final String xquery) throws XMLDBException {
        try {
            query(xquery);
            fail("expected XPTY0004 for: " + xquery);
        } catch (final XMLDBException e) {
            final String message = String.valueOf(e.getMessage());
            assertTrue("expected XPTY0004, got: " + message, message.contains("XPTY0004"));
        }
    }

    /** The case from the original report: a literal sequence was silently reduced to its first item. */
    @Test
    public void aLiteralSequenceIsATypeError() throws XMLDBException {
        assertRaisesTypeError("matches(('x','a'), 'a')");
    }

    /** The same holds for a multi-item node sequence evaluated as a value. */
    @Test
    public void aMultiItemNodeSequenceIsATypeError() throws XMLDBException {
        assertRaisesTypeError(
                "let $v := doc('/db/" + COLLECTION_NAME + "/" + DOC_NAME + "')//entry[2]/val return matches($v, 'a')");
    }

    /** A single item, the declared cardinality, still works. */
    @Test
    public void aSingleItemIsFine() throws XMLDBException {
        final ResourceSet rs = query("matches('a', 'a')");
        assertEquals(1, rs.getSize());
        assertEquals("true", rs.getResource(0).getContent().toString());
    }

    /** The empty sequence is permitted by {@code xs:string?} and is not a match. */
    @Test
    public void theEmptySequenceIsFine() throws XMLDBException {
        final ResourceSet rs = query("matches((), 'a')");
        assertEquals(1, rs.getSize());
        assertEquals("false", rs.getResource(0).getContent().toString());
    }

    /** A single node still works, and is not caught by the new check. */
    @Test
    public void aSingleNodeIsFine() throws XMLDBException {
        final ResourceSet rs = query(
                "let $v := doc('/db/" + COLLECTION_NAME + "/" + DOC_NAME + "')//entry[1]/val return matches($v, 'a')");
        assertEquals(1, rs.getSize());
        assertEquals("true", rs.getResource(0).getContent().toString());
    }

    /**
     * The conformant existential spelling, which the optimizer now routes through the index, is
     * unaffected: the bound variable is a single item on each iteration.
     */
    @Test
    public void theQuantifiedSpellingStillWorks() throws XMLDBException {
        final ResourceSet rs = query(
                "doc('/db/" + COLLECTION_NAME + "/" + DOC_NAME + "')//entry[some $v in val satisfies matches($v, 'a')]");
        assertEquals(2, rs.getSize());
    }
}
