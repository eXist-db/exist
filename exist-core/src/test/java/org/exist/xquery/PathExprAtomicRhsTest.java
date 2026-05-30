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
 * Regression test for PathExpr per-item iteration over atomic-returning steps.
 *
 * <p>XPath 3.1 §3.3.5 mandates that in {@code E1/E2}, E2 is evaluated once
 * <em>for each</em> item produced by E1. eXist's PathExpr.eval previously
 * short-circuited to a single E2 evaluation when E1 was a persistent node-set
 * and E2 did not declare a context dependency — collapsing multiplicity for
 * atomic-returning E2 (literals, etc.).</p>
 *
 * @see <a href="https://github.com/eXist-db/exist/issues/798">Issue #798</a>
 */
public class PathExprAtomicRhsTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer embedded =
            new ExistXmldbEmbeddedServer(false, true, true);

    @BeforeClass
    public static void store() throws XMLDBException {
        embedded.executeQuery(
                "xmldb:store('/db', 'pathexpr-issue798.xml', <a><b/><b/></a>)");
    }

    @AfterClass
    public static void cleanup() throws XMLDBException {
        try {
            embedded.executeQuery("xmldb:remove('/db', 'pathexpr-issue798.xml')");
        } catch (final XMLDBException ignored) {
            // best-effort cleanup
        }
    }

    /** In-memory baseline: //b/3 returns one 3 per b element. */
    @Test
    public void inMemoryAtomicRhsIteratesPerItem() throws XMLDBException {
        final ResourceSet rs = embedded.executeQuery(
                "count((<a><b/><b/></a>)//b/3)");
        assertEquals("2", rs.getResource(0).getContent());
    }

    /** Persistent input must iterate identically to in-memory. The bug. */
    @Test
    public void persistentAtomicRhsIteratesPerItem() throws XMLDBException {
        final ResourceSet rs = embedded.executeQuery(
                "count(doc('/db/pathexpr-issue798.xml')//b/3)");
        assertEquals("2", rs.getResource(0).getContent());
    }

    /** Both forms must return the same sequence content. */
    @Test
    public void inMemoryAndPersistentAgree() throws XMLDBException {
        final ResourceSet inMem = embedded.executeQuery(
                "string-join((for $x in (<a><b/><b/></a>)//b/3 return string($x)), ',')");
        final ResourceSet stored = embedded.executeQuery(
                "string-join((for $x in doc('/db/pathexpr-issue798.xml')//b/3 return string($x)), ',')");
        assertEquals(inMem.getResource(0).getContent(), stored.getResource(0).getContent());
        assertEquals("3,3", stored.getResource(0).getContent());
    }

    /**
     * Sanity: node-axis RHS still de-duplicates as before. Two b elements
     * with the same parent: //b/.. should de-dup to one a element.
     */
    @Test
    public void nodeRhsStillDedupes() throws XMLDBException {
        final ResourceSet rs = embedded.executeQuery(
                "count(doc('/db/pathexpr-issue798.xml')//b/..)");
        assertEquals("1", rs.getResource(0).getContent());
    }
}
