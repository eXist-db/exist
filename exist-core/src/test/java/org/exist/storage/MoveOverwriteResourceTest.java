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
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.QName;
import org.exist.dom.persistent.*;
import org.exist.indexing.*;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.btree.BTree;
import org.exist.storage.btree.DBException;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.util.Occurrences;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.NodeSelector;
import org.exist.xquery.QueryRewriter;
import org.exist.xquery.XQueryContext;
import org.junit.ClassRule;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;

import static org.exist.storage.ElementValue.ELEMENT;

public class MoveOverwriteResourceTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

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
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final DefaultDocumentSet docs = new DefaultDocumentSet();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            store(broker);
            docs.add(test1.getDocument(broker, doc1Name));
            docs.add(test2.getDocument(broker, doc2Name));
        }

        move(pool);

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            docs.add(test2.getDocument(broker, doc2Name));

            checkIndex(broker, docs);
        }
    }

    private void store(final DBBroker broker) throws Exception {
        try(final Txn transaction = broker.getBrokerPool().getTransactionManager().beginTransaction()) {
            test1 = createCollection(transaction, broker, TEST_COLLECTION_URI);
            test2 = createCollection(transaction, broker, SUB_TEST_COLLECTION_URI);

            store(transaction, broker, test1, doc1Name, XML1);
            store(transaction, broker, test2, doc2Name, XML2);

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

    private void move(final Database db) throws Exception {
        TestIndex index = new TestIndex();
        try (final DBBroker broker = db.get(Optional.of(db.getSecurityManager().getSystemSubject()))) {
            broker.getBrokerPool().getIndexManager().registerIndex(index);
        }

        //remove
        index.expectingDocument.add(test2.getURI().append(doc2Name));
        //remove
        index.expectingDocument.add(test1.getURI().append(doc1Name));
        //create
        index.expectingDocument.add(test2.getURI().append(doc2Name));

        try (final DBBroker broker = db.get(Optional.of(db.getSecurityManager().getSystemSubject()));
                final Txn transaction = db.getTransactionManager().beginTransaction()) {
            final DocumentImpl doc = test1.getDocument(broker, doc1Name);
            broker.moveResource(transaction, doc, test2, doc2Name);
            transaction.commit();
        }

        assertTrue(index.expectingDocument.isEmpty());
    }

    private void checkIndex(final DBBroker broker, final DocumentSet docs) throws Exception {
        final StructuralIndex index = broker.getStructuralIndex();
        final NodeSelector selector = NodeProxy::new;

        NodeSet nodes;

        nodes = index.findElementsByTagName(ELEMENT, docs, new QName("test2"), selector);
        assertTrue(nodes.isEmpty());

        nodes = index.findElementsByTagName(ELEMENT, docs, new QName("test1"), selector);
        assertFalse(nodes.isEmpty());
    }

    class TestIndex implements Index {

        LinkedList<XmldbURI> expectingDocument = new LinkedList<>();

        BrokerPool pool;

        @Override
        public String getIndexId() {
            return "test";
        }

        @Override
        public String getIndexName() {
            return "test";
        }

        @Override
        public BrokerPool getBrokerPool() {
            return pool;
        }

        @Override
        public void configure(BrokerPool pool, Path dataDir, Element config) throws DatabaseConfigurationException {
            this.pool = pool;
        }

        @Override
        public void open() throws DatabaseConfigurationException {

        }

        @Override
        public void close() throws DBException {

        }

        @Override
        public void sync() throws DBException {

        }

        @Override
        public void remove() throws DBException {

        }

        @Override
        public IndexWorker getWorker(DBBroker broker) {
            return new IndexWorker() {

                @Override
                public String getIndexId() {
                    return TestIndex.this.getIndexId();
                }

                @Override
                public String getIndexName() {
                    return TestIndex.this.getIndexName();
                }

                @Override
                public Object configure(IndexController controller, NodeList configNodes, Map<String, String> namespaces) throws DatabaseConfigurationException {
                    return null;
                }

                @Override
                public void setDocument(DocumentImpl doc) {
                    assertEquals(expectingDocument.pollFirst(), doc.getURI());
                }

                @Override
                public void setDocument(DocumentImpl doc, StreamListener.ReindexMode mode) {
                    assertEquals(expectingDocument.pollFirst(), doc.getFileURI());
                }

                @Override
                public void setMode(StreamListener.ReindexMode mode) {

                }

                @Override
                public DocumentImpl getDocument() {
                    return null;
                }

                @Override
                public StreamListener.ReindexMode getMode() {
                    return null;
                }

                @Override
                public <T extends IStoredNode> IStoredNode getReindexRoot(IStoredNode<T> node, NodePath path, boolean insert, boolean includeSelf) {
                    return null;
                }

                @Override
                public StreamListener getListener() {
                    return null;
                }

                @Override
                public MatchListener getMatchListener(DBBroker broker, NodeProxy proxy) {
                    return null;
                }

                @Override
                public void flush() {

                }

                @Override
                public void removeCollection(Collection collection, DBBroker broker, boolean reindex) throws PermissionDeniedException {

                }

                @Override
                public boolean checkIndex(DBBroker broker) {
                    return false;
                }

                @Override
                public Occurrences[] scanIndex(XQueryContext context, DocumentSet docs, NodeSet contextSet, Map<?, ?> hints) {
                    return new Occurrences[0];
                }

                @Override
                public QueryRewriter getQueryRewriter(XQueryContext context) {
                    return null;
                }
            };
        }

        @Override
        public boolean checkIndex(DBBroker broker) {
            return false;
        }

        @Override
        public BTree getStorage() {
            return null;
        }
    }
}
