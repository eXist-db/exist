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
package org.exist.xquery.value;

import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.map.MapType;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@code Sequence.containsReference} must detect an item that is nested inside a map (or array) held by
 * the sequence, not only items held directly. This is the guard that {@code XQueryContext.popLocalVariables}
 * uses (via {@code destroy(context, contextSequence)}) to avoid closing a value that is still part of a
 * result; for a value nested in a returned map it must report {@code true}.
 *
 * <p>Regression for the file-backed binary premature-close bug: the general-purpose value sequences
 * checked only direct reference equality and did not recurse into container items, unlike
 * {@code MapType}/{@code ArrayType}. Each method below fails (the nested item is not detected) without the
 * fix. {@code OrderedValueSequence} and {@code PreorderedValueSequence} receive the same one-line fix; they
 * are not constructed directly here (they require {@code OrderSpec} scaffolding) but share the identical
 * recursion.</p>
 */
public class ContainsReferenceNestedTest {

    @ClassRule
    public static final ExistEmbeddedServer SERVER = new ExistEmbeddedServer(true, true);

    @Test
    public void valueSequenceDetectsItemNestedInMap() throws Exception {
        withMapNesting((map, nested, notNested) -> {
            final ValueSequence vs = new ValueSequence();
            vs.add(map);
            assertTrue(vs.containsReference(nested));
            assertFalse(vs.containsReference(notNested));
        });
    }

    @Test
    public void arrayListValueSequenceDetectsItemNestedInMap() throws Exception {
        withMapNesting((map, nested, notNested) -> {
            final ArrayListValueSequence als = new ArrayListValueSequence();
            als.add(map);
            assertTrue(als.containsReference(nested));
            assertFalse(als.containsReference(notNested));
        });
    }

    @Test
    public void subSequenceDetectsItemNestedInMap() throws Exception {
        withMapNesting((map, nested, notNested) -> {
            final ValueSequence backing = new ValueSequence();
            backing.add(map);
            final SubSequence sub = new SubSequence(1, backing);
            assertTrue(sub.containsReference(nested));
            assertFalse(sub.containsReference(notNested));
        });
    }

    @FunctionalInterface
    private interface MapNestingTest {
        void test(MapType map, StringValue nested, StringValue notNested) throws XPathException;
    }

    /**
     * Builds a {@code map { "data": $nested }} in a fresh context and runs {@code test} with it, plus a
     * sibling {@code notNested} item that the sequence must NOT report as referenced.
     */
    private void withMapNesting(final MapNestingTest test) throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQueryContext context = new XQueryContext(broker.getBrokerPool());
            final StringValue nested = new StringValue("nested-marker");
            final StringValue notNested = new StringValue("not-nested");
            final MapType map = new MapType(context);
            map.add(new StringValue("data"), nested);
            test.test(map, nested, notNested);
        }
    }
}
