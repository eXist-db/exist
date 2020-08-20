package org.exist.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.util.io.FastByteArrayOutputStream;
import org.exist.xmldb.XmldbURI;
import org.exist.test.TestConstants;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.samples.Samples.SAMPLES;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertNotNull;
import org.junit.ClassRule;
import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.*;

public class DirtyShutdownTest {

    private static final Logger LOG = LogManager.getLogger(DirtyShutdownTest.class);

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);
    
    @Test
    public void run() throws ExecutionException, InterruptedException {
        final ExecutorService service = Executors.newSingleThreadExecutor();
        final Callable<Void> callable = () -> {
            storeRepeatedly();
            return null;
        };
        final Future<Void> future = service.submit(callable);

        synchronized (this) {
            try {
                wait(5000);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error(e.getMessage(), e);
            }
        }

        // will raise an exception, failing the test, if the callable raised an exception
        future.get();
    }

    public void storeRepeatedly() {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            Collection root;

            try(final Txn transaction = transact.beginTransaction()) {
                root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
                assertNotNull(root);
                broker.saveCollection(transaction, root);
                transact.commit(transaction);
            }

            final String data;
            try (final FastByteArrayOutputStream os = new FastByteArrayOutputStream();
                 final InputStream is = SAMPLES.getMacbethSample()) {
                os.write(is);
                data = new String(os.toByteArray(), UTF_8);
            }

            for (int i = 0; i < 50; i++) {
                try(final Txn transaction = transact.beginTransaction()) {

                    final IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"), data);
                    assertNotNull(info);
                    root.store(transaction, broker, info, data);

                    transact.commit(transaction);
                }
            }
        } catch (final PermissionDeniedException | EXistException | SAXException | LockException | IOException e) {
            LOG.error(e.getMessage(), e);
            fail(e.getMessage());
        }
    }
}
