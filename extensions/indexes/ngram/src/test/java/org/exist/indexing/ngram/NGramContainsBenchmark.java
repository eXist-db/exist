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
package org.exist.indexing.ngram;

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
import org.exist.test.TestConstants;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.*;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Throughput benchmark for ngram:contains() comparing literal string arguments
 * vs for-bound variable arguments.  The variable-argument path exercises the
 * getDependencies() fix for GH-2204 and should show no measurable overhead.
 *
 * <p>This class intentionally does <em>not</em> end in {@code *Test} so that
 * Surefire will not discover it during normal {@code mvn test} runs.
 * Run explicitly with:
 * <pre>
 *   mvn test -pl extensions/indexes/ngram \
 *       -Dtest=NGramContainsBenchmark \
 *       -Dexist.run.benchmarks=true \
 *       -Ddependency-check.skip=true
 * </pre>
 */
public class NGramContainsBenchmark {

    private static final int WARMUP = 100;
    private static final int ITERATIONS = 500;

    private static final String XML;
    static {
        // Generate a document with 100 items, each with a unique description.
        final StringBuilder sb = new StringBuilder("<test>\n");
        for (int i = 1; i <= 100; i++) {
            sb.append("  <item id='").append(i).append("'><description>")
              .append("Item").append(String.format("%03d", i))
              .append("</description></item>\n");
        }
        sb.append("</test>");
        XML = sb.toString();
    }

    private static final String COLLECTION_CONFIG =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
        "   <index>" +
        "       <ngram qname=\"item\"/>" +
        "   </index>" +
        "</collection>";

    /** Literal argument — the optimized path (no variable dependency). */
    private static final String QUERY_LITERAL =
        "count(//item[ngram:contains(., 'Item005')])";

    /** Variable bound via let — the optimized path (no variable dependency). */
    private static final String QUERY_LET_VAR =
        "let $q := 'Item005' return count(//item[ngram:contains(., $q)])";

    /** Variable bound via for — exercises the GH-2204 fix. */
    private static final String QUERY_FOR_VAR =
        "let $queries := ('Item005', 'Item010', 'Item050') " +
        "return for $q in $queries return count(//item[ngram:contains(., $q)])";

    private static final Map<String, String> QUERIES = new LinkedHashMap<>();
    static {
        QUERIES.put("literal   ", QUERY_LITERAL);
        QUERIES.put("let-var   ", QUERY_LET_VAR);
        QUERIES.put("for-var   ", QUERY_FOR_VAR);
    }

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer =
            new ExistEmbeddedServer(true, true);

    @BeforeClass
    public static void setUp() throws DatabaseConfigurationException, EXistException,
            PermissionDeniedException, IOException, CollectionConfigurationException,
            LockException, TriggerException, SAXException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = transact.beginTransaction()) {

            final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            broker.saveCollection(transaction, root);

            final CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(transaction, broker, root, COLLECTION_CONFIG);

            broker.storeDocument(transaction, XmldbURI.create("bench.xml"),
                    new StringInputSource(XML), MimeType.XML_TYPE, root);

            transact.commit(transaction);
        }
    }

    @AfterClass
    public static void tearDown() throws EXistException, PermissionDeniedException,
            IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = transact.beginTransaction()) {

            final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            broker.removeCollection(transaction, root);

            final Collection config = broker.getOrCreateCollection(transaction,
                    XmldbURI.create(CollectionConfigurationManager.CONFIG_COLLECTION + "/db"));
            broker.removeCollection(transaction, config);

            transact.commit(transaction);
        }
    }

    @Test
    public void benchmark() throws EXistException, PermissionDeniedException, XPathException {
        Assume.assumeTrue(
                "Benchmark skipped by default.  Re-run with -Dexist.run.benchmarks=true",
                Boolean.getBoolean("exist.run.benchmarks"));

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();

        System.out.println();
        System.out.printf("%-12s  %8s  %8s  %10s%n", "query", "avg(ms)", "min(ms)", "ops/s");
        System.out.println("─".repeat(46));

        try (final DBBroker broker = pool.get(
                Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            final XQuery xquery = pool.getXQueryService();

            for (final Map.Entry<String, String> entry : QUERIES.entrySet()) {
                final String label = entry.getKey();
                final String query = entry.getValue();

                // warm up
                for (int i = 0; i < WARMUP; i++) {
                    xquery.execute(broker, query, null);
                }

                // timed run
                final long[] times = new long[ITERATIONS];
                for (int i = 0; i < ITERATIONS; i++) {
                    final long t0 = System.nanoTime();
                    final Sequence result = xquery.execute(broker, query, null);
                    // force materialisation
                    result.getItemCount();
                    times[i] = System.nanoTime() - t0;
                }

                Arrays.sort(times);
                final long min = times[0];
                final double avg = Arrays.stream(times).average().orElse(0);
                final double opsPerSec = 1_000_000_000.0 / avg;

                System.out.printf("%-12s  %8.3f  %8.3f  %10.1f%n",
                        label,
                        avg / 1_000_000.0,
                        min / 1_000_000.0,
                        opsPerSec);
            }
        }

        System.out.println();
    }
}
