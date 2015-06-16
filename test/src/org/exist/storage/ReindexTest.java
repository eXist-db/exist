package org.exist.storage;

import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.File;

/**
 * Test crash recovery after reindexing a collection.
 */
public class ReindexTest {

    private static String directory = "samples/shakespeare";

    private static File dir = null;
    static {
      String existHome = System.getProperty("exist.home");
      File existDir = existHome==null ? new File(".") : new File(existHome);
      dir = new File(existDir,directory);
    }

    @Test
    public void reindexTests() {
        storeDocuments();
        closeDB();

        removeCollection();
        closeDB();

        restart();
    }

    /**
     * Store some documents, reindex the collection and crash without commit.
     */
    public void storeDocuments() {
        BrokerPool.FORCE_CORRUPTION = true;
        final BrokerPool pool = startDB();
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());) {


            try(final Txn transaction = transact.beginTransaction()) {
                assertNotNull(transaction);

                Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
                assertNotNull(root);
                broker.saveCollection(transaction, root);

                File files[] = dir.listFiles();
                assertNotNull(files);
                File f;
                IndexInfo info;
                for (int i = 0; i < files.length; i++) {
                    f = files[i];
                    MimeType mime = MimeTable.getInstance().getContentTypeFor(f.getName());
                    if (mime == null || mime.isXMLType()) {
                        try {
                            info = root.validateXMLResource(transaction, broker, XmldbURI.create(f.getName()), new InputSource(f.toURI().toASCIIString()));
                            assertNotNull(info);
                            root.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);
                        } catch (SAXException e) {
                            System.err.println("Error found while parsing document: " + f.getName() + ": " + e.getMessage());
                        }
                    }
                }
                transact.commit(transaction);
            }

            final Txn transaction = transact.beginTransaction();
            broker.reindexCollection(TestConstants.TEST_COLLECTION_URI);

            transact.getJournal().flushToLog(true);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    /**
     * Recover, remove the collection, then crash after commit.
     */
    public void removeCollection() {
        BrokerPool.FORCE_CORRUPTION = false;
        final BrokerPool pool = startDB();
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
                final Txn transaction = transact.beginTransaction();) {

            BrokerPool.FORCE_CORRUPTION = true;

            Collection root = broker.openCollection(TestConstants.TEST_COLLECTION_URI, Lock.WRITE_LOCK);
            assertNotNull(root);
            transaction.registerLock(root.getLock(), Lock.WRITE_LOCK);
            broker.removeCollection(transaction, root);
            transact.getJournal().flushToLog(true);
            transact.commit(transaction);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    /**
     * Just recover.
     */
    public void restart() {
        BrokerPool.FORCE_CORRUPTION = false;
        BrokerPool pool = null;
        DBBroker broker = null;
        try {
        	pool = startDB();
        	assertNotNull(pool);
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);

            Collection root = broker.openCollection(TestConstants.TEST_COLLECTION_URI, Lock.READ_LOCK);
            assertNull("Removed collection does still exist", root);
        } catch (Exception e) {
            e.printStackTrace();
	        fail(e.getMessage());
        } finally {
            if (pool != null)
                pool.release(broker);
        }
    }

    @After
    public void closeDB() {
        BrokerPool.stopAll(false);
    }

    protected BrokerPool startDB() {
        try {
            Configuration config = new Configuration();
            BrokerPool.configure(1, 5, config);
            return BrokerPool.getInstance();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        return null;
    }
}
