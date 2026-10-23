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
import java.util.stream.IntStream;

/**
 * JMH benchmark for predicates over nodes held in a variable, the paths where a query's matches go onto copies of the
 * nodes it was given instead of onto the nodes themselves.
 *
 * <ul>
 *   <li>{@code perItemFullText} - {@code $c[ft:query(., …)]}: a predicate evaluated per item; each hit is a copy of
 *       its context node with the match.</li>
 *   <li>{@code nodeSetFullText} - {@code $g[entry[ft:query(., …)]]}: a node-set predicate; each group kept is a copy
 *       with its entries' matches.</li>
 *   <li>{@code perItemNoMatch} and {@code nodeSetNoMatch} - the same two kinds of predicate without full text: no
 *       matches, so no copies; these must not get slower.</li>
 * </ul>
 *
 * <h2>Build &amp; run (from project root)</h2>
 * <pre>{@code
 * mvn package -Pperf-tests -pl exist-core-jmh -am -DskipTests \
 *     -Ddependency-check.skip=true -Ddocker=false
 * java -jar exist-core-jmh/target/exist-core-jmh-7.0.0-SNAPSHOT-benchmarks.jar QueryInputsBenchmark
 * }</pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class QueryInputsBenchmark {

    private static final XmldbURI TEST_COLLECTION = XmldbURI.create("/db/bench-query-inputs");

    private static final int GROUP_COUNT = 50;
    private static final int ENTRIES_PER_GROUP = 100;

    private static final String LUCENE_CONFIG = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
              <index>
                <lucene>
                  <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                  <text qname="entry"/>
                </lucene>
              </index>
            </collection>""";

    private static final String COLL = "collection('" + TEST_COLLECTION + "')";

    private ExistEmbeddedServer server;
    private BrokerPool pool;

    private CompiledXQuery perItemFullTextQuery;
    private CompiledXQuery nodeSetFullTextQuery;
    private CompiledXQuery perItemNoMatchQuery;
    private CompiledXQuery nodeSetNoMatchQuery;

    private long expectedEntryHits;
    private long expectedGroupHits;

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

        // the 'a'-prefixed headwords whose number starts with 4: aword4, aword40, aword400, ...
        expectedEntryHits = IntStream.range(0, GROUP_COUNT * ENTRIES_PER_GROUP)
                .filter(QueryInputsBenchmark::isTextWildcardHit).count();
        expectedGroupHits = IntStream.range(0, GROUP_COUNT)
                .filter(g -> IntStream.range(g * ENTRIES_PER_GROUP, (g + 1) * ENTRIES_PER_GROUP)
                        .anyMatch(QueryInputsBenchmark::isTextWildcardHit))
                .count();

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            perItemFullTextQuery = compile(xquery, broker,
                    "let $c := " + COLL + "//entry return count($c[ft:query(., 'aword4*')])");
            nodeSetFullTextQuery = compile(xquery, broker,
                    "let $g := " + COLL + "//group return count($g[entry[ft:query(., 'aword4*')]])");
            perItemNoMatchQuery = compile(xquery, broker,
                    "let $c := " + COLL + "//entry return count($c[. = 'nothing'])");
            nodeSetNoMatchQuery = compile(xquery, broker,
                    "let $c := " + COLL + "//entry return count($c[@type = 'a'])");
        }
    }

    private static boolean isTextWildcardHit(final int i) {
        return i % 2 == 0 && Integer.toString(i).startsWith("4");
    }

    private static CompiledXQuery compile(final XQuery xquery, final DBBroker broker, final String query)
            throws XPathException, PermissionDeniedException {
        final XQueryContext context = new XQueryContext(broker.getBrokerPool());
        return xquery.compile(context, query);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (server != null) {
            server.stopDb();
        }
    }

    @Benchmark
    public long perItemFullText() throws EXistException, PermissionDeniedException, XPathException {
        return execute(perItemFullTextQuery, expectedEntryHits);
    }

    @Benchmark
    public long nodeSetFullText() throws EXistException, PermissionDeniedException, XPathException {
        return execute(nodeSetFullTextQuery, expectedGroupHits);
    }

    @Benchmark
    public long perItemNoMatch() throws EXistException, PermissionDeniedException, XPathException {
        return execute(perItemNoMatchQuery, 0);
    }

    @Benchmark
    public long nodeSetNoMatch() throws EXistException, PermissionDeniedException, XPathException {
        return execute(nodeSetNoMatchQuery, (GROUP_COUNT * ENTRIES_PER_GROUP + 1) / 2);
    }

    /**
     * Runs the query and returns the count it computes, throwing if it is not the expected one - a "fast but wrong"
     * guard, not a performance threshold.
     */
    private long execute(final CompiledXQuery compiledQuery, final long expectedCount)
            throws EXistException, PermissionDeniedException, XPathException {
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            final Sequence result = xquery.execute(broker, compiledQuery, null);
            final long count = Long.parseLong(result.itemAt(0).getStringValue());
            if (count != expectedCount) {
                throw new IllegalStateException("Expected " + expectedCount + ", got " + count);
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

            broker.storeDocument(tx, XmldbURI.create("groups.xml"), new StringInputSource(generateCorpus()),
                    MimeType.XML_TYPE, coll);

            transact.commit(tx);
        }
    }

    /**
     * {@value #GROUP_COUNT} groups of {@value #ENTRIES_PER_GROUP} entries; entry i has the headword {@code aword<i>}
     * when i is even, {@code bword<i>} otherwise, and {@code type} 'a' or 'b' to match.
     */
    private static String generateCorpus() {
        final StringBuilder doc = new StringBuilder("<body>\n");
        for (int g = 0; g < GROUP_COUNT; g++) {
            doc.append("  <group>\n");
            for (int e = 0; e < ENTRIES_PER_GROUP; e++) {
                final int i = g * ENTRIES_PER_GROUP + e;
                final String letter = (i % 2 == 0) ? "a" : "b";
                doc.append("    <entry type=\"").append(letter).append("\">").append(letter).append("word").append(i)
                        .append(" with a short definition of the headword ").append(letter).append("word").append(i)
                        .append(".</entry>\n");
            }
            doc.append("  </group>\n");
        }
        return doc.append("</body>\n").toString();
    }
}
