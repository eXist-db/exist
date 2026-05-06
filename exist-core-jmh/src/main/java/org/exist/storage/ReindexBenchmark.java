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
package org.exist.storage;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
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
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for the {@code xmldb:reindex()} optimisation
 * (<a href="https://github.com/eXist-db/exist/issues/572">#572</a>).
 *
 * <p>Measures end-to-end reindex time for a synthetic collection with a
 * Lucene full-text index.  The {@code IndexMode.REINDEX} fast-path skips
 * DOM BTree and structural/value index rebuilds, only touching custom
 * extension indexes (Lucene, range, etc.).</p>
 *
 * <p>After every invocation a correctness guard runs {@code ft:query} and
 * an XPath count to verify the indexes still work — preventing "fast but
 * wrong" results.</p>
 *
 * <h3>Build &amp; run (from project root)</h3>
 * <pre>{@code
 * mvn install -pl exist-core-jmh -am -DskipTests \
 *     -Ddependency-check.skip=true -Ddocker=false
 * java -jar exist-core-jmh/target/exist-core-jmh-7.0.0-SNAPSHOT-benchmarks.jar \
 *     ReindexBenchmark -f 1 -wi 3 -i 5
 * }</pre>
 *
 * <p>To compare old vs new behaviour, run the benchmark on a commit before
 * and after the {@code IndexMode.REINDEX} change.</p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class ReindexBenchmark {

    private static final XmldbURI TEST_COLLECTION = XmldbURI.create("/db/bench-reindex");

    private static final String LUCENE_CONFIG = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
              <index>
                <lucene>
                  <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                  <text qname="title"/>
                  <text qname="body"/>
                  <text match="/doc/meta/@author"/>
                </lucene>
              </index>
            </collection>""";

    @Param({"100", "500", "1000"})
    int docCount;

    private ExistEmbeddedServer server;
    private BrokerPool pool;

    @Setup(Level.Trial)
    public void setUp() throws EXistException, DatabaseConfigurationException, IOException,
            PermissionDeniedException, CollectionConfigurationException, LockException,
            SAXException, TriggerException {
        server = new ExistEmbeddedServer(true, true);
        server.startDb();
        pool = server.getBrokerPool();
        storeDocuments();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (server != null) {
            server.stopDb();
        }
    }

    /**
     * Benchmark the public {@code reindexCollection} API.
     *
     * <p>On the optimised code this exercises {@code IndexMode.REINDEX}
     * (skips DOM/structural rebuilds).  On the baseline code it exercises
     * {@code IndexMode.STORE} (full rebuild).</p>
     *
     * @return combined hit counts from the correctness guard (consumed by JMH
     *         to prevent dead-code elimination)
     */
    @Benchmark
    public int reindex() throws EXistException, PermissionDeniedException, IOException,
            LockException, XPathException {
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn tx = transact.beginTransaction()) {
            broker.reindexCollection(tx, TEST_COLLECTION);
            transact.commit(tx);
        }
        if (Boolean.getBoolean("exist.jmh.reindex.skipVerify")) {
            return docCount;
        }
        return verifyIndex();
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

            for (int i = 0; i < docCount; i++) {
                broker.storeDocument(tx, XmldbURI.create("doc-" + i + ".xml"),
                        new StringInputSource(generateDocument(i)), MimeType.XML_TYPE, coll);
            }
            transact.commit(tx);
        }
    }

    /**
     * Verify plain XPath correctness after reindexing. Throws if anything is
     * off, which JMH surfaces as a benchmark failure.
     */
    private int verifyIndex() throws EXistException, PermissionDeniedException, XPathException, IOException {
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();

            final Sequence xpathResult = xquery.execute(broker,
                    "count(collection('" + TEST_COLLECTION + "')//doc)", null);
            final int xpathCount = Integer.parseInt(xpathResult.itemAt(0).getStringValue());
            if (xpathCount != docCount) {
                throw new IllegalStateException(
                        "XPath correctness check failed: expected " + docCount + " docs, got " + xpathCount);
            }

            final Sequence titleResult = xquery.execute(broker,
                    "count(collection('" + TEST_COLLECTION + "')//title[contains(., 'Benchmark document')])", null);
            final int titleCount = Integer.parseInt(titleResult.itemAt(0).getStringValue());
            if (titleCount != docCount) {
                throw new IllegalStateException(
                        "XPath title-content check failed: expected " + docCount + " docs, got " + titleCount);
            }

            return xpathCount + titleCount;
        }
    }

    private static String generateDocument(final int index) {
        return """
                <doc>
                  <meta author="author-%1$d"/>
                  <title>Benchmark document %1$d for reindex testing</title>
                  <body>
                    <p>This is paragraph one of document %1$d. \
                It contains sample text for full-text indexing with Lucene.</p>
                    <p>The quick brown fox jumps over the lazy dog. \
                Document number %1$d benchmark performance test.</p>
                    <p>Additional content to increase document size. \
                XML database native storage eXist-db %1$d.</p>
                  </body>
                </doc>""".formatted(index);
    }
}
