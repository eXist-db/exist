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
 * Regression test for the processing-instruction half of issue #6689, raised in review of the
 * attribute fix.
 *
 * <p>A processing instruction is addressable by name, but the structural index has only an element
 * half and an attribute half — there is nowhere to look one up. {@code LocationStep.getSelf} sent
 * every non-wildcard test to that index, so {@code self::processing-instruction(NAME)} searched for
 * an <em>element</em> of that name and never matched.</p>
 *
 * <p>Comments and text nodes are not affected: they have no name, so their tests never reach the
 * index lookup.</p>
 */
public class SelfAxisProcessingInstructionRegressionTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer =
            new ExistXmldbEmbeddedServer(false, true, true);

    private static final String TEST_DOC = "/db/i6689-pi.xml";

    /** Two processing instructions with different targets, plus an element. */
    private static final String CONTENT = "<root><?target inner?><?other x?><a/></root>";

    private static final String PERSISTENT = "let $r := doc('" + TEST_DOC + "')/root ";
    private static final String IN_MEMORY = "let $r := parse-xml('" + CONTENT + "')/root ";

    @BeforeClass
    public static void storeTestDocument() throws XMLDBException {
        query("xmldb:store('/db', 'i6689-pi.xml', '" + CONTENT + "', 'application/xml')");
    }

    @AfterClass
    public static void removeTestDocument() throws XMLDBException {
        query("xmldb:remove('/db', 'i6689-pi.xml')");
    }

    private static String query(final String xquery) throws XMLDBException {
        final XQueryService xqs = existEmbeddedServer.getRoot().getService(XQueryService.class);
        final ResourceSet result = xqs.query(xquery);
        return result.getSize() == 0 ? "" : result.getResource(0).getContent().toString();
    }

    private static String count(final String binding, final String expr) throws XMLDBException {
        return query(binding + "return count(" + expr + ")");
    }

    /** The reported shape: a named processing-instruction test on the self axis. */
    @Test
    public void namedProcessingInstructionTestOnSelfAxis() throws XMLDBException {
        assertEquals("1", count(IN_MEMORY, "$r/node()[self::processing-instruction('target')]"));
        assertEquals("1", count(PERSISTENT, "$r/node()[self::processing-instruction('target')]"));
    }

    /** A target matching no processing instruction must return nothing. */
    @Test
    public void nonMatchingProcessingInstructionTestOnSelfAxis() throws XMLDBException {
        assertEquals("0", count(IN_MEMORY, "$r/node()[self::processing-instruction('absent')]"));
        assertEquals("0", count(PERSISTENT, "$r/node()[self::processing-instruction('absent')]"));
    }

    /** The unnamed form must select both processing instructions and nothing else. */
    @Test
    public void unnamedProcessingInstructionTestOnSelfAxis() throws XMLDBException {
        assertEquals("2", count(IN_MEMORY, "$r/node()[self::processing-instruction()]"));
        assertEquals("2", count(PERSISTENT, "$r/node()[self::processing-instruction()]"));
    }

    /** In step position rather than predicate position. */
    @Test
    public void namedProcessingInstructionTestAsStep() throws XMLDBException {
        assertEquals("1", count(PERSISTENT, "$r/node()/self::processing-instruction('target')"));
        assertEquals("2", count(PERSISTENT, "$r/node()/self::processing-instruction()"));
    }

    /** The other named-node kinds on the same axis must be unaffected. */
    @Test
    public void namedElementAndAttributeTestsAreUnaffected() throws XMLDBException {
        assertEquals("1", count(PERSISTENT, "$r/node()[self::element(a)]"));
        assertEquals("0", count(PERSISTENT, "$r/node()[self::element(zz)]"));
    }
}
