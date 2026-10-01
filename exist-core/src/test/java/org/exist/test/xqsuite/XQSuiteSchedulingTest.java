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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;
import org.junit.platform.testkit.engine.EventType;
import org.opentest4j.AssertionFailedError;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * Tests how the {@link XQSuiteTestEngine} schedules the files of a suite, and that it fails a file that hangs.
 * <p>
 * Whether files overlap is read off the timeline of the engine's events. The fixtures each sleep for
 * a few seconds, so files overlap if, and only if, they were run concurrently.
 */
class XQSuiteSchedulingTest {

    private static final String WAITS = "src/test/resources/org/exist/test/xqsuite/";
    private static final String RUNNER = "src/test/resources/org/exist/test/runner/";

    @XQSuite({WAITS + "wait-a.xqm", WAITS + "wait-b.xqm", WAITS + "wait-c.xqm", WAITS + "wait-d.xqm"})
    static class SequentialWaits {
    }

    @XQSuite(value = {WAITS + "wait-a.xqm", WAITS + "wait-b.xqm", WAITS + "wait-c.xqm", WAITS + "wait-d.xqm"}, parallel = true)
    static class ParallelWaits {
    }

    /** the hanging file comes first, so a sequential suite only gets to the second file if the first is given up on */
    @XQSuite({WAITS + "hang.xqm", RUNNER + "single-test.xqm"})
    static class SequentialHang {
    }

    @XQSuite(value = {WAITS + "hang.xqm", RUNNER + "single-test.xqm"}, parallel = true)
    static class ParallelHang {
    }

    private static EngineExecutionResults run(final Class<?> suite, final Map<String, String> parameters) {
        final EngineTestKit.Builder builder = EngineTestKit.engine(XQSuiteTestEngine.ENGINE_ID).selectors(selectClass(suite));
        parameters.forEach(builder::configurationParameter);
        return builder.execute();
    }

    /** a hang is detected after a couple of seconds instead of minutes */
    private static Map<String, String> quickHangDetection() {
        return Map.of(
                XQSuiteSettings.HANG_THRESHOLD_MINUTES, "0.04",
                XQSuiteSettings.HANG_WATCHER_INTERVAL_SECONDS, "1",
                XQSuiteSettings.HANG_GRACE_SECONDS, "20");
    }

    /**
     * @return the most tests that were running at the same moment
     */
    private static int maxConcurrentTests(final EngineExecutionResults results) {
        final Map<UniqueId, Instant> starts = new HashMap<>();
        final Map<UniqueId, Instant> ends = new HashMap<>();
        for (final Event event : results.testEvents().list()) {
            final UniqueId id = event.getTestDescriptor().getUniqueId();
            if (event.getType() == EventType.STARTED) {
                starts.put(id, event.getTimestamp());
            } else if (event.getType() == EventType.FINISHED) {
                ends.put(id, event.getTimestamp());
            }
        }
        // sweep over the start and end points, handling an end before a start at the same instant
        record Point(Instant at, int change) { }
        final List<Point> points = new ArrayList<>();
        starts.forEach((id, start) -> {
            points.add(new Point(start, 1));
            points.add(new Point(ends.get(id), -1));
        });
        points.sort(Comparator.comparing(Point::at).thenComparingInt(Point::change));
        int running = 0;
        int max = 0;
        for (final Point point : points) {
            running += point.change();
            max = Math.max(max, running);
        }
        return max;
    }

    private static List<Throwable> failures(final EngineExecutionResults results) {
        return results.testEvents().failed().list().stream()
                .map(event -> event.getRequiredPayload(TestExecutionResult.class).getThrowable().orElseThrow())
                .toList();
    }

    @Test
    void sequentialSuiteRunsOneFileAtATime() {
        final EngineExecutionResults results = run(SequentialWaits.class, Map.of());
        results.testEvents().assertStatistics(stats -> stats.started(4).succeeded(4));
        assertEquals(1, maxConcurrentTests(results), "files of a sequential suite must not overlap");
    }

    @Test
    void parallelSuiteRunsFilesAtTheSameTime() {
        final EngineExecutionResults results = run(ParallelWaits.class, Map.of(XQSuiteSettings.PARALLELISM, "4"));
        results.testEvents().assertStatistics(stats -> stats.started(4).succeeded(4));
        assertEquals(4, maxConcurrentTests(results), "all four files should have been running together");
    }

    @Test
    void parallelismSettingLimitsHowManyFilesRunAtOnce() {
        final EngineExecutionResults results = run(ParallelWaits.class, Map.of(XQSuiteSettings.PARALLELISM, "2"));
        results.testEvents().assertStatistics(stats -> stats.started(4).succeeded(4));
        assertEquals(2, maxConcurrentTests(results), "no more than two files may run together");
    }

    @ParameterizedTest(name = "parallel suite: {0}")
    @ValueSource(booleans = {false, true})
    void hungFileIsFailedAndTheOtherFilesStillRun(final boolean parallel) {
        final Class<?> suite = parallel ? ParallelHang.class : SequentialHang.class;
        final EngineExecutionResults results = assertTimeoutPreemptively(Duration.ofSeconds(60), () -> run(suite, quickHangDetection()));

        results.testEvents().assertStatistics(stats -> stats.started(2).succeeded(1).failed(1));

        final Throwable failure = failures(results).getFirst();
        assertTrue(failure instanceof AssertionError && !(failure instanceof AssertionFailedError), "the hang is reported as an assertion error: " + failure);
        assertTrue(failure.getMessage().contains("appears hung"), failure.getMessage());
        assertTrue(failure.getMessage().contains("hang.xqm"), "names the file: " + failure.getMessage());
        assertTrue(failure.getMessage().contains("hangs"), "names the test that was running: " + failure.getMessage());

        // the abandoned thread is woken up and finishes the test normally; that must not be reported as well
        final Map<UniqueId, Long> finishes = new HashMap<>();
        results.allEvents().list().stream()
                .filter(event -> event.getType() == EventType.FINISHED)
                .forEach(event -> finishes.merge(event.getTestDescriptor().getUniqueId(), 1L, Long::sum));
        finishes.forEach((id, count) -> assertEquals(1L, count, id + " must finish exactly once"));
        assertEquals(1, results.containerEvents().failed().count(), "the hung file, and only it, is a failed container");
    }

    @Test
    void stopThatFinishesInTimeIsReportedAsStopped() throws InterruptedException {
        assertTrue(XQSuiteTestEngine.stopWithin(() -> { }, Duration.ofSeconds(5)));
    }

    /**
     * A runaway query that ignores both interruption and the kill flag keeps the database from shutting down.
     * That cannot be reproduced for real in a shared test JVM, so the bounded wait is tested on its own.
     */
    @Test
    void stopThatNeverReturnsIsGivenUpOn() throws InterruptedException {
        final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        try {
            assertFalse(XQSuiteTestEngine.stopWithin(() -> {
                try {
                    release.await();
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, Duration.ofMillis(300)));
        } finally {
            release.countDown();
        }
    }

    @Test
    void stopThatFailsIsReportedAsAFailure() {
        final IllegalStateException e = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> XQSuiteTestEngine.stopWithin(() -> { throw new IllegalStateException("boom"); }, Duration.ofSeconds(5)));
        assertEquals("boom", e.getCause().getMessage());
    }
}
