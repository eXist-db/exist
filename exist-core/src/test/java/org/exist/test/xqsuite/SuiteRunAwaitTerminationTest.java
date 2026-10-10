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
package org.exist.test.xqsuite;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the wait that {@link SuiteRun} makes for the threads of abandoned files before the database is
 * shut down. A file that hangs in a lock does not end when its thread is interrupted, so the wait has to be
 * bounded however many such threads there are.
 */
class SuiteRunAwaitTerminationTest {

    private static final Duration GRACE = Duration.ofMillis(500);

    /** a thread that ignores interruption, as one that is blocked on a database lock does */
    private static Thread stuck(final CountDownLatch release) {
        final Thread thread = new Thread(() -> {
            boolean released = false;
            while (!released) {
                try {
                    release.await();
                    released = true;
                } catch (final InterruptedException e) {
                    // ignored on purpose
                }
            }
        }, "stuck");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    @Test
    void severalStuckThreadsAreWaitedForAtMostTheGraceInAll() {
        final CountDownLatch release = new CountDownLatch(1);
        try {
            final List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                threads.add(stuck(release));
            }

            final long start = System.nanoTime();
            final boolean ended = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> SuiteRun.awaitTermination(threads, GRACE));
            final Duration waited = Duration.ofNanos(System.nanoTime() - start);

            assertFalse(ended, "the threads are still stuck");
            assertTrue(waited.compareTo(GRACE.multipliedBy(3)) < 0, "waited " + waited + " for three threads, grace is " + GRACE);
        } finally {
            release.countDown();
        }
    }

    @Test
    void zeroGraceDoesNotWait() {
        final CountDownLatch release = new CountDownLatch(1);
        try {
            final List<Thread> threads = List.of(stuck(release));
            final boolean ended = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> SuiteRun.awaitTermination(threads, Duration.ZERO));
            assertFalse(ended);
        } finally {
            release.countDown();
        }
    }

    @Test
    void threadsThatEndAreReportedAsEnded() throws InterruptedException {
        final CountDownLatch release = new CountDownLatch(1);
        final List<Thread> threads = List.of(stuck(release), stuck(release));
        release.countDown();
        assertTrue(SuiteRun.awaitTermination(threads, Duration.ofSeconds(10)));
    }
}
