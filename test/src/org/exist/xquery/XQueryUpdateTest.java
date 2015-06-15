package org.exist.xquery;

import java.io.IOException;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.SAXException;

import static org.junit.Assert.assertEquals;

public class XQueryUpdateTest {

    protected static XmldbURI TEST_COLLECTION = XmldbURI.create(XmldbURI.ROOT_COLLECTION + "/test");

    protected static String TEST_XML =
            "<?xml version=\"1.0\"?>" +
                    "<products/>";

    protected static String UPDATE_XML =
            "<progress total=\"100\" done=\"0\" failed=\"0\" passed=\"0\"/>";

    protected final static int ITEMS_TO_APPEND = 1000;

    private BrokerPool pool;

    @Test
    public void append() throws EXistException, PermissionDeniedException, XPathException, SAXException {
        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {

            XQuery xquery = broker.getXQueryService();
            String query =
                    "   declare variable $i external;\n" +
                            "	update insert\n" +
                            "		<product id='id{$i}' num='{$i}'>\n" +
                            "			<description>Description {$i}</description>\n" +
                            "			<price>{$i + 1.0}</price>\n" +
                            "			<stock>{$i * 10}</stock>\n" +
                            "		</product>\n" +
                            "	into /products";
            XQueryContext context = xquery.newContext(AccessContext.TEST);
            CompiledXQuery compiled = xquery.compile(context, query);
            for (int i = 0; i < ITEMS_TO_APPEND; i++) {
                context.declareVariable("i", Integer.valueOf(i));
                xquery.execute(compiled, null);
            }

            Sequence seq = xquery.execute("/products", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), 1);

            Serializer serializer = broker.getSerializer();
            serializer.serialize((NodeValue) seq.itemAt(0));

            seq = xquery.execute("//product", null, AccessContext.TEST);
            assertEquals(ITEMS_TO_APPEND, seq.getItemCount());

            seq = xquery.execute("//product[price > 0.0]", null, AccessContext.TEST);
            assertEquals(ITEMS_TO_APPEND, seq.getItemCount());
        }
    }

    @Test
    public void appendAttributes() throws EXistException, PermissionDeniedException, XPathException, SAXException, LockException, IOException {

        append();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {

            XQuery xquery = broker.getXQueryService();
            String query =
                    "   declare variable $i external;\n" +
                            "	update insert\n" +
                            "		attribute name { concat('n', $i) }\n" +
                            "	into //product[@num = $i]";
            XQueryContext context = xquery.newContext(AccessContext.TEST);
            CompiledXQuery compiled = xquery.compile(context, query);
            for (int i = 0; i < ITEMS_TO_APPEND; i++) {
                context.declareVariable("i", Integer.valueOf(i));
                xquery.execute(compiled, null);
            }

            Sequence seq = xquery.execute("/products", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), 1);

            Serializer serializer = broker.getSerializer();
            serializer.serialize((NodeValue) seq.itemAt(0));

            seq = xquery.execute("//product", null, AccessContext.TEST);
            assertEquals(ITEMS_TO_APPEND, seq.getItemCount());

            seq = xquery.execute("//product[@name = 'n20']", null, AccessContext.TEST);
            assertEquals(1, seq.getItemCount());

            store(broker, "attribs.xml", "<test attr1='aaa' attr2='bbb'>ccc</test>");
            query = "update insert attribute attr1 { 'eee' } into /test";

            //testing duplicate attribute ...
            xquery.execute(query, null, AccessContext.TEST);

            seq = xquery.execute("xmldb:document('" + TEST_COLLECTION + "/attribs.xml')/test[@attr1 = 'eee']", null, AccessContext.TEST);
            assertEquals(1, seq.getItemCount());
            serializer.serialize((NodeValue) seq.itemAt(0));
        }
    }

    @Test
    public void insertBefore() throws EXistException, PermissionDeniedException, XPathException, SAXException {
        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {

            String query =
                    "   update insert\n" +
                            "       <product id='original'>\n" +
                            "           <description>Description</description>\n" +
                            "           <price>0</price>\n" +
                            "           <stock>10</stock>\n" +
                            "       </product>\n" +
                            "   into /products";

            XQuery xquery = broker.getXQueryService();
            xquery.execute(query, null, AccessContext.TEST);

            query =
                    "   declare variable $i external;\n" +
                            "   update insert\n" +
                            "       <product id='id{$i}'>\n" +
                            "           <description>Description {$i}</description>\n" +
                            "           <price>{$i + 1.0}</price>\n" +
                            "           <stock>{$i * 10}</stock>\n" +
                            "       </product>\n" +
                            "   preceding /products/product[1]";
            XQueryContext context = xquery.newContext(AccessContext.TEST);
            CompiledXQuery compiled = xquery.compile(context, query);
            for (int i = 0; i < ITEMS_TO_APPEND; i++) {
                context.declareVariable("i", Integer.valueOf(i));
                xquery.execute(compiled, null);
            }

            Sequence seq = xquery.execute("/products", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), 1);

            Serializer serializer = broker.getSerializer();
            serializer.serialize((NodeValue) seq.itemAt(0));

            seq = xquery.execute("//product", null, AccessContext.TEST);
            assertEquals(ITEMS_TO_APPEND + 1, seq.getItemCount());

            seq = xquery.execute("//product[price > 0.0]", null, AccessContext.TEST);
            assertEquals(ITEMS_TO_APPEND, seq.getItemCount());
        }
    }

    @Test
    public void insertAfter() throws EXistException, PermissionDeniedException, XPathException, SAXException {
        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {

            String query =
                    "   update insert\n" +
                            "       <product id='original'>\n" +
                            "           <description>Description</description>\n" +
                            "           <price>0</price>\n" +
                            "           <stock>10</stock>\n" +
                            "       </product>\n" +
                            "   into /products";

            XQuery xquery = broker.getXQueryService();
            xquery.execute(query, null, AccessContext.TEST);

            query =
                    "   declare variable $i external;\n" +
                            "   update insert\n" +
                            "       <product id='id{$i}'>\n" +
                            "           <description>Description {$i}</description>\n" +
                            "           <price>{$i + 1.0}</price>\n" +
                            "           <stock>{$i * 10}</stock>\n" +
                            "       </product>\n" +
                            "   following /products/product[1]";
            XQueryContext context = xquery.newContext(AccessContext.TEST);
            CompiledXQuery compiled = xquery.compile(context, query);
            for (int i = 0; i < ITEMS_TO_APPEND; i++) {
                context.declareVariable("i", Integer.valueOf(i));
                xquery.execute(compiled, null);
            }

            Sequence seq = xquery.execute("/products", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), 1);

            Serializer serializer = broker.getSerializer();
            serializer.serialize((NodeValue) seq.itemAt(0));

            seq = xquery.execute("//product", null, AccessContext.TEST);
            assertEquals(ITEMS_TO_APPEND + 1, seq.getItemCount());

            seq = xquery.execute("//product[price > 0.0]", null, AccessContext.TEST);
            assertEquals(ITEMS_TO_APPEND, seq.getItemCount());
        }
    }

    @Test
    public void update() throws EXistException, PermissionDeniedException, XPathException, SAXException {

        append();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {

            XQuery xquery = broker.getXQueryService();

            String query =
                    "declare option exist:output-size-limit '-1';\n" +
                            "for $prod at $i in //product return\n" +
                            "	update value $prod/description\n" +
                            "	with concat('Updated Description', $i)";
            Sequence seq = xquery.execute(query, null, AccessContext.TEST);

            seq = xquery.execute("//product[starts-with(description, 'Updated')]", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), ITEMS_TO_APPEND);

            Serializer serializer = broker.getSerializer();
            serializer.serialize((NodeValue) seq.itemAt(0));

            for (int i = 1; i <= ITEMS_TO_APPEND; i++) {
                seq = xquery.execute("//product[description &= 'Description" + i + "']", null, AccessContext.TEST);
                assertEquals(1, seq.getItemCount());
                serializer.serialize((NodeValue) seq.itemAt(0));
            }
            seq = xquery.execute("//product[description &= 'Updated']", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), ITEMS_TO_APPEND);

            serializer.serialize((NodeValue) seq.itemAt(0));

            query =
                    "declare option exist:output-size-limit '-1';\n" +
                            "for $prod at $count in //product return\n" +
                            "	update value $prod/stock/text()\n" +
                            "	with (400 + $count)";
            seq = xquery.execute(query, null, AccessContext.TEST);

            seq = xquery.execute("//product[stock > 400]", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), ITEMS_TO_APPEND);
            seq = xquery.execute("//product[stock &= '401']", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), 1);

            serializer.serialize((NodeValue) seq.itemAt(0));

            query =
                    "declare option exist:output-size-limit '-1';\n" +
                            "for $prod in //product return\n" +
                            "	update value $prod/@num\n" +
                            "	with xs:int($prod/@num) * 3";
            seq = xquery.execute(query, null, AccessContext.TEST);

            seq = xquery.execute("/products", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), 1);

            seq = xquery.execute("//product[@num = 3]", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), 1);

            serializer.serialize((NodeValue) seq.itemAt(0));

            query =
                    "declare option exist:output-size-limit '-1';\n" +
                            "for $prod in //product return\n" +
                            "	update value $prod/stock\n" +
                            "	with (<local>10</local>,<external>1</external>)";
            seq = xquery.execute(query, null, AccessContext.TEST);

            seq = xquery.execute("/products", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), 1);

            seq = xquery.execute("//product/stock/external[. = 1]", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), ITEMS_TO_APPEND);
        }
    }

    @Test
    public void remove() throws EXistException, PermissionDeniedException, XPathException, SAXException {

        append();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {
            XQuery xquery = broker.getXQueryService();

            String query =
                    "for $prod in //product return\n" +
                            "	update delete $prod\n";
            Sequence seq = xquery.execute(query, null, AccessContext.TEST);

            seq = xquery.execute("//product", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), 0);

        }
    }

    @Test
    public void rename() throws EXistException, PermissionDeniedException, XPathException, SAXException {

        append();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {

            XQuery xquery = broker.getXQueryService();

            String query =
                    "for $prod in //product return\n" +
                            "	update rename $prod/description as 'desc'\n";
            Sequence seq = xquery.execute(query, null, AccessContext.TEST);

            seq = xquery.execute("//product/desc", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), ITEMS_TO_APPEND);

            query =
                    "for $prod in //product return\n" +
                            "	update rename $prod/@num as 'count'\n";
            seq = xquery.execute(query, null, AccessContext.TEST);

            seq = xquery.execute("//product/@count", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), ITEMS_TO_APPEND);

        }
    }

    @Test
    public void replace() throws EXistException, PermissionDeniedException, XPathException, SAXException {

        append();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {

            XQuery xquery = broker.getXQueryService();

            String query =
                    "for $prod in //product return\n" +
                            "	update replace $prod/description with <desc>An updated description.</desc>\n";
            Sequence seq = xquery.execute(query, null, AccessContext.TEST);

            seq = xquery.execute("//product/desc", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), ITEMS_TO_APPEND);

            query =
                    "for $prod in //product return\n" +
                            "	update replace $prod/@num with '1'\n";
            seq = xquery.execute(query, null, AccessContext.TEST);

            seq = xquery.execute("//product/@num", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), ITEMS_TO_APPEND);

            query =
                    "for $prod in //product return\n" +
                            "	update replace $prod/desc/text() with 'A new update'\n";
            seq = xquery.execute(query, null, AccessContext.TEST);

            seq = xquery.execute("//product[starts-with(desc, 'A new')]", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), ITEMS_TO_APPEND);
        }
    }

    @Test
    public void attrUpdate() throws EXistException, LockException, SAXException, PermissionDeniedException, IOException, XPathException {
        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {
            store(broker, "test.xml", UPDATE_XML);

            String query =
                    "let $progress := /progress\n" +
                            "for $i in 1 to 100\n" +
                            "let $done := $progress/@done\n" +
                            "return (\n" +
                            "   update value $done with xs:int($done + 1),\n" +
                            "   xs:int(/progress/@done)\n" +
                            ")";
            XQuery xquery = broker.getXQueryService();
            @SuppressWarnings("unused")
            Sequence result = xquery.execute(query, null, AccessContext.TEST);
        }
    }

    @Test
    public void appendCDATA() throws EXistException, PermissionDeniedException, XPathException, SAXException {
        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {

            XQuery xquery = broker.getXQueryService();
            String query =
                    "   declare variable $i external;\n" +
                            "	update insert\n" +
                            "		<product>\n" +
                            "			<description><![CDATA[me & you <>]]></description>\n" +
                            "		</product>\n" +
                            "	into /products";
            XQueryContext context = xquery.newContext(AccessContext.TEST);
            CompiledXQuery compiled = xquery.compile(context, query);
            for (int i = 0; i < ITEMS_TO_APPEND; i++) {
                xquery.execute(compiled, null);
            }

            Sequence seq = xquery.execute("/products", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), 1);

            Serializer serializer = broker.getSerializer();
            serializer.serialize((NodeValue) seq.itemAt(0));

            seq = xquery.execute("//product", null, AccessContext.TEST);
            assertEquals(ITEMS_TO_APPEND, seq.getItemCount());
        }
    }

    @Ignore
    @Test
    public void insertAttribDoc_1730726() throws EXistException, PermissionDeniedException, XPathException {
        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {
            String query =
                    "declare namespace xmldb = \"http://exist-db.org/xquery/xmldb\"; " +
                            "let $uri := xmldb:store(\"/db\", \"insertAttribDoc.xml\", <C/>) " +
                            "let $node := doc($uri)/element() " +
                            "let $attrib := <Value f=\"ATTRIB VALUE\"/>/@* " +
                            "return update insert $attrib into $node";

            XQuery xquery = broker.getXQueryService();
            @SuppressWarnings("unused")
            Sequence result = xquery.execute(query, null, AccessContext.TEST);
        }
    }

    @Before
    public void setUp() throws EXistException, DatabaseConfigurationException, LockException, SAXException, PermissionDeniedException, IOException {
        this.pool = startDB();
        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {
            store(broker, "test.xml", TEST_XML);
        }
    }

    private void store(DBBroker broker, String docName, String data) throws PermissionDeniedException, EXistException, SAXException, LockException, IOException {
        Collection root;
        final TransactionManager mgr = pool.getTransactionManager();
        try (final Txn transaction = mgr.beginTransaction()) {

            root = broker.getOrCreateCollection(transaction, TEST_COLLECTION);
            broker.saveCollection(transaction, root);

            IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create(docName), data);
            //TODO : unlock the collection here ?
            root.store(transaction, broker, info, data, false);

            mgr.commit(transaction);
        }
        DocumentImpl doc = root.getDocument(broker, XmldbURI.create(docName));
        broker.getSerializer().serialize(doc);
    }

    protected BrokerPool startDB() throws DatabaseConfigurationException, EXistException {
        String home, file = "conf.xml";
        home = System.getProperty("exist.home");
        if (home == null) {
            home = System.getProperty("user.dir");
        }

        Configuration config = new Configuration(file, home);
        BrokerPool.configure(1, 5, config);
        return BrokerPool.getInstance();
    }

    @After
    public void tearDown() {
        pool = null;
        BrokerPool.stopAll(false);
    }
}
