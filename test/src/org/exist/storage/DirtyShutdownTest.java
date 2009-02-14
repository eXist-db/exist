package org.exist.storage;

import org.exist.util.Configuration;
import org.exist.TestUtils;
import org.exist.EXistException;
import org.exist.xquery.XQuery;
import org.exist.xmldb.XmldbURI;
import org.exist.security.PermissionDeniedException;
import org.exist.security.xacml.AccessContext;
import org.exist.test.TestConstants;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DirtyShutdownTest {

    private final static String query =
            "import module namespace t=\"http://exist-db.org/xquery/test\" " +
            "at \"java:org.exist.storage.util.TestUtilModule\";\n" +
            "t:pause(120)";

    private BrokerPool pool;
    
    @Test
    public void run() {
        ExecutorService service = Executors.newSingleThreadExecutor();
        service.execute(new Runnable() {
            public void run() {
                storeRepeatedly();
            }
        });
        synchronized (this) {
            try {
                wait(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        shutdown();
    }

    public void storeRepeatedly() {
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            transact = pool.getTransactionManager();

            transaction = transact.beginTransaction();
            Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);
            transact.commit(transaction);

            for (int i = 0; i < 50; i++) {
                System.out.println("Storing " + i + " out of 50...");
                transaction = transact.beginTransaction();

                File f = new File("samples/shakespeare/macbeth.xml");
                IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"),
                        new InputSource(f.toURI().toASCIIString()));
                assertNotNull(info);
                root.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);

                transact.commit(transaction);
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }
    public void storeAndWait() {
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            transact = pool.getTransactionManager();
            transaction = transact.beginTransaction();
            Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            File f = new File("samples/shakespeare/hamlet.xml");
            IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"),
                    new InputSource(f.toURI().toASCIIString()));
            assertNotNull(info);
            root.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);

            transact.commit(transaction);

            transaction = transact.beginTransaction();
            XQuery xquery = broker.getXQueryService();
            xquery.execute(query, null, AccessContext.TEST);
            transact.commit(transaction);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Before
    public void startDB() {
        try {
            Configuration config = new Configuration();
            BrokerPool.configure(1, 5, config);
            pool = BrokerPool.getInstance();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    public void shutdown() {
        BrokerPool.stopAll(false);
    }
}
