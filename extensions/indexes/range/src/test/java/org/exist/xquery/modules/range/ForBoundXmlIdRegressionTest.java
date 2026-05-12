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
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

/**
 * Regression test: range index lookup with for-bound value comparand.
 *
 * Before the fix, {@code //*[@indexed = $v]} with {@code $v} bound by a {@code for}
 * expression and {@code @indexed} configured with a Lucene-backed range index returned
 * every element with {@code @indexed} (one row per outer iter) instead of the matching
 * elements only. The literal-key form {@code //*[@indexed = "X"]} was unaffected.
 *
 * Root cause: Lookup.eval's preselect-result branch evaluated the path expression
 * against {@code contextSequence} (the full preloaded set) instead of against the
 * per-item {@code effectiveContextSequence}, so BOOLEAN-mode per-item predicate
 * evaluation returned the same non-empty NodeSet for every context item.
 */
public class ForBoundXmlIdRegressionTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String CONFIG_LEGACY_ONLY = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
              <index/>
            </collection>""";

    private static final String CONFIG_RANGE_INDEX = """
            <collection xmlns="http://exist-db.org/collection-config/1.0"
                        xmlns:xs="http://www.w3.org/2001/XMLSchema"
                        xmlns:xml="http://www.w3.org/XML/1998/namespace">
              <index>
                <range>
                  <create qname="@xml:id" type="xs:string"/>
                </range>
              </index>
            </collection>""";

    private static final int N_ITEMS = 100;
    private static final int N_KEYS = 5;

    private static final XmldbURI COL_LEGACY = XmldbURI.ROOT_COLLECTION_URI.append("forbound-xmlid-L");
    private static final XmldbURI COL_RANGE = XmldbURI.ROOT_COLLECTION_URI.append("forbound-xmlid-N");

    @BeforeClass
    public static void setupCollections() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            createCollectionAndStore(pool, broker, COL_LEGACY, CONFIG_LEGACY_ONLY);
            createCollectionAndStore(pool, broker, COL_RANGE, CONFIG_RANGE_INDEX);
        }
    }

    @AfterClass
    public static void teardownCollections() {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        cleanup(pool, COL_LEGACY);
        cleanup(pool, COL_RANGE);
    }

    @Test
    public void legacyAutoIndexOnlyReturnsExpectedHits() throws Exception {
        final long[] hits = runScenario(COL_LEGACY, N_KEYS);
        assertEquals("L literal expected 1 hit", 1, hits[0]);
        assertEquals("L for-bound expected " + N_KEYS + " hits", N_KEYS, hits[1]);
    }

    @Test
    public void rangeIndexConfiguredReturnsCorrectHits() throws Exception {
        final long[] hits = runScenario(COL_RANGE, N_KEYS);
        assertEquals("N literal expected 1 hit", 1, hits[0]);
        assertEquals("N for-bound expected " + N_KEYS + " hits", N_KEYS, hits[1]);
    }

    /**
     * @return {@code [literalHits, forBoundHits]}
     */
    private long[] runScenario(final XmldbURI col, final int nKeys) throws Exception {
        final String literal = """
                collection("%s")//*[@xml:id = "id0"]""".formatted(col);
        final String forBound = """
                for $v in (for $i in 0 to %d return "id" || $i)
                return collection("%s")//*[@xml:id = $v]""".formatted(nKeys - 1, col);

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            return new long[] { run(broker, literal), run(broker, forBound) };
        }
    }

    private long run(final DBBroker broker, final String query) throws XPathException, PermissionDeniedException {
        final BrokerPool pool = broker.getBrokerPool();
        final XQuery xqs = pool.getXQueryService();
        final XQueryContext ctx = new XQueryContext(pool);
        final CompiledXQuery compiled = xqs.compile(ctx, query);
        final Sequence r = xqs.execute(broker, compiled, null);
        return r.getItemCount();
    }

    private static void createCollectionAndStore(final BrokerPool pool, final DBBroker broker,
                                                 final XmldbURI col, final String configXml) throws Exception {
        final TransactionManager tm = pool.getTransactionManager();
        try (final Txn txn = tm.beginTransaction()) {
            final Collection collection = broker.getOrCreateCollection(txn, col);
            broker.saveCollection(txn, collection);
            final CollectionConfigurationManager cm = pool.getConfigurationManager();
            cm.addConfiguration(txn, broker, collection, configXml);

            final String items = IntStream.range(0, N_ITEMS)
                    .mapToObj(i -> "<item xml:id=\"id" + i + "\">v" + i + "</item>")
                    .collect(Collectors.joining());
            final String doc = """
                    <root xmlns:xml="http://www.w3.org/XML/1998/namespace">%s</root>"""
                    .formatted(items);

            broker.storeDocument(txn, XmldbURI.create("data.xml"),
                    new StringInputSource(doc), MimeType.XML_TYPE, collection);
            broker.reindexCollection(txn, collection.getURI());
            tm.commit(txn);
        }
    }

    private static void cleanup(final BrokerPool pool, final XmldbURI col) {
        final TransactionManager tm = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn txn = tm.beginTransaction();
             final Collection c = broker.openCollection(col, org.exist.storage.lock.Lock.LockMode.WRITE_LOCK)) {
            if (c != null) broker.removeCollection(txn, c);
            tm.commit(txn);
        } catch (Exception e) {
            System.err.println("cleanup failed for " + col + ": " + e);
        }
    }
}
