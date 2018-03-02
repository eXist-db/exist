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
import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Test crash recovery after reindexing a collection.
 */
public class ReindexTest {

    // we don't use @ClassRule/@Rule as we want to force corruption in some tests
    private ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, false);

    private static Path dir = TestUtils.shakespeareSamples();

    @Test
    public void reindexTests() throws EXistException, PermissionDeniedException, IOException, DatabaseConfigurationException, LockException, TriggerException {
        BrokerPool.FORCE_CORRUPTION = true;
        BrokerPool pool = startDb();
        storeDocuments(pool);

        stopDb();

        BrokerPool.FORCE_CORRUPTION = false;
        pool = startDb();
        removeCollection(pool);

        stopDb();

        restart();
    }

    /**
     * Store some documents, reindex the collection and crash without commit.
     */
    public void storeDocuments(final BrokerPool pool) throws EXistException, PermissionDeniedException, IOException, TriggerException, LockException {
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));) {


            try(final Txn transaction = transact.beginTransaction()) {
                assertNotNull(transaction);

                Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
                assertNotNull(root);
                broker.saveCollection(transaction, root);

                final List<Path> files = FileUtils.list(dir, XMLFilenameFilter.asPredicate());
                for (final Path f : files) {
                    try {
                        final IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create(FileUtils.fileName(f)), new InputSource(f.toUri().toASCIIString()));
                        assertNotNull(info);
                        root.store(transaction, broker, info, new InputSource(f.toUri().toASCIIString()));
                    } catch (SAXException e) {
                        fail("Error found while parsing document: " + FileUtils.fileName(f) + ": " + e.getMessage());
                    }
                }
                transact.commit(transaction);
            }

            final Txn transaction = transact.beginTransaction();
            broker.reindexCollection(TestConstants.TEST_COLLECTION_URI);

            pool.getJournalManager().get().flush(true, false);
        }
    }

    /**
     * Recover, remove the collection, then crash after commit.
     */
    public void removeCollection(final BrokerPool pool) {
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction();) {

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
    public void restart() throws EXistException, PermissionDeniedException, IOException, DatabaseConfigurationException {
        BrokerPool.FORCE_CORRUPTION = false;
        final BrokerPool pool = startDb();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            Collection root = broker.openCollection(TestConstants.TEST_COLLECTION_URI, LockMode.READ_LOCK);
            assertNull("Removed collection does still exist", root);
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
}
