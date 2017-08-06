package org.exist.storage;

import org.exist.TestUtils;
import org.exist.util.Configuration;
import org.exist.xquery.XQuery;
import org.exist.xmldb.XmldbURI;
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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
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
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            Collection root;

            try(final Txn transaction = transact.beginTransaction()) {
                root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
                assertNotNull(root);
                broker.saveCollection(transaction, root);
                transact.commit(transaction);
            }

            for (int i = 0; i < 50; i++) {
                try(final Txn transaction = transact.beginTransaction()) {

                    final Path f = TestUtils.resolveShakespeareSample("macbeth.xml");
                    IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"),
                            new InputSource(f.toUri().toASCIIString()));
                    assertNotNull(info);
                    root.store(transaction, broker, info, new InputSource(f.toUri().toASCIIString()));

                    transact.commit(transaction);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    public void storeAndWait() {
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            try(final Txn transaction = transact.beginTransaction()) {
                Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
                assertNotNull(root);
                broker.saveCollection(transaction, root);

                final Path f = TestUtils.resolveShakespeareSample("hamlet.xml");
                IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"),
                        new InputSource(f.toUri().toASCIIString()));
                assertNotNull(info);
                root.store(transaction, broker, info, new InputSource(f.toUri().toASCIIString()));

                transact.commit(transaction);
            }

            try(final Txn transaction = transact.beginTransaction()) {
                XQuery xquery = pool.getXQueryService();
                xquery.execute(broker, query, null);
                transact.commit(transaction);
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
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
