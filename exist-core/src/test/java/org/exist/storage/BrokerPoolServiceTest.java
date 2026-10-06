/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.DatabaseConfigurationException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests for exercising the BrokerPoolService
 * infrastructure.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
class BrokerPoolServiceTest {

    // NOTE: this is a concurrent list because it is shared between the test and the BackgroundJobsBrokerPoolService
    private final List<Future<List<BackgroundJobsBrokerPoolService.TimestampAndId>>> futures = new CopyOnWriteArrayList<>();

    /**
     * This test registers a custom BrokerPoolService
     * and then runs the database.
     *
     * The custom BrokerPoolService launches a bunch of
     * background jobs when the database starts, and then
     * attempts to stop them cleanly when the database
     * stops.
     */
    @Test
    void backgroundJobsShutdownCleanly() throws EXistException, IOException, DatabaseConfigurationException, InterruptedException, ExecutionException {
        // Create and add our BackgroundJobsBrokerPoolService to the BrokerPool
        final BrokerPoolService testBrokerPoolService = new BackgroundJobsBrokerPoolService(futures);
        final Properties configProps = new Properties();
        configProps.put("exist.testBrokerPoolService", testBrokerPoolService);

        // run the database
        final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(configProps, true, true);
        existEmbeddedServer.startDb();
        try {

            // do nothing for a while (background jobs will be running)
            Thread.sleep(3000);

        } finally {
            existEmbeddedServer.stopDb();
        }

        // check results
        int totalBackgroundJobResults = 0;
        for (int i = 0; i < futures.size(); i++) {
            final Future<List<BackgroundJobsBrokerPoolService.TimestampAndId>> future = futures.get(i);
            try {
                final List<BackgroundJobsBrokerPoolService.TimestampAndId> backgroundJobResult = future.get();

                // the jobs that got a thread straight away have been running for the whole test, so they
                // must contain at least 1 result; the others only start once those have stopped, which is
                // after the BrokerPool began to shut down, and may be refused a broker (see BackgroundJob)
                if (i < BackgroundJobsBrokerPoolService.NUM_THREADS) {
                    assertFalse(backgroundJobResult.isEmpty());
                }

                totalBackgroundJobResults += backgroundJobResult.size();

            } catch (final CancellationException e) {
                // ignore as the background job was cancelled OK
            } catch (final ExecutionException e) {
                // a background task threw an exception... shouldn't happen, so throw it!
                throw e;
            } catch (final InterruptedException e) {
                // interrupted while waiting on the future, can't recover...

                // restore interrupt flag status
                Thread.currentThread().interrupt();

                throw e;
            }
        }

        assertTrue(totalBackgroundJobResults > 0);
    }

    /**
     * The BrokerPool changes to its shutting down state before it stops its services, and refuses a new broker
     * from then on when it has no idle one. A background job that wakes up in that window must finish cleanly.
     * This is deterministic (the pool is a mock that always refuses), unlike backgroundJobsShutdownCleanly, which
     * only hits that window by chance.
     */
    @Test
    void backgroundJobRefusedABrokerWhileShuttingDownFinishesCleanly() throws Exception {
        final BrokerPool mockBrokerPool = createMock(BrokerPool.class);
        expect(mockBrokerPool.getBroker()).andThrow(new EXistException("BrokerPool is not operational (state: SHUTTING_DOWN_MULTI_USER_MODE)")).anyTimes();
        expect(mockBrokerPool.isShuttingDown()).andReturn(true).anyTimes();
        replay(mockBrokerPool);

        final BackgroundJobsBrokerPoolService service = new BackgroundJobsBrokerPoolService(futures);
        service.startMultiUser(mockBrokerPool);
        try {
            for (final Future<List<BackgroundJobsBrokerPoolService.TimestampAndId>> future : futures) {
                // the job ends without an exception, and without a result as it never got a broker
                assertTrue(future.get(30, TimeUnit.SECONDS).isEmpty());
            }
        } finally {
            service.stopMultiUser(mockBrokerPool);
        }
    }

    /**
     * Not getting a broker for any other reason than a shutting down BrokerPool is still a failure of the job.
     */
    @Test
    void backgroundJobRefusedABrokerWhileNotShuttingDownFails() throws Exception {
        final BrokerPool mockBrokerPool = createMock(BrokerPool.class);
        expect(mockBrokerPool.getBroker()).andThrow(new EXistException("no broker")).anyTimes();
        expect(mockBrokerPool.isShuttingDown()).andReturn(false).anyTimes();
        replay(mockBrokerPool);

        final BackgroundJobsBrokerPoolService service = new BackgroundJobsBrokerPoolService(futures);
        service.startMultiUser(mockBrokerPool);
        try {
            for (final Future<List<BackgroundJobsBrokerPoolService.TimestampAndId>> future : futures) {
                final ExecutionException e = assertThrows(ExecutionException.class, () -> future.get(30, TimeUnit.SECONDS));
                assertInstanceOf(EXistException.class, e.getCause());
            }
        } finally {
            service.stopMultiUser(mockBrokerPool);
        }
    }

    public static class BackgroundJobsBrokerPoolService implements BrokerPoolService {
        private static final Logger LOG = LogManager.getLogger(BackgroundJobsBrokerPoolService.class);

        static final int NUM_THREADS = 10;
        private static final int NUM_JOBS = 30;  // It is intentional that there are more jobs than threads
        private static final long JOB_LOOP_DELAY = 1000;

        private final AtomicReference<BrokerPool> brokerPoolRef = new AtomicReference<>();
        private final ExecutorService executorService = Executors.newFixedThreadPool(NUM_THREADS);
        private final List<Future<List<BackgroundJobsBrokerPoolService.TimestampAndId>>> futures;
        private volatile boolean stopBackgroundJobs = false;

        public BackgroundJobsBrokerPoolService(final List<Future<List<BackgroundJobsBrokerPoolService.TimestampAndId>>> futures) {
            this.futures = futures;
        }

        @Override
        public void startMultiUser(final BrokerPool brokerPool) throws BrokerPoolServiceException {
            if (!brokerPoolRef.compareAndSet(null, brokerPool)) {
                throw new BrokerPoolServiceException("BrokerPool reference is already set");
            }

            startBackgroundJobs();
        }

        @Override
        public void stopMultiUser(final BrokerPool brokerPool) throws BrokerPoolServiceException {
            if (brokerPoolRef.get() != brokerPool) {
                throw new BrokerPoolServiceException("BrokerPool does not match BrokerPoolRef");
            }

            stopBackgroundJobs();

            if (!brokerPoolRef.compareAndSet(brokerPool, null)) {
                throw new BrokerPoolServiceException("Could not clear BrokerPool reference");
            }
        }

        private void startBackgroundJobs() {
            for (int i = 0; i < NUM_JOBS; i++) {
                final Future<List<TimestampAndId>> future = executorService.submit(new BackgroundJob(i));
                futures.add(future);
            }
        }

        private void stopBackgroundJobs() {
            // let the executor service know we will be shutting down
            executorService.shutdown();

            // signal the background jobs that they should stop
            stopBackgroundJobs = true;

            // wait for all jobs to finish... or timeout!
            final long timeout = NUM_JOBS * JOB_LOOP_DELAY * 2;
            try {
                final boolean cleanFinish = executorService.awaitTermination(timeout, TimeUnit.MILLISECONDS);
                if (!cleanFinish) {
                    LOG.warn("Clean shutdown of background tasks failed... calling shutdownNow");
                    // do the best we can
                    executorService.shutdownNow();
                }

            } catch (final InterruptedException e) {
                // restore interrupt flag status
                Thread.currentThread().interrupt();

                LOG.error(e.getMessage(), e);

                // ... nothing else that we can do!
                executorService.shutdownNow();
            }
        }

        private class BackgroundJob implements Callable<List<TimestampAndId>> {
            private final List<TimestampAndId> list = new ArrayList<>();
            private final int jobId;

            public BackgroundJob(final int jobId) {
                this.jobId = jobId;
            }

            @Override
            public List<TimestampAndId> call() throws Exception {
                do {
                    final BrokerPool brokerPool = brokerPoolRef.get();
                    if (brokerPool == null) {
                        throw new Exception("Unable to get BrokerPoolRef");
                    }

                    try (final DBBroker broker = brokerPool.getBroker()) {
                        final long timestamp = System.nanoTime();
                        final String id = "job_" + jobId + "_" + broker.getId();

                        list.add(new TimestampAndId(timestamp, id));
                    } catch (final EXistException e) {
                        // The BrokerPool changes to its shutting down state before it stops this service
                        // (which is what tells the jobs to stop), and from then on it refuses a new broker
                        // when it has no idle one. A job that wakes up in between is finished, any other
                        // reason for not getting a broker is a failure.
                        if (brokerPool.isShuttingDown()) {
                            return list;
                        }
                        throw e;
                    }

                    // just to slow things down a little
                    Thread.sleep(JOB_LOOP_DELAY);

                } while (!stopBackgroundJobs);

                return list;
            }
        }

        private static class TimestampAndId {
            public final long timestamp;
            public final String id;

            private TimestampAndId(final long timestamp, final String id) {
                this.timestamp = timestamp;
                this.id = id;
            }
        }
    }
}
