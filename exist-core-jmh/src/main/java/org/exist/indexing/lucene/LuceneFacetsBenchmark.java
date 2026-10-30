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
 * JMH benchmark for the facets of a Lucene search: a search counts them when it finishes, for a taxonomy that all the
 * collections share.
 *
 * <ul>
 *   <li>{@code searchOnly} - a search whose facets nobody reads.</li>
 *   <li>{@code searchAndReadFacets} - the same search with {@code ft:facets}.</li>
 * </ul>
 *
 * <h2>Build &amp; run (from project root)</h2>
 * <pre>{@code
 * mvn package -Pperf-tests -pl exist-core-jmh -am -DskipTests \
 *     -Ddependency-check.skip=true -Ddocker=false
 * java -jar exist-core-jmh/target/exist-core-jmh-7.0.0-SNAPSHOT-benchmarks.jar LuceneFacetsBenchmark
 * }</pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class LuceneFacetsBenchmark {

    private static final XmldbURI TEST_COLLECTION = XmldbURI.create("/db/bench-lucene-facets");
    private static final String HITS = "collection('" + TEST_COLLECTION + "')//item[ft:query(., 'word')]";
    private static final int KINDS = 10;

    @Param({"100", "10000"})
    private int items;

    /** the labels in the taxonomy, which all the collections share: what a count is allocated for */
    @Param({"10", "100000"})
    private int labels;

    private static final String LUCENE_CONFIG = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
              <index>
                <lucene>
                  <text qname="item">
                    <facet dimension="kind" expression="@kind"/>
                  </text>
                  <text qname="other">
                    <facet dimension="label" expression="@label"/>
                  </text>
                </lucene>
              </index>
            </collection>""";

    private ExistEmbeddedServer server;
    private BrokerPool pool;

    private CompiledXQuery searchOnlyQuery;
    private CompiledXQuery searchAndReadFacetsQuery;

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

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            searchOnlyQuery = xquery.compile(new XQueryContext(pool), "count(" + HITS + ")");
            searchAndReadFacetsQuery = xquery.compile(new XQueryContext(pool),
                    "map:size(ft:facets(" + HITS + ", 'kind', ()))");
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (server != null) {
            server.stopDb();
        }
    }

    @Benchmark
    public long searchOnly() throws EXistException, PermissionDeniedException, XPathException {
        return execute(searchOnlyQuery, items);
    }

    @Benchmark
    public long searchAndReadFacets() throws EXistException, PermissionDeniedException, XPathException {
        return execute(searchAndReadFacetsQuery, KINDS);
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
            final Collection collection = broker.getOrCreateCollection(tx, TEST_COLLECTION);
            broker.saveCollection(tx, collection);
            final CollectionConfigurationManager configManager = pool.getConfigurationManager();
            configManager.addConfiguration(tx, broker, collection, LUCENE_CONFIG);

            final StringBuilder xml = new StringBuilder("<items>");
            for (int i = 0; i < items; i++) {
                xml.append("<item kind=\"k").append(i % KINDS).append("\">a word</item>");
            }
            xml.append("</items>");
            broker.storeDocument(tx, XmldbURI.create("items.xml"), new StringInputSource(xml.toString()),
                    MimeType.XML_TYPE, collection);

            final StringBuilder others = new StringBuilder("<others>");
            for (int i = 0; i < labels; i++) {
                others.append("<other label=\"l").append(i).append("\">a text</other>");
            }
            others.append("</others>");
            broker.storeDocument(tx, XmldbURI.create("others.xml"), new StringInputSource(others.toString()),
                    MimeType.XML_TYPE, collection);
            transact.commit(tx);
        }
    }
}
