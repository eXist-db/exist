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
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XQuery;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.value.Sequence;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
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
 * JMH benchmark for query-time Lucene phrase lookup on many small documents.
 *
 * <p>Inspired by the performance regression report in
 * <a href="https://github.com/eXist-db/exist/issues/2812">#2812</a>: a query of the form
 * {@code collection(...)//custom:id[ft:query(., <phrase>datum1</phrase>)]/..} over many
 * small documents.</p>
 *
 * <p>This benchmark focuses on query execution cost (not reindex), with the Lucene
 * index created via collection configuration before loading documents.</p>
 *
 * <h2>Build &amp; run (from project root)</h2>
 * <pre>{@code
 * mvn install -pl exist-core-jmh -am -DskipTests \
 *     -Ddependency-check.skip=true -Ddocker=false
 * java -jar exist-core-jmh/target/exist-core-jmh-7.0.0-SNAPSHOT-benchmarks.jar \
 *     LucenePhraseQueryBenchmark -f 1 -wi 3 -i 5
 * }</pre>
 *
 * <h2>Common variants</h2>
 * <pre>{@code
 * # Smaller data volume for smoke checks
 * java -jar exist-core-jmh/target/exist-core-jmh-7.0.0-SNAPSHOT-benchmarks.jar \
 *     LucenePhraseQueryBenchmark -f 1 -wi 1 -i 2 -p docCount=100 -p matchEvery=10
 *
 * # Skip correctness check (throughput-only)
 * java -jar exist-core-jmh/target/exist-core-jmh-7.0.0-SNAPSHOT-benchmarks.jar \
 *     LucenePhraseQueryBenchmark -f 1 -wi 3 -i 5 -p verificationMode=SKIP
 * }</pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class LucenePhraseQueryBenchmark {

    private static final XmldbURI TEST_COLLECTION = XmldbURI.create("/db/bench-lucene-phrase");
    private static final XmldbURI TEST_COLLECTION_ONE = XmldbURI.create("/db/bench-lucene-phrase/col1");
    private static final XmldbURI TEST_COLLECTION_TWO = XmldbURI.create("/db/bench-lucene-phrase/col2");

    private static final String CUSTOM_NS = "urn:custom";

    private static final String LUCENE_CONFIG = """
            <collection xmlns="http://exist-db.org/collection-config/1.0"
                        xmlns:custom="%s">
              <index>
                <lucene>
                  <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                  <text qname="custom:id"/>
                </lucene>
              </index>
            </collection>""".formatted(CUSTOM_NS);

    @Param({"1000", "5000"})
    int docCount;

    /**
     * Every Nth document contains the phrase. 1 means all docs match; 10 means 10% match.
     */
    @Param({"1", "10"})
    int matchEvery;

    @Param({"STRICT"})
    String verificationMode;

    @Param({"single", "unionExplicit", "unionCollectionParens"})
    String queryVariant;

    private ExistEmbeddedServer server;
    private BrokerPool pool;

    private CompiledXQuery compiledQuery;
    private int expectedHits;

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

        storeDocuments();

        expectedHits = (docCount + matchEvery - 1) / matchEvery;

        final String query = buildQuery(queryVariant);
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            final XQueryContext context = new XQueryContext(broker.getBrokerPool());
            compiledQuery = xquery.compile(context, query);
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (server != null) {
            server.stopDb();
        }
    }

    @Benchmark
    public int phraseQuery() throws EXistException, PermissionDeniedException, XPathException, IOException {
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            final Sequence result = xquery.execute(broker, compiledQuery, null);
            final int hits = result.getItemCount();
            if (shouldVerify(verificationMode) && hits != expectedHits) {
                throw new IllegalStateException("Expected " + expectedHits + " hits, got " + hits);
            }
            return hits;
        }
    }

    private static boolean shouldVerify(final String verificationMode) {
        return !"SKIP".equalsIgnoreCase(verificationMode);
    }

    private void storeDocuments() throws EXistException, PermissionDeniedException, IOException,
            CollectionConfigurationException, LockException, SAXException, TriggerException {
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn tx = transact.beginTransaction()) {

            final Collection coll = broker.getOrCreateCollection(tx, TEST_COLLECTION);
            broker.saveCollection(tx, coll);

            final CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(tx, broker, coll, LUCENE_CONFIG);

            final Collection col1 = broker.getOrCreateCollection(tx, TEST_COLLECTION_ONE);
            broker.saveCollection(tx, col1);
            mgr.addConfiguration(tx, broker, col1, LUCENE_CONFIG);

            final Collection col2 = broker.getOrCreateCollection(tx, TEST_COLLECTION_TWO);
            broker.saveCollection(tx, col2);
            mgr.addConfiguration(tx, broker, col2, LUCENE_CONFIG);

            for (int i = 1; i <= docCount; i++) {
                final boolean matches = ((i - 1) % matchEvery) == 0;
                final Collection target = (i % 2 == 0) ? col2 : col1;
                broker.storeDocument(tx, XmldbURI.create("doc-" + i + ".xml"),
                        new StringInputSource(generateDocument(i, matches)), MimeType.XML_TYPE, target);
            }

            transact.commit(tx);
        }
    }

    private static String buildQuery(final String variant) {
        final String prolog = """
                xquery version "3.1";
                declare namespace custom="%s";
                import module namespace ft="http://exist-db.org/xquery/lucene"
                  at "java:org.exist.xquery.modules.lucene.LuceneModule";
                let $q := <query><phrase>datum1</phrase></query>
                """.formatted(CUSTOM_NS);

        return switch (variant) {
            case "single" -> prolog + """
                    return collection('%s')//custom:id[ft:query(., $q)]/..
                    """.formatted(TEST_COLLECTION);
            case "unionExplicit" -> prolog + """
                    return (
                      (collection('%s')//custom:id[ft:query(., $q)]/..)
                      | (collection('%s')//custom:id[ft:query(., $q)]/..)
                    )
                    """.formatted(TEST_COLLECTION_ONE, TEST_COLLECTION_TWO);
            case "unionCollectionParens" -> prolog + """
                    return (
                      (collection('%s') | collection('%s'))//custom:id[ft:query(., $q)]/..
                    )
                    """.formatted(TEST_COLLECTION_ONE, TEST_COLLECTION_TWO);
            default -> throw new IllegalArgumentException("Unknown queryVariant: " + variant);
        };
    }

    private static String generateDocument(final int index, final boolean matches) {
        final String value = matches ? "datum1" : ("datum" + index);
        return """
                <data xmlns:custom="%s">
                  <custom:id>%s</custom:id>
                  <payload>Small document %d for lucene phrase query benchmark.</payload>
                </data>""".formatted(CUSTOM_NS, value, index);
    }
}

