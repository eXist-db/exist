/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
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

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ReindexTest {

    @ClassRule
    public static ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final XmldbURI DOCUMENT_WITH_CHILD_NODES_COLLECTION = XmldbURI.create("/db/reindex-document-child-nodes-test");
    private static final XmldbURI DOCUMENT_WITH_CHILD_NODES_NAME = XmldbURI.create("doc-child-nodes.xml");

    private static final String DOCUMENT_WITH_CHILD_NODES_XML =
            "<?some-pi?>\n" +
                    "<!-- 1 --><!-- 2 -->\n" +
                    "<n/>\n" +
                    "<!-- 3 -->";

    private static final XmldbURI ELEMENT_WITH_CHILD_NODES_COLLECTION = XmldbURI.create("/db/reindex-element-child-nodes-test");
    private static final XmldbURI ELEMENT_WITH_CHILD_NODES_NAME = XmldbURI.create("elem-child-nodes.xml");

    private static final String ELEMENT_WITH_CHILD_NODES_XML =
            "<n>" +
                "<?some-pi?>" +
                "<!-- 1 --><!-- 2 -->" +
                "<nn/>" +
                "<!-- 3 -->" +
            "</n>";

    @Test
    public void reindexDocumentChildNodes() throws IOException, EXistException, PermissionDeniedException, SAXException, LockException {
        reindexDocumentChildNodes_checkNodes();

        reindex(DOCUMENT_WITH_CHILD_NODES_COLLECTION);

        reindexDocumentChildNodes_checkNodes();
    }

    @Test
    public void reindexElementChildren() throws EXistException, PermissionDeniedException, IOException, LockException {
        reindexElementChildren_checkNodes();

        reindex(ELEMENT_WITH_CHILD_NODES_COLLECTION);

        reindexElementChildren_checkNodes();
    }

    private void reindexDocumentChildNodes_checkNodes() throws EXistException, PermissionDeniedException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            final Document doc = broker.getXMLResource(DOCUMENT_WITH_CHILD_NODES_COLLECTION.append(DOCUMENT_WITH_CHILD_NODES_NAME));
            assertNotNull(doc);

            // navigate by child nodes
            final NodeList children = doc.getChildNodes();
            assertNotNull(children);
            assertEquals(5, children.getLength());
            assertEquals(Node.PROCESSING_INSTRUCTION_NODE, children.item(0).getNodeType());
            assertEquals(Node.COMMENT_NODE, children.item(1).getNodeType());
            assertEquals(Node.COMMENT_NODE, children.item(2).getNodeType());
            assertEquals(Node.ELEMENT_NODE, children.item(3).getNodeType());
            assertEquals("n", children.item(3).getNodeName());
            assertEquals(Node.COMMENT_NODE, children.item(4).getNodeType());

            // navigate by next sibling
            final Node first = doc.getFirstChild();
            assertNotNull(first);
            assertEquals(Node.PROCESSING_INSTRUCTION_NODE, first.getNodeType());
            final Node second = first.getNextSibling();
            assertNotNull(second);
            assertEquals(Node.COMMENT_NODE, second.getNodeType());
            final Node third = second.getNextSibling();
            assertNotNull(third);
            assertEquals(Node.COMMENT_NODE, third.getNodeType());
            final Node fourth = third.getNextSibling();
            assertNotNull(fourth);
            assertEquals(Node.ELEMENT_NODE, fourth.getNodeType());
            assertEquals("n", fourth.getNodeName());
            final Node fifth = fourth.getNextSibling();
            assertNotNull(fifth);
            assertEquals(Node.COMMENT_NODE, fifth.getNodeType());

            transaction.commit();
        }
    }

    private void reindexElementChildren_checkNodes() throws EXistException, PermissionDeniedException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            final Document doc = broker.getXMLResource(ELEMENT_WITH_CHILD_NODES_COLLECTION.append(ELEMENT_WITH_CHILD_NODES_NAME));
            assertNotNull(doc);

            // navigate by child nodes
            final NodeList children = doc.getChildNodes();
            assertNotNull(children);
            assertEquals(1, children.getLength());
            final Node elem = children.item(0);
            assertNotNull(elem);
            assertEquals(Node.ELEMENT_NODE, elem.getNodeType());
            assertEquals("n", elem.getNodeName());

            final NodeList elemChildren = elem.getChildNodes();
            assertNotNull(elemChildren);
            assertEquals(5, elemChildren.getLength());
            assertEquals(Node.PROCESSING_INSTRUCTION_NODE, elemChildren.item(0).getNodeType());
            assertEquals(Node.COMMENT_NODE, elemChildren.item(1).getNodeType());
            assertEquals(Node.COMMENT_NODE, elemChildren.item(2).getNodeType());
            assertEquals(Node.ELEMENT_NODE, elemChildren.item(3).getNodeType());
            assertEquals("nn", elemChildren.item(3).getNodeName());
            assertEquals(Node.COMMENT_NODE, elemChildren.item(4).getNodeType());

            // navigate by next sibling
            final Node first = doc.getFirstChild();
            assertNotNull(first);
            assertEquals(Node.ELEMENT_NODE, first.getNodeType());
            assertEquals("n", first.getNodeName());

            final Node elemFirst = first.getFirstChild();
            assertNotNull(elemFirst);
            assertEquals(Node.PROCESSING_INSTRUCTION_NODE, elemFirst.getNodeType());
            final Node elemSecond = elemFirst.getNextSibling();
            assertNotNull(elemSecond);
            assertEquals(Node.COMMENT_NODE, elemSecond.getNodeType());
            final Node elemThird = elemSecond.getNextSibling();
            assertNotNull(elemThird);
            assertEquals(Node.COMMENT_NODE, elemThird.getNodeType());
            final Node elemFourth = elemThird.getNextSibling();
            assertNotNull(elemFourth);
            assertEquals("nn", elemFourth.getNodeName());
            assertEquals(Node.ELEMENT_NODE, elemFourth.getNodeType());
            final Node elemFifth = elemFourth.getNextSibling();
            assertNotNull(elemFifth);
            assertEquals(Node.COMMENT_NODE, elemFifth.getNodeType());

            transaction.commit();
        }
    }

    private static void reindex(final XmldbURI collectionUri) throws EXistException, PermissionDeniedException, IOException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            broker.reindexCollection(transaction, collectionUri);
            transaction.commit();
        }
    }

    private static void storeDocument(final XmldbURI collectionUri,
                                      final XmldbURI docName, final String doc)
            throws PermissionDeniedException, IOException, SAXException, EXistException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            final Collection collection = broker.getOrCreateCollection(transaction, collectionUri);
            assertNotNull(collection);
            broker.saveCollection(transaction, collection);

            final IndexInfo info = collection.validateXMLResource(transaction, broker, docName, doc);
            assertNotNull(info);
            collection.store(transaction, broker, info, doc);

            transaction.commit();
        }
    }

    private static void removeCollection(final XmldbURI collectionUri) throws PermissionDeniedException, LockException, IOException, TriggerException, EXistException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction();
             final Collection collection = broker.openCollection(collectionUri, Lock.LockMode.WRITE_LOCK)) {

            if (collection != null) {
                broker.removeCollection(transaction, collection);
            }

            transaction.commit();
        }
    }

    @BeforeClass
    public static void setup() throws LockException, SAXException, PermissionDeniedException, EXistException, IOException {
        storeDocument(DOCUMENT_WITH_CHILD_NODES_COLLECTION, DOCUMENT_WITH_CHILD_NODES_NAME, DOCUMENT_WITH_CHILD_NODES_XML);
        storeDocument(ELEMENT_WITH_CHILD_NODES_COLLECTION, ELEMENT_WITH_CHILD_NODES_NAME, ELEMENT_WITH_CHILD_NODES_XML);
    }

    @AfterClass
    public static void cleanup() throws LockException, TriggerException, PermissionDeniedException, EXistException, IOException {
        removeCollection(ELEMENT_WITH_CHILD_NODES_COLLECTION);
        removeCollection(DOCUMENT_WITH_CHILD_NODES_COLLECTION);
    }
}
