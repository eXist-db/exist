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

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.BrokerPool;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Regression test for GitHub #4074: range:index-keys-for-field returns empty
 * when called from XQueryServlet (or when getStaticallyKnownDocuments is empty).
 * <p>
 * This test simulates the XQueryServlet execution context by setting
 * statically known documents to an empty set before running the query,
 * ensuring our fallback to getAllXMLResources is exercised.
 *
 * @see <a href="https://github.com/eXist-db/exist/issues/4074">#4074</a>
 */
public class Issue4074IndexKeysServletContextTest {

    private static final String COLLECTION_NAME = "i4074-servlet-test";
    private static final String COLLECTION_CONFIG =
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
                    + "<index xmlns:tei=\"http://www.tei-c.org/ns/1.0\">"
                    + "<range>"
                    + "<create qname=\"tei:test\">"
                    + "<field name=\"elem-field\" match=\"tei:elem\" type=\"xs:string\" case=\"no\"/>"
                    + "</create>"
                    + "</range>"
                    + "</index>"
                    + "</collection>";

    private static final String DATA1 = "<test xmlns=\"http://www.tei-c.org/ns/1.0\">"
            + "<elem>a</elem><elem>b</elem><elem>c</elem>"
            + "</test>";

    private static final String DATA2 = "<test xmlns=\"http://www.tei-c.org/ns/1.0\">"
            + "<elem>a</elem><elem>b</elem><elem>c</elem><elem>b</elem><elem>y</elem>"
            + "</test>";

    private static final String INDEX_KEYS_QUERY =
            """
            import module namespace range = "http://exist-db.org/xquery/range" \
            at "java:org.exist.xquery.modules.range.RangeIndexModule";
            range:index-keys-for-field("elem-field", function($key, $nums) { $key }, 100)""";

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static XmldbURI collectionUri;

    @BeforeClass
    public static void setUp() throws EXistException, PermissionDeniedException, LockException, TriggerException, SAXException, CollectionConfigurationException, IOException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        collectionUri = XmldbURI.ROOT_COLLECTION_URI.append(COLLECTION_NAME);
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            createFixtureCollection(pool, broker, collectionUri, COLLECTION_CONFIG, DATA1, DATA2);
        }
    }

    @AfterClass
    public static void tearDown() throws EXistException, PermissionDeniedException, LockException, TriggerException, IOException {
        if (collectionUri == null) {
            return;
        }
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = transact.beginTransaction();
             final Collection collection = broker.openCollection(collectionUri, org.exist.storage.lock.Lock.LockMode.WRITE_LOCK)) {
            if (collection != null) {
                broker.removeCollection(transaction, collection);
            }
            transact.commit(transaction);
        }
    }

    @Test
    public void indexKeysForFieldWithEmptyStaticDocsReturnsKeys() throws EXistException, PermissionDeniedException, XPathException, ParserConfigurationException, IOException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Sequence result = executeWithEmptyStaticDocs(pool, broker, INDEX_KEYS_QUERY);
            assertKeys(result, Set.of("a", "b", "c", "y"), 4,
                "With empty static docs, range:index-keys-for-field should fall back to all docs and return 4 keys");
        }
    }

    private static void createFixtureCollection(final BrokerPool pool, final DBBroker broker, final XmldbURI targetCollectionUri,
                                                final String collectionConfig, final String doc1, final String doc2)
            throws LockException, TriggerException, PermissionDeniedException, IOException, EXistException, CollectionConfigurationException, SAXException {
        final TransactionManager transact = pool.getTransactionManager();
        try (final Txn transaction = transact.beginTransaction()) {
            final Collection col = broker.getOrCreateCollection(transaction, targetCollectionUri);
            broker.saveCollection(transaction, col);

            final CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(transaction, broker, col, collectionConfig);

            broker.storeDocument(transaction, XmldbURI.create("test.xml"),
                new StringInputSource(doc1), MimeType.XML_TYPE, col);
            broker.storeDocument(transaction, XmldbURI.create("test2.xml"),
                new StringInputSource(doc2), MimeType.XML_TYPE, col);

            broker.reindexCollection(transaction, col.getURI());
            transact.commit(transaction);
        }
    }

    private static Sequence executeWithEmptyStaticDocs(final BrokerPool pool, final DBBroker broker, final String queryText)
            throws XPathException, PermissionDeniedException {
        final XQuery xquery = pool.getXQueryService();
        assertNotNull(xquery);

        final org.exist.xquery.XQueryContext context = new org.exist.xquery.XQueryContext(pool);
        context.setBaseURI(new org.exist.xquery.value.AnyURIValue("/db"));
        final CompiledXQuery compiled = xquery.compile(context, queryText);

        // Simulate XQueryServlet/REST: statically known documents is empty.
        compiled.getContext().setStaticallyKnownDocuments(new XmldbURI[0]);
        return xquery.execute(broker, compiled, null);
    }

    private static void assertKeys(final Sequence result, final Set<String> expectedKeys, final int expectedCount, final String message)
            throws XPathException {
        assertNotNull(result);
        assertEquals(message, expectedCount, result.getItemCount());

        final Set<String> keys = new HashSet<>();
        for (int i = 0; i < result.getItemCount(); i++) {
            keys.add(result.itemAt(i).getStringValue());
        }
        assertEquals(expectedKeys, keys);
    }
}
