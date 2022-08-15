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
package org.exist.collections.triggers;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.junit.*;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.collections.CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE_URI;
import static org.exist.collections.CollectionConfigurationManager.CONFIG_COLLECTION_URI;
import static org.exist.test.Util.*;
import static org.junit.Assert.*;

/**
 * Checks that multiple XQuery Triggers can be formed into a chain,
 * by having the output event of one trigger be the input event
 * of another trigger.
 *
 * Creates the following Collections:
 *  /db/testXQueryTriggerChain/collection1/input
 *  /db/testXQueryTriggerChain/collection1/output
 *  /db/testXQueryTriggerChain/collection2/input
 *  /db/testXQueryTriggerChain/collection2/output
 *
 * A chain is established between two triggers, trigger1 and trigger2
 * via the collection /db/testXQueryTriggerChain/collection1/input.
 *
 *  The first trigger (trigger1) is installed on /db/testXQueryTriggerChain/collection1/input,
 *  and is notified of `after-create-document` events. When such an event occurs,
 *  trigger1 creates a new sub-collection (named with a UUID)
 *  in /db/testXQueryTriggerChain/collection1/output
 *  and moves the document that triggered the event into the new sub-collection.
 *
 *  The second trigger (trigger2) is installed on /db/testXQueryTriggerChain/collection1/input,
 *  and is notified of `after-move-document` events (caused by trigger1). When such an event occurs,
 *  trigger2 first copies the sub-collection created by trigger1 from /db/testXQueryTriggerChain/collection1/output
 *  to /db/testXQueryTriggerChain/collection2/input, it then copies all documents
 *  from the copy of the sub-collection to /db/testXQueryTriggerChain/collection2/output.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class XQueryTriggerChainTest {

    @ClassRule
    public static final ExistEmbeddedServer EXIST_EMBEDDED_SERVER = new ExistEmbeddedServer(true, true);

	private final static XmldbURI TEST_COLLECTION_URI = XmldbURI.create("/db/testXQueryTriggerChain");

    private final static XmldbURI COLLECTION_1_URI = TEST_COLLECTION_URI.append("collection1");
    private final static XmldbURI COLLECTION_1_INPUT_URI = COLLECTION_1_URI.append("input");
    private final static XmldbURI COLLECTION_1_OUTPUT_URI = COLLECTION_1_URI.append("output");

    private final static XmldbURI COLLECTION_2_URI = TEST_COLLECTION_URI.append("collection2");
    private final static XmldbURI COLLECTION_2_INPUT_URI = COLLECTION_2_URI.append("input");
    private final static XmldbURI COLLECTION_2_OUTPUT_URI = COLLECTION_2_URI.append("output");

    private final static XmldbURI TRIGGER_1_MODULE_URI = TEST_COLLECTION_URI.append("XQueryTrigger1.xqm");
    private final static String TRIGGER_1_MODULE =
            "module namespace trigger = 'http://exist-db.org/xquery/trigger';\n" +
            "import module namespace util = 'http://exist-db.org/xquery/util';\n" +
            "import module namespace xmldb = 'http://exist-db.org/xquery/xmldb';\n" +
            "\n" +
            "declare function trigger:after-create-document($uri as xs:anyURI) {\n" +
            "  let $in-collection := fn:replace($uri, '(.+)/.+', '$1')\n" +
            "  let $in-document-name := fn:replace($uri, '.+/(.+)', '$1')\n" +
            "  let $out-sub-collection := xmldb:create-collection('" + COLLECTION_1_OUTPUT_URI + "', util:uuid())\n" +
            "  return\n" +
            "    xmldb:move($in-collection, $out-sub-collection, $in-document-name)\n" +
            "};";

    private final static XmldbURI TRIGGER_2_MODULE_URI = TEST_COLLECTION_URI.append("XQueryTrigger2.xqm");
    private final static String TRIGGER_2_MODULE =
            "module namespace trigger = 'http://exist-db.org/xquery/trigger';\n" +
            "import module namespace util = 'http://exist-db.org/xquery/util';\n" +
            "import module namespace xmldb = 'http://exist-db.org/xquery/xmldb';\n" +
            "\n" +
            "declare function trigger:after-move-document($new-uri as xs:anyURI, $uri as xs:anyURI) {\n" +
            "  let $parent-collection := fn:replace($new-uri, '(.+)/.+', '$1')\n" +
                    "let $_ := util:log('ERROR', ('$new-uri=', $new-uri, '$parent-collection=', $parent-collection))\n" +
            "  let $in-sub-collection := xmldb:copy-collection($parent-collection, '" + COLLECTION_2_INPUT_URI + "')\n" +
            "  return\n" +
            "    xmldb:get-child-resources($in-sub-collection) ! xmldb:copy-resource($in-sub-collection, ., '" + COLLECTION_2_OUTPUT_URI + "', ())\n" +
            "};";

    private final static String TRIGGER_1_COLLECTION_CONFIG =
            "<exist:collection xmlns:exist='http://exist-db.org/collection-config/1.0'>" +
            "  <exist:triggers>" +
            "     <exist:trigger class='org.exist.collections.triggers.XQueryTrigger'>" +
            "	     <exist:parameter name='url' value='" + TRIGGER_1_MODULE_URI + "'/>" +
            "     </exist:trigger>" +
            "     <exist:trigger class='org.exist.collections.triggers.XQueryTrigger'>" +
            "	     <exist:parameter name='url' value='" + TRIGGER_2_MODULE_URI + "'/>" +
            "     </exist:trigger>" +
            "  </exist:triggers>" +
            "</exist:collection>";

    @BeforeClass
    public static void setup() throws EXistException, PermissionDeniedException, IOException, SAXException, LockException {
        final BrokerPool pool = EXIST_EMBEDDED_SERVER.getBrokerPool();
        try (final Txn transaction = pool.getTransactionManager().beginTransaction();
                final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            // store the trigger modules
            try (final Collection collection = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI)) {
                assertNotNull(collection);

                final XmldbURI trigger1ModuleUri = storeQuery(broker, transaction, new StringInputSource(TRIGGER_1_MODULE.getBytes(UTF_8)), collection, TRIGGER_1_MODULE_URI);
                assertEquals(TRIGGER_1_MODULE_URI, trigger1ModuleUri);

                final XmldbURI trigger2ModuleUri = storeQuery(broker, transaction, new StringInputSource(TRIGGER_2_MODULE.getBytes(UTF_8)), collection, TRIGGER_2_MODULE_URI);
                assertEquals(TRIGGER_2_MODULE_URI, trigger2ModuleUri);
            }

            // create the collections for trigger 1 and trigger 2 to act upon
            for (final XmldbURI collectionUri : new XmldbURI[]{ COLLECTION_1_INPUT_URI,  COLLECTION_1_OUTPUT_URI, COLLECTION_2_INPUT_URI, COLLECTION_2_OUTPUT_URI}) {
                try (final Collection collection = broker.getOrCreateCollection(transaction, collectionUri)) {
                    assertNotNull(collection);
                }
            }

            // install the collection.xconf for the collection1 input for trigger1 (for after-create-document event) and trigger2 (for after-move-document event)
            final XmldbURI configCollection1InputUri = CONFIG_COLLECTION_URI.append(COLLECTION_1_INPUT_URI);
            try (final Collection collection = broker.getOrCreateCollection(transaction, configCollection1InputUri)) {
                assertNotNull(collection);
                broker.storeDocument(transaction, configCollection1InputUri.append(DEFAULT_COLLECTION_CONFIG_FILE_URI), new StringInputSource(TRIGGER_1_COLLECTION_CONFIG), MimeType.XML_TYPE, collection);
            }

            transaction.commit();
        }
    }

    @Test
    public void xqueryTriggerChain() throws EXistException, PermissionDeniedException, IOException, SAXException, LockException, XPathException {
        final String uuid = UUID.randomUUID().toString();
        final String documentName = uuid + ".xml";
        final String documentContent = "<id>" + uuid + "</id>";

        final BrokerPool pool = EXIST_EMBEDDED_SERVER.getBrokerPool();
        try (final Txn transaction = pool.getTransactionManager().beginTransaction();
             final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            // store a document into the /db/testXQueryTriggerChain/collection1/input, this should cause the first trigger to fire
            try (final Collection collection = broker.getOrCreateCollection(transaction, COLLECTION_1_INPUT_URI)) {
                assertNotNull(collection);
                broker.storeDocument(transaction, XmldbURI.create(documentName), new StringInputSource(documentContent), MimeType.XML_TYPE, collection);
            }

            transaction.commit();
        }

        // trigger 1 should have completed by this stage... which will have also caused trigger 2 to fire and complete by this stage... so now check the content of the collections/documents produced by the triggers

        try (final Txn transaction = pool.getTransactionManager().beginTransaction();
             final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            // 1) assert that trigger1 moved the document from its input collection
            try (final Collection collection = broker.getOrCreateCollection(transaction, COLLECTION_1_INPUT_URI)) {
                assertNotNull(collection);
                assertEquals(0, collection.getDocumentCount(broker));
                assertFalse(collection.hasDocument(broker, XmldbURI.create(documentName)));
            }

            // 2) assert that trigger1 created a sub-collection in its output collection
            XmldbURI collection1OutputSubCollectionUri = null;
            try (final Collection collection = broker.getOrCreateCollection(transaction, COLLECTION_1_OUTPUT_URI)) {
                assertNotNull(collection);

                assertEquals(1, collection.getChildCollectionCount(broker));
                final Iterator<XmldbURI> childCollectionIterator = collection.collectionIterator(broker);
                assertTrue(childCollectionIterator.hasNext());
                final XmldbURI subCollectionName = childCollectionIterator.next();
                assertNotNull(subCollectionName);
                collection1OutputSubCollectionUri = COLLECTION_1_OUTPUT_URI.append(subCollectionName);
            }

            // 3) assert that trigger1 moved the document to the sub-collection in its output collection
            try (final Collection collection = broker.getOrCreateCollection(transaction, collection1OutputSubCollectionUri)) {
                assertNotNull(collection);

                assertEquals(0, collection.getChildCollectionCount(broker));
                assertEquals(1, collection.getDocumentCount(broker));
                assertTrue(collection.hasDocument(broker, XmldbURI.create(documentName)));
            }

            // 4) assert that trigger2 copied the sub-collection from trigger1's output collection to the input collection of trigger2
            try (final Collection collection = broker.getOrCreateCollection(transaction, COLLECTION_2_INPUT_URI)) {
                assertNotNull(collection);

                assertEquals(1, collection.getChildCollectionCount(broker));
                assertEquals(0, collection.getDocumentCount(broker));

                final XmldbURI collection2InputSubCollectionUri = COLLECTION_2_INPUT_URI.append(collection1OutputSubCollectionUri.lastSegment());
                assertTrue(collection.hasChildCollection(broker, collection2InputSubCollectionUri.lastSegment()));

                // check that the sub-collection contains the expected document
                try (final Collection subCollection = broker.getOrCreateCollection(transaction, collection2InputSubCollectionUri)) {
                    assertNotNull(subCollection);

                    assertEquals(0, subCollection.getChildCollectionCount(broker));
                    assertEquals(1, subCollection.getDocumentCount(broker));
                    assertTrue(subCollection.hasDocument(broker, XmldbURI.create(documentName)));
                }
            }

            // 5) assert that trigger2 copied the document from the sub-collection of its input collection to its output collection
            try (final Collection collection = broker.getOrCreateCollection(transaction, COLLECTION_2_OUTPUT_URI)) {
                assertEquals(0, collection.getChildCollectionCount(broker));
                assertEquals(1, collection.getDocumentCount(broker));
                assertTrue(collection.hasDocument(broker, XmldbURI.create(documentName)));
            }

            transaction.commit();
        }
    }
}