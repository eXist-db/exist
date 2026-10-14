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
package org.exist.xquery.modules.range;

import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A predicate inside a "for" loop, comparing against the loop variable, is rewritten into a
 * range index lookup. The lookup must not be evaluated once per item of the path's context
 * sequence, otherwise each iteration of the loop is quadratic in the size of the collection.
 *
 * @see <a href="https://github.com/eXist-db/exist/issues/6619">Loop-invariant collection() inside a for loop not hoisted</a>
 */
public class LoopVariableLookupRegressionTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final int N_DOCS = 4000;
    private static final int N_LOOKUPS = 20;

    private static final String QUERY = """
            count(
                for $id in (1 to %d) ! ('id-' || . * 100)
                return collection('/db/issue-6619')/item[id = $id]
            )
            """.formatted(N_LOOKUPS);

    private static final String UNOPTIMIZED_QUERY = "declare option exist:optimize 'enable=no'; " + QUERY;

    @BeforeAll
    public static void storeDocuments() throws Exception {
        // no collection.xconf: as in the issue, the collection is not indexed
        execute("""
                let $_ := xmldb:create-collection('/db', 'issue-6619')
                return count(
                    for $i in 1 to %d
                    return xmldb:store('/db/issue-6619', 'doc' || $i || '.xml', <item><id>id-{$i}</id></item>)
                )
                """.formatted(N_DOCS));
    }

    @AfterAll
    public static void removeDocuments() throws Exception {
        execute("xmldb:remove('/db/issue-6619')");
    }

    private static Sequence execute(final String query) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            final CompiledXQuery compiled = xquery.compile(new XQueryContext(pool), query);
            return xquery.execute(broker, compiled, null);
        }
    }

    private static long executeMillis(final String query) throws Exception {
        final long start = System.nanoTime();
        execute(query);
        return (System.nanoTime() - start) / 1_000_000L;
    }

    @Test
    public void sameResultWithAndWithoutOptimization() throws Exception {
        assertEquals(String.valueOf(N_LOOKUPS), execute(QUERY).getStringValue());
        assertEquals(String.valueOf(N_LOOKUPS), execute(UNOPTIMIZED_QUERY).getStringValue());
    }

    @Test
    public void optimizedPerformanceCloseToUnoptimized() throws Exception {
        // Warm-up - first run pays parsing and caching costs we don't want to measure.
        execute(QUERY);
        execute(UNOPTIMIZED_QUERY);

        final long unoptimizedMs = executeMillis(UNOPTIMIZED_QUERY);
        final long optimizedMs = executeMillis(QUERY);

        // Pre-fix the optimized query was ~10x slower on this corpus, and the ratio grows
        // with the size of the collection. Threshold is intentionally loose so it
        // tolerates CI variance but still catches a re-regression.
        final long threshold = Math.max(500L, unoptimizedMs * 4L);
        assertTrue(
                optimizedMs <= threshold,
                "optimized=" + optimizedMs + "ms, unoptimized=" + unoptimizedMs
                        + "ms; threshold=" + threshold + "ms (4x unoptimized, min 500ms)");
    }
}
