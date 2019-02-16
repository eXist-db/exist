/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2016 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage;

import com.evolvedbinary.j8fu.tuple.Tuple3;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.QName;
import org.exist.dom.persistent.*;
import org.exist.indexing.StructuralIndex;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.NodeSelector;
import org.junit.ClassRule;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static org.exist.storage.ElementValue.ELEMENT;

public class MoveOverwriteCollectionTest {

    @ClassRule
    public static ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private final static String XML1 =
            "<?xml version=\"1.0\"?>" +
                    "<test1>" +
                    "  <title>Hello1</title>" +
                    "</test1>";

    private final static String XML2 =
            "<?xml version=\"1.0\"?>" +
                    "<test2>" +
                    "  <title>Hello2</title>" +
                    "</test2>";

    private final static String XML3 =
            "<?xml version=\"1.0\"?>" +
                    "<test3>" +
                    "  <title>Hello3</title>" +
                    "</test3>";

    private final static XmldbURI TEST_COLLECTION_URI = XmldbURI.ROOT_COLLECTION_URI.append("test");
    private final static XmldbURI SUB_TEST_COLLECTION_URI = TEST_COLLECTION_URI.append("test2");

    private final static XmldbURI TEST3_COLLECTION_URI = XmldbURI.ROOT_COLLECTION_URI.append("test3");

    private final static XmldbURI doc1Name = XmldbURI.create("doc1.xml");
    private final static XmldbURI doc2Name = XmldbURI.create("doc2.xml");
    private final static XmldbURI doc3Name = XmldbURI.create("doc3.xml");

    /**
     * This test ensures that when moving an Collection over the top of an existing Collection,
     * the overwritten resource is completely removed from the database;
     * i.e. its nodes are no longer present in the structural index
     */
    @Test
    public void moveAndOverwriteCollection() throws Exception  {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Tuple3<Collection, Collection, Collection> collections = store(broker);
            try {

                final DefaultDocumentSet docs = new DefaultDocumentSet();
                docs.add(collections._1.getDocument(broker, doc1Name));
                docs.add(collections._2.getDocument(broker, doc2Name));
                docs.add(collections._3.getDocument(broker, doc3Name));

                moveToRoot(broker, collections._3);

                final Collection col = broker.getCollection(TEST_COLLECTION_URI);
                docs.add(col.getDocument(broker, doc3Name));

                checkIndex(broker, docs);
            } finally {
                collections._3.close();
                collections._2.close();
                collections._1.close();
            }
        }
    }

    private Tuple3<Collection, Collection, Collection> store(final DBBroker broker) throws Exception {
        try(final Txn transaction = broker.getBrokerPool().getTransactionManager().beginTransaction()) {

            final Collection test1 = createCollection(transaction, broker, TEST_COLLECTION_URI);
            final Collection test2 = createCollection(transaction, broker, SUB_TEST_COLLECTION_URI);
            final Collection test3 = createCollection(transaction, broker, TEST3_COLLECTION_URI);

            store(transaction, broker, test1, doc1Name, XML1);
            store(transaction, broker, test2, doc2Name, XML2);
            store(transaction, broker, test3, doc3Name, XML3);

            transaction.commit();

            return new Tuple3<>(test1, test2, test3);
        }
    }

    private Collection createCollection(final Txn txn, final DBBroker broker, final XmldbURI uri) throws PermissionDeniedException, IOException, TriggerException {
        final Collection col = broker.getOrCreateCollection(txn, uri);
        broker.saveCollection(txn, col);
        return col;
    }

    private void store(final Txn txn, final DBBroker broker, final Collection col, final XmldbURI name, final String data) throws LockException, SAXException, PermissionDeniedException, EXistException, IOException {
        final IndexInfo info = col.validateXMLResource(txn, broker, name, data);
        col.store(txn, broker, info, data);
    }

    private void moveToRoot(final DBBroker broker, final Collection sourceCollection) throws Exception {
        try(final Txn transaction = broker.getBrokerPool().getTransactionManager().beginTransaction();
                final Collection root = broker.getCollection(XmldbURI.ROOT_COLLECTION_URI)) {
            broker.moveCollection(transaction, sourceCollection, root, XmldbURI.create("test"));
            transaction.commit();
        }
    }

    private void checkIndex(final DBBroker broker, final DocumentSet docs) throws Exception {
        final StructuralIndex index = broker.getStructuralIndex();
        final NodeSelector selector = NodeProxy::new;

        NodeSet nodes;

        nodes = index.findElementsByTagName(ELEMENT, docs, new QName("test2"), selector);
        assertTrue(nodes.isEmpty());

        nodes = index.findElementsByTagName(ELEMENT, docs, new QName("test1"), selector);
        assertTrue(nodes.isEmpty());

        nodes = index.findElementsByTagName(ELEMENT, docs, new QName("test3"), selector);
        assertFalse(nodes.isEmpty());
    }
}
