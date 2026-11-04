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

import org.exist.dom.persistent.DocumentImpl;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The URIs a document's and a collection's lock keys are made from are the same whichever way they are made, also
 * for names not stored in canonical form, which the fast paths of {@link DocumentImpl#getURI()} and
 * {@link MutableCollection#getParentURI()} leave to the parsing they replace.
 */
class CanonicalLockKeysTest {

    @RegisterExtension
    static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    void aRawDocumentNameGivesTheParsedURI() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Collection collection = new MutableCollection(broker, 1, XmldbURI.create("/db/c"));
            final DocumentImpl document = new DocumentImpl(pool, collection, 1, XmldbURI.createInternal("My Coll.xml"));
            assertEquals(XmldbURI.create("/db/c/My Coll.xml"), document.getURI());
            assertEquals("/db/c/My%20Coll.xml", document.getURI().toString());
        }
    }

    /**
     * A collection's parent is the path before its last segment, as {@link XmldbURI#removeLastSegment()} gives it.
     * A collection's path is always parsed (the constructor rejects one that isn't, such as {@code /db/My Coll}),
     * so only its percent-encodings and lower-case escapes test the fast path's check.
     */
    @Test
    void aCollectionsParentIsTheParsedOne() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            for (final String path : new String[] {"/db/caf%C3%A9/sub", "/db/caf%c3%a9/sub", "/db/My%20Coll/sub", "/db/a/b/c"}) {
                final XmldbURI uri = XmldbURI.create(path);
                assertEquals(uri.removeLastSegment(), new MutableCollection(broker, 1, uri).getParentURI(), path);
            }
        }
    }

    /**
     * A document stored under a name that isn't in canonical form: the URI its locks are keyed by is the same as
     * the one getDocumentWithLock locks, made from the collection's URI and the name.
     */
    @Test
    void aDocumentStoredUnderARawNameHasOneLockKey() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XmldbURI name = XmldbURI.createInternal("raw name.xml");
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            final Collection collection = broker.getOrCreateCollection(transaction, XmldbURI.create("/db/raw-names"));
            broker.storeDocument(transaction, name, new StringInputSource("<doc/>"), MimeType.XML_TYPE, collection);
            broker.saveCollection(transaction, collection);
            pool.getTransactionManager().commit(transaction);
        }
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Collection collection = broker.openCollection(XmldbURI.create("/db/raw-names"), LockMode.READ_LOCK)) {
            final DocumentImpl document = collection.getDocument(broker, name);
            assertNotNull(document);
            assertEquals(collection.getURI().append(name.lastSegment()), document.getURI(),
                    "the key getDocumentWithLock locks is the document's own");
        }
    }
}
