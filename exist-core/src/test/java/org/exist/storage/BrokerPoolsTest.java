/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.storage;

import org.exist.EXistException;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.BrokerPools;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xmldb.LocalCollection;
import org.exist.xmldb.XmldbURI;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xmldb.api.base.XMLDBException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

import static java.nio.file.Files.createDirectory;
import static java.nio.file.Files.write;
import static java.util.Collections.singleton;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.*;

/**
 * @author <a href="mailto:patrick@reini.net">Patrick Reinhart</a>
 */
public class BrokerPoolsTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shutdownConcurrent() throws InterruptedException, ExecutionException, EXistException, DatabaseConfigurationException, IOException {
        final int testThreads = 5;
        final CountDownLatch shutdownLatch = new CountDownLatch(1);
        final CountDownLatch acquiredLatch = new CountDownLatch(testThreads);
        final List<Future<Exception>> shutdownTasks = new ArrayList<>();
        final ExecutorService executorService = Executors.newFixedThreadPool(testThreads);
        for (int i = 0; i < testThreads; i ++) {
            final Path dataDir = temporaryFolder.newFolder("exist" + i).toPath().normalize().toAbsolutePath();
            final Path conf = dataDir.resolve("conf.xml");
            write(conf, singleton("<exist><db-connection database='native' files='" + datadir + "'/></exist>"));
            BrokerPool.configure("instance" + i, 0, 1, new Configuration(conf.normalize().toAbsolutePath().toString(), Optional.of(dataDir)));
            shutdownTasks.add(executorService.submit(new BrokerPoolShutdownTask(acquiredLatch, shutdownLatch)));
        }

        // wait for all shutdown threads to be acquired
        acquiredLatch.await();
        shutdownLatch.countDown();

        executorService.shutdown();
        assertTrue(executorService.awaitTermination(4, TimeUnit.SECONDS));

        for (Future<Exception> shutdownTask: shutdownTasks) {
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
            } catch (Exception e) {
                return e;
            }
        }
    }
}
