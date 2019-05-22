package org.exist.xquery;

import java.io.IOException;
import java.util.Optional;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
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
            	"	update insert\n" +
            	"		<product id='id{$i}' num='{$i}'>\n" +
            	"			<description>Description {$i}</description>\n" +
            	"			<price>{$i + 1.0}</price>\n" +
            	"			<stock>{$i * 10}</stock>\n" +
            	"		</product>\n" +
            	"	into /products";
            XQueryContext context = new XQueryContext(pool);
            CompiledXQuery compiled = xquery.compile(broker, context, query);
            for (int i = 0; i < ITEMS_TO_APPEND; i++) {
                context.declareVariable("i", Integer.valueOf(i));
                xquery.execute(broker, compiled, null);
            }

            Sequence seq = xquery.execute(broker, "/products", null);
            assertEquals(seq.getItemCount(), 1);

            Serializer serializer = broker.getSerializer();
            serializer.serialize((NodeValue) seq.itemAt(0));

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
            String query =
            	"   declare variable $i external;\n" +
            	"	update insert\n" +
            	"		attribute name { concat('n', $i) }\n" +
            	"	into //product[@num = $i]";
            XQueryContext context = new XQueryContext(pool);
            CompiledXQuery compiled = xquery.compile(broker, context, query);
            for (int i = 0; i < ITEMS_TO_APPEND; i++) {
                context.declareVariable("i", Integer.valueOf(i));
                xquery.execute(broker, compiled, null);
            }

            Sequence seq = xquery.execute(broker, "/products", null);
            assertEquals(seq.getItemCount(), 1);

            Serializer serializer = broker.getSerializer();
            serializer.serialize((NodeValue) seq.itemAt(0));

            seq = xquery.execute(broker, "//product", null);
            assertEquals(ITEMS_TO_APPEND, seq.getItemCount());

            seq = xquery.execute(broker, "//product[@name = 'n20']", null);
            assertEquals(1, seq.getItemCount());

            store(broker, "attribs.xml", "<test attr1='aaa' attr2='bbb'>ccc</test>");
            query = "update insert attribute attr1 { 'eee' } into /test";

            //testing duplicate attribute ...
            xquery.execute(broker, query, null);

            seq = xquery.execute(broker, "doc('" + TEST_COLLECTION + "/attribs.xml')/test[@attr1 = 'eee']", null);
            assertEquals(1, seq.getItemCount());
            serializer.serialize((NodeValue) seq.itemAt(0));
        }
    }

    @Test
    public void insertBefore() throws EXistException, PermissionDeniedException, XPathException, SAXException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            String query =
                    "   update insert\n" +
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
                "   update insert\n" +
                "       <product id='id{$i}'>\n" +
                "           <description>Description {$i}</description>\n" +
                "           <price>{$i + 1.0}</price>\n" +
                "           <stock>{$i * 10}</stock>\n" +
                "       </product>\n" +
                "   preceding /products/product[1]";
            XQueryContext context = new XQueryContext(pool);
            CompiledXQuery compiled = xquery.compile(broker, context, query);
            for (int i = 0; i < ITEMS_TO_APPEND; i++) {
                context.declareVariable("i", Integer.valueOf(i));
                xquery.execute(broker, compiled, null);
            }

            seq = xquery.execute(broker, "/products", null);
            assertEquals(seq.getItemCount(), 1);

            Serializer serializer = broker.getSerializer();
            serializer.serialize((NodeValue) seq.itemAt(0));

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
                    "   update insert\n" +
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
                "   update insert\n" +
                "       <product id='id{$i}'>\n" +
                "           <description>Description {$i}</description>\n" +
                "           <price>{$i + 1.0}</price>\n" +
                "           <stock>{$i * 10}</stock>\n" +
                "       </product>\n" +
                "   following /products/product[1]";
            XQueryContext context = new XQueryContext(pool);
            CompiledXQuery compiled = xquery.compile(broker, context, query);
            for (int i = 0; i < ITEMS_TO_APPEND; i++) {
                context.declareVariable("i", Integer.valueOf(i));
                xquery.execute(broker, compiled, null);
            }

            seq = xquery.execute(broker, "/products", null);
            assertEquals(seq.getItemCount(), 1);

            Serializer serializer = broker.getSerializer();
            serializer.serialize((NodeValue) seq.itemAt(0));

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
                "	update value $prod/description\n" +
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

            query =
                    "declare option exist:output-size-limit '-1';\n" +
                            "for $prod in //product return\n" +
                            "	update value $prod/stock\n" +
                            "	with (<local>10</local>,<external>1</external>)";
            seq = xquery.execute(broker, query, null);

            seq = xquery.execute(broker, "//product/stock/external[. cast as xs:integer eq 1]", null);
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
        		"	update delete $prod\n";
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
            	"	update rename $prod/description as 'desc'\n";
            Sequence seq = xquery.execute(broker, query, null);

            seq = xquery.execute(broker, "//product/desc", null);
            assertEquals(seq.getItemCount(), ITEMS_TO_APPEND);

            query =
            	"for $prod in //product return\n" +
            	"	update rename $prod/@num as 'count'\n";
            seq = xquery.execute(broker, query, null);

            seq = xquery.execute(broker, "//product/@count", null);
            assertEquals(seq.getItemCount(), ITEMS_TO_APPEND);

        }
    }

    @Test
    public void replace() throws EXistException, PermissionDeniedException, XPathException, SAXException {

        append();

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            XQuery xquery = pool.getXQueryService();

            String query =
            	"for $prod in //product return\n" +
            	"	update replace $prod/description with <desc>An updated description.</desc>\n";
            Sequence seq = xquery.execute(broker, query, null);

            seq = xquery.execute(broker, "//product/desc", null);
            assertEquals(seq.getItemCount(), ITEMS_TO_APPEND);

            query =
            	"for $prod in //product return\n" +
            	"	update replace $prod/@num with '1'\n";
            seq = xquery.execute(broker, query, null);

            seq = xquery.execute(broker, "//product/@num", null);
            assertEquals(seq.getItemCount(), ITEMS_TO_APPEND);

            query =
            	"for $prod in //product return\n" +
            	"	update replace $prod/desc/text() with 'A new update'\n";
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

            String query =
                    "let $progress := /progress\n" +
                    "for $i in 1 to 100\n" +
                    "let $done := $progress/@done\n" +
                    "return (\n" +
                    "   update value $done with xs:int($done + 1),\n" +
                    "   xs:int(/progress/@done)\n" +
                    ")";
            XQuery xquery = pool.getXQueryService();
            @SuppressWarnings("unused")
			Sequence result = xquery.execute(broker, query, null);
        }
    }

    @Test
    public void appendCDATA() throws EXistException, PermissionDeniedException, XPathException, SAXException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            XQuery xquery = pool.getXQueryService();
            String query =
            	"	update insert\n" +
            	"		<product>\n" +
            	"			<description><![CDATA[me & you <>]]></description>\n" +
            	"		</product>\n" +
            	"	into /products";
            XQueryContext context = new XQueryContext(pool);
            CompiledXQuery compiled = xquery.compile(broker, context, query);
            for (int i = 0; i < ITEMS_TO_APPEND; i++) {
                xquery.execute(broker, compiled, null);
            }

            Sequence seq = xquery.execute(broker, "/products", null);
            assertEquals(seq.getItemCount(), 1);

            Serializer serializer = broker.getSerializer();
            serializer.serialize((NodeValue) seq.itemAt(0));

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
                "return update insert $attrib into $node";

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

            IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create(docName), data);
            //TODO : unlock the collection here ?
            root.store(transaction, broker, info, data);

            mgr.commit(transaction);
        }
        DocumentImpl doc = root.getDocument(broker, XmldbURI.create(docName));
        broker.getSerializer().serialize(doc);
    }
}
