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
package org.exist.numbering;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.NodeHandle;
import org.exist.dom.persistent.NodeProxy;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.StorageAddress;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Text;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DLNStorageTest {

    private static final XmldbURI TEST_COLLECTION = XmldbURI.create(XmldbURI.ROOT_COLLECTION + "/test");

    private static final String TEST_XML =
            "<test>" +
            "<para>My first paragraph.</para>" +
            "<!-- A comment -->" +
            "<para>This one contains a <a href=\"#\">link</a>.</para>" +
            "<?echo \"A processing instruction\"?>" +
            "<para>Another <b>paragraph</b>.</para>" +
            "</test>";

    @Test
    public void nodeStorage() throws Exception {
        BrokerPool pool = BrokerPool.getInstance();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            // test element ids
            Sequence seq = xquery.execute(broker, "doc('/db/test/test_string.xml')/test/para",
                    null);
            assertEquals(3, seq.getItemCount());
            NodeProxy comment = (NodeProxy) seq.itemAt(0);
            assertEquals("1.1", comment.getNodeId().toString());
            comment = (NodeProxy) seq.itemAt(1);
            assertEquals("1.3", comment.getNodeId().toString());
            comment = (NodeProxy) seq.itemAt(2);
            assertEquals("1.5", comment.getNodeId().toString());

            seq = xquery.execute(broker, "doc('/db/test/test_string.xml')/test//a",
                    null);
            assertEquals(1, seq.getItemCount());
            NodeProxy a = (NodeProxy) seq.itemAt(0);
            assertEquals("1.3.2", a.getNodeId().toString());

            // test attribute id
            seq = xquery.execute(broker, "doc('/db/test/test_string.xml')/test//a/@href",
                    null);
            assertEquals(1, seq.getItemCount());
            NodeProxy href = (NodeProxy) seq.itemAt(0);
            StorageAddress.toString(href);
            assertEquals("1.3.2.1", href.getNodeId().toString());
            // test Attr deserialization
            Attr attr = (Attr) href.getNode();
            StorageAddress.toString(((NodeHandle)attr));
            // test Attr fields
            assertEquals("href", attr.getNodeName());
            assertEquals("href", attr.getName());
            assertEquals("#", attr.getValue());
            // test DOMFile.getNodeValue()
            assertEquals("#", href.getStringValue());

            // test text node
            seq = xquery.execute(broker, "doc('/db/test/test_string.xml')/test//b/text()",
                    null);
            assertEquals(1, seq.getItemCount());
            NodeProxy text = (NodeProxy) seq.itemAt(0);
            assertEquals("1.5.2.1", text.getNodeId().toString());
            // test DOMFile.getNodeValue()
            assertEquals("paragraph", text.getStringValue());
            // test Text deserialization
            Text node = (Text) text.getNode();
            assertEquals("paragraph", node.getNodeValue());
            assertEquals("paragraph", node.getData());
        }
    }

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @BeforeClass
    public static void setUp() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction()) {

            Collection test = broker.getOrCreateCollection(transaction, TEST_COLLECTION);
            broker.saveCollection(transaction, test);

            broker.storeDocument(transaction, XmldbURI.create("test_string.xml"), new StringInputSource(TEST_XML), MimeType.XML_TYPE, test);
            //TODO : unlock the collection here ?

            transact.commit(transaction);
        }
    }

    @AfterClass
    public static void tearDown() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction()) {

            final Collection root = broker.getOrCreateCollection(transaction, XmldbURI.create(XmldbURI.ROOT_COLLECTION + TEST_COLLECTION));
            assertNotNull(root);
            broker.removeCollection(transaction, root);

            transact.commit(transaction);
        }
    }
}
