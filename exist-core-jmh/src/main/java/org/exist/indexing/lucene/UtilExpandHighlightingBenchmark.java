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
package org.exist.indexing.lucene;

import org.exist.EXistException;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.CompiledXQuery;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for {@code util:expand} match-highlighting cost against Lucene full-text
 * hits, migrated from the perf-ratio assertions in the {@code UtilExpandHighlightingPerformanceTest}
 * that PR #6318 initially added and then removed (see #6387).
 *
 * <p>PR #6318 fixed <a href="https://github.com/eXist-db/exist/issues/5738">#5738</a>: {@code
 * util:expand} with match-highlighting on was orders of magnitude slower than with highlighting
 * off, because {@link LuceneMatchListener#reset(org.exist.storage.DBBroker, org.exist.dom.persistent.NodeProxy)}
 * re-rewrote each query's Lucene terms on every node. The fix caches rewritten terms per Query
 * identity (bounded LRU, see {@code LuceneMatchListener}) and short-circuits {@code scanMatches}
 * when the configured-field exclusion (PR #3467) produces an empty term map.</p>
 *
 * <p>Each shape below has a paired {@code *HighlightingOff} / {@code *HighlightingOn} benchmark
 * over an identical corpus and query, mirroring {@code ArrowOperatorBenchmark}'s
 * {@code arrow*}/{@code direct*} pairing: the two series plotted together on the JMH dashboard
 * show both the absolute cost and its trend, without a hard-coded ratio threshold (which would be
 * flaky on shared CI runners - the reason these two tests were pulled out of surefire in the
 * first place).</p>
 *
 * <ul>
 *   <li>{@code expandSingleHit*} - a single hit expanded via {@code subsequence($hits, 1, 1)}.
 *       Pre-#6318 this was already fast (the per-Query cache mostly helps batch callers), so this
 *       pair mainly guards against a future regression re-introducing per-hit term rewriting.</li>
 *   <li>{@code expandBatchWildcard*} - {@code util:expand($hits)} over ~half of a 5,000-entry
 *       corpus matched by a {@code lemma:a*} wildcard. This is the path the term-rewrite cache
 *       targets: every hit shares one query identity, so the cache turns N term-rewrites into 1.</li>
 * </ul>
 *
 * <h2>Build &amp; run (from project root)</h2>
 * <pre>{@code
 * mvn install -pl exist-core-jmh -am -DskipTests \
 *     -Ddependency-check.skip=true -Ddocker=false
 * java -jar exist-core-jmh/target/exist-core-jmh-7.0.0-SNAPSHOT-benchmarks.jar \
 *     UtilExpandHighlightingBenchmark -f 1 -wi 2 -i 5
 * }</pre>
 *
 * @see <a href="https://github.com/eXist-db/exist/issues/5738">#5738</a>
 * @see <a href="https://github.com/eXist-db/exist/pull/6318">#6318</a>
 * @see <a href="https://github.com/eXist-db/exist/issues/6387">#6387</a>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class UtilExpandHighlightingBenchmark {

    private static final XmldbURI TEST_COLLECTION = XmldbURI.create("/db/bench-util-expand-highlight");

    private static final String COLL = "collection('" + TEST_COLLECTION + "')";
    private static final String HIGHLIGHT_OFF_OPTIONS = "'highlight-matches=none expand-xincludes=no'";

    private ExistEmbeddedServer server;
    private BrokerPool pool;

    private CompiledXQuery singleHitHighlightingOffQuery;
    private CompiledXQuery singleHitHighlightingOnQuery;
    private CompiledXQuery batchWildcardHighlightingOffQuery;
    private CompiledXQuery batchWildcardHighlightingOnQuery;

    private int expectedWildcardHitCount;

    @Setup(Level.Trial)
    public void setUp() throws EXistException, DatabaseConfigurationException, IOException,
            PermissionDeniedException, CollectionConfigurationException, LockException,
            SAXException, TriggerException, XPathException {
        server = UtilExpandHighlightingCorpus.startServer();
        pool = server.getBrokerPool();

        UtilExpandHighlightingCorpus.storeCorpus(pool, TEST_COLLECTION);

        // Half the corpus (even i) gets an 'a'-prefixed headword; matches the lemma:a* wildcard.
        expectedWildcardHitCount = UtilExpandHighlightingCorpus.A_WORD_ENTRY_COUNT;

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();

            singleHitHighlightingOffQuery = UtilExpandHighlightingCorpus.compile(xquery, broker,
                    "util:expand(subsequence(" + COLL + "//entry[ft:query(., 'aword42')], 1, 1), "
                            + HIGHLIGHT_OFF_OPTIONS + ")");
            singleHitHighlightingOnQuery = UtilExpandHighlightingCorpus.compile(xquery, broker,
                    "util:expand(subsequence(" + COLL + "//entry[ft:query(., 'aword42')], 1, 1))");
            batchWildcardHighlightingOffQuery = UtilExpandHighlightingCorpus.compile(xquery, broker,
                    "util:expand(" + COLL + "//entry[ft:query(., 'lemma:a*')], " + HIGHLIGHT_OFF_OPTIONS + ")");
            batchWildcardHighlightingOnQuery = UtilExpandHighlightingCorpus.compile(xquery, broker,
                    "util:expand(" + COLL + "//entry[ft:query(., 'lemma:a*')])");
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (server != null) {
            server.stopDb();
        }
    }

    @Benchmark
    public int expandSingleHitHighlightingOff() throws EXistException, PermissionDeniedException, XPathException, IOException {
        return UtilExpandHighlightingCorpus.execute(pool, singleHitHighlightingOffQuery, 1);
    }

    @Benchmark
    public int expandSingleHitHighlightingOn() throws EXistException, PermissionDeniedException, XPathException, IOException {
        return UtilExpandHighlightingCorpus.execute(pool, singleHitHighlightingOnQuery, 1);
    }

    @Benchmark
    public int expandBatchWildcardHighlightingOff() throws EXistException, PermissionDeniedException, XPathException, IOException {
        return UtilExpandHighlightingCorpus.execute(pool, batchWildcardHighlightingOffQuery, expectedWildcardHitCount);
    }

    @Benchmark
    public int expandBatchWildcardHighlightingOn() throws EXistException, PermissionDeniedException, XPathException, IOException {
        return UtilExpandHighlightingCorpus.execute(pool, batchWildcardHighlightingOnQuery, expectedWildcardHitCount);
    }
}
