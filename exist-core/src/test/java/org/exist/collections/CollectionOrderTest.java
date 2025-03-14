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

package org.exist.collections;

import org.exist.EXistException;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * Tests around the ordering of Collections and Documents
 * within a Collection
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class CollectionOrderTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static XmldbURI TEST_COLLECTION = XmldbURI.ROOT_COLLECTION_URI.append("testCollectionOrder");

    private static final int SUB_COLLECTION_COUNT = 1000;
    private static final int DOCUMENT_COUNT = 1000;
    private static final int MIN_NAME_LEN = 8;
    private static final int MAX_NAME_LEN = 20;

    private final Random random = new Random();

    /**
     * Ensures that when iterating over Collections the order of iteration is always the same
     */
    @Test
    public void collectionOrderIsOldestInsertedFirst() throws EXistException, PermissionDeniedException, IOException, TriggerException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();

        final List<String> subCollectionNames1 = generateRandomNames(SUB_COLLECTION_COUNT / 2);
        createSubCollections(pool, subCollectionNames1);

        // we delete some collections to ensure that collection ids will be reused in the next createSubCollections step
        final List<String> subCollectionNames1_toDelete = selectRandom(subCollectionNames1, SUB_COLLECTION_COUNT / 10);
        deleteSubCollections(pool, subCollectionNames1_toDelete);

        final List<String> subsetSubCollectionNames1 = filterFrom(subCollectionNames1, subCollectionNames1_toDelete);

        final List<String> subCollectionNames2 = generateRandomNames(SUB_COLLECTION_COUNT / 2);
        createSubCollections(pool, subCollectionNames2);

        final List<String> allSubCollectionNames = new ArrayList<>(subsetSubCollectionNames1.size() + subCollectionNames2.size());
        allSubCollectionNames.addAll(subsetSubCollectionNames1);
        allSubCollectionNames.addAll(subCollectionNames2);
        assertOrderOfSubCollections(pool, allSubCollectionNames);
    }

    /**
     * Ensures that when iterating over Collections the order of iteration is always the same
     * and that this persists across restarts of database
     */
    @Test
    public void collectionOrderIsOldestInsertedFirst_persistedOverRestart() throws EXistException, PermissionDeniedException, IOException, TriggerException, LockException, DatabaseConfigurationException {
        BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final List<String> subCollectionNames1 = generateRandomNames(SUB_COLLECTION_COUNT);
        createSubCollections(pool, subCollectionNames1);

        // we delete some collections to ensure that collection ids will be reused in the next createSubCollections step
        final List<String> subCollectionNames1_toDelete = selectRandom(subCollectionNames1, SUB_COLLECTION_COUNT / 10);
        deleteSubCollections(pool, subCollectionNames1_toDelete);

        final List<String> subsetSubCollectionNames1 = filterFrom(subCollectionNames1, subCollectionNames1_toDelete);

        // check the order of sub-collections
        assertOrderOfSubCollections(pool, subsetSubCollectionNames1);

        // restart the server to ensure the order is correctly persisted
        existEmbeddedServer.restart();
        pool = existEmbeddedServer.getBrokerPool();

        // check the order of sub-collections
        assertOrderOfSubCollections(pool, subsetSubCollectionNames1);

        // add some more sub-collections
        final List<String> subCollectionNames2 = generateRandomNames(SUB_COLLECTION_COUNT / 2);
        createSubCollections(pool, subCollectionNames2);

        // check the order of all sub-collections
        final List<String> allSubCollectionNames = new ArrayList<>(subsetSubCollectionNames1.size() + subCollectionNames2.size());
        allSubCollectionNames.addAll(subsetSubCollectionNames1);
        allSubCollectionNames.addAll(subCollectionNames2);
        assertOrderOfSubCollections(pool, allSubCollectionNames);

        // restart the server to ensure the order is correctly persisted
        existEmbeddedServer.restart();
        pool = existEmbeddedServer.getBrokerPool();

        // check the order of all sub-collections
        assertOrderOfSubCollections(pool, allSubCollectionNames);
    }

    /**
     * Ensures that when iterating over Documents the order of iteration is always the same
     */
    @Test
    public void documentOrderIsOldestInsertedFirst() throws EXistException, PermissionDeniedException, IOException, SAXException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();

        final List<String> documentNames1 = generateRandomNames(DOCUMENT_COUNT / 2);
        createDocuments(pool, documentNames1);

        // we delete some documents to ensure that documents ids will be reused in the next createDocuments step
        final List<String> documentNames1_toDelete = selectRandom(documentNames1, DOCUMENT_COUNT / 10);
        deleteDocuments(pool, documentNames1_toDelete);

        final List<String> subsetDocumentNames1 = filterFrom(documentNames1, documentNames1_toDelete);

        final List<String> documentNames2 = generateRandomNames(DOCUMENT_COUNT / 2);
        createDocuments(pool, documentNames2);

        final List<String> allDocumentNames = new ArrayList<>(subsetDocumentNames1.size() + documentNames2.size());
        allDocumentNames.addAll(subsetDocumentNames1);
        allDocumentNames.addAll(documentNames2);
        assertOrderOfDocuments(pool, allDocumentNames);
    }

// document order does not need to be persisted over restart, as long as it is consistent when the server is running! Which is shown by {@link #documentOrderIsOldestInsertedFirst())
//    /**
//     * Ensures that when iterating over Documents the order of iteration is always the same
//     * and that this persists across restarts of database
//     */
//    @Test
//    public void documentOrderIsOldestInsertedFirst_persistedOverRestart() throws EXistException, PermissionDeniedException, IOException, SAXException, LockException, DatabaseConfigurationException {
//        BrokerPool pool = existEmbeddedServer.getBrokerPool();
//        final List<String> documentNames1 = generateRandomNames(DOCUMENT_COUNT);
//        createDocuments(pool, documentNames1);
//
//        // we delete some documents to ensure that document ids will be reused in the next createDocuments step
//        final List<String> documentNames1_toDelete = selectRandom(documentNames1, DOCUMENT_COUNT / 10);
//        deleteDocuments(pool, documentNames1_toDelete);
//
//        final List<String> subsetDocumentNames1 = filterFrom(documentNames1, documentNames1_toDelete);
//
//        // check the order of documents
//        assertOrderOfDocuments(pool, subsetDocumentNames1);
//
//        // restart the server to ensure the order is correctly persisted
//        existEmbeddedServer.restart();
//        pool = existEmbeddedServer.getBrokerPool();
//
//        // check the order of documents
//        assertOrderOfDocuments(pool, subsetDocumentNames1);
//
//        // add some more documents
//        final List<String> documentNames2 = generateRandomNames(DOCUMENT_COUNT / 2);
//        createDocuments(pool, documentNames2);
//
//        // check the order of all documents
//        final List<String> allDocumentNames = new ArrayList<>(subsetDocumentNames1.size() + documentNames2.size());
//        allDocumentNames.addAll(subsetDocumentNames1);
//        allDocumentNames.addAll(documentNames2);
//        assertOrderOfDocuments(pool, allDocumentNames);
//
//        // restart the server to ensure the order is correctly persisted
//        existEmbeddedServer.restart();
//        pool = existEmbeddedServer.getBrokerPool();
//
//        // check the order of all documents
//        assertOrderOfDocuments(pool, allDocumentNames);
//    }

    private void createSubCollections(final BrokerPool pool, final List<String> subCollectionNames) throws EXistException, PermissionDeniedException, IOException, TriggerException {
        // create the collections
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            for(final String subCollectionName : subCollectionNames) {
                broker.getOrCreateCollection(transaction, TEST_COLLECTION.append(subCollectionName));
            }
            transaction.commit();
        }
    }

    private void deleteSubCollections(final BrokerPool pool, final List<String> subCollectionNames) throws EXistException, PermissionDeniedException, IOException, TriggerException {
        // delete the collections
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            for(final String subCollectionName : subCollectionNames) {
                try(final Collection collection = broker.openCollection(TEST_COLLECTION.append(subCollectionName), Lock.LockMode.WRITE_LOCK)) {
                    if(collection == null) {
                        throw new IllegalStateException("Cannot remove non-existent Collection");
                    }

                    broker.removeCollection(transaction, collection);
                }
            }
            transaction.commit();
        }
    }

    /**
     * Filters items out of a list
     *
     * @param sourceList The list to filter items from
     * @param excludeList The items to filter out of the sourceList
     *
     * @return sourceList - excludeList
     */
    private <T> List<T> filterFrom(final List<T> sourceList, final List<T> excludeList) {
        final Set<T> set = new HashSet<>(excludeList);
        return sourceList.stream()
                .filter(sourceItem -> !excludeList.contains(sourceItem))
                .collect(Collectors.toList());

    }

    /**
     * Select n random elements from a list
     *
     * @param list A list to select from
     * @param count The number of random items to select from the list
     */
    private <T> List<T> selectRandom(final List<T> list, final int count) {
        if(count > list.size()) {
            throw new IllegalArgumentException("count is larger than the list size");
        }

        final Set<Integer> idxs = new HashSet<>();
        while(idxs.size() < count) {
            idxs.add(random(0, list.size() -1));
        }

        return idxs.stream()
                .map(list::get)
                .collect(Collectors.toList());
    }

    private void assertOrderOfSubCollections(final BrokerPool pool, final List<String> subCollectionNames) throws EXistException, PermissionDeniedException, LockException {
        // iterate the collections ensuring they are in the same order as we created them
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            try(final Collection testCollection = broker.openCollection(TEST_COLLECTION, Lock.LockMode.READ_LOCK)) {
                final Iterator<XmldbURI> subCollections = testCollection.collectionIterator(broker);
                int idx = 0;
                while(subCollections.hasNext()) {
                    final XmldbURI subCollection = subCollections.next();

                    final String subCollectionName = subCollectionNames.get(idx++);

                    assertEquals("sub-Collection names are not equal at index: " + idx, subCollectionName, subCollection.lastSegment().toString());
                }
            }

            transaction.commit();
        }
    }

    private void createDocuments(final BrokerPool pool, final List<String> documentNames) throws EXistException, PermissionDeniedException, IOException, SAXException, LockException {
        // create the documents
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            try(final Collection testCollection = broker.openCollection(TEST_COLLECTION, Lock.LockMode.WRITE_LOCK)) {
                for (final String documentName : documentNames) {
                    final String xml = "<document id='" + UUID.randomUUID() + "'><name>" + documentName + "</name></document>";

                    broker.storeDocument(transaction, XmldbURI.create(documentName), new StringInputSource(xml), MimeType.XML_TYPE, testCollection);
                }
            }

            transaction.commit();
        }
    }

    private void deleteDocuments(final BrokerPool pool, final List<String> documentNames) throws EXistException, PermissionDeniedException, IOException, TriggerException, LockException {
        // delete the collections
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            try(final Collection collection = broker.openCollection(TEST_COLLECTION, Lock.LockMode.WRITE_LOCK)) {

                for (final String documentName : documentNames) {
                    collection.removeXMLResource(transaction, broker, XmldbURI.create(documentName));
                }
            }
            transaction.commit();
        }
    }

    private void assertOrderOfDocuments(final BrokerPool pool, final List<String> documentNames) throws EXistException, PermissionDeniedException, LockException {
        // iterate the collections ensuring they are in the same order as we created them
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            try(final Collection testCollection = broker.openCollection(TEST_COLLECTION, Lock.LockMode.READ_LOCK)) {
                final Iterator<DocumentImpl> documents = testCollection.iterator(broker);
                int idx = 0;
                while(documents.hasNext()) {
                    final DocumentImpl document = documents.next();

                    final String documentName = documentNames.get(idx++);

                    assertEquals("Document names are not equal at index: " + idx, documentName, document.getFileURI().lastSegment().toString());
                }
            }

            transaction.commit();
        }
    }

    private List<String> generateRandomNames(final int count) {
        final List<String> randomNames = new ArrayList<>();
        for(int i = 0; i < count; i++) {
            randomNames.add(generateRandomName(random(MIN_NAME_LEN, MAX_NAME_LEN)));
        }
        return randomNames;
    }

    private String generateRandomName(final int nameLength) {
        final StringBuilder name = new StringBuilder();
        final int a = 'a';
        final int z = 'z';
        for(int i = 0; i < nameLength; i++) {
            final char c = (char)(random(a, z) & 0xFF);
            name.append(c);
        }
        return name.toString();
    }

    private int random(final int minInc, final int maxInc) {
        return random.nextInt((maxInc - minInc) + 1) + minInc;
    }

    @Before
    public void createTestCollection() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            broker.getOrCreateCollection(transaction, TEST_COLLECTION);
            transaction.commit();
        }
    }

    @After
    public void removeTestCollection() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            try(final Collection testCollection = broker.openCollection(TEST_COLLECTION, Lock.LockMode.WRITE_LOCK)) {
                if(testCollection != null) {
                    broker.removeCollection(transaction, testCollection);
                }
                transaction.commit();
            }
        }
    }
}
