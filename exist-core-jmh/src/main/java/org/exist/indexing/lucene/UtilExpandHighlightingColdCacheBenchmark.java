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
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
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
 * JMH benchmark for the first-call cost of {@code util:expand} match-highlighting, i.e. with a
 * cold {@link LuceneMatchListener} term-rewrite cache.
 *
 * <p>{@link UtilExpandHighlightingBenchmark} measures steady state: after warmup the cache holds
 * the rewritten terms of every benchmark query, which is the case PR #6318 speeds up. This
 * benchmark measures the other case, the first call of a query after the index changed. Before
 * every invocation it replaces a small document in the indexed collection, then expands a hit in
 * that document, outside the timed region. That expand commits the change and refreshes the
 * Lucene searcher and reader, and the new reader version makes the listener drop its cache, so
 * every timed call rewrites its terms against the index.</p>
 *
 * <ul>
 *   <li>{@code coldExpandSingleHitWildcard} - a single {@code aword*} hit; the one rewrite is the
 *       whole highlighting cost, so this is the pure first-call case.</li>
 *   <li>{@code coldExpandBatchWildcard} - {@code util:expand($hits)} over the ~2,500
 *       {@code aword*} hits; the first hit pays the rewrite and the rest reuse it.</li>
 * </ul>
 *
 * <p>A plain-term single hit is left out: it takes well under a millisecond, where the
 * per-invocation fixture skews JMH's timing.</p>
 *
 * <h2>Build &amp; run (from project root)</h2>
 * <pre>{@code
 * mvn install -P perf-tests -pl exist-core-jmh -am -DskipTests \
 *     -Ddependency-check.skip=true -Ddocker=false
 * java -jar exist-core-jmh/target/exist-core-jmh-7.0.0-SNAPSHOT-benchmarks.jar \
 *     UtilExpandHighlightingColdCacheBenchmark -f 1 -wi 2 -i 5
 * }</pre>
 *
 * @see UtilExpandHighlightingBenchmark
 * @see <a href="https://github.com/eXist-db/exist/pull/6318">#6318</a>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class UtilExpandHighlightingColdCacheBenchmark {

    private static final XmldbURI TEST_COLLECTION = XmldbURI.create("/db/bench-util-expand-cold-cache");
    private static final XmldbURI BUMP_DOCUMENT = XmldbURI.create("bump.xml");

    private static final String COLL = "collection('" + TEST_COLLECTION + "')";

    private ExistEmbeddedServer server;
    private BrokerPool pool;
    private CompiledXQuery singleHitWildcardQuery;
    private CompiledXQuery batchWildcardQuery;
    private CompiledXQuery expandBumpDocumentQuery;

    private int expectedWildcardHitCount;
    private long bumpCounter;

    @Setup(Level.Trial)
    public void setUp() throws EXistException, DatabaseConfigurationException, IOException,
            PermissionDeniedException, CollectionConfigurationException, LockException,
            SAXException, TriggerException, XPathException {
        server = UtilExpandHighlightingCorpus.startServer();
        pool = server.getBrokerPool();

        UtilExpandHighlightingCorpus.storeCorpus(pool, TEST_COLLECTION);

        // Half the corpus (even i) gets an 'a'-prefixed headword; matches the aword* wildcard.
        expectedWildcardHitCount = UtilExpandHighlightingCorpus.A_WORD_ENTRY_COUNT;

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            singleHitWildcardQuery = UtilExpandHighlightingCorpus.compile(xquery, broker,
                    "util:expand(subsequence(" + COLL + "//entry[ft:query(., 'aword*')], 1, 1))");
            batchWildcardQuery = UtilExpandHighlightingCorpus.compile(xquery, broker,
                    "util:expand(" + COLL + "//entry[ft:query(., 'aword*')])");
            expandBumpDocumentQuery = UtilExpandHighlightingCorpus.compile(xquery, broker,
                    "util:expand(doc('" + TEST_COLLECTION + "/" + BUMP_DOCUMENT + "')//entry[ft:query(., 'zzzbump*')])");
        }
    }

    /**
     * Moves the Lucene index on to a new snapshot, so the next timed call starts with a cold
     * term-rewrite cache. Expanding a hit in the replaced document makes the commit and the
     * searcher and reader refresh happen here rather than lazily inside the timed query.
     */
    @Setup(Level.Invocation)
    public void moveIndexToNewSnapshot() throws EXistException, PermissionDeniedException, IOException,
            LockException, SAXException, TriggerException, XPathException {
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn tx = transact.beginTransaction()) {
            final Collection coll = broker.getCollection(TEST_COLLECTION);
            final String bump = "<dict><entry><sense><def>zzzbump" + bumpCounter++ + "</def></sense></entry></dict>";
            broker.storeDocument(tx, BUMP_DOCUMENT, new StringInputSource(bump), MimeType.XML_TYPE, coll);
            transact.commit(tx);
        }
        UtilExpandHighlightingCorpus.execute(pool, expandBumpDocumentQuery, 1);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (server != null) {
            server.stopDb();
        }
    }

    @Benchmark
    public int coldExpandSingleHitWildcard() throws EXistException, PermissionDeniedException, XPathException, IOException {
        return UtilExpandHighlightingCorpus.execute(pool, singleHitWildcardQuery, 1);
    }

    @Benchmark
    public int coldExpandBatchWildcard() throws EXistException, PermissionDeniedException, XPathException, IOException {
        return UtilExpandHighlightingCorpus.execute(pool, batchWildcardQuery, expectedWildcardHitCount);
    }
}
