package org.exist.indexes.lucene;

import org.exist.TestUtils;
import org.exist.indexing.lucene.LuceneIndexWorker;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.OrderedValuesIndex;
import org.exist.indexing.QNamedKeysIndex;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentSet;
import org.exist.dom.MutableDocumentSet;
import org.exist.dom.QName;
import org.exist.security.xacml.AccessContext;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.util.Occurrences;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.InputSource;

import java.io.File;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class LuceneIndexTest {

    private static String XML1 =
            "<section>" +
            "   <head>The title in big letters</head>" +
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
        "       <text qname=\"LINE\"/>" +
        "	</index>" +
    	"</collection>";

    private static BrokerPool pool;
    private static Collection root;

    @Test
    public void simpleQueries() {
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG1, XML1, "test.xml");
        DBBroker broker = null;
        try {
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            assertNotNull(broker);

            checkIndex(docs, broker, new QName[] { new QName("head", "") }, "title", 1);
            checkIndex(docs, broker, new QName[] { new QName("p", "") }, "with", 2);
            checkIndex(docs, broker, null, "in", 2);
            
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

    @Test
    public void dropDocument() {
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG1, XML1, "dropDocument.xml");
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

            System.out.println("Removing document dropDocument.xml");
            root.removeXMLResource(transaction, broker, XmldbURI.create("dropDocument.xml"));
            transact.commit(transaction);

            checkIndex(docs, broker, null, null, 0);
        } catch (Exception e) {
            transact.abort(transaction);
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Test
    public void removeCollection() {
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG1, "samples/shakespeare");
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

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//SPEECH[ft:query(LINE, 'love')]", null, AccessContext.TEST);
            assertNotNull(seq);
            System.out.println("Found: " + seq.getItemCount());
            assertEquals(166, seq.getItemCount());

            System.out.println("Removing collection");
            root.removeCollection(root.getURI());
            transact.commit(transaction);

            checkIndex(docs, broker, null, null, 0);
        } catch (Exception e) {
            transact.abort(transaction);
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

    private DocumentSet configureAndStore(String configuration, String directory) {
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

            File file = new File(directory);
            File[] files = file.listFiles();
            MimeTable mimeTab = MimeTable.getInstance();
            for (int j = 0; j < files.length; j++) {
                MimeType mime = mimeTab.getContentTypeFor(files[j].getName());
                if(mime != null && mime.isXMLType()) {
                    System.out.println("Storing document " + files[j].getName());
                    InputSource is = new InputSource(files[j].getAbsolutePath());
                    IndexInfo info =
                            root.validateXMLResource(transaction, broker, XmldbURI.create(files[j].getName()), is);
                    assertNotNull(info);
                    is = new InputSource(files[j].getAbsolutePath());
                    root.store(transaction, broker, info, is, false);
                    docs.add(info.getDocument());
                }
            }
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

    private Occurrences[] checkIndex(DocumentSet docs, DBBroker broker, QName[] qn, String term, int expected) throws PermissionDeniedException {
        LuceneIndexWorker index = (LuceneIndexWorker)
            broker.getIndexController().getWorkerByIndexId(LuceneIndex.ID);
        Map hints = new HashMap();
        if (term != null)
            hints.put(OrderedValuesIndex.START_VALUE, term);
        if (qn != null && qn.length > 0) {
            List qnames = new ArrayList();
            for (int i = 0; i < qn.length; i++) {
                qnames.add(qn[i]);
            }
            hints.put(QNamedKeysIndex.QNAMES_KEY, qnames);
        }
        XQueryContext context = new XQueryContext(broker, AccessContext.TEST);
        Occurrences[] occur = index.scanIndex(context, docs, docs.docsToNodeSet(), hints);
        return occur;
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
//        TestUtils.cleanupDB();
        BrokerPool.stopAll(false);
        pool = null;
        root = null;
    }
}

