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
package org.exist.xquery.modules.range;

import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test: one compiled range-optimizable predicate evaluated first against a collection with a range
 * index on the compared attribute, then against a collection without one. Covers both the Lucene-based range index
 * and the legacy range index, which reaches the same code through {@link Lookup}'s fallback.
 *
 * Before the fix, the second evaluation returned no hits: the pre-selection made for the indexed collection was
 * still held by {@link Lookup} when the optimizer declined to optimize for the unindexed one, and was used in place
 * of a plain comparison.
 */
class StalePreselectAcrossCollectionsTest {

    @RegisterExtension
    static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String CONFIG_RANGE_INDEX = """
            <collection xmlns="http://exist-db.org/collection-config/1.0"
                        xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <index>
                <range>
                  <create qname="@cat" type="xs:string"/>
                </range>
              </index>
            </collection>""";

    private static final String CONFIG_LEGACY_RANGE_INDEX = """
            <collection xmlns="http://exist-db.org/collection-config/1.0"
                        xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <index>
                <create qname="@cat" type="xs:string"/>
              </index>
            </collection>""";

    private static final String DOC = """
            <doc><div cat="zebra">a</div><div cat="pony">b</div><div cat="zebra">c</div></doc>""";

    private static final XmldbURI COL_INDEXED = XmldbURI.ROOT_COLLECTION_URI.append("stale-preselect-indexed");
    private static final XmldbURI COL_LEGACY = XmldbURI.ROOT_COLLECTION_URI.append("stale-preselect-legacy");
    private static final XmldbURI COL_PLAIN = XmldbURI.ROOT_COLLECTION_URI.append("stale-preselect-plain");

    @BeforeAll
    static void setupCollections() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            createCollectionAndStore(pool, broker, COL_INDEXED, CONFIG_RANGE_INDEX);
            createCollectionAndStore(pool, broker, COL_LEGACY, CONFIG_LEGACY_RANGE_INDEX);
            createCollectionAndStore(pool, broker, COL_PLAIN, null);
        }
    }

    @AfterAll
    static void teardownCollections() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        remove(pool, COL_INDEXED);
        remove(pool, COL_LEGACY);
        remove(pool, COL_PLAIN);
    }

    @Test
    void indexedCollectionFirst() throws Exception {
        assertIndexedCollectionFirst(COL_INDEXED);
    }

    @Test
    void plainCollectionFirst() throws Exception {
        assertPlainCollectionFirst(COL_INDEXED);
    }

    @Test
    void indexedCollectionTwiceAroundPlain() throws Exception {
        assertIndexedCollectionTwiceAroundPlain(COL_INDEXED);
    }

    @Test
    void legacyIndexedCollectionFirst() throws Exception {
        assertIndexedCollectionFirst(COL_LEGACY);
    }

    @Test
    void legacyPlainCollectionFirst() throws Exception {
        assertPlainCollectionFirst(COL_LEGACY);
    }

    @Test
    void legacyIndexedCollectionTwiceAroundPlain() throws Exception {
        assertIndexedCollectionTwiceAroundPlain(COL_LEGACY);
    }

    private void assertIndexedCollectionFirst(final XmldbURI indexed) throws Exception {
        assertEquals(indexed + "=2 " + COL_PLAIN + "=2", countPerCollection(indexed, COL_PLAIN),
                "the unindexed collection must not reuse the indexed collection's pre-selection");
    }

    private void assertPlainCollectionFirst(final XmldbURI indexed) throws Exception {
        assertEquals(COL_PLAIN + "=2 " + indexed + "=2", countPerCollection(COL_PLAIN, indexed),
                "both collections must return their hits in this order too");
    }

    private void assertIndexedCollectionTwiceAroundPlain(final XmldbURI indexed) throws Exception {
        assertEquals(indexed + "=2 " + COL_PLAIN + "=2 " + indexed + "=2",
                countPerCollection(indexed, COL_PLAIN, indexed),
                "the indexed collection must still be optimized after an unindexed one");
    }

    private String countPerCollection(final XmldbURI... collections) throws Exception {
        final StringBuilder paths = new StringBuilder();
        for (final XmldbURI col : collections) {
            if (!paths.isEmpty()) {
                paths.append(", ");
            }
            paths.append('"').append(col).append('"');
        }
        final String query = """
                string-join(
                    for $c in (%s)
                    return $c || "=" || count(collection($c)//div[@cat = "zebra"]),
                    " ")""".formatted(paths);

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xqs = pool.getXQueryService();
            final CompiledXQuery compiled = xqs.compile(new XQueryContext(pool), query);
            final Sequence result = xqs.execute(broker, compiled, null);
            return result.getStringValue();
        }
    }

    private static void createCollectionAndStore(final BrokerPool pool, final DBBroker broker, final XmldbURI col,
            final String configXml) throws Exception {
        final TransactionManager tm = pool.getTransactionManager();
        try (final Txn txn = tm.beginTransaction()) {
            final Collection collection = broker.getOrCreateCollection(txn, col);
            broker.saveCollection(txn, collection);
            if (configXml != null) {
                final CollectionConfigurationManager cm = pool.getConfigurationManager();
                cm.addConfiguration(txn, broker, collection, configXml);
            }
            broker.storeDocument(txn, XmldbURI.create("doc.xml"), new StringInputSource(DOC), MimeType.XML_TYPE,
                    collection);
            tm.commit(txn);
        }
    }

    private static void remove(final BrokerPool pool, final XmldbURI col) throws Exception {
        final TransactionManager tm = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn txn = tm.beginTransaction();
                final Collection collection = broker.openCollection(col, Lock.LockMode.WRITE_LOCK)) {
            if (collection != null) {
                broker.removeCollection(txn, collection);
            }
            tm.commit(txn);
        }
    }
}
