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

package org.exist.collections.triggers;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfiguration;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.junit.*;
import org.xml.sax.SAXException;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;

import static org.junit.Assert.*;

public class HistoryTriggerTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static XmldbURI TEST_COLLECTION_URI = XmldbURI.ROOT_COLLECTION_URI.append("test-history-trigger");
    private static XmldbURI TEST_CONFIG_COLLECTION_URI = XmldbURI.CONFIG_COLLECTION_URI.append(TEST_COLLECTION_URI);

    private static String COLLECTION_CONFIG =
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">\n" +
            "    <triggers>\n" +
            "        <trigger class=\"org.exist.collections.triggers.HistoryTrigger\"/>\n" +
            "    </triggers>\n" +
            "</collection>";

    @Before
    public void setup() throws EXistException, PermissionDeniedException, IOException, SAXException, LockException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
                final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

            // create and store the collection.xconf for the test collection
            Collection configCollection = broker.getOrCreateCollection(transaction, TEST_CONFIG_COLLECTION_URI);
            broker.saveCollection(transaction, configCollection);
            broker.storeDocument(transaction, CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE_URI, new StringInputSource(COLLECTION_CONFIG), MimeType.XML_TYPE, configCollection);

            // create the test collection
            Collection testCollection = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI);
            broker.saveCollection(transaction, testCollection);

            transaction.commit();
        }
    }

    @After
    public void cleanup() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
            final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

            removeCollection(broker, transaction, TEST_CONFIG_COLLECTION_URI);
            removeCollection(broker, transaction, TEST_COLLECTION_URI);
            removeCollection(broker, transaction, HistoryTrigger.DEFAULT_ROOT_PATH);

            transaction.commit();
        }
    }

    private void removeCollection(final DBBroker broker, final Txn transaction, final XmldbURI collectionUri) throws PermissionDeniedException, IOException, TriggerException {
        try(final Collection collection = broker.openCollection(collectionUri, Lock.LockMode.WRITE_LOCK)) {
            broker.removeCollection(transaction, collection);
        }
    }

    /**
     * Ensure that we can store a document and then overwrite it
     * when the {@link HistoryTrigger} is enabled on the Collection
     *
     * @see <a href="https://github.com/eXist-db/exist/issues/139">History trigger fails #139</a>
     */
    @Test
    public void storeAndOverwriteByCopy() throws EXistException, PermissionDeniedException, LockException, SAXException, IOException {
        final XmldbURI testDoc1Name = XmldbURI.create("test_store-and-overwrite-by-copy.xml");
        final String testDoc1Content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<hello>12345</hello>";

        final XmldbURI testDoc2Name = XmldbURI.create("other.xml");
        final String testDoc2Content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<other>thing</other>";

        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
                final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

            // store the first document
            storeInTestCollection(transaction, broker, testDoc1Name, testDoc1Content);

            // store the second document
            storeInTestCollection(transaction, broker, testDoc2Name, testDoc2Content);

            // overwrite the first document by copying the second over it (and make sure we don't get a StackOverflow exception)
            try(final Collection testCollection = broker.openCollection(TEST_COLLECTION_URI, Lock.LockMode.WRITE_LOCK)) {
                assertNotNull(testCollection);

                try(final LockedDocument lockedDoc2 = testCollection.getDocumentWithLock(broker, testDoc2Name, Lock.LockMode.READ_LOCK)) {

                    assertNotNull(lockedDoc2);

                    // copy doc2 over doc1
                    broker.copyResource(transaction, lockedDoc2.getDocument(), testCollection, testDoc1Name);

                    // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                    testCollection.close();
                }
            }

            transaction.commit();
        }

        // check that a copy of the original document was made
        checkHistoryOfOriginal(brokerPool, testDoc1Name, testDoc1Content);
    }

    @Test
    public void storeAndOverwrite() throws EXistException, PermissionDeniedException, LockException, SAXException, IOException {
        final XmldbURI testDocName = XmldbURI.create("test_store-and-overwrite.xml");
        final String testDocContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<hello>world</hello>";
        final String testDoc2Content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<and>another thing</and>";

        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
                final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

            // store the document
            storeInTestCollection(transaction, broker, testDocName, testDocContent);

            // overwrite the document (and make sure we don't get a StackOverflow exception)
            storeInTestCollection(transaction, broker, testDocName, testDoc2Content);

            transaction.commit();
        }

        // check that a copy of the original document was made
        checkHistoryOfOriginal(brokerPool, testDocName, testDocContent);
    }

    private void storeInTestCollection(final Txn transaction, final DBBroker broker, final XmldbURI docName, final String docContent) throws PermissionDeniedException, LockException, SAXException, EXistException, IOException {
        try(final Collection testCollection = broker.openCollection(TEST_COLLECTION_URI, Lock.LockMode.WRITE_LOCK)) {

            assertNotNull(testCollection);

            broker.storeDocument(transaction, docName, new StringInputSource(docContent), MimeType.XML_TYPE, testCollection);
        }
    }

    private void checkHistoryOfOriginal(final BrokerPool brokerPool, final XmldbURI originalDocName, final String orginalDocContent) throws EXistException, PermissionDeniedException, LockException {
        try(final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
                final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

            try(final Collection historyCollection = broker.openCollection(HistoryTrigger.DEFAULT_ROOT_PATH.append(TEST_COLLECTION_URI).append(originalDocName), Lock.LockMode.READ_LOCK)) {
                assertNotNull(historyCollection);

                final DocumentSet documentSet = historyCollection.getDocuments(broker, new DefaultDocumentSet());
                assertEquals(1, documentSet.getDocumentCount());

                final Iterator<DocumentImpl> it = documentSet.getDocumentIterator();
                assertTrue(it.hasNext());

                final DocumentImpl doc = it.next();

                final Diff diff = DiffBuilder.compare(Input.from(orginalDocContent))
                        .withTest(Input.from(doc))
                        .build();
                assertFalse(diff.toString(), diff.hasDifferences());

                assertFalse(it.hasNext());

            }

            transaction.commit();
        }
    }
}
