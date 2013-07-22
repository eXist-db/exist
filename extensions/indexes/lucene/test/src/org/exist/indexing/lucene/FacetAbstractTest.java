package org.exist.indexing.lucene;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.*;
import java.util.Map.Entry;

import org.exist.Indexer;
import org.exist.TestUtils;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.MutableDocumentSet;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.md.MetaData;
import org.exist.storage.md.Metas;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

public class FacetAbstractTest {

    protected static BrokerPool db;
    protected static Collection root;
    protected Boolean savedConfig;

    protected DocumentSet configureAndStore(String configuration, Resource[] resources) {
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        MutableDocumentSet docs = new DefaultDocumentSet();
        try {
            broker = db.get(db.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            transact = db.getTransactionManager();
            assertNotNull(transact);
            transaction = transact.beginTransaction();
            assertNotNull(transaction);

            MetaData md = MetaData.get();
            assertNotNull(md);

            if (configuration != null) {
                CollectionConfigurationManager mgr = db.getConfigurationManager();
                mgr.addConfiguration(transaction, broker, root, configuration);
            }
            
            for (Resource resource : resources) {
                IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create(resource.docName), resource.data);
                assertNotNull(info);
    
                if (docs != null) {
                    Metas docMD = md.getMetas(info.getDocument());
                    if (docMD == null) {
                        docMD = md.addMetas(info.getDocument());
                    }
                    assertNotNull(docMD);
                    
                    for (Entry<String, String> entry : resource.metas.entrySet()) {
                        docMD.put(entry.getKey(), entry.getValue());
                    }
                }
                
                root.store(transaction, broker, info, resource.data, false);
    
                docs.add(info.getDocument());
            }
            
            transact.commit(transaction);
        } catch (Exception e) {
            if (transact != null)
                transact.abort(transaction);
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            db.release(broker);
        }
        
        return docs;
    }

    @Before
    public void setup() {
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            broker = db.get(db.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            transact = db.getTransactionManager();
            assertNotNull(transact);
            transaction = transact.beginTransaction();
            assertNotNull(transaction);

            root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            transact.commit(transaction);

            Configuration config = BrokerPool.getInstance().getConfiguration();
            savedConfig = (Boolean) config.getProperty(Indexer.PROPERTY_PRESERVE_WS_MIXED_CONTENT);
            config.setProperty(Indexer.PROPERTY_PRESERVE_WS_MIXED_CONTENT, Boolean.TRUE);
        } catch (Exception e) {
            if (transact != null)
                transact.abort(transaction);
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (db != null)
                db.release(broker);
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
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            transact = pool.getTransactionManager();
            assertNotNull(transact);
            transaction = transact.beginTransaction();
            assertNotNull(transaction);

            Collection collConfig = broker.getOrCreateCollection(transaction,
                XmldbURI.create(XmldbURI.CONFIG_COLLECTION + "/db"));
            assertNotNull(collConfig);
            broker.removeCollection(transaction, collConfig);

            if (root != null) {
                assertNotNull(root);
                broker.removeCollection(transaction, root);
            }
            transact.commit(transaction);

            Configuration config = BrokerPool.getInstance().getConfiguration();
            config.setProperty(Indexer.PROPERTY_PRESERVE_WS_MIXED_CONTENT, savedConfig);
        } catch (Exception e) {
            if (transact != null)
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
            config.setProperty(Indexer.PROPERTY_SUPPRESS_WHITESPACE, "none");
            config.setProperty(Indexer.PRESERVE_WS_MIXED_CONTENT_ATTRIBUTE, Boolean.TRUE);
            BrokerPool.configure(1, 5, config);
            db = BrokerPool.getInstance();
            assertNotNull(db);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @AfterClass
    public static void stopDB() {
        TestUtils.cleanupDB();
        BrokerPool.stopAll(false);
        db = null;
        root = null;
    }
    
    protected class Resource {
        final String docName;
        final String data;
        final Map<String, String> metas;
        
        Resource(String docName, String data, Map<String, String> metas) {
            this.docName = docName;
            this.data = data;
            this.metas = metas;
        }
    }
    
    protected class CountDocuments implements SearchCallback<DocumentImpl> {

        int count = 0;
        
        @Override
        public void found(DocumentImpl document, float score) {
            count++;
        }
        
    }
}

