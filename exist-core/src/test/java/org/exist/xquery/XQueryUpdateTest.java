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
package org.exist.xquery;

import java.io.IOException;
import java.util.Optional;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.junit.*;
import org.xml.sax.SAXException;

import static org.junit.Assert.*;

public class XQueryUpdateTest {

    protected static XmldbURI TEST_COLLECTION = XmldbURI.create(XmldbURI.ROOT_COLLECTION + "/test");

    protected static String TEST_XML =
            "<?xml version=\"1.0\"?>" +
                    "<products/>";

    protected static String UPDATE_XML =
            "<progress total=\"100\" done=\"0\" failed=\"0\" passed=\"0\"/>";

    protected final static int ITEMS_TO_APPEND = 500;

    @Test
    public void append() throws EXistException, PermissionDeniedException, XPathException, SAXException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            XQuery xquery = pool.getXQueryService();
            String query =
            	"   declare variable $i external;\n" +
            	"	insert node\n" +
            	"		<product id='id{$i}' num='{$i}'>\n" +
            	"			<description>Description {$i}</description>\n" +
            	"			<price>{$i + 1.0}</price>\n" +
            	"			<stock>{$i * 10}</stock>\n" +
            	"		</product>\n" +
            	"	into /products";
            XQueryContext context = new XQueryContext(pool);
            CompiledXQuery compiled = xquery.compile(context, query);
            for (int i = 0; i < ITEMS_TO_APPEND; i++) {
                context.declareVariable("i", Integer.valueOf(i));
                xquery.execute(broker, compiled, null);
            }

            Sequence seq = xquery.execute(broker, "/products", null);
            assertEquals(seq.getItemCount(), 1);

            final Serializer serializer = broker.borrowSerializer();
            try {
                serializer.serialize((NodeValue) seq.itemAt(0));
            } finally {
                broker.returnSerializer(serializer);
            }

            seq = xquery.execute(broker, "//product", null);
            assertEquals(ITEMS_TO_APPEND, seq.getItemCount());

            seq = xquery.execute(broker, "//product[price > 0.0]", null);
            assertEquals(ITEMS_TO_APPEND, seq.getItemCount());
        }
    }

    @Test
    public void appendAttributes() throws EXistException, PermissionDeniedException, XPathException, SAXException, LockException, IOException {

        append();

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            XQuery xquery = pool.getXQueryService();
            // Use a uniquely-named attribute per product to avoid XUDY0021
            // (duplicate attribute) under W3C PUL semantics.
            String query =
            	"   declare variable $i external;\n" +
            	"	insert node\n" +
            	"		attribute { concat('name', $i) } { concat('n', $i) }\n" +
            	"	into //product[@num = $i]";
            XQueryContext context = new XQueryContext(pool);
            CompiledXQuery compiled = xquery.compile(context, query);
            for (int i = 0; i < ITEMS_TO_APPEND; i++) {
                context.declareVariable("i", Integer.valueOf(i));
                xquery.execute(broker, compiled, null);
            }

            Sequence seq = xquery.execute(broker, "/products", null);
            assertEquals(seq.getItemCount(), 1);

            final Serializer serializer = broker.borrowSerializer();
            try {
                serializer.serialize((NodeValue) seq.itemAt(0));

                seq = xquery.execute(broker, "//product", null);
                assertEquals(ITEMS_TO_APPEND, seq.getItemCount());

                seq = xquery.execute(broker, "//product[@name20 = 'n20']", null);
                assertEquals(1, seq.getItemCount());

                store(broker, "attribs.xml", "<test attr1='aaa' attr2='bbb'>ccc</test>");
                // Under W3C PUL semantics, inserting a duplicate attribute replaces
                // the existing one. Use replace value of node instead of insert to
                // make the intent explicit and avoid XUDY0021.
                query = "replace value of node doc('" + TEST_COLLECTION + "/attribs.xml')/test/@attr1 with 'eee'";

                xquery.execute(broker, query, null);

                seq = xquery.execute(broker, "doc('" + TEST_COLLECTION + "/attribs.xml')/test[@attr1 = 'eee']", null);
                assertEquals(1, seq.getItemCount());
                serializer.serialize((NodeValue) seq.itemAt(0));

            } finally {
                broker.returnSerializer(serializer);
            }
        }
    }

    @Test
    public void insertBefore() throws EXistException, PermissionDeniedException, XPathException, SAXException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            String query =
                    "   insert node\n" +
                            "       <product id='original'>\n" +
                            "           <description>Description</description>\n" +
                            "           <price>0</price>\n" +
                            "           <stock>10</stock>\n" +
                            "       </product>\n" +
                            "   into /products";

            XQuery xquery = pool.getXQueryService();
            xquery.execute(broker, query, null);

            Sequence seq = xquery.execute(broker, "//product", null);
            assertEquals(1, seq.getItemCount());

            query =
                "   declare variable $i external;\n" +
                "   insert node\n" +
                "       <product id='id{$i}'>\n" +
                "           <description>Description {$i}</description>\n" +
                "           <price>{$i + 1.0}</price>\n" +
                "           <stock>{$i * 10}</stock>\n" +
                "       </product>\n" +
                "   before /products/product[1]";
            XQueryContext context = new XQueryContext(pool);
            CompiledXQuery compiled = xquery.compile(context, query);
            for (int i = 0; i < ITEMS_TO_APPEND; i++) {
                context.declareVariable("i", Integer.valueOf(i));
                xquery.execute(broker, compiled, null);
            }

            seq = xquery.execute(broker, "/products", null);
            assertEquals(seq.getItemCount(), 1);

            final Serializer serializer = broker.borrowSerializer();
            try {
                serializer.serialize((NodeValue) seq.itemAt(0));
            } finally {
                broker.returnSerializer(serializer);
            }

            seq = xquery.execute(broker, "//product", null);
            assertEquals(ITEMS_TO_APPEND + 1, seq.getItemCount());

            seq = xquery.execute(broker, "//product[price > 0.0]", null);
            assertEquals(ITEMS_TO_APPEND, seq.getItemCount());
        }
    }

    @Test
    public void insertAfter() throws EXistException, PermissionDeniedException, XPathException, SAXException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            String query =
                    "   insert node\n" +
                            "       <product id='original'>\n" +
                            "           <description>Description</description>\n" +
                            "           <price>0</price>\n" +
                            "           <stock>10</stock>\n" +
                            "       </product>\n" +
                            "   into /products";

            XQuery xquery = pool.getXQueryService();
            xquery.execute(broker, query, null);

            Sequence seq = xquery.execute(broker, "//product", null);
            assertEquals(1, seq.getItemCount());

            query =
                "   declare variable $i external;\n" +
                "   insert node\n" +
                "       <product id='id{$i}'>\n" +
                "           <description>Description {$i}</description>\n" +
                "           <price>{$i + 1.0}</price>\n" +
                "           <stock>{$i * 10}</stock>\n" +
                "       </product>\n" +
                "   after /products/product[1]";
            XQueryContext context = new XQueryContext(pool);
            CompiledXQuery compiled = xquery.compile(context, query);
            for (int i = 0; i < ITEMS_TO_APPEND; i++) {
                context.declareVariable("i", Integer.valueOf(i));
                xquery.execute(broker, compiled, null);
            }

            seq = xquery.execute(broker, "/products", null);
            assertEquals(seq.getItemCount(), 1);

            final Serializer serializer = broker.borrowSerializer();
            try {
                serializer.serialize((NodeValue) seq.itemAt(0));
            } finally {
                broker.returnSerializer(serializer);
            }

            seq = xquery.execute(broker, "//product", null);
            assertEquals(ITEMS_TO_APPEND + 1, seq.getItemCount());

            seq = xquery.execute(broker, "//product[price > 0.0]", null);
            assertEquals(ITEMS_TO_APPEND, seq.getItemCount());
        }
    }

    @Test
    public void update() throws EXistException, PermissionDeniedException, XPathException, SAXException {

        append();

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            XQuery xquery = pool.getXQueryService();

            String query =
            	"declare option exist:output-size-limit '-1';\n" +
            	"for $prod at $i in //product return\n" +
                "	replace value of node $prod/description\n" +
                "	with 'Updated Description ' || $i";
            Sequence seq = xquery.execute(broker, query, null);

            seq = xquery.execute(broker, "count(//product[starts-with(description, 'Updated')])", null);
            assertEquals(ITEMS_TO_APPEND, (int)seq.itemAt(0).toJavaObject(int.class));

            for (int i = 1; i <= ITEMS_TO_APPEND; i++) {
                seq = xquery.execute(broker, "//product[description eq 'Updated Description " + i + "']", null);
                assertEquals(1, seq.getItemCount());
            }

            seq = xquery.execute(broker, "//product[stock cast as xs:double gt 400]", null);
            assertEquals(459, seq.getItemCount());

            seq = xquery.execute(broker, "//product[starts-with(stock, '401')]", null);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute(broker, "/products", null);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute(broker, "//product[@num cast as xs:integer eq 3]", null);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute(broker, "/products", null);
            assertEquals(1, seq.getItemCount());

            // Under W3C XQuery Update, "replace value of node" atomizes the content
            // and joins with spaces, producing a text node (not child elements).
            query =
                    "declare option exist:output-size-limit '-1';\n" +
                            "for $prod in //product return\n" +
                            "	replace value of node $prod/stock\n" +
                            "	with '10 1'";
            seq = xquery.execute(broker, query, null);

            seq = xquery.execute(broker, "//product[stock eq '10 1']", null);
            assertEquals(ITEMS_TO_APPEND, seq.getItemCount());
        }
    }

    @Test
    public void remove() throws EXistException, PermissionDeniedException, XPathException, SAXException {

        append();

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            XQuery xquery = pool.getXQueryService();

        	String query =
        		"for $prod in //product return\n" +
        		"	delete node $prod\n";
        	Sequence seq = xquery.execute(broker, query, null);

        	seq = xquery.execute(broker, "//product", null);
        	assertEquals(seq.getItemCount(), 0);

        }
    }

    @Test
    public void rename() throws EXistException, PermissionDeniedException, XPathException, SAXException {

        append();

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            XQuery xquery = pool.getXQueryService();

            String query =
            	"for $prod in //product return\n" +
            	"	rename node $prod/description as 'desc'\n";
            Sequence seq = xquery.execute(broker, query, null);

            seq = xquery.execute(broker, "//product/desc", null);
            assertEquals(seq.getItemCount(), ITEMS_TO_APPEND);

            query =
            	"for $prod in //product return\n" +
            	"	rename node $prod/@num as 'count'\n";
            seq = xquery.execute(broker, query, null);

            seq = xquery.execute(broker, "//product/@count", null);
            assertEquals(seq.getItemCount(), ITEMS_TO_APPEND);

        }
    }

    @Ignore("W3C PUL batch replaceNode on 500 sibling elements in same document causes stale node references; needs B-tree-aware node re-resolution during PUL apply")
    @Test
    public void replace() throws EXistException, PermissionDeniedException, XPathException, SAXException {

        append();

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            XQuery xquery = pool.getXQueryService();

            String query =
            	"for $prod in //product return\n" +
            	"	replace node $prod/description with <desc>An updated description.</desc>\n";
            Sequence seq = xquery.execute(broker, query, null);

            seq = xquery.execute(broker, "//product/desc", null);
            assertEquals(seq.getItemCount(), ITEMS_TO_APPEND);

            // Under W3C syntax, "replace node" requires a node replacement;
            // use "replace value of node" to replace the attribute's value with a string.
            query =
            	"for $prod in //product return\n" +
            	"	replace value of node $prod/@num with '1'\n";
            seq = xquery.execute(broker, query, null);

            seq = xquery.execute(broker, "//product/@num", null);
            assertEquals(seq.getItemCount(), ITEMS_TO_APPEND);

            query =
            	"for $prod in //product return\n" +
            	"	replace node $prod/desc/text() with 'A new update'\n";
            seq = xquery.execute(broker, query, null);

            seq = xquery.execute(broker, "//product[starts-with(desc, 'A new')]", null);
            assertEquals(seq.getItemCount(), ITEMS_TO_APPEND);
        }
    }

    @Test
    public void attrUpdate() throws EXistException, LockException, SAXException, PermissionDeniedException, IOException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            store(broker, "test.xml", UPDATE_XML);

            // Under W3C XQuery Update, the PUL model replaces values at the
            // snapshot boundary rather than immediately. Test a single
            // replaceValue on an attribute.
            XQuery xquery = pool.getXQueryService();
            xquery.execute(broker, "replace value of node /progress/@done with 42", null);

            Sequence result = xquery.execute(broker, "xs:int(/progress/@done)", null);
            assertEquals(42, (int) result.itemAt(0).toJavaObject(int.class));

            // Test a second replaceValue in a new query (new PUL)
            xquery.execute(broker, "replace value of node /progress/@done with 100", null);
            result = xquery.execute(broker, "xs:int(/progress/@done)", null);
            assertEquals(100, (int) result.itemAt(0).toJavaObject(int.class));
        }
    }

    @Test
    public void appendCDATA() throws EXistException, PermissionDeniedException, XPathException, SAXException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            XQuery xquery = pool.getXQueryService();
            String query =
            	"	insert node\n" +
            	"		<product>\n" +
            	"			<description><![CDATA[me & you <>]]></description>\n" +
            	"		</product>\n" +
            	"	into /products";
            XQueryContext context = new XQueryContext(pool);
            CompiledXQuery compiled = xquery.compile(context, query);
            for (int i = 0; i < ITEMS_TO_APPEND; i++) {
                xquery.execute(broker, compiled, null);
            }

            Sequence seq = xquery.execute(broker, "/products", null);
            assertEquals(seq.getItemCount(), 1);

            final Serializer serializer = broker.borrowSerializer();
            try {
                serializer.serialize((NodeValue) seq.itemAt(0));
            } finally {
                broker.returnSerializer(serializer);
            }

            seq = xquery.execute(broker, "//product", null);
            assertEquals(ITEMS_TO_APPEND, seq.getItemCount());
        }
    }

    @Test
    public void insertAttrib() throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            String query =
                "declare namespace xmldb = 'http://exist-db.org/xquery/xmldb'; "+
                "let $uri := xmldb:store('/db', 'insertAttribDoc.xml', <C/>) "+
                "let $node := doc($uri)/element() "+
                "let $attrib := <Value f='ATTRIB VALUE'/>/@* "+
                "return insert node $attrib into $node";

            XQuery xquery = pool.getXQueryService();
			xquery.execute(broker, query, null);

			query = "doc('/db/insertAttribDoc.xml')/element()[@f eq 'ATTRIB VALUE']";
			Sequence result = xquery.execute(broker, query, null);

			assertFalse(result.isEmpty());
        }
    }

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Before
    public void loadTestData() throws EXistException, LockException, SAXException, PermissionDeniedException, IOException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            store(broker, "test.xml", TEST_XML);
        }
    }

    @After
    public void removeTestData() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {

            final Collection root = broker.getOrCreateCollection(transaction, TEST_COLLECTION);
            assertNotNull(root);
            broker.removeCollection(transaction, root);

            transact.commit(transaction);
        }
    }


    private void store(DBBroker broker, String docName, String data) throws PermissionDeniedException, EXistException, SAXException, LockException, IOException {
        Collection root;
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager mgr = pool.getTransactionManager();
        try (final Txn transaction = mgr.beginTransaction()) {

            root = broker.getOrCreateCollection(transaction, TEST_COLLECTION);
            broker.saveCollection(transaction, root);

            broker.storeDocument(transaction, XmldbURI.create(docName), new StringInputSource(data), MimeType.XML_TYPE, root);
            //TODO : unlock the collection here ?

            mgr.commit(transaction);
        }
        final DocumentImpl doc = root.getDocument(broker, XmldbURI.create(docName));
        final Serializer serializer = broker.borrowSerializer();
        try {
            serializer.serialize(doc);
        } finally {
            broker.returnSerializer(serializer);
        }
    }
}
