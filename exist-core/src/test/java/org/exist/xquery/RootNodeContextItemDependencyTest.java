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
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import static org.junit.Assert.assertEquals;

/**
 * Regression test for RootNode dependency declaration.
 *
 * <p>{@link RootNode} represents the lone-slash root-step ({@code /}). Without
 * declaring {@link Dependency#CONTEXT_ITEM} the optimizer's predicate hoister
 * treats expressions containing only {@code /} as context-independent and
 * evaluates them once outside the iteration context. With no context item to
 * resolve against, {@code RootNode.eval} falls through to the static-context
 * default (all statically known documents) and returns whatever the broker is
 * allowed to see — including {@code /db/system/security} content for admin
 * users.</p>
 *
 * <p>These XPath cases come from W3C qt3tests {@code prod-PathExpr/PathExpr-1},
 * {@code -2}, {@code -15} (Nicolae Brinza, 2009): "Leading lone slash syntax
 * constraints". Per the spec, with the bid element as context item, {@code /}
 * resolves to its document node; arithmetic produces a single numeric value;
 * the positional predicate has no match; count is zero.</p>
 */
public class RootNodeContextItemDependencyTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer =
            new ExistXmldbEmbeddedServer(false, true, true);

    /**
     * {@code 5 * /} inside a positional predicate. With proper context flow,
     * {@code /} resolves to the bid element's owner document (single item),
     * {@code 5 * <bid>23</bid>} atomizes to {@code 5 * 23 = 115}, and the
     * positional predicate {@code [115]} matches nothing.
     */
    @Test
    public void loneSlashInArithmeticPredicateMultiplyOnRight() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "let $ctx := document { <bid>23</bid> }/bid return fn:count($ctx[5 * /])");
        assertEquals(1, result.getSize());
        assertEquals("0", result.getResource(0).getContent());
    }

    /**
     * Mirror of the above with the operands swapped — {@code (/) * 5}.
     */
    @Test
    public void loneSlashInArithmeticPredicateMultiplyOnLeft() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "let $ctx := document { <bid>23</bid> }/bid return fn:count($ctx[(/) * 5])");
        assertEquals(1, result.getSize());
        assertEquals("0", result.getResource(0).getContent());
    }

    /**
     * Without the dependency fix, {@code /} in a hoisted predicate falls
     * through to the static-context default — for an admin-level broker
     * (the default in this test setup), that historically included
     * {@code /db/system/security/exist/accounts/admin.xml}. Confirm the
     * single-item document-node return path, not the multi-doc fallback,
     * is the one that runs.
     */
    @Test
    public void loneSlashInPredicateResolvesToSingleOwnerDocument() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "let $ctx := document { <bid>23</bid> }/bid " +
                "return fn:count($ctx[fn:count(/) eq 1])");
        assertEquals(1, result.getSize());
        assertEquals("1", result.getResource(0).getContent());
    }
}
