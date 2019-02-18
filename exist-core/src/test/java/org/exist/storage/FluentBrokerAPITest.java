/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.storage;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import com.evolvedbinary.j8fu.tuple.Tuple3;
import org.easymock.IMocksControl;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionMetadata;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentMetadata;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.Test;

import java.util.function.BiFunction;
import java.util.function.Function;

import static org.easymock.EasyMock.createStrictControl;
import static org.easymock.EasyMock.expect;
import static org.exist.storage.FluentBrokerAPI.uri;
import static org.exist.storage.lock.Lock.LockMode.*;
import static org.junit.Assert.assertEquals;

public class FluentBrokerAPITest {

    private static final XmldbURI TEST_COLLECTION_URI = uri("/db/fluent-broker-api-test");

    @Test
    public void all() throws PermissionDeniedException, EXistException, LockException {
        final XmldbURI docUri = uri("all-test.xml");
        final long collectionCreated = 1234;
        final long docLastModified = 5678;

        final IMocksControl ctrl = createStrictControl();
        ctrl.checkOrder(true);

        final BrokerPool mockBrokerPool = ctrl.createMock(BrokerPool.class);
        final DBBroker mockBroker = ctrl.createMock(DBBroker.class);
        final Collection mockCollection = ctrl.createMock(Collection.class);
        final CollectionMetadata mockCollectionMetadata = ctrl.createMock(CollectionMetadata.class);
        final LockedDocument mockLockedDocument = ctrl.createMock(LockedDocument.class);
        final DocumentImpl mockDocument = ctrl.createMock(DocumentImpl.class);
        final DocumentMetadata mockDocumentMetadata = ctrl.createMock(DocumentMetadata.class);

        expect(mockBrokerPool.getBroker()).andReturn(mockBroker);
        expect(mockBroker.openCollection(TEST_COLLECTION_URI, READ_LOCK)).andReturn(mockCollection);
        expect(mockCollection.getMetadata()).andReturn(mockCollectionMetadata);
        expect(mockCollectionMetadata.getCreated()).andReturn(collectionCreated);
        expect(mockCollection.getDocumentWithLock(mockBroker, docUri, READ_LOCK)).andReturn(mockLockedDocument);
        expect(mockLockedDocument.getDocument()).andReturn(mockDocument);
        expect(mockCollection.getURI()).andReturn(TEST_COLLECTION_URI);
        expect(mockDocument.getFileURI()).andReturn(docUri);
        mockCollection.close();      // NOTE: checks that Collection lock is release before Document lock
        expect(mockDocument.getMetadata()).andReturn(mockDocumentMetadata);
        expect(mockDocumentMetadata.getLastModified()).andReturn(docLastModified);
        mockLockedDocument.close();
        mockBroker.close();


        ctrl.replay();

        final Function<Collection, Long> collectionOp = collection -> collection.getMetadata().getCreated();
        final BiFunction<Collection, DocumentImpl, String> collectionDocOp = (collection, doc) -> collection.getURI().append(doc.getFileURI()).toString();
        final Function<DocumentImpl, Long> documentOp = document -> document.getMetadata().getLastModified();

        final Tuple3<Long, String, Long> result = FluentBrokerAPI.builder(mockBrokerPool)
                .withCollection(TEST_COLLECTION_URI, READ_LOCK)
                .execute(collectionOp)
                .withDocument(collection -> new Tuple2<>(docUri, READ_LOCK))
                .execute(collectionDocOp)
                .withoutCollection()
                .execute(documentOp)
                .doAll();

        assertEquals(collectionCreated, result._1.longValue());
        assertEquals(TEST_COLLECTION_URI.append(docUri), result._2);
        assertEquals(docLastModified, result._3.longValue());

        ctrl.verify();
    }

    @Test
    public void collectionOnly() throws PermissionDeniedException, EXistException, LockException {
        final long collectionCreated = 1234;

        final IMocksControl ctrl = createStrictControl();
        ctrl.checkOrder(true);

        final BrokerPool mockBrokerPool = ctrl.createMock(BrokerPool.class);
        final DBBroker mockBroker = ctrl.createMock(DBBroker.class);
        final Collection mockCollection = ctrl.createMock(Collection.class);
        final CollectionMetadata mockCollectionMetadata = ctrl.createMock(CollectionMetadata.class);

        expect(mockBrokerPool.getBroker()).andReturn(mockBroker);
        expect(mockBroker.openCollection(TEST_COLLECTION_URI, READ_LOCK)).andReturn(mockCollection);
        expect(mockCollection.getMetadata()).andReturn(mockCollectionMetadata);
        expect(mockCollectionMetadata.getCreated()).andReturn(collectionCreated);
        mockCollection.close();
        mockBroker.close();


        ctrl.replay();

        final Function<Collection, Long> collectionOp = collection -> collection.getMetadata().getCreated();

        final long result = FluentBrokerAPI.builder(mockBrokerPool)
                .withCollection(TEST_COLLECTION_URI, READ_LOCK)
                .execute(collectionOp)
                .doAll();

        assertEquals(collectionCreated, result);

        ctrl.verify();
    }

    @Test
    public void collectionAndDocOnly() throws PermissionDeniedException, EXistException, LockException {
        final XmldbURI docUri = uri("all-test.xml");

        final IMocksControl ctrl = createStrictControl();
        ctrl.checkOrder(true);

        final BrokerPool mockBrokerPool = ctrl.createMock(BrokerPool.class);
        final DBBroker mockBroker = ctrl.createMock(DBBroker.class);
        final Collection mockCollection = ctrl.createMock(Collection.class);
        final LockedDocument mockLockedDocument = ctrl.createMock(LockedDocument.class);
        final DocumentImpl mockDocument = ctrl.createMock(DocumentImpl.class);
        final Lock mockDocumentLock = ctrl.createMock(Lock.class);

        expect(mockBrokerPool.getBroker()).andReturn(mockBroker);
        expect(mockBroker.openCollection(TEST_COLLECTION_URI, READ_LOCK)).andReturn(mockCollection);
        expect(mockCollection.getDocumentWithLock(mockBroker, docUri, READ_LOCK)).andReturn(mockLockedDocument);
        expect(mockLockedDocument.getDocument()).andReturn(mockDocument);
        expect(mockCollection.getURI()).andReturn(TEST_COLLECTION_URI);
        expect(mockDocument.getFileURI()).andReturn(docUri);
        mockCollection.close();      // NOTE: checks that Collection lock is release before Document lock
        mockLockedDocument.close();
        mockBroker.close();


        ctrl.replay();

        final BiFunction<Collection, DocumentImpl, String> collectionDocOp = (collection, doc) -> collection.getURI().append(doc.getFileURI()).toString();

        final String result = FluentBrokerAPI.builder(mockBrokerPool)
                .withCollection(TEST_COLLECTION_URI, READ_LOCK)
                .withDocument(collection -> new Tuple2<>(docUri, READ_LOCK))
                .execute(collectionDocOp)
                .doAll();

        assertEquals(TEST_COLLECTION_URI.append(docUri), result);

        ctrl.verify();
    }

    @Test
    public void docOnly() throws PermissionDeniedException, EXistException, LockException {
        final XmldbURI docUri = uri("all-test.xml");
        final long docLastModified = 5678;

        final IMocksControl ctrl = createStrictControl();
        ctrl.checkOrder(true);

        final BrokerPool mockBrokerPool = ctrl.createMock(BrokerPool.class);
        final DBBroker mockBroker = ctrl.createMock(DBBroker.class);
        final Collection mockCollection = ctrl.createMock(Collection.class);
        final LockedDocument mockLockedDocument = ctrl.createMock(LockedDocument.class);
        final DocumentImpl mockDocument = ctrl.createMock(DocumentImpl.class);
        final DocumentMetadata mockDocumentMetadata = ctrl.createMock(DocumentMetadata.class);

        expect(mockBrokerPool.getBroker()).andReturn(mockBroker);
        expect(mockBroker.openCollection(TEST_COLLECTION_URI, READ_LOCK)).andReturn(mockCollection);
        expect(mockCollection.getDocumentWithLock(mockBroker, docUri, READ_LOCK)).andReturn(mockLockedDocument);
        expect(mockLockedDocument.getDocument()).andReturn(mockDocument);
        mockCollection.close();      // NOTE: checks that Collection lock is release before Document lock
        expect(mockDocument.getMetadata()).andReturn(mockDocumentMetadata);
        expect(mockDocumentMetadata.getLastModified()).andReturn(docLastModified);
        mockLockedDocument.close();
        mockBroker.close();


        ctrl.replay();

        final Function<DocumentImpl, Long> documentOp = document -> document.getMetadata().getLastModified();

        final long result = FluentBrokerAPI.builder(mockBrokerPool)
                .withCollection(TEST_COLLECTION_URI, READ_LOCK)
                .withDocument(collection -> new Tuple2<>(docUri, READ_LOCK))
                .withoutCollection()
                .execute(documentOp)
                .doAll();

        assertEquals(docLastModified, result);

        ctrl.verify();
    }

    @Test
    public void collectionThenCollectionAndDoc() throws PermissionDeniedException, EXistException, LockException {
        final XmldbURI docUri = uri("all-test.xml");
        final long collectionCreated = 1234;

        final IMocksControl ctrl = createStrictControl();
        ctrl.checkOrder(true);

        final BrokerPool mockBrokerPool = ctrl.createMock(BrokerPool.class);
        final DBBroker mockBroker = ctrl.createMock(DBBroker.class);
        final Collection mockCollection = ctrl.createMock(Collection.class);
        final CollectionMetadata mockCollectionMetadata = ctrl.createMock(CollectionMetadata.class);
        final LockedDocument mockLockedDocument = ctrl.createMock(LockedDocument.class);
        final DocumentImpl mockDocument = ctrl.createMock(DocumentImpl.class);

        expect(mockBrokerPool.getBroker()).andReturn(mockBroker);
        expect(mockBroker.openCollection(TEST_COLLECTION_URI, READ_LOCK)).andReturn(mockCollection);
        expect(mockCollection.getMetadata()).andReturn(mockCollectionMetadata);
        expect(mockCollectionMetadata.getCreated()).andReturn(collectionCreated);
        expect(mockCollection.getDocumentWithLock(mockBroker, docUri, READ_LOCK)).andReturn(mockLockedDocument);
        expect(mockLockedDocument.getDocument()).andReturn(mockDocument);
        expect(mockCollection.getURI()).andReturn(TEST_COLLECTION_URI);
        expect(mockDocument.getFileURI()).andReturn(docUri);
        mockCollection.close();      // NOTE: checks that Collection lock is release before Document lock
        mockLockedDocument.close();
        mockBroker.close();


        ctrl.replay();

        final Function<Collection, Long> collectionOp = collection -> collection.getMetadata().getCreated();
        final BiFunction<Collection, DocumentImpl, String> collectionDocOp = (collection, doc) -> collection.getURI().append(doc.getFileURI()).toString();

        final Tuple2<Long, String> result = FluentBrokerAPI.builder(mockBrokerPool)
                .withCollection(TEST_COLLECTION_URI, READ_LOCK)
                .execute(collectionOp)
                .withDocument(collection -> new Tuple2<>(docUri, READ_LOCK))
                .execute(collectionDocOp)
                .doAll();

        assertEquals(collectionCreated, result._1.longValue());
        assertEquals(TEST_COLLECTION_URI.append(docUri), result._2);

        ctrl.verify();
    }

    @Test
    public void collectionThenDoc() throws PermissionDeniedException, EXistException, LockException {
        final XmldbURI docUri = uri("all-test.xml");
        final long collectionCreated = 1234;
        final long docLastModified = 5678;

        final IMocksControl ctrl = createStrictControl();
        ctrl.checkOrder(true);

        final BrokerPool mockBrokerPool = ctrl.createMock(BrokerPool.class);
        final DBBroker mockBroker = ctrl.createMock(DBBroker.class);
        final Collection mockCollection = ctrl.createMock(Collection.class);
        final CollectionMetadata mockCollectionMetadata = ctrl.createMock(CollectionMetadata.class);
        final LockedDocument mockLockedDocument = ctrl.createMock(LockedDocument.class);
        final DocumentImpl mockDocument = ctrl.createMock(DocumentImpl.class);
        final DocumentMetadata mockDocumentMetadata = ctrl.createMock(DocumentMetadata.class);

        expect(mockBrokerPool.getBroker()).andReturn(mockBroker);
        expect(mockBroker.openCollection(TEST_COLLECTION_URI, READ_LOCK)).andReturn(mockCollection);
        expect(mockCollection.getMetadata()).andReturn(mockCollectionMetadata);
        expect(mockCollectionMetadata.getCreated()).andReturn(collectionCreated);
        expect(mockCollection.getDocumentWithLock(mockBroker, docUri, READ_LOCK)).andReturn(mockLockedDocument);
        expect(mockLockedDocument.getDocument()).andReturn(mockDocument);
        mockCollection.close();      // NOTE: checks that Collection lock is release before Document lock
        expect(mockDocument.getMetadata()).andReturn(mockDocumentMetadata);
        expect(mockDocumentMetadata.getLastModified()).andReturn(docLastModified);
        mockLockedDocument.close();
        mockBroker.close();


        ctrl.replay();

        final Function<Collection, Long> collectionOp = collection -> collection.getMetadata().getCreated();
        final Function<DocumentImpl, Long> documentOp = document -> document.getMetadata().getLastModified();

        final Tuple2<Long, Long> result = FluentBrokerAPI.builder(mockBrokerPool)
                .withCollection(TEST_COLLECTION_URI, READ_LOCK)
                .execute(collectionOp)
                .withDocument(collection -> new Tuple2<>(docUri, READ_LOCK))
                .withoutCollection()
                .execute(documentOp)
                .doAll();

        assertEquals(collectionCreated, result._1.longValue());
        assertEquals(docLastModified, result._2.longValue());

        ctrl.verify();
    }

    @Test
    public void collectionAndDocThenDoc() throws PermissionDeniedException, EXistException, LockException {
        final XmldbURI docUri = uri("all-test.xml");
        final long docLastModified = 5678;

        final IMocksControl ctrl = createStrictControl();
        ctrl.checkOrder(true);

        final BrokerPool mockBrokerPool = ctrl.createMock(BrokerPool.class);
        final DBBroker mockBroker = ctrl.createMock(DBBroker.class);
        final Collection mockCollection = ctrl.createMock(Collection.class);
        final LockedDocument mockLockedDocument = ctrl.createMock(LockedDocument.class);
        final DocumentImpl mockDocument = ctrl.createMock(DocumentImpl.class);
        final DocumentMetadata mockDocumentMetadata = ctrl.createMock(DocumentMetadata.class);

        expect(mockBrokerPool.getBroker()).andReturn(mockBroker);
        expect(mockBroker.openCollection(TEST_COLLECTION_URI, READ_LOCK)).andReturn(mockCollection);
        expect(mockCollection.getDocumentWithLock(mockBroker, docUri, READ_LOCK)).andReturn(mockLockedDocument);
        expect(mockLockedDocument.getDocument()).andReturn(mockDocument);
        expect(mockCollection.getURI()).andReturn(TEST_COLLECTION_URI);
        expect(mockDocument.getFileURI()).andReturn(docUri);
        mockCollection.close();      // NOTE: checks that Collection lock is release before Document lock
        expect(mockDocument.getMetadata()).andReturn(mockDocumentMetadata);
        expect(mockDocumentMetadata.getLastModified()).andReturn(docLastModified);
        mockLockedDocument.close();
        mockBroker.close();


        ctrl.replay();

        final BiFunction<Collection, DocumentImpl, String> collectionDocOp = (collection, doc) -> collection.getURI().append(doc.getFileURI()).toString();
        final Function<DocumentImpl, Long> documentOp = document -> document.getMetadata().getLastModified();

        final Tuple2<String, Long> result = FluentBrokerAPI.builder(mockBrokerPool)
                .withCollection(TEST_COLLECTION_URI, READ_LOCK)
                .withDocument(collection -> new Tuple2<>(docUri, READ_LOCK))
                .execute(collectionDocOp)
                .withoutCollection()
                .execute(documentOp)
                .doAll();

        assertEquals(TEST_COLLECTION_URI.append(docUri), result._1);
        assertEquals(docLastModified, result._2.longValue());

        ctrl.verify();
    }


}
