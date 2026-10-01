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
 * Benchmarks {@code let $a := X return count($a[ft:query(...)])}, where the
 * {@code count()} argument (not just its let-bound source) is what's typed,
 * against the direct {@code count(X[ft:query(...)])} form.
 *
 * <p>This is a different, deeper case than {@code LetBoundSourceLuceneBenchmark}
 * on the sibling GH-873 fix: there, the let-bound variable is the
 * FilteredExpression's <em>source</em>, and the FilteredExpression itself
 * (the whole {@code $a[pred]}) is what gets passed into {@code count()}
 * unchanged. Here, {@code count()}'s own parameter type check wraps that
 * FilteredExpression in a {@link org.exist.xquery.DynamicCardinalityCheck}
 * <em>after</em> {@code analyze()} has already fixed its {@code parent}
 * pointer to the {@code count()} call -- so a rewrite that resolves its
 * target's parent via {@code getParent()} finds the wrapper is in the way.
 * This is the general case: any typed function argument can be wrapped this
 * way, not just ones with a let-bound source.
 *
 * <p>This is the shape guarded for correctness (not by wall-clock timing) by
 * {@code fto:count-wrapped-matches-direct} in {@code optimizer-ft.xql}: before
 * the fix for <a href="https://github.com/eXist-db/exist/issues/6759">GH-6759</a>,
 * {@link org.exist.xquery.Function#checkArgument}'s runtime cardinality-check
 * wrapper around {@code count()}'s argument hid the filtered expression from
 * {@link org.exist.xquery.Function#replace}, so
 * {@link org.exist.xquery.Optimizer#visitFilteredExpr} could not attach its
 * {@code (#exist:optimize#)} pragma and the query fell back to a full,
 * unindexed scan.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class TypedArgumentSourceLuceneBenchmark {

    private static final String CORPUS_RESOURCE = "/org/exist/indexing/jmh/hamlet.xml";
    private static final String CONFIG_RESOURCE = "/org/exist/indexing/jmh/collection.xconf";

    // Corpus needs to be large enough that an unindexed, per-node full scan
    // is measurably slower than an index pre-select; a single hamlet.xml is
    // too small for that gap to be reliable, so it's stored repeatedly under
    // distinct names.
    private static final int HAMLET_COPIES = 40;

    private static final String COLLECTION_PATH =
            TestConstants.TEST_COLLECTION_URI.toString();

    private static final String QUERY_DIRECT = """
            declare namespace ft="http://exist-db.org/xquery/lucene";
            count(collection('%s')//SPEECH[ft:query(LINE, 'king')])
            """.formatted(COLLECTION_PATH);

    private static final String QUERY_COUNT_WRAPPED = """
            declare namespace ft="http://exist-db.org/xquery/lucene";
            let $a := collection('%s')//SPEECH
            return count($a[ft:query(LINE, 'king')])
            """.formatted(COLLECTION_PATH);

    private ExistEmbeddedServer server;
    private BrokerPool pool;
    private XQuery xquery;

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        server = new ExistEmbeddedServer(true, true);
        server.startDb();
        pool = server.getBrokerPool();

        final String hamletContent;
        try (InputStream in = TypedArgumentSourceLuceneBenchmark.class.getResourceAsStream(CORPUS_RESOURCE)) {
            if (in == null) {
                throw new IOException("Missing corpus resource: " + CORPUS_RESOURCE);
            }
            hamletContent = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        final String collectionConfig;
        try (InputStream in = TypedArgumentSourceLuceneBenchmark.class.getResourceAsStream(CONFIG_RESOURCE)) {
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

            for (int i = 0; i < HAMLET_COPIES; i++) {
                broker.storeDocument(transaction, XmldbURI.create("hamlet-copy-" + i + ".xml"),
                        new StringInputSource(hamletContent), MimeType.XML_TYPE, root);
            }

            transact.commit(transaction);
        }

        xquery = pool.getXQueryService();

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Sequence result = xquery.execute(broker, QUERY_DIRECT, null);
            final long hits = result.itemAt(0).toJavaObject(Long.class);
            if (hits == 0) {
                throw new IllegalStateException("Corpus loaded but ft:query for 'king' produced 0 hits -- index not active?");
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
    public Sequence direct() throws Exception {
        return run(QUERY_DIRECT);
    }

    @Benchmark
    public Sequence countWrapped() throws Exception {
        return run(QUERY_COUNT_WRAPPED);
    }
}
