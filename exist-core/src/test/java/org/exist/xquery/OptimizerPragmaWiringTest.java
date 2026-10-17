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

import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.util.ExpressionDumper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Structural regression test for <a href="https://github.com/eXist-db/exist/issues/6759">GH-6759</a>:
 * the optimizer must wire an {@code (#exist:optimize#)} pragma into the compiled tree for a
 * filtered expression nested inside a typed function argument.
 *
 * <p>{@link Function#checkArgument} wraps an argument in a runtime check
 * ({@link DynamicCardinalityCheck} and siblings) <em>after</em> {@code analyze()} has fixed the
 * argument's parent, which used to make {@code Function#replace} miss it and the pragma silently
 * not get attached. Query results are identical either way, so result-count assertions cannot
 * catch this; only the shape of the compiled tree (or wall-clock time) can. This test asserts
 * the tree, which is deterministic.
 *
 * <p>{@code c = 'x'} is used as the {@link Optimizable} predicate because the optimizer decides
 * on the predicate's axis alone (a child step is eligible, {@code .} is not), so no index module is needed to reach the code under test.
 */
public class OptimizerPragmaWiringTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final Pattern OPTIMIZE_PRAGMA = Pattern.compile("\\(#\\s*\\S*optimize\\b");

    private static final String SOURCE = "collection('/db')//b";

    @Test
    public void directFormIsOptimized() throws Exception {
        assertPragmaCount(1, "count(" + SOURCE + "[c = 'x'])");
    }

    @Test
    public void letBoundFilterInsideTypedArgumentIsOptimized() throws Exception {
        assertPragmaCount(1, "let $a := " + SOURCE + " return count($a[c = 'x'])");
    }

    /**
     * {@code fn:sum} takes {@code xs:anyAtomicType*}, so its argument is wrapped in several
     * layers: {@code UntypedValueCheck(Atomize(DynamicCardinalityCheck(...)))}.
     */
    @Test
    public void letBoundFilterInsideMultiLevelAtomizingWrapperIsOptimized() throws Exception {
        assertPragmaCount(1, "let $a := " + SOURCE + " return sum($a[c = 'x'])");
    }

    /**
     * {@code fn:in-scope-prefixes} takes exactly one {@code element()}, so its argument is
     * wrapped as {@code DynamicNameCheck(DynamicCardinalityCheck(...))}.
     */
    @Test
    public void letBoundFilterInsideMultiLevelElementWrapperIsOptimized() throws Exception {
        assertPragmaCount(1, "let $a := " + SOURCE + " return in-scope-prefixes($a[c = 'x'])");
    }

    @Test
    public void letBoundFilterInSequenceIsOptimized() throws Exception {
        assertPragmaCount(2, "let $a := " + SOURCE + " return ($a[c = 'x'], $a[c = 'y'])");
    }

    private static void assertPragmaCount(final int expected, final String query) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final String dump;
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQueryContext context = new XQueryContext(pool);
            final CompiledXQuery compiled = pool.getXQueryService().compile(broker, context, new StringSource(query));
            dump = ExpressionDumper.dump((Expression) compiled);
        }
        final long actual = OPTIMIZE_PRAGMA.matcher(dump).results().count();

        assertEquals(expected, actual,
                "Unexpected number of (#exist:optimize#) pragmas in the compiled tree of: " + query + "\n" + dump);
    }
}
