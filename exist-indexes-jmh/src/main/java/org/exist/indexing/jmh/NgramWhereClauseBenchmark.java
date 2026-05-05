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
 * Benchmarks four shapes of {@code ngram:contains()} usage to characterise the
 * compile- and runtime-paths an index lookup can take inside a FLWOR
 * expression.  The shapes correspond directly to the cases discussed in
 * <a href="https://github.com/eXist-db/exist/pull/6295">PR #6295</a> and the
 * "where-clause optimization" tasking.
 *
 * <table>
 *   <tr><th>Shape</th><th>Form</th><th>Expected path</th></tr>
 *   <tr><td>A.literal</td><td>{@code //LINE[ngram:contains(., 'Denmark')]}</td>
 *       <td>Single bulk index probe</td></tr>
 *   <tr><td>A.letVar</td><td>{@code let $q := 'Denmark' return //LINE[ngram:contains(., $q)]}</td>
 *       <td>Single bulk index probe (Shape A)</td></tr>
 *   <tr><td>B.forVarPredicate</td><td>{@code for $q in $TERMS return //LINE[ngram:contains(., $q)]}</td>
 *       <td>One bulk probe per outer iteration -- the rewrite target</td></tr>
 *   <tr><td>B.forVarWhere</td><td>{@code for $q in $TERMS where //LINE[ngram:contains(., $q)] return $q}</td>
 *       <td>Currently per-tuple EBV -- the regression case</td></tr>
 * </table>
 *
 * <p>Run with:
 * <pre>
 *   mvn -P micro-benchmarks package -pl exist-indexes-jmh -am -DskipTests
 *   java -jar exist-indexes-jmh/target/exist-indexes-jmh-*-benchmarks.jar \
 *       NgramWhereClauseBenchmark -wi 3 -i 5 -f 1
 * </pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class NgramWhereClauseBenchmark {

    private static final String CORPUS_RESOURCE = "/org/exist/indexing/jmh/hamlet.xml";
    private static final String CONFIG_RESOURCE = "/org/exist/indexing/jmh/collection.xconf";
    private static final String CORPUS_DOC_NAME = "hamlet.xml";

    /**
     * A fixed list of search terms that all appear in Hamlet.  Using a
     * deterministic list keeps benchmark numbers comparable across runs.
     */
    private static final String TERMS_SEQUENCE =
            "('Denmark', 'England', 'Norway', 'Polonius', 'France')";

    private static final String DECLARE_NGRAM =
            "declare namespace ngram=\"http://exist-db.org/xquery/ngram\";\n";

    private static final String COLLECTION_PATH =
            TestConstants.TEST_COLLECTION_URI.toString();

    /** Shape A.literal: search term is a string literal. */
    private static final String QUERY_LITERAL =
            DECLARE_NGRAM
            + "count(collection('" + COLLECTION_PATH + "')//LINE"
            + "[ngram:contains(., 'Denmark')])";

    /** Shape A.letVar: search term is bound by an outer let -- invariant. */
    private static final String QUERY_LET_VAR =
            DECLARE_NGRAM
            + "let $q := 'Denmark'\n"
            + "return count(collection('" + COLLECTION_PATH + "')//LINE"
            + "[ngram:contains(., $q)])";

    /**
     * Shape B.forVarPredicate: search term varies per iteration but the
     * ngram:contains() call is in predicate position.  This is the form
     * BaseX's {@code toPredicate} rewrite would produce from Shape B.
     */
    private static final String QUERY_FOR_VAR_PREDICATE =
            DECLARE_NGRAM
            + "for $q in " + TERMS_SEQUENCE + "\n"
            + "return count(collection('" + COLLECTION_PATH + "')//LINE"
            + "[ngram:contains(., $q)])";

    /**
     * Shape B.forVarWhere: the literal regression case from GH-2204 /
     * PR #6207 / PR #6295.  Today this falls back to per-tuple EBV.
     */
    private static final String QUERY_FOR_VAR_WHERE =
            DECLARE_NGRAM
            + "for $q in " + TERMS_SEQUENCE + "\n"
            + "where collection('" + COLLECTION_PATH + "')//LINE"
            + "[ngram:contains(., $q)]\n"
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
        try (InputStream in = NgramWhereClauseBenchmark.class.getResourceAsStream(CORPUS_RESOURCE)) {
            if (in == null) {
                throw new IOException("Missing corpus resource: " + CORPUS_RESOURCE);
            }
            corpus = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        final String collectionConfig;
        try (InputStream in = NgramWhereClauseBenchmark.class.getResourceAsStream(CONFIG_RESOURCE)) {
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

        // Sanity-check: every term must produce a non-empty result, otherwise
        // the benchmark is silently measuring "no hits".
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Sequence result = xquery.execute(broker, QUERY_LITERAL, null);
            final long hits = result.itemAt(0).toJavaObject(Long.class);
            if (hits == 0) {
                throw new IllegalStateException("Corpus loaded but 'Denmark' produced 0 hits -- index not active?");
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
            // Force materialisation so JIT can't elide the work.
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
