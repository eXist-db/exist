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

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks a predicate keyed by a variable, evaluated against a collection of many
 * small documents, as in <a href="https://github.com/eXist-db/exist/issues/6619">#6619</a>.
 *
 * The range index rewriter turns {@code item[id = $id]} into a range lookup, also when no
 * index is configured. If the lookup is evaluated once per document of the context sequence
 * instead of once per step, the cost of each loop iteration is quadratic in the number of
 * documents, so the interesting parameter here is {@code docCount}, not the number of terms.
 *
 * <ul>
 *   <li>{@code predicateLiteral}, {@code predicateOuterVar}: the key does not change in the loop.</li>
 *   <li>{@code predicateLoopVar}: the key is the loop variable -- the shape of #6619.</li>
 *   <li>{@code predicateLoopVarUnoptimized}: the same with the optimizer disabled, as a reference.</li>
 *   <li>{@code whereNavigating}: a where clause navigating from the "for" variable, which can be
 *       evaluated in a single step for all items.</li>
 *   <li>{@code whereVariableAsKey}: a where clause using the "for" variable as a comparison key
 *       (<a href="https://github.com/eXist-db/exist/issues/2204">#2204</a>), which has to be
 *       evaluated per item.</li>
 * </ul>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class LoopVariablePredicateBenchmark {

    private static final int LOOKUPS = 10;

    private static final XmldbURI QUERIES_COLLECTION_URI = TestConstants.TEST_COLLECTION_URI.append("queries");

    private static final String COLLECTION_CONFIG = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
                <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <range>
                        <create qname="id" type="xs:string"/>
                    </range>
                </index>
            </collection>
            """;

    @Param({"100", "1000", "4000"})
    public int docCount;

    @Param({"false", "true"})
    public boolean indexed;

    private ExistEmbeddedServer server;
    private BrokerPool pool;
    private XQuery xquery;

    private String queryPredicateLiteral;
    private String queryPredicateOuterVar;
    private String queryPredicateLoopVar;
    private String queryWhereNavigating;
    private String queryWhereVariableAsKey;

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        server = new ExistEmbeddedServer(true, true);
        server.startDb();
        pool = server.getBrokerPool();

        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = transact.beginTransaction()) {
            final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            broker.saveCollection(transaction, root);
            if (indexed) {
                final CollectionConfigurationManager mgr = pool.getConfigurationManager();
                mgr.addConfiguration(transaction, broker, root, COLLECTION_CONFIG);
            }
            for (int i = 1; i <= docCount; i++) {
                broker.storeDocument(transaction, XmldbURI.create("doc" + i + ".xml"),
                        new StringInputSource("<item><id>id-" + i + "</id></item>"), MimeType.XML_TYPE, root);
            }

            // the "for" input of whereVariableAsKey must be persistent nodes outside of the item collection
            final Collection queries = broker.getOrCreateCollection(transaction, QUERIES_COLLECTION_URI);
            broker.saveCollection(transaction, queries);
            final StringBuilder queriesDoc = new StringBuilder("<queries>");
            for (int i = 1; i <= LOOKUPS; i++) {
                queriesDoc.append("<q>").append(key(i)).append("</q>");
            }
            queriesDoc.append("</queries>");
            broker.storeDocument(transaction, XmldbURI.create("queries.xml"),
                    new StringInputSource(queriesDoc.toString()), MimeType.XML_TYPE, queries);

            transact.commit(transaction);
        }

        final StringBuilder keys = new StringBuilder("(");
        for (int i = 1; i <= LOOKUPS; i++) {
            if (i > 1) {
                keys.append(", ");
            }
            keys.append('\'').append(key(i)).append('\'');
        }
        keys.append(')');

        final String items = "collection('" + TestConstants.TEST_COLLECTION_URI + "')/item";
        queryPredicateLiteral = """
                count(for $i in 1 to %d return %s[id = '%s'])
                """.formatted(LOOKUPS, items, key(1));
        queryPredicateOuterVar = """
                let $key := '%s'
                return count(for $i in 1 to %d return %s[id = $key])
                """.formatted(key(1), LOOKUPS, items);
        queryPredicateLoopVar = """
                count(for $key in %s return %s[id = $key])
                """.formatted(keys, items);
        queryWhereNavigating = """
                count(for $item in %s where $item/id = '%s' return $item)
                """.formatted(items, key(1));
        queryWhereVariableAsKey = """
                declare variable $items := %s;
                count(for $q in doc('%s/queries.xml')//q where $items[id = $q] return $q)
                """.formatted(items, QUERIES_COLLECTION_URI);

        xquery = pool.getXQueryService();
        final long hits = run(queryPredicateLoopVar).itemAt(0).toJavaObject(Long.class);
        if (hits != LOOKUPS) {
            throw new IllegalStateException("Expected " + LOOKUPS + " hits for the loop variable predicate, got " + hits);
        }
    }

    /**
     * Keys spread across the collection, not just its start.
     */
    private String key(final int lookup) {
        return "id-" + Math.max(1, lookup * docCount / LOOKUPS);
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
    public Sequence predicateLiteral() throws Exception {
        return run(queryPredicateLiteral);
    }

    @Benchmark
    public Sequence predicateOuterVar() throws Exception {
        return run(queryPredicateOuterVar);
    }

    @Benchmark
    public Sequence predicateLoopVar() throws Exception {
        return run(queryPredicateLoopVar);
    }

    @Benchmark
    public Sequence predicateLoopVarUnoptimized() throws Exception {
        return run("declare option exist:optimize 'enable=no'; " + queryPredicateLoopVar);
    }

    @Benchmark
    public Sequence whereNavigating() throws Exception {
        return run(queryWhereNavigating);
    }

    @Benchmark
    public Sequence whereVariableAsKey() throws Exception {
        return run(queryWhereVariableAsKey);
    }
}
