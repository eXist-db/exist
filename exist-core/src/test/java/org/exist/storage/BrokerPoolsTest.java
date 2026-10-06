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

import org.exist.EXistException;
import org.exist.storage.journal.Journal;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author <a href="mailto:patrick@reini.net">Patrick Reinhart</a>
 */
public class BrokerPoolsTest {

    @TempDir
    Path temporaryFolder;

    @Test
    void shutdownConcurrent() throws InterruptedException, ExecutionException, EXistException, DatabaseConfigurationException, IOException {
        final int testThreads = 5;
        final CountDownLatch shutdownLatch = new CountDownLatch(1);
        final CountDownLatch acquiredLatch = new CountDownLatch(testThreads);
        final List<Future<Exception>> shutdownTasks = new ArrayList<>();
        final ExecutorService executorService = Executors.newFixedThreadPool(testThreads);
        for (int i = 0; i < testThreads; i ++) {
            final Path dataDir = Files.createDirectories(temporaryFolder.resolve("exist" + i)).normalize().toAbsolutePath();

            // load config from classpath and override data and journal dir
            final Configuration configuration = new Configuration("conf.xml");
            configuration.setProperty(BrokerPool.PROPERTY_DATA_DIR, dataDir);
            configuration.setProperty(Journal.PROPERTY_RECOVERY_JOURNAL_DIR, dataDir);
            BrokerPool.configure("instance" + i, 0, 1, configuration);
            shutdownTasks.add(executorService.submit(new BrokerPoolShutdownTask(acquiredLatch, shutdownLatch)));
        }

        // wait for all shutdown threads to be acquired
        acquiredLatch.await();
        shutdownLatch.countDown();

        executorService.shutdown();
        assertTrue(executorService.awaitTermination(8, TimeUnit.SECONDS));

        for (final Future<Exception> shutdownTask: shutdownTasks) {
            assertNull(shutdownTask.get());
        }
    }

    public static class BrokerPoolShutdownTask implements Callable<Exception> {
        private final CountDownLatch acquiredLatch;
        private final CountDownLatch shutdownLatch;

        public BrokerPoolShutdownTask(final CountDownLatch acquiredLatch, final CountDownLatch shutdownLatch) {
            this.acquiredLatch = acquiredLatch;
            this.shutdownLatch = shutdownLatch;
        }

        @Override
        public Exception call() throws Exception {
            try {
                acquiredLatch.countDown();
                // wait for signal to release the broker
                shutdownLatch.await();
                // shutdown
                BrokerPools.stopAll(true);
                return null;
            } catch (final Exception e) {
                return e;
            }
        }
    }

}
