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

    private static XmldbURI TEST_COLLECTION_URI = XmldbURI.ROOT_COLLECTION_URI.append("test");
    
    private static String XML1 =
        "<?xml version=\"1.0\"?>" +
        "<test1>" +
        "  <title>Hello</title>" +
        "  <para>Hello World!</para>" +
        "</test1>";

    private static String XML2 =
        "<?xml version=\"1.0\"?>" +
        "<test2>" +
        "  <title>Hello</title>" +
        "  <para>Hello World!</para>" +
        "</test2>";

    @Test
    public void moveAndOverwriteXML() throws Exception  {

        Database db = startDB();

        try (DBBroker broker = db.get(Optional.of(db.getSecurityManager().getSystemSubject()))) {
            store(broker);

            DefaultDocumentSet docs = new DefaultDocumentSet();
            docs.add(test1.getDocument(broker, doc1Name));
            docs.add(test2.getDocument(broker, doc2Name));

            move(broker);

            docs.add(test2.getDocument(broker, doc2Name));

            checkIndex(broker, docs);
        }
    }

    static XmldbURI doc1Name = XmldbURI.create("doc1.xml");
    static XmldbURI doc2Name = XmldbURI.create("doc2.xml");

    static Collection test1;
    static Collection test2;

    private void store(DBBroker broker) throws Exception {
        try(Txn tx = broker.beginTx()) {

            test1 = broker.getOrCreateCollection(tx, TEST_COLLECTION_URI);
            broker.saveCollection(tx, test1);
            
            test2 = broker.getOrCreateCollection(tx, TEST_COLLECTION_URI.append("test2"));
            broker.saveCollection(tx, test2);

            IndexInfo info = test1.validateXMLResource(tx, broker, doc1Name, XML1);
            test1.store(tx, broker, info, XML1, false);

            info = test2.validateXMLResource(tx, broker, doc2Name, XML2);
            test1.store(tx, broker, info, XML2, false);

            tx.success();
        }
    }

    private void move(DBBroker broker) throws Exception {
        try(Txn tx = broker.beginTx()) {

            DocumentImpl doc = test1.getDocument(broker, doc1Name);

            broker.moveResource(tx, doc, test2, doc2Name);

            tx.success();
        }
    }

    private void checkIndex(DBBroker broker, DocumentSet docs) throws Exception {

        QName qn;
        NodeSet nodes;

        StructuralIndex index = broker.getStructuralIndex();

        NodeSelector selector = (doc, nodeId) -> {
//            System.out.println(doc + " " +nodeId.toString());
            return new NodeProxy(doc, nodeId);
        };

        qn = new QName("test2", "");

//        System.out.println("overwrite");
        nodes = index.findElementsByTagName(ElementValue.ELEMENT, docs, qn, selector);
        assertTrue(nodes.isEmpty());

        qn = new QName("test1", "");

//        System.out.println("current");
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
