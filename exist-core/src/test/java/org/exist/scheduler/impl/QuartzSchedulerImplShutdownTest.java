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
package org.exist.scheduler.impl;

import org.exist.scheduler.JobException;
import org.exist.scheduler.UserJavaJob;
import org.exist.storage.BrokerPool;
import org.exist.test.ExistEmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that {@link QuartzSchedulerImpl#shutdown(long)} stops an idle scheduler
 * without waiting, and still waits for a job that is executing.
 */
class QuartzSchedulerImplShutdownTest {

    // not static: every test shuts down the scheduler of its database, so each gets its own
    @RegisterExtension
    final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    void idleSchedulerShutsDown() {
        final QuartzSchedulerImpl scheduler = (QuartzSchedulerImpl) existEmbeddedServer.getBrokerPool().getScheduler();
        assertFalse(scheduler.isShutdown());

        scheduler.shutdown(5_000);

        assertTrue(scheduler.isShutdown());
    }

    @Test
    void idleSchedulerDoesNotWaitForTheQuartzThreads() {
        final List<String> calls = new ArrayList<>();
        final org.quartz.Scheduler recording = (org.quartz.Scheduler) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {org.quartz.Scheduler.class}, (proxy, method, args) -> {
                    calls.add(method.getName() + (args == null ? "" : java.util.Arrays.toString(args)));
                    return method.getName().equals("getCurrentlyExecutingJobs") ? List.of() : null;
                });
        // no BrokerPool needed: the idle path returns before it would name the watchdog thread
        final QuartzSchedulerImpl scheduler = new QuartzSchedulerImpl(null) {
            @Override
            protected org.quartz.Scheduler getScheduler() {
                return recording;
            }
        };

        scheduler.shutdown(5_000);

        assertEquals(List.of("getCurrentlyExecutingJobs", "shutdown[false]"), calls);
    }

    @Test
    void runningJobIsWaitedFor() throws InterruptedException {
        final QuartzSchedulerImpl scheduler = (QuartzSchedulerImpl) existEmbeddedServer.getBrokerPool().getScheduler();
        SlowJob.reset();
        assertTrue(scheduler.createPeriodicJob(1_000, new SlowJob(), 0, null, 0));
        assertTrue(SlowJob.STARTED.await(10, TimeUnit.SECONDS), "job did not start");

        scheduler.shutdown(30_000);

        assertTrue(SlowJob.FINISHED.get(), "shutdown returned before the running job finished");
        assertTrue(scheduler.isShutdown());
    }

    /** Quartz creates the job itself, so the coordination state is static. */
    public static class SlowJob extends UserJavaJob {
        static volatile CountDownLatch STARTED = new CountDownLatch(1);
        static final AtomicBoolean FINISHED = new AtomicBoolean();

        static void reset() {
            STARTED = new CountDownLatch(1);
            FINISHED.set(false);
        }

        @Override
        public String getName() {
            return "QuartzSchedulerImplShutdownTest.SlowJob";
        }

        @Override
        public void setName(final String name) {
            // fixed name
        }

        @Override
        public void execute(final BrokerPool brokerpool, final Map<String, ?> params) throws JobException {
            STARTED.countDown();
            try {
                Thread.sleep(1_000);
            } catch (final InterruptedException e) {
                // interrupted by a shutdown that did not wait: not finished
                Thread.currentThread().interrupt();
                return;
            }
            FINISHED.set(true);
        }
    }
}
