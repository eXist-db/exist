package org.exist.indexes.lucene;

import org.exist.TestUtils;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentSet;
import org.exist.dom.MutableDocumentSet;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

public class LuceneIndexTest {

    private static String XML1 =
            "<section>" +
            "   <head>The title</head>" +
            "   <p>A simple paragraph with <hi>just</hi> text in it.</p>" +
            "   <p>paragraphs with <span>mix</span><span>ed</span> content are <span>danger</span>ous.</p>" +
            "</section>";

    private static String COLLECTION_CONFIG1 =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
    	"	<index>" +
    	"		<fulltext default=\"none\">" +
        "		</fulltext>" +
        "       <text qname=\"p\"/>" +
        "       <text qname=\"head\"/>" +
        "	</index>" +
    	"</collection>";

    private static BrokerPool pool;
    private static Collection root;

    @Test
    public void simpleQueries() {
        configureAndStore(COLLECTION_CONFIG1, XML1, "test.xml");
        DBBroker broker = null;
        try {
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            assertNotNull(broker);
            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("/section[ft:query(p, 'content')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    private DocumentSet configureAndStore(String configuration, String data, String docName) {
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        MutableDocumentSet docs = new DefaultDocumentSet();
        try {
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            assertNotNull(broker);
            transact = pool.getTransactionManager();
            assertNotNull(transact);
            transaction = transact.beginTransaction();
            assertNotNull(transaction);

            if (configuration != null) {
                CollectionConfigurationManager mgr = pool.getConfigurationManager();
                mgr.addConfiguration(transaction, broker, root, configuration);
            }

            IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create(docName), data);
            assertNotNull(info);
            root.store(transaction, broker, info, data, false);

            docs.add(info.getDocument());
            transact.commit(transaction);
        } catch (Exception e) {
            e.printStackTrace();
            transact.abort(transaction);
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
        return docs;
    }

    @Before
    public void setup() {
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            assertNotNull(broker);
            transact = pool.getTransactionManager();
            assertNotNull(transact);
            transaction = transact.beginTransaction();
            assertNotNull(transaction);

            root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            transact.commit(transaction);
        } catch (Exception e) {
            e.printStackTrace();
            transact.abort(transaction);
            fail(e.getMessage());
        } finally {
            if (pool != null)
                pool.release(broker);
        }
    }

    @After
    public void cleanup() {
        BrokerPool pool = null;
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            pool = BrokerPool.getInstance();
            assertNotNull(pool);
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            assertNotNull(broker);
            transact = pool.getTransactionManager();
            assertNotNull(transact);
            transaction = transact.beginTransaction();
            assertNotNull(transaction);

            Collection config = broker.getOrCreateCollection(transaction,
                XmldbURI.create(CollectionConfigurationManager.CONFIG_COLLECTION + "/db"));
            assertNotNull(config);
            broker.removeCollection(transaction, config);

            assertNotNull(root);
            broker.removeCollection(transaction, root);

            transact.commit(transaction);
        } catch (Exception e) {
        	transact.abort(transaction);
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (pool != null) pool.release(broker);
        }
    }

    @BeforeClass
    public static void startDB() {
        try {
            File confFile = ConfigurationHelper.lookup("conf.xml");
            Configuration config = new Configuration(confFile.getAbsolutePath());
            BrokerPool.configure(1, 5, config);
            pool = BrokerPool.getInstance();
        	assertNotNull(pool);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @AfterClass
    public static void stopDB() {
        TestUtils.cleanupDB();
        BrokerPool.stopAll(false);
        pool = null;
        root = null;
    }
}
