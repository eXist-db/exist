package org.exist.storage;

import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.*;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import static org.junit.Assert.*;

import org.junit.AfterClass;
import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Test crash recovery after reindexing a collection.
 */
public class ReindexRecoveryTest {

    // we don't use @ClassRule/@Rule as we want to force corruption in some tests
    private ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static Path dir = TestUtils.shakespeareSamples();

    @Test
    public void reindexRecoveryTest() throws EXistException, PermissionDeniedException, IOException, DatabaseConfigurationException, LockException, TriggerException {
        BrokerPool.FORCE_CORRUPTION = true;
        BrokerPool pool = startDb();
        storeDocuments(pool);

        existEmbeddedServer.stopDb(false);

        BrokerPool.FORCE_CORRUPTION = false;
        pool = startDb();
        removeCollection(pool);

        existEmbeddedServer.stopDb(false);

        restart();
    }

    /**
     * Store some documents, reindex the collection and crash without commit.
     */
    private void storeDocuments(final BrokerPool pool) throws EXistException, PermissionDeniedException, IOException, TriggerException, LockException {
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));) {


            try(final Txn transaction = transact.beginTransaction()) {
                assertNotNull(transaction);

                Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
                assertNotNull(root);
                broker.saveCollection(transaction, root);

                final List<Path> files = FileUtils.list(dir, XMLFilenameFilter.asPredicate());
                for (final Path f : files) {
                    storeDocument(broker, transaction, root, XmldbURI.create(FileUtils.fileName(f)), () -> new InputSource(f.toUri().toASCIIString()));
                }
                transact.commit(transaction);
            }

            final Txn transaction = transact.beginTransaction();
            broker.reindexCollection(TestConstants.TEST_COLLECTION_URI);

            //NOTE: do not commit the transaction

            pool.getJournalManager().get().flush(true, false);
        }
    }

    private void storeDocument(final DBBroker broker, final Txn transaction, final Collection collection,
            final XmldbURI docName, final Supplier<InputSource> doc) {
        try {
            final IndexInfo info = collection.validateXMLResource(transaction, broker, docName, doc.get());
            assertNotNull(info);
            collection.store(transaction, broker, info, doc.get());
        } catch (final SAXException | EXistException | PermissionDeniedException | LockException | IOException e) {
            fail("Error found while parsing document: " + docName + ": " + e.getMessage());
        }
    }

    /**
     * Recover, remove the collection, then crash after commit.
     */
    private void removeCollection(final BrokerPool pool) {
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {

            BrokerPool.FORCE_CORRUPTION = true;

            Collection root = null;
            try {
                root = broker.openCollection(TestConstants.TEST_COLLECTION_URI, LockMode.WRITE_LOCK);
                assertNotNull(root);
                transaction.acquireLock(root.getLock(), LockMode.WRITE_LOCK);
                broker.removeCollection(transaction, root);
                pool.getJournalManager().get().flush(true, false);
            } finally {
                if(root != null) {
                    root.release(LockMode.WRITE_LOCK);
                }
            }
            transact.commit(transaction);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    /**
     * Just recover.
     */
    private void restart() throws EXistException, PermissionDeniedException, IOException, DatabaseConfigurationException {
        BrokerPool.FORCE_CORRUPTION = false;
        final BrokerPool pool = startDb();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            Collection root = null;
            try {
                root = broker.openCollection(TestConstants.TEST_COLLECTION_URI, LockMode.READ_LOCK);
                assertNull("Removed collection still exists", root);
            } finally {
                if(root != null) {
                    root.release(LockMode.READ_LOCK);
                }
            }
        }
    }

    private BrokerPool startDb() throws EXistException, IOException, DatabaseConfigurationException {
        existEmbeddedServer.startDb();
        return existEmbeddedServer.getBrokerPool();
    }

    @After
    public void stopDb() {
        existEmbeddedServer.stopDb();
    }

    @AfterClass
    public static void cleanup() {
        BrokerPool.FORCE_CORRUPTION = false;
    }
}
