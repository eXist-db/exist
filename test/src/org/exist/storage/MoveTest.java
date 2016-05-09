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
import org.exist.dom.QName;
import org.exist.dom.persistent.*;
import org.exist.indexing.StructuralIndex;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.NodeSelector;
import org.junit.After;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

public class MoveTest {

    private final static String XML1 =
            "<?xml version=\"1.0\"?>" +
                    "<test1>" +
                    "  <title>Hello</title>" +
                    "  <para>Hello World!</para>" +
                    "</test1>";

    private final static String XML2 =
            "<?xml version=\"1.0\"?>" +
                    "<test2>" +
                    "  <title>Hello</title>" +
                    "  <para>Hello World!</para>" +
                    "</test2>";

    private final static XmldbURI TEST_COLLECTION_URI = XmldbURI.ROOT_COLLECTION_URI.append("test");
    private final static XmldbURI SUB_TEST_COLLECTION_URI = TEST_COLLECTION_URI.append("test2");

    private final static XmldbURI doc1Name = XmldbURI.create("doc1.xml");
    private final static XmldbURI doc2Name = XmldbURI.create("doc2.xml");

    private static Collection test1;
    private static Collection test2;

    /**
     * This test ensures that when moving an
     * XML resource over the top of an existing
     * XML resource, the overwritten resource
     * is completely removed from the database; i.e.
     * its nodes are no longer present in the structural
     * index
     */
    @Test
    public void moveAndOverwriteXML() throws Exception  {
        final Database db = startDB();

        try (final DBBroker broker = db.get(Optional.of(db.getSecurityManager().getSystemSubject()))) {
            store(broker);

            final DefaultDocumentSet docs = new DefaultDocumentSet();
            docs.add(test1.getDocument(broker, doc1Name));
            docs.add(test2.getDocument(broker, doc2Name));

            move(broker);

            docs.add(test2.getDocument(broker, doc2Name));

            checkIndex(broker, docs);
        }
    }

    private void store(final DBBroker broker) throws Exception {
        try(final Txn transaction = broker.beginTx()) {

            test1 = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI);
            broker.saveCollection(transaction, test1);

            test2 = broker.getOrCreateCollection(transaction, SUB_TEST_COLLECTION_URI);
            broker.saveCollection(transaction, test2);

            IndexInfo info = test1.validateXMLResource(transaction, broker, doc1Name, XML1);
            test1.store(transaction, broker, info, XML1, false);

            info = test2.validateXMLResource(transaction, broker, doc2Name, XML2);
            test2.store(transaction, broker, info, XML2, false);

            transaction.commit();
        }
    }

    private void move(final DBBroker broker) throws Exception {
        try(final Txn transaction = broker.beginTx()) {
            final DocumentImpl doc = test1.getDocument(broker, doc1Name);
            broker.moveResource(transaction, doc, test2, doc2Name);
            transaction.commit();
        }
    }

    private void checkIndex(final DBBroker broker, final DocumentSet docs) throws Exception {
        final StructuralIndex index = broker.getStructuralIndex();
        final NodeSelector selector = (doc, nodeId) -> new NodeProxy(doc, nodeId);

        QName qn = new QName("test2");
        NodeSet nodes = index.findElementsByTagName(ElementValue.ELEMENT, docs, qn, selector);
        assertTrue(nodes.isEmpty());

        qn = new QName("test1");
        nodes = index.findElementsByTagName(ElementValue.ELEMENT, docs, qn, selector);
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
