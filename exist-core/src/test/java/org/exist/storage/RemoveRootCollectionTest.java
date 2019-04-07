package org.exist.storage;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;
import static org.exist.samples.Samples.SAMPLES;

import java.io.InputStream;
import java.util.Optional;

import org.exist.collections.*;
import org.exist.storage.txn.*;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.io.InputStreamUtil;
import org.exist.xmldb.XmldbURI;
import org.junit.*;


public class RemoveRootCollectionTest {

    private DBBroker broker;
    Collection root;

    @Test
    public void removeEmptyRootCollection() throws Exception {
        final BrokerPool pool = BrokerPool.getInstance();
        final TransactionManager transact = pool.getTransactionManager();
        try (final Txn transaction = transact.beginTransaction()) {
            broker.removeCollection(transaction, root);
            transact.commit(transaction);
        }
        assertEquals(0, root.getChildCollectionCount(broker));
        assertEquals(0, root.getDocumentCount(broker));
    }

    @Test
    public void removeRootCollectionWithChildCollection() throws Exception {
        addChildToRoot();
        final BrokerPool pool = BrokerPool.getInstance();
        final TransactionManager transact = pool.getTransactionManager();
        try (final Txn transaction = transact.beginTransaction()) {
            broker.removeCollection(transaction, root);
            transact.commit(transaction);
        }
        assertEquals(0, root.getChildCollectionCount(broker));
        assertEquals(0, root.getDocumentCount(broker));
    }

    @Ignore
    @Test
    public void removeRootCollectionWithDocument() throws Exception {
        addDocumentToRoot();
        final BrokerPool pool = BrokerPool.getInstance();
        final TransactionManager transact = pool.getTransactionManager();
        try (final Txn transaction = transact.beginTransaction()) {
            broker.removeCollection(transaction, root);
            transact.commit(transaction);
        }
        assertEquals(0, root.getChildCollectionCount(broker));
        assertEquals(0, root.getDocumentCount(broker));
    }

    @Rule
    public final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Before
    public void startDB() throws Exception {
        final BrokerPool pool = BrokerPool.getInstance();
        broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
        root = broker.getCollection(XmldbURI.ROOT_COLLECTION_URI);
    }

    @After
    public void stopDB() {
        if (broker != null) {
            broker.close();
        }
    }

    private void addDocumentToRoot() throws Exception {
        final BrokerPool pool = BrokerPool.getInstance();
        final TransactionManager transact = pool.getTransactionManager();
        try (final Txn transaction = transact.beginTransaction();
             final InputStream is = SAMPLES.getHamletSample()) {
            assertNotNull(is);
            final String sample = InputStreamUtil.readString(is, UTF_8);
            final IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("hamlet.xml"), sample);
            assertNotNull(info);
            root.store(transaction, broker, info, sample);
            transact.commit(transaction);
        }
    }

    private void addChildToRoot() throws Exception {
        final BrokerPool pool = BrokerPool.getInstance();
        final TransactionManager transact = pool.getTransactionManager();
        try (final Txn transaction = transact.beginTransaction()) {
            final Collection child = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI.append("child"));
            broker.saveCollection(transaction, child);
            transact.commit(transaction);
        }
    }
}
