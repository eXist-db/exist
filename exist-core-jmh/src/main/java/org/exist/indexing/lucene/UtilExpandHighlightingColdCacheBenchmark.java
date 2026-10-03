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
import org.exist.collections.CollectionConfigurationManager;
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
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
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
import java.util.Properties;
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

    private static final int ENTRY_COUNT = 5000;
    private static final int PARAGRAPHS_PER_ENTRY = 20;

    private static final String LUCENE_CONFIG = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
              <index>
                <lucene>
                  <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                  <text qname="entry">
                    <field name="text" expression="normalize-space()"/>
                    <field name="lemma" expression=".//form[@type='lemma']/orth"/>
                  </text>
                </lucene>
              </index>
            </collection>""";

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
        final Properties configProperties = new Properties();
        // BrokerPool expects a Long for this property (see BrokerPool.PROPERTY_SHUTDOWN_DELAY).
        configProperties.put("wait-before-shutdown", 0L);
        server = new ExistEmbeddedServer(configProperties, true, true);
        server.startDb();
        pool = server.getBrokerPool();

        storeCorpus();

        // Half the corpus (even i) gets an 'a'-prefixed headword; matches the aword* wildcard.
        expectedWildcardHitCount = (ENTRY_COUNT + 1) / 2;

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            singleHitWildcardQuery = compile(xquery, broker,
                    "util:expand(subsequence(" + COLL + "//entry[ft:query(., 'aword*')], 1, 1))");
            batchWildcardQuery = compile(xquery, broker,
                    "util:expand(" + COLL + "//entry[ft:query(., 'aword*')])");
            expandBumpDocumentQuery = compile(xquery, broker,
                    "util:expand(doc('" + TEST_COLLECTION + "/" + BUMP_DOCUMENT + "')//entry[ft:query(., 'zzzbump*')])");
        }
    }

    private static CompiledXQuery compile(final XQuery xquery, final DBBroker broker, final String query)
            throws XPathException, PermissionDeniedException {
        final XQueryContext context = new XQueryContext(broker.getBrokerPool());
        return xquery.compile(context, query);
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
        execute(expandBumpDocumentQuery, 1);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (server != null) {
            server.stopDb();
        }
    }

    @Benchmark
    public int coldExpandSingleHitWildcard() throws EXistException, PermissionDeniedException, XPathException, IOException {
        return execute(singleHitWildcardQuery, 1);
    }

    @Benchmark
    public int coldExpandBatchWildcard() throws EXistException, PermissionDeniedException, XPathException, IOException {
        return execute(batchWildcardQuery, expectedWildcardHitCount);
    }

    /**
     * Runs the query and returns the resulting node count, throwing if it doesn't match the
     * expected hit count - a "fast but wrong" guard, not a performance threshold.
     */
    private int execute(final CompiledXQuery compiledQuery, final int expectedCount)
            throws EXistException, PermissionDeniedException, XPathException, IOException {
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            final Sequence result = xquery.execute(broker, compiledQuery, null);
            final int count = result.getItemCount();
            if (count != expectedCount) {
                throw new IllegalStateException("Expected " + expectedCount + " top-level results, got " + count);
            }
            return count;
        }
    }

    private void storeCorpus() throws EXistException, PermissionDeniedException, IOException,
            CollectionConfigurationException, LockException, SAXException, TriggerException {
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn tx = transact.beginTransaction()) {

            final Collection coll = broker.getOrCreateCollection(tx, TEST_COLLECTION);
            broker.saveCollection(tx, coll);

            final CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(tx, broker, coll, LUCENE_CONFIG);

            broker.storeDocument(tx, XmldbURI.create("dict.xml"), new StringInputSource(generateCorpus()),
                    MimeType.XML_TYPE, coll);

            transact.commit(tx);
        }
    }

    /**
     * Same dict/entry corpus as {@link UtilExpandHighlightingBenchmark}: {@value #ENTRY_COUNT}
     * entries, half with an 'a'-prefixed headword, each padded with
     * {@value #PARAGRAPHS_PER_ENTRY} paragraphs.
     */
    private static String generateCorpus() {
        final StringBuilder doc = new StringBuilder();
        doc.append("<dict>\n");
        for (int i = 0; i < ENTRY_COUNT; i++) {
            final String letter = (i % 2 == 0) ? "a" : "b";
            final String word = letter + "word" + i;
            doc.append("  <entry xml:id=\"e").append(i).append("\">")
                    .append("<form type=\"lemma\"><orth>").append(word).append("</orth></form>")
                    .append("<sense><def>Definition for ").append(word).append(". ");
            for (int j = 0; j < PARAGRAPHS_PER_ENTRY; j++) {
                doc.append("This is paragraph ").append(j).append(" of the explanation for ")
                        .append(word).append(", with additional descriptive sentences ")
                        .append("that emulate real lexicographic content. The headword ")
                        .append(word).append(" appears multiple times in the body. ");
            }
            doc.append("</def></sense></entry>\n");
        }
        doc.append("</dict>\n");
        return doc.toString();
    }
}
