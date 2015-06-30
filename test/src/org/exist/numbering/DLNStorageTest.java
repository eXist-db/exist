package org.exist.numbering;

import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.persistent.NodeHandle;
import org.exist.dom.persistent.NodeProxy;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.StorageAddress;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Attr;
import org.w3c.dom.Text;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DLNStorageTest {

    private static XmldbURI TEST_COLLECTION = XmldbURI.create(XmldbURI.ROOT_COLLECTION + "/test");

    private static String TEST_XML =
            "<test>\n" +
                    "    <para>My first paragraph.</para>\n" +
                    "    <!-- A comment -->\n" +
                    "    <para>This one contains a <a href=\"#\">link</a>.</para>\n" +
                    "    <?echo \"A processing instruction\"?>\n" +
                    "    <para>Another <b>paragraph</b>.</para>\n" +
                    "</test>";

    @Test
    public void nodeStorage() throws Exception {
        BrokerPool pool = BrokerPool.getInstance();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {
            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            // test element ids
            Sequence seq = xquery.execute("doc('/db/test/test_string.xml')/test/para",
                    null, AccessContext.TEST);
            assertEquals(3, seq.getItemCount());
            NodeProxy comment = (NodeProxy) seq.itemAt(0);
            assertEquals(comment.getNodeId().toString(), "1.1");
            comment = (NodeProxy) seq.itemAt(1);
            assertEquals(comment.getNodeId().toString(), "1.3");
            comment = (NodeProxy) seq.itemAt(2);
            assertEquals(comment.getNodeId().toString(), "1.5");

            seq = xquery.execute("doc('/db/test/test_string.xml')/test//a",
                    null, AccessContext.TEST);
            assertEquals(1, seq.getItemCount());
            NodeProxy a = (NodeProxy) seq.itemAt(0);
            assertEquals("1.3.2", a.getNodeId().toString());

            // test attribute id
            seq = xquery.execute("doc('/db/test/test_string.xml')/test//a/@href",
                    null, AccessContext.TEST);
            assertEquals(1, seq.getItemCount());
            NodeProxy href = (NodeProxy) seq.itemAt(0);
            StorageAddress.toString(href);
            assertEquals("1.3.2.1", href.getNodeId().toString());
            // test Attr deserialization
            Attr attr = (Attr) href.getNode();
            StorageAddress.toString(((NodeHandle)attr));
            // test Attr fields
            assertEquals(attr.getNodeName(), "href");
            assertEquals(attr.getName(), "href");
            assertEquals(attr.getValue(), "#");
             // test DOMFile.getNodeValue()
            assertEquals(href.getStringValue(), "#");

            // test text node
            seq = xquery.execute("doc('/db/test/test_string.xml')/test//b/text()",
                    null, AccessContext.TEST);
            assertEquals(1, seq.getItemCount());
            NodeProxy text = (NodeProxy) seq.itemAt(0);
            assertEquals("1.5.2.1", text.getNodeId().toString());
            // test DOMFile.getNodeValue()
            assertEquals(text.getStringValue(), "paragraph");
            // test Text deserialization
            Text node = (Text) text.getNode();
            assertEquals(node.getNodeValue(), "paragraph");
            assertEquals(node.getData(), "paragraph");
        }
    }

    @Before
    public void setUp() throws Exception {
        String home, file = "conf.xml";
        home = System.getProperty("exist.home");
        if (home == null) {
            home = System.getProperty("user.dir");
        }

        Configuration config = new Configuration(file, home);
        BrokerPool.configure(1, 5, config);

        BrokerPool pool = BrokerPool.getInstance();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
            final Txn transaction = transact.beginTransaction()) {

            Collection test = broker.getOrCreateCollection(transaction, TEST_COLLECTION);
            broker.saveCollection(transaction, test);

            IndexInfo info = test.validateXMLResource(transaction, broker, XmldbURI.create("test_string.xml"),
                    TEST_XML);
            //TODO : unlock the collection here ?
            assertNotNull(info);

            test.store(transaction, broker, info, TEST_XML, false);

            transact.commit(transaction);
        }
    }

    @After
    protected void tearDown() {
        BrokerPool.stopAll(false);
    }
}
