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

package org.exist.dom.persistent;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.ManagedCollectionLock;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import javax.annotation.Nullable;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Optional;
import java.util.Properties;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static org.junit.Assert.*;

public class PersistentDomTest {

    @ClassRule
    public static ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final XmldbURI TEST_SIMPLE_XML_COLLECTION = XmldbURI.create("/db/persistent-dom-simple-test");
    private static final XmldbURI SIMPLE_XML_NAME = XmldbURI.create("simple.xml");
    private static final String SIMPLE_XML =
            "<document-element><child-level1><child-level2/></child-level1></document-element>";

    private static final XmldbURI TEST_MIXED_XML_COLLECTION = XmldbURI.create("/db/persistent-dom-mixed-test");
    private static final XmldbURI MIXED_XML_NAME = XmldbURI.create("mixed.xml");
    private static final String MIXED_XML =
            "<!-- 1 -->\n" +
            "<x>\n" +
            "  <!-- x.1 -->\n" +
            "  <y1>text1<z1/><!-- y.1 --></y1>\n" +
            "  <!-- x.2 -->\n" +
            "  <y2>text2<z2/><!-- y.2 --></y2>\n" +
            "  <!-- x.3 -->\n" +
            "</x>\n" +
            "<!-- 2 -->";

    private static final XmldbURI TEST_CDATA_XML_COLLECTION = XmldbURI.create("/db/persistent-dom-cdata-test");
    private static final XmldbURI CDATA_XML_NAME = XmldbURI.create("cdata.xml");
    private static final String CDATA_CONTENT = "Hello there \"Bob?\"";
    private static final String CDATA_XML =
            "<cdataText><![CDATA[" + CDATA_CONTENT + "]]></cdataText>";


    @Test
    public void mixed_childNodes() throws EXistException, PermissionDeniedException, IOException, SAXException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            try (final LockedDocument lockedDocument = broker.getXMLResource(TEST_MIXED_XML_COLLECTION.append(MIXED_XML_NAME), Lock.LockMode.READ_LOCK)) {
                assertNotNull(lockedDocument);

                final Document document = lockedDocument.getDocument();
                assertNotNull(document);

                final NodeList documentChildNodes = document.getChildNodes();
                assertEquals(3, documentChildNodes.getLength());
                assertComment(broker, "<!-- 1 -->", documentChildNodes.item(0));
                assertMixedChildXNodes(broker, documentChildNodes.item(1));
                assertComment(broker, "<!-- 2 -->", documentChildNodes.item(2));
            }

            transaction.commit();
        }
    }

    private void assertMixedChildXNodes(final DBBroker broker, final Node nodeX) throws IOException, SAXException {
        assertElement("x", nodeX);
        final NodeList xChildNodes = nodeX.getChildNodes();
        assertEquals(11, xChildNodes.getLength());
        assertTextNode(xChildNodes.item(0));
        assertComment(broker, "<!-- x.1 -->", xChildNodes.item(1));
        assertTextNode(xChildNodes.item(2));
        assertMixedChildY1Nodes(broker, xChildNodes.item(3));
        assertTextNode(xChildNodes.item(4));
        assertComment(broker, "<!-- x.2 -->", xChildNodes.item(5));
        assertTextNode(xChildNodes.item(6));
        assertMixedChildY2Nodes(broker, xChildNodes.item(7));
        assertTextNode(xChildNodes.item(8));
        assertComment(broker, "<!-- x.3 -->", xChildNodes.item(9));
        assertTextNode(xChildNodes.item(10));
    }

    private void assertMixedChildY1Nodes(final DBBroker broker, final Node nodeY1) throws IOException, SAXException {
        assertElement("y1", nodeY1);
        final NodeList y1ChildNodes = nodeY1.getChildNodes();
        assertEquals(3, y1ChildNodes.getLength());
        assertTextNode("text1", y1ChildNodes.item(0));
        assertElement("z1", y1ChildNodes.item(1));
        assertComment(broker, "<!-- y.1 -->", y1ChildNodes.item(2));
    }

    private void assertMixedChildY2Nodes(final DBBroker broker, final Node nodeY2) throws IOException, SAXException {
        assertElement("y2", nodeY2);
        final NodeList y2ChildNodes = nodeY2.getChildNodes();
        assertEquals(3, y2ChildNodes.getLength());
        assertTextNode("text2", y2ChildNodes.item(0));
        assertElement("z2", y2ChildNodes.item(1));
        assertComment(broker, "<!-- y.2 -->", y2ChildNodes.item(2));
    }

    @Test
    public void mixed_siblings() throws EXistException, PermissionDeniedException, IOException, SAXException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            try (final LockedDocument lockedDocument = broker.getXMLResource(TEST_MIXED_XML_COLLECTION.append(MIXED_XML_NAME), Lock.LockMode.READ_LOCK)) {
                assertNotNull(lockedDocument);

                final Document document = lockedDocument.getDocument();
                assertNotNull(document);
                assertNull(document.getPreviousSibling());
                assertNull(document.getNextSibling());

                final Node comment1 = document.getFirstChild();
                assertComment(broker, "<!-- 1 -->", comment1);

                assertNull(comment1.getPreviousSibling());
                final Node nodeX = comment1.getNextSibling();
                assertMixedSiblingsNodeX(broker, nodeX);

                assertComment(broker, "<!-- 1 -->", nodeX.getPreviousSibling());
                final Node comment2 = nodeX.getNextSibling();
                assertComment(broker, "<!-- 2 -->", comment2);

                assertElement("x", comment2.getPreviousSibling());
                assertNull(comment2.getNextSibling());
            }

            transaction.commit();
        }
    }

    private void assertMixedSiblingsNodeX(final DBBroker broker, final Node nodeX) throws IOException, SAXException {
        assertElement("x", nodeX);
        final Node text1 = nodeX.getFirstChild();
        assertTextNode(text1);

        assertNull(text1.getPreviousSibling());
        final Node comment1 = text1.getNextSibling();
        assertComment(broker, "<!-- x.1 -->", comment1);

        assertTextNode(comment1.getPreviousSibling());
        final Node text2 = comment1.getNextSibling();
        assertTextNode(text2);

        assertComment(broker, "<!-- x.1 -->", text2.getPreviousSibling());
        final Node nodeY1 = text2.getNextSibling();
        assertMixedSiblingsNodeY1(broker, nodeY1);

        assertTextNode(nodeY1.getPreviousSibling());
        final Node text3 = nodeY1.getNextSibling();
        assertTextNode(text3);

        assertElement("y1", text3.getPreviousSibling());
        final Node comment2 = text3.getNextSibling();
        assertComment(broker,"<!-- x.2 -->", comment2);

        assertTextNode(comment2.getPreviousSibling());
        final Node text4 = comment2.getNextSibling();
        assertTextNode(text4);

        assertComment(broker, "<!-- x.2 -->", text4.getPreviousSibling());
        final Node nodeY2 = text4.getNextSibling();
        assertMixedSiblingsNodeY2(broker, nodeY2);

        assertTextNode(nodeY2.getPreviousSibling());
        final Node text5 = nodeY2.getNextSibling();
        assertTextNode(text5);

        assertElement("y2", text5.getPreviousSibling());
        final Node comment3 = text5.getNextSibling();
        assertComment(broker, "<!-- x.3 -->", comment3);

        assertTextNode(comment3.getPreviousSibling());
        final Node text6 = comment3.getNextSibling();
        assertTextNode(text6);

        assertComment(broker, "<!-- x.3 -->", text6.getPreviousSibling());
        assertNull(text6.getNextSibling());
    }

    private void assertMixedSiblingsNodeY1(final DBBroker broker, final Node nodeY1) throws IOException, SAXException {
        assertElement("y1", nodeY1);
        final Node text1 = nodeY1.getFirstChild();
        assertTextNode("text1", text1);

        assertNull(text1.getPreviousSibling());
        final Node nodeZ1 = text1.getNextSibling();
        assertElement("z1", nodeZ1);

        assertTextNode("text1", nodeZ1.getPreviousSibling());
        final Node comment1 = nodeZ1.getNextSibling();
        assertComment(broker, "<!-- y.1 -->", comment1);

        assertElement("z1", comment1.getPreviousSibling());
        assertNull(comment1.getNextSibling());
    }

    private void assertMixedSiblingsNodeY2(final DBBroker broker, final Node nodeY2) throws IOException, SAXException {
        assertElement("y2", nodeY2);
        final Node text1 = nodeY2.getFirstChild();
        assertTextNode("text2", text1);

        assertNull(text1.getPreviousSibling());
        final Node nodeZ1 = text1.getNextSibling();
        assertElement("z2", nodeZ1);

        assertTextNode("text2", nodeZ1.getPreviousSibling());
        final Node comment1 = nodeZ1.getNextSibling();
        assertComment(broker, "<!-- y.2 -->", comment1);

        assertElement("z2", comment1.getPreviousSibling());
        assertNull(comment1.getNextSibling());
    }

    @Test
    public void documentElement_previousSibling_simple() throws EXistException, PermissionDeniedException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            try (final LockedDocument lockedDocument = broker.getXMLResource(TEST_SIMPLE_XML_COLLECTION.append(SIMPLE_XML_NAME), Lock.LockMode.READ_LOCK)) {
                assertNotNull(lockedDocument);

                final Document document = lockedDocument.getDocument();
                assertNotNull(document);
                assertNull(document.getPreviousSibling());

                final Element documentElement = document.getDocumentElement();
                assertNotNull(documentElement);
                assertNull(documentElement.getPreviousSibling());

                final Element childLevel1 = (Element)documentElement.getFirstChild();
                assertNotNull(childLevel1);
                assertNull(childLevel1.getPreviousSibling());

                final Element childLevel2 = (Element)childLevel1.getFirstChild();
                assertNotNull(childLevel2);
                assertNull(childLevel2.getPreviousSibling());
            }

            transaction.commit();
        }
    }

    @Test
    public void documentElement_nextSibling_simple() throws EXistException, PermissionDeniedException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            try(final LockedDocument lockedDocument = broker.getXMLResource(TEST_SIMPLE_XML_COLLECTION.append(SIMPLE_XML_NAME), Lock.LockMode.READ_LOCK)) {
                assertNotNull(lockedDocument);

                final Document document = lockedDocument.getDocument();
                assertNotNull(document);
                assertNull(document.getNextSibling());

                final Element documentElement = document.getDocumentElement();
                assertNotNull(documentElement);
                assertNull(documentElement.getNextSibling());

                final Element childLevel1 = (Element)documentElement.getFirstChild();
                assertNotNull(childLevel1);
                assertNull(childLevel1.getNextSibling());

                final Element childLevel2 = (Element)childLevel1.getFirstChild();
                assertNotNull(childLevel2);
                assertNull(childLevel2.getNextSibling());
            }

            transaction.commit();
        }
    }

    @Test
    public void cdata() throws EXistException, PermissionDeniedException, IOException, SAXException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            try(final LockedDocument lockedDocument = broker.getXMLResource(TEST_CDATA_XML_COLLECTION.append(CDATA_XML_NAME), Lock.LockMode.READ_LOCK)) {
                assertNotNull(lockedDocument);

                final Document document = lockedDocument.getDocument();
                final Element documentElement = document.getDocumentElement();
                assertNotNull(documentElement);

                final CDATASection cdataSection = (CDATASection) documentElement.getFirstChild();
                assertNotNull(cdataSection);
                assertEquals(CDATA_CONTENT, cdataSection.getTextContent());

                final Properties defaultOutputProperties = new Properties();
                defaultOutputProperties.setProperty(OutputKeys.METHOD, "xml");
                defaultOutputProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                defaultOutputProperties.setProperty(OutputKeys.INDENT, "no");
                defaultOutputProperties.setProperty(OutputKeys.ENCODING, "UTF-8");

                // normal document serialization
                Properties outputProperties = new Properties(defaultOutputProperties);
                outputProperties.setProperty(EXistOutputKeys.XDM_SERIALIZATION, "no");
                assertEquals(CDATA_XML, serialize(broker, documentElement, outputProperties));

                // XDM serialization
                outputProperties = new Properties(defaultOutputProperties);
                outputProperties.setProperty(EXistOutputKeys.XDM_SERIALIZATION, "yes");
                final String expected = "<cdataText>" + CDATA_CONTENT.replace("<", "&lt;").replace(">", "&gt;") + "</cdataText>";
                assertEquals(expected, serialize(broker, documentElement, outputProperties));

                // XDM serialization with cdata-section-elements
                outputProperties = new Properties(defaultOutputProperties);
                outputProperties.setProperty(EXistOutputKeys.XDM_SERIALIZATION, "yes");
                outputProperties.setProperty(OutputKeys.CDATA_SECTION_ELEMENTS, "{}cdataText");
                assertEquals(CDATA_XML, serialize(broker, documentElement, outputProperties));

            }

            transaction.commit();
        }
    }

    private void assertElement(final String expectedName, final Node actual) {
        assertElement(actual);
        assertEquals(expectedName, actual.getNodeName());
    }

    private void assertElement(final Node actual) {
        assertType(Node.ELEMENT_NODE, actual);
    }

    private void assertComment(final DBBroker broker, final String expectedComment, final Node actual) throws IOException, SAXException {
        assertType(Node.COMMENT_NODE, actual);
        assertEquals(expectedComment, serialize(broker, actual));
    }

    private void assertTextNode(final String expected, final Node actual) {
        assertTextNode(actual);
        assertEquals(expected, actual.getTextContent());
    }

    private void assertTextNode(final Node actual) {
        assertType(Node.TEXT_NODE, actual);
    }

    private void assertType(final short expectedType, final Node actual) {
        assertEquals(expectedType, actual.getNodeType());
    }

    private void assertXml(final String expected, final Node actual) {
        final Source srcExpected = Input.fromString(expected).build();
        final Source srcActual = Input.fromNode(actual).build();

        final Diff diff = DiffBuilder.compare(srcActual)
                .withTest(srcExpected)
                .checkForIdentical()
                .build();

        assertFalse(diff.toString(), diff.hasDifferences());
    }

    private static String serialize(final DBBroker broker, final Node node) throws IOException, SAXException {
        final Properties outputProperties = new Properties();
        outputProperties.setProperty(OutputKeys.METHOD, "xml");
        outputProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        outputProperties.setProperty(OutputKeys.INDENT, "no");
        outputProperties.setProperty(OutputKeys.ENCODING, "UTF-8");
        return serialize(broker, node, outputProperties);
    }

    private static String serialize(final DBBroker broker, final Node node, @Nullable final Properties outputProperties)
            throws IOException, SAXException {
        // serialize the results to the response output stream
        final Serializer serializer = broker.borrowSerializer();
        SAXSerializer sax = null;
        try(final StringWriter writer = new StringWriter()) {
            sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(
                    SAXSerializer.class);

            sax.setOutput(writer, outputProperties);

            serializer.setProperties(outputProperties);
            serializer.setSAXHandlers(sax, sax);

            serializer.toSAX(new NodeProxy(null, (NodeHandle)node));

            return writer.toString();

        } finally {
            if (sax != null) {
                SerializerPool.getInstance().returnObject(sax);
            }
            broker.returnSerializer(serializer);
        }
    }

    @BeforeClass
    public static void setup() throws EXistException, LockException, SAXException, PermissionDeniedException, IOException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            createCollection(broker, transaction, TEST_SIMPLE_XML_COLLECTION,
                    Tuple(SIMPLE_XML_NAME, SIMPLE_XML)
            );

            createCollection(broker, transaction, TEST_MIXED_XML_COLLECTION,
                    Tuple(MIXED_XML_NAME, MIXED_XML)
            );

            createCollection(broker, transaction, TEST_CDATA_XML_COLLECTION,
                    Tuple(CDATA_XML_NAME, CDATA_XML));

            transaction.commit();
        }
    }

    @AfterClass
    public static void cleanup() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            deleteCollection(broker, transaction, TEST_SIMPLE_XML_COLLECTION);
            deleteCollection(broker, transaction, TEST_MIXED_XML_COLLECTION);
            deleteCollection(broker, transaction, TEST_CDATA_XML_COLLECTION);

            transaction.commit();
        }
    }

    private static void createCollection(final DBBroker broker, final Txn transaction, final XmldbURI collectionUri, final Tuple2<XmldbURI, String>... docs) throws PermissionDeniedException, IOException, SAXException, LockException, EXistException {
        try (final ManagedCollectionLock collectionLock = broker.getBrokerPool().getLockManager().acquireCollectionWriteLock(collectionUri)) {
            final Collection collection = broker.getOrCreateCollection(transaction, collectionUri);
            broker.saveCollection(transaction, collection);
            for (final Tuple2<XmldbURI, String> doc : docs) {
                broker.storeDocument(transaction, doc._1, new StringInputSource(doc._2), MimeType.XML_TYPE, collection);
            }
        }
    }

    private static void deleteCollection(final DBBroker broker, final Txn transaction, final XmldbURI collectionUri) throws PermissionDeniedException, IOException, TriggerException {
        try(final Collection collection = broker.openCollection(collectionUri, Lock.LockMode.WRITE_LOCK)) {
            if (collection != null) {
                broker.removeCollection(transaction, collection);
            }
        }
    }
}
