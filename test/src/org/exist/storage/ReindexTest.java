package org.exist.storage;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.FileUtils;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * Test crash recovery after reindexing a collection.
 */
public class ReindexTest {

    private static String directory = "samples/shakespeare";

    private static Path dir = null;
    static {
        final String existHome = System.getProperty("exist.home");
        Path existDir = existHome == null ? Paths.get(".") : Paths.get(existHome);
        existDir = existDir.normalize();
        dir = existDir.resolve(directory);
    }

    @Test
    public void reindexTests() throws EXistException, PermissionDeniedException {
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

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));) {


            try(final Txn transaction = transact.beginTransaction()) {
                assertNotNull(transaction);

                Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
                assertNotNull(root);
                broker.saveCollection(transaction, root);

                final List<Path> files = FileUtils.list(dir);
                for (final Path f : files) {
                    final MimeType mime = MimeTable.getInstance().getContentTypeFor(FileUtils.fileName(f));
                    if (mime == null || mime.isXMLType()) {
                        try {
                            final IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create(FileUtils.fileName(f)), new InputSource(f.toUri().toASCIIString()));
                            assertNotNull(info);
                            root.store(transaction, broker, info, new InputSource(f.toUri().toASCIIString()));
                        } catch (SAXException e) {
                            System.err.println("Error found while parsing document: " + FileUtils.fileName(f) + ": " + e.getMessage());
                        }
                    }
                }
                transact.commit(transaction);
            }

            final Txn transaction = transact.beginTransaction();
            broker.reindexCollection(TestConstants.TEST_COLLECTION_URI);

            pool.getJournalManager().get().flush(true, false);
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

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction();) {

            BrokerPool.FORCE_CORRUPTION = true;

            Collection root = broker.openCollection(TestConstants.TEST_COLLECTION_URI, LockMode.WRITE_LOCK);
            assertNotNull(root);
            transaction.registerLock(root.getLock(), LockMode.WRITE_LOCK);
            broker.removeCollection(transaction, root);
            pool.getJournalManager().get().flush(true, false);
            transact.commit(transaction);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    /**
     * Just recover.
     */
    public void restart() throws EXistException, PermissionDeniedException {
        BrokerPool.FORCE_CORRUPTION = false;
        final BrokerPool pool = startDB();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            Collection root = broker.openCollection(TestConstants.TEST_COLLECTION_URI, LockMode.READ_LOCK);
            assertNull("Removed collection does still exist", root);
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
