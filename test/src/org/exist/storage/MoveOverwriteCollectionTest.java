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

import org.exist.Database;
import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.QName;
import org.exist.dom.persistent.*;
import org.exist.indexing.StructuralIndex;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.NodeSelector;
import org.junit.After;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static org.exist.storage.ElementValue.ELEMENT;

public class MoveOverwriteCollectionTest {

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

    private static Collection test1;
    private static Collection test2;
    private static Collection test3;

    /**
     * This test ensures that when moving an Collection over the top of an existing Collection,
     * the overwritten resource is completely removed from the database;
     * i.e. its nodes are no longer present in the structural index
     */
    @Test
    public void moveAndOverwriteCollection() throws Exception  {
        final Database db = startDB();

        try (final DBBroker broker = db.get(Optional.of(db.getSecurityManager().getSystemSubject()))) {
            store(broker);

            final DefaultDocumentSet docs = new DefaultDocumentSet();
            docs.add(test1.getDocument(broker, doc1Name));
            docs.add(test2.getDocument(broker, doc2Name));
            docs.add(test3.getDocument(broker, doc3Name));

            move(broker);

            Collection col = broker.getCollection(TEST_COLLECTION_URI);

            docs.add(col.getDocument(broker, doc3Name));

            checkIndex(broker, docs);
        }
    }

    private void store(final DBBroker broker) throws Exception {
        try(final Txn transaction = broker.getBrokerPool().getTransactionManager().beginTransaction()) {

            test1 = createCollection(transaction, broker, TEST_COLLECTION_URI);
            test2 = createCollection(transaction, broker, SUB_TEST_COLLECTION_URI);
            test3 = createCollection(transaction, broker, TEST3_COLLECTION_URI);

            store(transaction, broker, test1, doc1Name, XML1);
            store(transaction, broker, test2, doc2Name, XML2);
            store(transaction, broker, test3, doc3Name, XML3);

            transaction.commit();
        }
    }

    private Collection createCollection(Txn txn, DBBroker broker, XmldbURI uri) throws PermissionDeniedException, IOException, TriggerException {
        Collection col = broker.getOrCreateCollection(txn, uri);
        broker.saveCollection(txn, col);
        return col;
    }

    private void store(Txn txn, DBBroker broker, Collection col, XmldbURI name, String data) throws LockException, SAXException, PermissionDeniedException, EXistException, IOException {
        IndexInfo info = col.validateXMLResource(txn, broker, name, data);
        col.store(txn, broker, info, data);
    }

    private void move(final DBBroker broker) throws Exception {
        try(final Txn transaction = broker.getBrokerPool().getTransactionManager().beginTransaction()) {
            Collection root = broker.getCollection(XmldbURI.ROOT_COLLECTION_URI);
            broker.moveCollection(transaction, test3, root, XmldbURI.create("test"));
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

    protected BrokerPool startDB() throws DatabaseConfigurationException, EXistException {
        Configuration config = new Configuration();
        BrokerPool.configure(1, 5, config);
        return BrokerPool.getInstance();
    }

    @After
    public void tearDown() {
        TestUtils.cleanupDB();
        BrokerPool.stopAll(false);
    }
}
