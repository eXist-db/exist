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

import org.exist.test.ExistEmbeddedServer;
import org.exist.xmldb.XmldbURI;
import org.openjdk.jmh.annotations.*;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.modules.XQueryService;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for the {@code preceding::*} half of issue #2129.
 *
 * <p>craigberry's #2129 follow-up reproduced position-dependence for the
 * wildcard preceding axis on a 50,000-element flat document: a query at
 * {@code @xml:id='45000'} took roughly twice as long as the same query at
 * {@code @xml:id='25000'}. The K-bounded sliding window in
 * {@code LocationStep.PrecedingFilter} caps the retained match set at K,
 * eliminating the unbounded accumulation that produced the late-position
 * tax.</p>
 *
 * <p>This benchmark exercises the wildcard-vs-sibling and early-vs-late
 * comparisons that the original mixed-purpose JUnit class measured with
 * {@code System.nanoTime} and median-of-N. JMH handles statistical
 * aggregation natively; the correctness assertions live in the
 * companion XQSuite test {@code exist-core/src/test/xquery/preceding-axis.xql}.</p>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class PrecedingAxisBenchmark {

    private static final String LARGE_DOC = "/db/words-large.xml";

    @Param({"5000", "25000", "45000"})
    private int refPosition;

    private ExistEmbeddedServer existServer;
    private Database database;
    private Collection root;
    private XQueryService xqs;

    /**
     * Boots an embedded eXist server, registers the XML:DB driver, and stores
     * a 50,000-element flat words document used by the benchmark queries.
     *
     * @throws Exception if the embedded server fails to start, the database
     *     driver cannot be registered, or the corpus document cannot be stored
     */
    @Setup(Level.Trial)
    public void setUp() throws Exception {
        existServer = new ExistEmbeddedServer(true, true);
        existServer.startDb();

        final Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        database = (Database) cl.getDeclaredConstructor().newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);
        root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        xqs = root.getService(XQueryService.class);

        xqs.query(
                """
                let $words := for $i in (1 to 50000) return <w xml:id="{$i}">{$i}</w>
                return xmldb:store('/db', 'words-large.xml', document {<words>{$words}</words>})
                """);
    }

    /**
     * Removes the corpus document, closes the test collection, and shuts down
     * the embedded server.
     *
     * @throws Exception if removing the corpus document, closing the
     *     collection, or stopping the embedded server fails
     */
    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        try {
            xqs.query("xmldb:remove('/db', 'words-large.xml')");
        } finally {
            root.close();
            DatabaseManager.deregisterDatabase(database);
            existServer.stopDb(true);
        }
    }

    /**
     * Wildcard preceding axis with a positional predicate gated by a self::w
     * filter. Pre-fix this accumulated every preceding match from doc start;
     * post-fix the sliding window caps retention at K=5.
     *
     * @return the result-set size, returned so JMH's blackhole prevents the
     *     call being optimized away
     * @throws Exception if the embedded query fails
     */
    @Benchmark
    public long wildcardPrecedingWithPositionalPredicate() throws Exception {
        return xqs.query(
                ("""
                xquery version "3.1";
                let $w := doc('%s')//w[@xml:id='%d']
                return for $i in (1 to 5) return $w/preceding::*[5 + 1 - $i][self::w]/text()
                """).formatted(LARGE_DOC, refPosition)
        ).getSize();
    }

    /**
     * preceding-sibling::w[K] baseline: walks the persistent sibling chain
     * directly rather than the full preceding axis. Used as a relative
     * lower-bound to interpret the wildcard preceding number.
     *
     * @return the result-set size, returned so JMH's blackhole prevents the
     *     call being optimized away
     * @throws Exception if the embedded query fails
     */
    @Benchmark
    public long precedingSiblingBaseline() throws Exception {
        return xqs.query(
                ("""
                xquery version "3.1";
                let $w := doc('%s')//w[@xml:id='%d']
                return for $i in (1 to 5) return $w/preceding-sibling::w[$i]/text()
                """).formatted(LARGE_DOC, refPosition)
        ).getSize();
    }
}
