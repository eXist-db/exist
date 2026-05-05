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
 * Benchmarks four shapes of {@code ft:query()} usage against a Lucene full-text
 * index on {@code LINE}.  This is the path PR #6286 originally guarded.
 *
 * @see NgramWhereClauseBenchmark for shape semantics.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class LuceneWhereClauseBenchmark {

    private static final String CORPUS_RESOURCE = "/org/exist/indexing/jmh/hamlet.xml";
    private static final String CONFIG_RESOURCE = "/org/exist/indexing/jmh/collection.xconf";
    private static final String CORPUS_DOC_NAME = "hamlet.xml";

    /** Same term list as NgramWhereClauseBenchmark -- all appear in Hamlet's LINE text. */
    private static final String TERMS_SEQUENCE =
            "('Denmark', 'England', 'Norway', 'Polonius', 'France')";

    private static final String DECLARE_FT =
            "declare namespace ft=\"http://exist-db.org/xquery/lucene\";\n";

    private static final String COLLECTION_PATH =
            TestConstants.TEST_COLLECTION_URI.toString();

    private static final String QUERY_LITERAL =
            DECLARE_FT
            + "count(ft:query(collection('" + COLLECTION_PATH + "')//LINE, 'Denmark'))";

    private static final String QUERY_LET_VAR =
            DECLARE_FT
            + "let $q := 'Denmark'\n"
            + "return count(ft:query(collection('" + COLLECTION_PATH + "')//LINE, $q))";

    private static final String QUERY_FOR_VAR_PREDICATE =
            DECLARE_FT
            + "for $q in " + TERMS_SEQUENCE + "\n"
            + "return count(ft:query(collection('" + COLLECTION_PATH + "')//LINE, $q))";

    private static final String QUERY_FOR_VAR_WHERE =
            DECLARE_FT
            + "for $q in " + TERMS_SEQUENCE + "\n"
            + "where ft:query(collection('" + COLLECTION_PATH + "')//LINE, $q)\n"
            + "return $q";

    private ExistEmbeddedServer server;
    private BrokerPool pool;
    private XQuery xquery;

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        server = new ExistEmbeddedServer(true, true);
        server.startDb();
        pool = server.getBrokerPool();

        final String corpus;
        try (InputStream in = LuceneWhereClauseBenchmark.class.getResourceAsStream(CORPUS_RESOURCE)) {
            if (in == null) {
                throw new IOException("Missing corpus resource: " + CORPUS_RESOURCE);
            }
            corpus = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        final String collectionConfig;
        try (InputStream in = LuceneWhereClauseBenchmark.class.getResourceAsStream(CONFIG_RESOURCE)) {
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

            broker.storeDocument(transaction, XmldbURI.create(CORPUS_DOC_NAME),
                    new StringInputSource(corpus), MimeType.XML_TYPE, root);

            transact.commit(transaction);
        }

        xquery = pool.getXQueryService();

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Sequence result = xquery.execute(broker, QUERY_LITERAL, null);
            final long hits = result.itemAt(0).toJavaObject(Long.class);
            if (hits == 0) {
                throw new IllegalStateException("Corpus loaded but ft:query for 'Denmark' produced 0 hits -- index not active?");
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
    public Sequence shapeALiteral() throws Exception {
        return run(QUERY_LITERAL);
    }

    @Benchmark
    public Sequence shapeALetVar() throws Exception {
        return run(QUERY_LET_VAR);
    }

    @Benchmark
    public Sequence shapeBForVarPredicate() throws Exception {
        return run(QUERY_FOR_VAR_PREDICATE);
    }

    @Benchmark
    public Sequence shapeBForVarWhere() throws Exception {
        return run(QUERY_FOR_VAR_WHERE);
    }
}
