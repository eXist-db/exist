/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.storage;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.journal.Journal;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.*;
import org.exist.xmldb.XmldbURI;
import org.junit.Test;
import org.xml.sax.SAXException;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import javax.xml.transform.Source;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class ConcurrentBrokerPoolTest {

    private final ThreadGroup threadGroup = new ThreadGroup("concurrentBrokerPoolTest");
    private final AtomicInteger threadNum = new AtomicInteger();
    private final int MAX_CONCURRENT_THREADS = 6;

    /**
     * Tests storing documents across multiple db instances within the same JVM in parallel.
     *
     * Creates n tasks which are distributed over {@code MAX_CONCURRENT_THREADS} threads.
     *
     * Within the same JVM, each task:
     *   1. Gets a new BrokerPool instance from the global BrokerPools
     *   2. With the BrokerPool instance:
     *     2.1 starts the instance
     *     2.2 stores a document into the instance's /db collection
     *     2.2 stops the instance
     *   3. Returns the instance to the global BrokerPools
     */
    @Test
    public void multiInstanceStore() throws InterruptedException, ExecutionException, DatabaseConfigurationException, PermissionDeniedException, EXistException, IOException, URISyntaxException {
        final ThreadFactory threadFactory = runnable -> new Thread(threadGroup, runnable, "leaseStoreRelease-" + threadNum.getAndIncrement());
        final ExecutorService executorService = Executors.newFixedThreadPool(MAX_CONCURRENT_THREADS, threadFactory);

        // the number of instances to use
        final int instances = 10;

        // setup store data tasks
        final List<Callable<Tuple2<Path, UUID>>> tasks = IntStream.range(0, instances)
                .mapToObj(i -> new StoreInstance())
                .collect(Collectors.toList());

        // store data
        final List<Future<Tuple2<Path, UUID>>> futures = executorService.invokeAll(tasks);
        executorService.shutdown();

        // validate stored data
        for (final Future<Tuple2<Path, UUID>> future : futures) {
            validateStoredDoc(future.get());
        }
    }

    private void validateStoredDoc(final Tuple2<Path, UUID> pathUuid) throws EXistException, IOException, DatabaseConfigurationException, PermissionDeniedException, URISyntaxException {
        final Path dataDir = pathUuid._1;
        assertTrue(Files.exists(dataDir));
        final UUID uuid = pathUuid._2;

        final Properties config = new Properties();
        config.put(BrokerPool.PROPERTY_DATA_DIR, dataDir);
        config.put(Journal.PROPERTY_RECOVERY_JOURNAL_DIR, dataDir);

        final ExistEmbeddedServer server = new ExistEmbeddedServer("validate-" + uuid.toString(), getConfigFile(getClass()), config, true, false);
        server.startDb();
        try {
            try (final DBBroker broker = server.getBrokerPool().getBroker()) {
                try (final LockedDocument doc = broker.getXMLResource(XmldbURI.DB.append(docName(uuid)), Lock.LockMode.READ_LOCK)) {
                    assertNotNull(doc);

                    final Source expected = Input.fromString(docContent(uuid)).build();
                    final Source actual = Input.fromNode(doc.getDocument()).build();

                    final Diff diff = DiffBuilder.compare(expected)
                            .withTest(actual)
                            .checkForSimilar()
                            .build();

                    // ASSERT
                    assertFalse(diff.toString(), diff.hasDifferences());

                }
            }
        } finally {
            server.stopDb();

            // clear temp files
            FileUtils.deleteQuietly(dataDir);
        }
    }

    private static XmldbURI docName(final UUID uuid) {
        return XmldbURI.create(uuid.toString() + ".xml");
    }

    private static String docContent(final UUID uuid) {
        return "<uuid>" + uuid.toString() + "</uuid>";
    }

    private static Path getConfigFile(final Class instance) throws URISyntaxException {
        return Paths.get(instance.getResource("ConcurrentBrokerPoolTest.conf.xml").toURI());
    }

    private static class StoreInstance implements Callable<Tuple2<Path, UUID>> {
        private final UUID uuid = UUID.randomUUID();

        @Override
        public Tuple2<Path, UUID> call() throws Exception {
            final ExistEmbeddedServer server = new ExistEmbeddedServer("store-" + uuid, getConfigFile(getClass()), null, true, true);

            server.startDb();
            try {
                store(server.getBrokerPool());
                return Tuple(server.getTemporaryStorage().get(), uuid);
            } finally {
                server.stopDb(false);  // NOTE: false flag ensures we don't delete the temporary storage!
            }
        }

        private void store(final BrokerPool brokerPool) throws EXistException, PermissionDeniedException, LockException, SAXException, IOException {
            try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
                    final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {
                try (final Collection collection = broker.openCollection(XmldbURI.DB, Lock.LockMode.WRITE_LOCK)){

                    final String docContent = docContent(uuid);

                    broker.storeDocument(transaction, docName(uuid), new StringInputSource(docContent), MimeType.XML_TYPE, collection);

                    transaction.commit();
                }
            }
        }
    }
}
