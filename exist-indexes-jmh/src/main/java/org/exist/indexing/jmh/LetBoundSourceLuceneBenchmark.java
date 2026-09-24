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
package org.exist.indexing.jmh;

import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks <a href="https://github.com/eXist-db/exist/issues/873">GH-873</a>:
 * a full text query whose <em>source</em> node set is a let-bound variable
 * ({@code let $a := //SPEECH return $a[ft:query(., ...)]}) against the
 * equivalent direct form ({@code //SPEECH[ft:query(., ...)]}).
 *
 * <p>This is a different regression from the one {@link LuceneWhereClauseBenchmark}
 * guards: that benchmark scales the {@code ft:query} <em>argument</em>
 * (a let/for-bound search term) across FLWOR iterations (GH-2204 / PR #6286).
 * Here the let-bound variable is the FilteredExpression's <em>source</em> --
 * the sequence being filtered, not a value passed into the predicate -- and
 * the regression scales with corpus size rather than iteration count: before
 * the GH-873 fix, the {@code (#exist:optimize#)} pragma either never got
 * wired into the executed tree (for the plain {@code let ... return $a[pred]}
 * shape) or, once wired, discarded the index pre-selected candidate set and
 * re-evaluated the predicate across {@code $a}'s full, unfiltered value.
 *
 * <p>Shapes:
 * <ul>
 *   <li>{@code shapeDirect} -- {@code //SPEECH[ft:query(LINE, 'Denmark')]}
 *   <li>{@code shapeLetBoundSource} -- the GH-873 case above; should track
 *       {@code shapeDirect}
 *   <li>{@code shapeLetBoundSourceAbbreviated} -- {@code outer//$a[pred]},
 *       which parses {@code $a[pred]} as an <em>abbreviated</em>
 *       FilteredExpression (see the DSLASH rule in XQueryTree.g) and exercises
 *       a different branch in {@code FilteredExpression#filter}; should also
 *       track {@code shapeDirect}
 *   <li>{@code shapeLetBoundSourceExtraPredicate} -- a second predicate
 *       chained after the indexed one ({@code $a[ft:query(...)][SPEAKER = ...]}).
 *       <strong>This one is NOT expected to track {@code shapeDirect} yet</strong>:
 *       two bracket groups on a non-{@code LocationStep} source parse as
 *       <em>nested</em> {@code FilteredExpression}s, and {@code FilteredExpression}
 *       itself does not implement {@code RewritableExpression}. The inner FE
 *       (holding the Optimizable {@code ft:query} predicate) can therefore
 *       never get its own {@code (#exist:optimize#)} pragma -- its parent is
 *       the outer FE, not something {@code replace()}-able -- and the outer
 *       FE's own Optimizable-detection only inspects its own predicate list,
 *       never recursing into the nested inner FE to find one. This is a
 *       separate, pre-existing optimizer limitation (predates and is
 *       independent of the GH-873 fix, and is not specific to let-bound
 *       variables -- any non-LocationStep source with two bracket groups hits
 *       it) tracked for a follow-up fix. Kept here so the dashboard shows the
 *       gap until it's closed.
 * </ul>
 *
 * <p>{@code corpusCopies} parameterises the corpus size (hamlet.xml stored
 * repeatedly under distinct names) so we can see whether a shape scales
 * linearly with corpus size (unoptimized) or tracks {@code shapeDirect} (the
 * index-pre-select path).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class LetBoundSourceLuceneBenchmark {

    private static final String CORPUS_RESOURCE = "/org/exist/indexing/jmh/hamlet.xml";
    private static final String CONFIG_RESOURCE = "/org/exist/indexing/jmh/letbound-source-collection.xconf";

    private static final String COLLECTION_PATH =
            TestConstants.TEST_COLLECTION_URI.toString();

    private static final String QUERY_DIRECT = """
            declare namespace ft="http://exist-db.org/xquery/lucene";
            count(collection('%s')//SPEECH[ft:query(LINE, 'Denmark')])
            """.formatted(COLLECTION_PATH);

    // NOTE: count() wraps the *entire* let-expression here, not the filtered
    // expression inside its return clause. `let $a := X return count($a[pred])`
    // is a DIFFERENT, still-broken shape: Function#checkArgument wraps an
    // already-analyzed argument in a DynamicCardinalityCheck (or similar type
    // check) *after* analyze() has already fixed the argument's parent
    // pointer, so replace() during the optimizer pass can't find it there
    // either -- the same family of bug as GH-873, but in typed function
    // arguments generally. Not yet fixed; see the GH-873 discussion for
    // details (this is why: quote a query that literally matches your real
    // use case when reporting a perf regression, don't assume an equivalent
    // one behaves the same).
    private static final String QUERY_LET_BOUND_SOURCE = """
            declare namespace ft="http://exist-db.org/xquery/lucene";
            count(let $a := collection('%s')//SPEECH return $a[ft:query(LINE, 'Denmark')])
            """.formatted(COLLECTION_PATH);

    private static final String QUERY_LET_BOUND_SOURCE_EXTRA_PREDICATE = """
            declare namespace ft="http://exist-db.org/xquery/lucene";
            count(let $a := collection('%s')//SPEECH return $a[ft:query(LINE, 'Denmark')][SPEAKER = 'HAMLET'])
            """.formatted(COLLECTION_PATH);

    private static final String QUERY_LET_BOUND_SOURCE_ABBREVIATED = """
            declare namespace ft="http://exist-db.org/xquery/lucene";
            count(let $a := collection('%s')//SPEECH return collection('%s')//$a[ft:query(LINE, 'Denmark')])
            """.formatted(COLLECTION_PATH, COLLECTION_PATH);

    @Param({"1", "10", "40"})
    public int corpusCopies;

    private ExistEmbeddedServer server;
    private BrokerPool pool;
    private XQuery xquery;

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        server = new ExistEmbeddedServer(true, true);
        server.startDb();
        pool = server.getBrokerPool();

        final String hamletContent;
        try (InputStream in = LetBoundSourceLuceneBenchmark.class.getResourceAsStream(CORPUS_RESOURCE)) {
            if (in == null) {
                throw new IOException("Missing corpus resource: " + CORPUS_RESOURCE);
            }
            hamletContent = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        final String collectionConfig;
        try (InputStream in = LetBoundSourceLuceneBenchmark.class.getResourceAsStream(CONFIG_RESOURCE)) {
            if (in == null) {
                throw new IOException("Missing collection config resource: " + CONFIG_RESOURCE);
            }
            collectionConfig = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = transact.beginTransaction()) {

            final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            broker.saveCollection(transaction, root);

            final CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(transaction, broker, root, collectionConfig);

            for (int i = 0; i < corpusCopies; i++) {
                broker.storeDocument(transaction, XmldbURI.create("hamlet-" + i + ".xml"),
                        new StringInputSource(hamletContent), MimeType.XML_TYPE, root);
            }

            transact.commit(transaction);
        }

        xquery = pool.getXQueryService();

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Sequence direct = xquery.execute(broker, QUERY_DIRECT, null);
            final Sequence letBound = xquery.execute(broker, QUERY_LET_BOUND_SOURCE, null);
            final long directHits = direct.itemAt(0).toJavaObject(Long.class);
            final long letBoundHits = letBound.itemAt(0).toJavaObject(Long.class);
            if (directHits == 0) {
                throw new IllegalStateException("Corpus loaded but ft:query for 'Denmark' produced 0 hits -- index not active?");
            }
            if (directHits != letBoundHits) {
                throw new IllegalStateException(
                        "Direct (" + directHits + ") and let-bound-source (" + letBoundHits
                                + ") hit counts differ -- correctness regression, not just a perf one");
            }
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (server != null) {
            server.stopDb();
        }
    }

    private Sequence run(final String query) throws Exception {
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Sequence result = xquery.execute(broker, query, null);
            result.getItemCount();
            return result;
        }
    }

    @Benchmark
    public Sequence shapeDirect() throws Exception {
        return run(QUERY_DIRECT);
    }

    @Benchmark
    public Sequence shapeLetBoundSource() throws Exception {
        return run(QUERY_LET_BOUND_SOURCE);
    }

    @Benchmark
    public Sequence shapeLetBoundSourceExtraPredicate() throws Exception {
        return run(QUERY_LET_BOUND_SOURCE_EXTRA_PREDICATE);
    }

    @Benchmark
    public Sequence shapeLetBoundSourceAbbreviated() throws Exception {
        return run(QUERY_LET_BOUND_SOURCE_ABBREVIATED);
    }
}
