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
 * Benchmarks four shapes of {@code range:field-eq()} usage against a combined
 * (multi-field) range index with a {@code <field name="speaker" match="SPEAKER"/>}
 * declaration on {@code SPEECH}.  Different {@code getDependencies()} code path
 * from {@code range:eq}: takes (fields, keys), no node-set.
 *
 * @see NgramWhereClauseBenchmark for shape semantics.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class RangeFieldEqWhereClauseBenchmark {

    private static final String[] CORPUS_RESOURCES = {
            "/org/exist/indexing/jmh/hamlet.xml",
            "/org/exist/indexing/jmh/macbeth.xml",
            "/org/exist/indexing/jmh/r_and_j.xml"
    };
    private static final String[] CORPUS_DOC_NAMES = {
            "hamlet.xml",
            "macbeth.xml",
            "r_and_j.xml"
    };
    private static final String CONFIG_RESOURCE = "/org/exist/indexing/jmh/collection.xconf";

    private static final String[] BASE_TERMS = {
            "HAMLET", "OPHELIA", "KING CLAUDIUS", "POLONIUS", "HORATIO"
    };

    private static final String DECLARE_RANGE =
            "declare namespace range=\"http://exist-db.org/xquery/range\";\n";

    private static final String COLLECTION_PATH =
            TestConstants.TEST_COLLECTION_URI.toString();

    private static final String QUERY_LITERAL =
            DECLARE_RANGE
            + "count(range:field-eq('speaker', 'HAMLET'))";

    private static final String QUERY_LET_VAR =
            DECLARE_RANGE
            + "let $q := 'HAMLET'\n"
            + "return count(range:field-eq('speaker', $q))";

    @Param({"5", "50", "100"})
    public int termCount;

    private ExistEmbeddedServer server;
    private BrokerPool pool;
    private XQuery xquery;
    private String queryForVarPredicate;
    private String queryForVarWhere;

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        server = new ExistEmbeddedServer(true, true);
        server.startDb();
        pool = server.getBrokerPool();

        final String[] corpora = new String[CORPUS_RESOURCES.length];
        for (int i = 0; i < CORPUS_RESOURCES.length; i++) {
            try (InputStream in = RangeFieldEqWhereClauseBenchmark.class.getResourceAsStream(CORPUS_RESOURCES[i])) {
                if (in == null) {
                    throw new IOException("Missing corpus resource: " + CORPUS_RESOURCES[i]);
                }
                corpora[i] = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        final String collectionConfig;
        try (InputStream in = RangeFieldEqWhereClauseBenchmark.class.getResourceAsStream(CONFIG_RESOURCE)) {
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

            for (int i = 0; i < CORPUS_DOC_NAMES.length; i++) {
                broker.storeDocument(transaction, XmldbURI.create(CORPUS_DOC_NAMES[i]),
                        new StringInputSource(corpora[i]), MimeType.XML_TYPE, root);
            }

            transact.commit(transaction);
        }

        xquery = pool.getXQueryService();

        final String terms = NgramWhereClauseBenchmark.buildTermSequence(BASE_TERMS, termCount);
        queryForVarPredicate =
                DECLARE_RANGE
                + "for $q in " + terms + "\n"
                + "return count(range:field-eq('speaker', $q))";
        queryForVarWhere =
                DECLARE_RANGE
                + "for $q in " + terms + "\n"
                + "where range:field-eq('speaker', $q)\n"
                + "return $q";

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Sequence result = xquery.execute(broker, QUERY_LITERAL, null);
            final long hits = result.itemAt(0).toJavaObject(Long.class);
            if (hits == 0) {
                throw new IllegalStateException("Corpus loaded but range:field-eq for 'HAMLET' produced 0 hits -- index not active?");
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
        return run(queryForVarPredicate);
    }

    @Benchmark
    public Sequence shapeBForVarWhere() throws Exception {
        return run(queryForVarWhere);
    }
}
