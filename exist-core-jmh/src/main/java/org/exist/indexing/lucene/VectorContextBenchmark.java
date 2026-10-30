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
 * JMH benchmark for the cost of searching a vector query among its context's nodes. Lucene's kNN search finds the k
 * nearest among the documents a filter matches; for a context narrower than whole documents, {@code InContextQuery}
 * builds that filter by reading the node id of each vector document and looking it up in the context: linear in the
 * vector documents of the segments searched.
 *
 * <ul>
 *   <li>{@code documentContext} - the documents as context: no filter, the plain kNN search.</li>
 *   <li>{@code allArticlesContext} - every article as context: the filter is built and matches every vector
 *       document, so the difference to {@code documentContext} plus {@code selectAllArticles} is what building it
 *       costs.</li>
 *   <li>{@code narrowContext} - a seventh of the articles as context, the case the filter is for; compare with
 *       {@code selectNarrowArticles}, which only selects them.</li>
 * </ul>
 *
 * <p>The nodes are passed to {@code ft:query-vector} as its first argument: in a predicate,
 * {@code //article[ft:query-vector(., ...)]}, the optimizer searches with the step's context, the documents, and no
 * filter is built.</p>
 *
 * <h2>Build &amp; run (from project root)</h2>
 * <pre>{@code
 * mvn package -Pperf-tests -pl exist-core-jmh -am -DskipTests \
 *     -Ddependency-check.skip=true -Ddocker=false
 * java -jar exist-core-jmh/target/exist-core-jmh-7.0.0-SNAPSHOT-benchmarks.jar VectorContextBenchmark
 * }</pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class VectorContextBenchmark {

    private static final XmldbURI TEST_COLLECTION = XmldbURI.create("/db/bench-vector-context");
    private static final String COLL = "collection('" + TEST_COLLECTION + "')";

    private static final int DIMENSION = 64;
    private static final int K = 5;

    @Param({"5000", "20000"})
    private int articles;

    private static final String LUCENE_CONFIG = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
              <index>
                <lucene>
                  <text qname="article">
                    <vector-field name="emb" expression="embedding" dimension="64" similarity="cosine" encoding="text"/>
                  </text>
                </lucene>
              </index>
            </collection>""";

    private ExistEmbeddedServer server;
    private BrokerPool pool;

    private CompiledXQuery documentContextQuery;
    private CompiledXQuery allArticlesContextQuery;
    private CompiledXQuery narrowContextQuery;
    private CompiledXQuery selectAllArticlesQuery;
    private CompiledXQuery selectNarrowArticlesQuery;

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

        final String vector = "array { (" + vectorText(0.5, ", ") + ") }";
        final String all = COLL + "//article";
        final String narrow = COLL + "//article[@lang = 'en']";
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            documentContextQuery = compile(xquery, broker,
                    "count(ft:query-vector(" + COLL + ", " + vector + ", " + K + "))");
            allArticlesContextQuery = compile(xquery, broker,
                    "count(ft:query-vector(" + all + ", " + vector + ", " + K + "))");
            narrowContextQuery = compile(xquery, broker,
                    "count(ft:query-vector(" + narrow + ", " + vector + ", " + K + "))");
            selectAllArticlesQuery = compile(xquery, broker, "count(" + all + ")");
            selectNarrowArticlesQuery = compile(xquery, broker, "count(" + narrow + ")");
        }
    }

    private static CompiledXQuery compile(final XQuery xquery, final DBBroker broker, final String query)
            throws XPathException, PermissionDeniedException {
        return xquery.compile(new XQueryContext(broker.getBrokerPool()), query);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (server != null) {
            server.stopDb();
        }
    }

    /** The one document holding the nearest articles. */
    @Benchmark
    public long documentContext() throws EXistException, PermissionDeniedException, XPathException {
        return execute(documentContextQuery, 1);
    }

    @Benchmark
    public long allArticlesContext() throws EXistException, PermissionDeniedException, XPathException {
        return execute(allArticlesContextQuery, K);
    }

    @Benchmark
    public long narrowContext() throws EXistException, PermissionDeniedException, XPathException {
        return execute(narrowContextQuery, K);
    }

    @Benchmark
    public long selectAllArticles() throws EXistException, PermissionDeniedException, XPathException {
        return execute(selectAllArticlesQuery, articles);
    }

    @Benchmark
    public long selectNarrowArticles() throws EXistException, PermissionDeniedException, XPathException {
        return execute(selectNarrowArticlesQuery, articles / 7);
    }

    /**
     * Runs the query and returns the count it gives, throwing if it is not the one expected - a "fast but wrong"
     * guard, not a performance threshold.
     */
    private long execute(final CompiledXQuery compiledQuery, final long expected)
            throws EXistException, PermissionDeniedException, XPathException {
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Sequence result = pool.getXQueryService().execute(broker, compiledQuery, null);
            final long count = Long.parseLong(result.itemAt(0).getStringValue());
            if (count != expected) {
                throw new IllegalStateException("Expected " + expected + ", got " + count);
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

            broker.storeDocument(tx, XmldbURI.create("articles.xml"), new StringInputSource(generateCorpus()),
                    MimeType.XML_TYPE, coll);

            transact.commit(tx);
        }
    }

    /** One document of articles, every seventh in English, each with a pseudo-random vector. */
    private String generateCorpus() {
        final StringBuilder doc = new StringBuilder("<articles>\n");
        for (int i = 1; i <= articles; i++) {
            doc.append("  <article n=\"").append(i).append("\" lang=\"").append(i % 7 == 0 ? "en" : "de")
                    .append("\"><embedding>").append(vectorText(i, " ")).append("</embedding></article>\n");
        }
        return doc.append("</articles>\n").toString();
    }

    /** The same pseudo-random vector as query-vector-candidates.xqm, for a seed. */
    private static String vectorText(final double seed, final String separator) {
        final StringBuilder text = new StringBuilder();
        for (int j = 1; j <= DIMENSION; j++) {
            final double x = Math.sin(seed * 12.9898 + j * 78.233) * 43758.5453;
            if (j > 1) {
                text.append(separator);
            }
            text.append(x - Math.floor(x) - 0.5);
        }
        return text.toString();
    }
}
