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

package org.exist.stax;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.LockedDocument;
import org.exist.dom.persistent.NodeHandle;
import org.exist.numbering.NodeId;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.ManagedCollectionLock;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static org.exist.stax.ExtendedXMLStreamReader.PROPERTY_NODE_ID;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.exist.stax.EmbeddedXMLStreamReaderTest.NamedEvent.*;

@RunWith(ParallelRunner.class)
public class EmbeddedXMLStreamReaderTest {

    @ClassRule
    public static ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

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

    /**
     * Attempts to read all nodes in the document starting from the first node of the document.
     */
    @Test
    public void allNodesInDocument_fromFirstChild() throws EXistException, PermissionDeniedException, IOException, XMLStreamException {
        final NamedEvent[] expected = {
                COMMENT,            // <!-- 1 -->
                START_ELEMENT,      // <x>
                CHARACTERS,         // "\n  "
                COMMENT,            // <!-- x.1 -->
                CHARACTERS,         // "\n  "
                START_ELEMENT,      // <y1>
                CHARACTERS,         // text1
                START_ELEMENT,      // <z1>
                END_ELEMENT,        // </z1>
                COMMENT,            // <!-- y.1 -->
                END_ELEMENT,        // </y1>
                CHARACTERS,         // "\n  "
                COMMENT,            // <!-- x.2 -->
                CHARACTERS,         // "\n  "
                START_ELEMENT,      // <y2>
                CHARACTERS,         // text2
                START_ELEMENT,      // <z2>
                END_ELEMENT,        // </z2>
                COMMENT,            // <!-- y.2 -->
                END_ELEMENT,        // </y2>
                CHARACTERS,         // "\n  "
                COMMENT,            // <!-- x.3 -->
                CHARACTERS,         // "\n"
                END_ELEMENT,        // </x>
                COMMENT             // <!-- 2 -->
        };

        assertNodesIn(expected, document -> (NodeHandle)document.getFirstChild());
    }

    /**
     * Attempts to read all nodes in the document element.
     */
    @Test
    public void allNodesInDocumentElement() throws EXistException, PermissionDeniedException, IOException, XMLStreamException {
        final NamedEvent[] expected = {
                START_ELEMENT,      // <x>
                CHARACTERS,         // "\n  "
                COMMENT,            // <!-- x.1 -->
                CHARACTERS,         // "\n  "
                START_ELEMENT,      // <y1>
                CHARACTERS,         // text1
                START_ELEMENT,      // <z1>
                END_ELEMENT,        // </z1>
                COMMENT,            // <!-- y.1 -->
                END_ELEMENT,        // </y1>
                CHARACTERS,         // "\n  "
                COMMENT,            // <!-- x.2 -->
                CHARACTERS,         // "\n  "
                START_ELEMENT,      // <y2>
                CHARACTERS,         // text2
                START_ELEMENT,      // <z2>
                END_ELEMENT,        // </z2>
                COMMENT,            // <!-- y.2 -->
                END_ELEMENT,        // </y2>
                CHARACTERS,         // "\n  "
                COMMENT,            // <!-- x.3 -->
                CHARACTERS,         // "\n"
                END_ELEMENT         // </x>
        };

        final Function<Document, NodeHandle> docElementFun = document -> (NodeHandle)document.getDocumentElement();
        assertNodesIn(expected, docElementFun, Optional.of(docElementFun));
    }

    /**
     * Attempts to read all nodes in the "y1" element.
     */
    @Test
    public void allNodesInY1Element() throws EXistException, PermissionDeniedException, IOException, XMLStreamException {
        final NamedEvent[] expected = {
                START_ELEMENT,      // <y1>
                CHARACTERS,         // text1
                START_ELEMENT,      // <z1>
                END_ELEMENT,        // </z1>
                COMMENT,            // <!-- y.1 -->
                END_ELEMENT         // </y1>
        };

        final Function<Document, NodeHandle> y1Fun = document -> (NodeHandle)document.getDocumentElement().getElementsByTagName("y1").item(0);
        assertNodesIn(expected, y1Fun, Optional.of(y1Fun));
    }

    /**
     * Attempts to read all nodes in the "y2" element.
     */
    @Test
    public void allNodesInY2Element() throws EXistException, PermissionDeniedException, IOException, XMLStreamException {
        final NamedEvent[] expected = {
                START_ELEMENT,      // <y2>
                CHARACTERS,         // text2
                START_ELEMENT,      // <z2>
                END_ELEMENT,        // </z2>
                COMMENT,            // <!-- y.2 -->
                END_ELEMENT         // </y2>
        };

        final Function<Document, NodeHandle> y2Fun = document -> (NodeHandle)document.getDocumentElement().getElementsByTagName("y2").item(0);
        assertNodesIn(expected, y2Fun, Optional.of(y2Fun));
    }

    /**
     * Attempts to read all nodes in the "z1" element.
     */
    @Test
    public void allNodesInZ1Element() throws EXistException, PermissionDeniedException, IOException, XMLStreamException {
        final NamedEvent[] expected = {
                START_ELEMENT,      // <z1>
                END_ELEMENT         // </z1>
        };

        final Function<Document, NodeHandle> z1Fun = document -> (NodeHandle)document.getDocumentElement().getElementsByTagName("z1").item(0);
        assertNodesIn(expected, z1Fun, Optional.of(z1Fun));
    }

    /**
     * Attempts to read all nodes in the "z1" element.
     */
    @Test
    public void allNodesInZ2Element() throws EXistException, PermissionDeniedException, IOException, XMLStreamException {
        final NamedEvent[] expected = {
                START_ELEMENT,      // <z2>
                END_ELEMENT         // </z2>
        };

        final Function<Document, NodeHandle> z2Fun = document -> (NodeHandle)document.getDocumentElement().getElementsByTagName("z2").item(0);
        assertNodesIn(expected, z2Fun, Optional.of(z2Fun));
    }

    /**
     * Attempts to read all nodes in the document element.
     */
    @Test
    public void allNodesInDocumentElement_fromFirstChild() throws EXistException, PermissionDeniedException, IOException, XMLStreamException {
        final NamedEvent[] expected = {
                CHARACTERS,         // "\n  "
                COMMENT,            // <!-- x.1 -->
                CHARACTERS,         // "\n  "
                START_ELEMENT,      // <y1>
                CHARACTERS,         // text1
                START_ELEMENT,      // <z1>
                END_ELEMENT,        // </z1>
                COMMENT,            // <!-- y.1 -->
                END_ELEMENT,        // </y1>
                CHARACTERS,         // "\n  "
                COMMENT,            // <!-- x.2 -->
                CHARACTERS,         // "\n  "
                START_ELEMENT,      // <y2>
                CHARACTERS,         // text2
                START_ELEMENT,      // <z2>
                END_ELEMENT,        // </z2>
                COMMENT,            // <!-- y.2 -->
                END_ELEMENT,        // </y2>
                CHARACTERS,         // "\n  "
                COMMENT,            // <!-- x.3 -->
                CHARACTERS          // "\n"
        };

        assertNodesIn(expected, document -> (NodeHandle)document.getDocumentElement().getFirstChild(), Optional.of(document -> (NodeHandle)document.getDocumentElement()));
    }

    /**
     * Attempts to read all nodes in the document element.
     */
    @Test
    public void allNodesInDocumentElement_fromY1() throws EXistException, PermissionDeniedException, IOException, XMLStreamException {
        final NamedEvent[] expected = {
                START_ELEMENT,      // <y1>
                CHARACTERS,         // text1
                START_ELEMENT,      // <z1>
                END_ELEMENT,        // </z1>
                COMMENT,            // <!-- y.1 -->
                END_ELEMENT,        // </y1>
                CHARACTERS,         // "\n  "
                COMMENT,            // <!-- x.2 -->
                CHARACTERS,         // "\n  "
                START_ELEMENT,      // <y2>
                CHARACTERS,         // text2
                START_ELEMENT,      // <z2>
                END_ELEMENT,        // </z2>
                COMMENT,            // <!-- y.2 -->
                END_ELEMENT,        // </y2>
                CHARACTERS,         // "\n  "
                COMMENT,            // <!-- x.3 -->
                CHARACTERS          // "\n"
        };

        assertNodesIn(expected, document -> (NodeHandle)document.getDocumentElement().getElementsByTagName("y1").item(0), Optional.of(document -> (NodeHandle)document.getDocumentElement()));
    }

    public void assertNodesIn(final NamedEvent[] expected, final Function<Document, NodeHandle> initialNodeFun) throws EXistException, PermissionDeniedException, IOException, XMLStreamException {
        assertNodesIn(expected, initialNodeFun, Optional.empty());
    }

    public void assertNodesIn(final NamedEvent[] expected, final Function<Document, NodeHandle> initialNodeFun, final Optional<Function<Document, NodeHandle>> containerFun) throws EXistException, PermissionDeniedException, IOException, XMLStreamException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            try (final LockedDocument lockedDocument = broker.getXMLResource(TEST_MIXED_XML_COLLECTION.append(MIXED_XML_NAME), Lock.LockMode.WRITE_LOCK)) {
                assertNotNull(lockedDocument);

                final Document document = lockedDocument.getDocument();
                assertNotNull(document);

                final NodeHandle initialNode = initialNodeFun.apply(document);
                final Optional<NodeHandle> maybeContainerNode = containerFun.map(f -> f.apply(document));
                final IEmbeddedXMLStreamReader xmlStreamReader = broker.getXMLStreamReader(initialNode, false);

                final NamedEvent[] actual = readAllEvents(maybeContainerNode, xmlStreamReader);

                assertArrayEquals(formatExpectedActual(expected, actual), expected, actual);
            }

            transaction.commit();
        }
    }

    private String formatExpectedActual(final NamedEvent[] expected, final NamedEvent[] actual) {
        final int maxLen = Math.max(expected.length, actual.length);
        final int maxIdxLen = String.valueOf(maxLen).length();
        final int maxNameLen = Math.max(maxNameLen(expected), maxNameLen(actual));
        final String EOL = System.lineSeparator();
        final StringBuilder tableBuilder = new StringBuilder();
        append(tableBuilder, "#", maxIdxLen).append(" | ");
        append(tableBuilder, "Expected", maxNameLen).append(" | Actual").append(EOL);
        appendRep(tableBuilder, '-', maxIdxLen + 3 + maxNameLen + 9).append(EOL);

        int diffIdx = -1;  // index of where the arrays first differ
        for (int i = 0; i < maxLen; i++) {
            append(tableBuilder, String.valueOf(i), maxIdxLen).append(" | ");

            final String expectedStr = i < expected.length ? expected[i].name() : "null";
            append(tableBuilder, expectedStr, maxNameLen);

            tableBuilder.append(" | ");

            final String actualStr = i < actual.length ?  actual[i].name() : "null";
            tableBuilder.append(actualStr);

            tableBuilder.append(EOL);

            if (!expectedStr.equals(actualStr) && diffIdx == -1) {
                diffIdx = i;
            }
        }

        final StringBuilder builder = new StringBuilder("Expected and actual arrays differ starting at index: ")
                .append(diffIdx)
                .append(EOL)
                .append(tableBuilder);

        return builder.toString();
    }

    private StringBuilder append(final StringBuilder builder, final String str, final int fixedLen) {
        builder.append(str);
        for (int i = str.length(); i < fixedLen; i++) {
            builder.append(' ');
        }
        return builder;
    }

    private StringBuilder appendRep(final StringBuilder builder, final char c, final int fixedLen) {
        final char[] tmp = new char[fixedLen];
        Arrays.fill(tmp, c);
        builder.append(tmp);
        return builder;
    }

    private int maxNameLen(final NamedEvent[] namedEvents) {
        int len = 0;
        for (final NamedEvent namedEvent : namedEvents) {
            final int nameLen = namedEvent.name().length();
            if (nameLen > len) {
                len = nameLen;
            }
        }
        return len;
    }

    private NamedEvent[] readAllEvents(final Optional<NodeHandle> maybeContainerNode, final IEmbeddedXMLStreamReader xmlStreamReader) throws XMLStreamException {
        final List<NamedEvent> events = new ArrayList<>();

        while (xmlStreamReader.hasNext()) {
            final NamedEvent namedEvent = NamedEvent.fromEvent(xmlStreamReader.next());
            // filter for descendant-or-self if specified
            if(maybeContainerNode.isPresent() && !descendantOrSelf(maybeContainerNode.get(), xmlStreamReader)) {
                break;  // exit-while
            }
            events.add(namedEvent);
        }

        return events.toArray(new NamedEvent[events.size()]);
    }

    private boolean descendantOrSelf(final NodeHandle root, final IEmbeddedXMLStreamReader xmlStreamReader) {
        final NodeId other = (NodeId)xmlStreamReader.getProperty(PROPERTY_NODE_ID);
        return other.isDescendantOrSelfOf(root.getNodeId());
    }

    @BeforeClass
    public static void setup() throws EXistException, LockException, SAXException, PermissionDeniedException, IOException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            createCollection(broker, transaction, TEST_MIXED_XML_COLLECTION,
                    Tuple(MIXED_XML_NAME, MIXED_XML)
            );

            transaction.commit();
        }
    }

    @AfterClass
    public static void cleanup() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            deleteCollection(broker, transaction, TEST_MIXED_XML_COLLECTION);

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

    enum NamedEvent {
        START_ELEMENT(XMLStreamConstants.START_ELEMENT),
        END_ELEMENT(XMLStreamConstants.END_ELEMENT),
        PROCESSING_INSTRUCTION(XMLStreamConstants.PROCESSING_INSTRUCTION),
        CHARACTERS(XMLStreamConstants.CHARACTERS),
        COMMENT(XMLStreamConstants.COMMENT),
        SPACE(XMLStreamConstants.SPACE),
        START_DOCUMENT(XMLStreamConstants.START_DOCUMENT),
        END_DOCUMENT(XMLStreamConstants.END_DOCUMENT),
        ENTITY_REFERENCE(XMLStreamConstants.ENTITY_REFERENCE),
        ATTRIBUTE(XMLStreamConstants.ATTRIBUTE),
        DTD(XMLStreamConstants.DTD),
        CDATA(XMLStreamConstants.CDATA),
        NAMESPACE(XMLStreamConstants.NAMESPACE),
        NOTATION_DECLARATION(XMLStreamConstants.NOTATION_DECLARATION),
        ENTITY_DECLARATION(XMLStreamConstants.ENTITY_DECLARATION);

        private final int event;

        NamedEvent(final int event) {
            this.event = event;
        }

        public int getEvent() {
            return event;
        }

        public static NamedEvent fromEvent(final int event) {
            for (final NamedEvent namedEvent : NamedEvent.values()) {
                if (namedEvent.event == event) {
                    return namedEvent;
                }
            }
            throw new IllegalArgumentException("No named event for XMLStreamConstants event: " + event);
        }
    }
}
