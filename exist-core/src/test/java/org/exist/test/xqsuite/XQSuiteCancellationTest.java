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

import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.CancellationToken;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * Tests that the {@link XQSuiteTestEngine} honours the cancellation token of the JUnit Platform: a run that is
 * cancelled (Ctrl-C, an IDE's stop button, a tool that gave up) ends promptly instead of when its files finish.
 */
class XQSuiteCancellationTest {

    private static final String WAITS = "src/test/resources/org/exist/test/xqsuite/";
    private static final String RUNNER = "src/test/resources/org/exist/test/runner/";

    /** the name of the threads that the engine runs the test files on, see SuiteRun */
    private static final String TEST_FILE_THREAD_PREFIX = "xqsuite-";

    /** the first file sleeps for ten minutes, so the second file is only reached if the first is given up on */
    @XQSuite(value = {WAITS + "hang.xqm", RUNNER + "single-test.xqm"}, fixture = true)
    static class HangThenAnotherFile {
    }

    /** a second suite, which is only reached when the first one has ended */
    @XQSuite(value = {RUNNER + "single-test.xqm"}, fixture = true)
    static class AnotherSuite {
    }

    /**
     * Whether a query is running on a thread of a test file of the run. The database of a suite is started by the
     * engine, so this is how the test finds out that the hanging file has really started; queries that the database
     * runs for itself on its own threads, for example while it starts, do not count.
     */
    private static boolean aTestFileQueryIsRunning() {
        if (!BrokerPool.isConfigured()) {
            return false;
        }
        try {
            return Arrays.stream(BrokerPool.getInstance().getProcessMonitor().getRunningXQueries())
                    .anyMatch(watchDog -> watchDog.getRunningThread().startsWith(TEST_FILE_THREAD_PREFIX));
        } catch (final EXistException e) {
            // the database is being stopped or has stopped, so nothing is running
            return false;
        }
    }

    /**
     * Cancelling a run while a file is running (and would be for ten minutes, the hang detection is left at its
     * default of five) fails that file, does not start the next file or the next suite, and ends the run within
     * seconds.
     */
    @Test
    void cancellingARunStopsTheRunningFileAndSkipsTheRest() {
        final CancellationToken token = CancellationToken.create();
        final ScheduledExecutorService canceller = Executors.newSingleThreadScheduledExecutor();
        try {
            // cancel as soon as the hanging file has started its query; a fixed delay would depend on how long the
            // database takes to start
            canceller.scheduleWithFixedDelay(() -> {
                if (aTestFileQueryIsRunning()) {
                    token.cancel();
                }
            }, 100, 100, TimeUnit.MILLISECONDS);

            final EngineExecutionResults results = assertTimeoutPreemptively(Duration.ofSeconds(60), () -> EngineTestKit
                    .engine(XQSuiteTestEngine.ENGINE_ID)
                    .selectors(selectClass(HangThenAnotherFile.class), selectClass(AnotherSuite.class))
                    .configurationParameter(XQSuiteSettings.FIXTURES, "true")
                    .cancellationToken(token)
                    .execute());

            results.testEvents().assertStatistics(stats -> stats.started(1).failed(1));
            final Throwable failure = results.testEvents().failed().list().getFirst()
                    .getRequiredPayload(TestExecutionResult.class).getThrowable().orElseThrow();
            assertTrue(failure.getMessage().contains("cancelled"), "says why: " + failure.getMessage());
            assertTrue(failure.getMessage().contains("hang.xqm"), "names the file: " + failure.getMessage());
            assertEquals(2, results.containerEvents().skipped().count(), "the second file and the second suite are skipped, not run");
        } finally {
            canceller.shutdownNow();
        }
    }
}
