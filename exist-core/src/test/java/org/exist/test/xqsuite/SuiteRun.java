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

import org.exist.storage.BrokerPool;
import org.exist.xquery.XQueryWatchDog;
import org.junit.platform.engine.CancellationToken;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.reporting.ReportEntry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Runs the test files of one {@link XQSuite} against a running database, and fails any file that hangs.
 * <p>
 * Every file runs on a thread of its own, so that a file which stops making progress can be given up on
 * without taking the whole run down with it. A sequential suite runs one file at a time; a parallel suite
 * runs up to {@link XQSuiteSettings#parallelism(int)} files at a time. The tests within a file always run
 * one after the other.
 * <p>
 * A watcher thread checks the running files. A file that has reported nothing for longer than the hang
 * threshold is <em>abandoned</em>: its running and not yet run tests are failed, the file is finished as
 * failed, its XQuery is killed and its thread interrupted, and the remaining files carry on. Whatever the
 * abandoned thread reports afterwards is ignored.
 * <p>
 * The watcher also looks at the {@link CancellationToken} of the run, much more often than at the files. Once
 * the run is cancelled (Ctrl-C, an IDE's stop button, a tool that gave up) the running files are abandoned
 * the same way, with the cancellation as the reason, and the files that have not started are skipped.
 * <p>
 * Giving up on a file is reliable, stopping its thread is not. A thread that is sleeping or blocked ends when it
 * is interrupted, and eXist can stop a query at the points where it checks for being killed, but a query that is
 * spinning in code that does neither keeps running (which expressions check differs, and queries nested inside the
 * test runner do not always see the kill). Such a thread is left behind, and since it can stop the database from
 * shutting down the engine bounds that shutdown, see {@link XQSuiteTestEngine}.
 */
final class SuiteRun {
    /** how many of the slowest files the time summary names */
    private static final int SLOWEST_FILES_SHOWN = 3;

    /** how often the watcher looks at the cancellation token, so that a cancelled run ends in a moment */
    private static final long CANCELLATION_POLL_MILLIS = 200;

    private static final String NOT_RUN_CANCELLED = "Not run: the test run was cancelled";

    private final SuiteDescriptor suite;
    private final EngineExecutionListener listener;
    private final BrokerPool brokerPool;
    private final XQSuiteSettings settings;
    private final CancellationToken cancellation;

    private final List<FileRun> runs = new CopyOnWriteArrayList<>();
    private final Semaphore permits;
    private final Duration hangThreshold;

    SuiteRun(final SuiteDescriptor suite, final EngineExecutionListener listener, final BrokerPool brokerPool, final XQSuiteSettings settings,
            final CancellationToken cancellation) {
        this.suite = suite;
        this.listener = listener;
        this.brokerPool = brokerPool;
        this.settings = settings;
        this.cancellation = cancellation;
        this.permits = new Semaphore(suite.parallel() ? settings.parallelism(brokerPool.getMax()) : 1);
        this.hangThreshold = settings.hangThreshold();
    }

    void run() throws InterruptedException {
        final AtomicInteger threadNumber = new AtomicInteger();
        final ThreadFactory factory = runnable -> {
            final Thread thread = new Thread(runnable, "xqsuite-" + suite.suiteClass().getSimpleName() + "-" + threadNumber.incrementAndGet());
            // a hung file's thread must not keep the JVM alive
            thread.setDaemon(true);
            return thread;
        };
        final ExecutorService threads = Executors.newCachedThreadPool(factory);
        final Thread watcher = new Thread(this::watch, "xqsuite-hang-watcher-" + suite.suiteClass().getSimpleName());
        watcher.setDaemon(true);
        watcher.start();
        try {
            for (final TestDescriptor child : new ArrayList<>(suite.getChildren())) {
                permits.acquire();
                if (cancellation.isCancellationRequested()) {
                    permits.release();
                    listener.executionSkipped(child, NOT_RUN_CANCELLED);
                } else {
                    final FileRun run = new FileRun((FileDescriptor) child);
                    runs.add(run);
                    threads.execute(run);
                }
            }
            for (final FileRun run : runs) {
                run.awaitSettled();
            }
            printTimeSummary();
        } finally {
            watcher.interrupt();
            threads.shutdown();
            awaitAbandonedThreads();
        }
    }

    /**
     * Says once, when the suite is done, how long its files took. Printing a line as each file ends would
     * put it into the output of whichever test is running at that moment, which with files running side by
     * side is a test of another file; the per-file times stay available as report entries.
     */
    private void printTimeSummary() {
        final List<FileTime> times = runs.stream()
                .filter(run -> run.millis >= 0)
                .map(run -> new FileTime(run.file.getDisplayName(), run.millis))
                .toList();
        if (!times.isEmpty()) {
            System.out.println(timeSummary(suite.suiteClass().getSimpleName(), times, SLOWEST_FILES_SHOWN));
        }
    }

    /** how long one file of a suite took */
    record FileTime(String file, long millis) {
    }

    /**
     * @param suiteName the simple name of the suite class
     * @param times the time of each file that completed
     * @param slowest how many of the slowest files to name
     *
     * @return one line: the number of files, the time they took in all, and the slowest files
     */
    static String timeSummary(final String suiteName, final List<FileTime> times, final int slowest) {
        final long total = times.stream().mapToLong(FileTime::millis).sum();
        final String slowestFiles = times.stream()
                .sorted(Comparator.comparingLong(FileTime::millis).reversed().thenComparing(FileTime::file))
                .limit(slowest)
                .map(t -> t.file() + " " + t.millis() + " ms")
                .collect(Collectors.joining(", "));
        return "XQSuite " + suiteName + ": " + times.size() + (times.size() == 1 ? " file, " : " files, ")
                + total + " ms in all, slowest: " + slowestFiles;
    }

    /**
     * Give the threads of abandoned files a chance to stop before the database is shut down under them.
     */
    private void awaitAbandonedThreads() throws InterruptedException {
        final List<Thread> abandoned = runs.stream()
                .filter(run -> run.state.get() == State.ABANDONED && run.thread != null)
                .map(run -> run.thread)
                .toList();
        awaitTermination(abandoned, settings.hangGrace());
    }

    /**
     * Waits for the threads to end, for at most {@code grace} in all, not for each of them.
     * <p>
     * A thread that is stuck in a lock does not end when it is interrupted, so the first such thread uses up
     * the whole time. The threads after it must then not be waited for at all: {@code Thread.join(0)} does not
     * mean "do not wait", it means "wait forever".
     *
     * @param threads the threads to wait for
     * @param grace how long to wait in all
     *
     * @return true if all the threads have ended
     */
    static boolean awaitTermination(final List<Thread> threads, final Duration grace) throws InterruptedException {
        final long deadline = System.nanoTime() + grace.toNanos();
        boolean allEnded = true;
        for (final Thread thread : threads) {
            final long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos > 0) {
                thread.join(Duration.ofNanos(remainingNanos));
            }
            allEnded &= !thread.isAlive();
        }
        return allEnded;
    }

    private void watch() {
        final long hangCheckNanos = settings.hangWatcherInterval().toNanos();
        long nextHangCheck = System.nanoTime() + hangCheckNanos;
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(CANCELLATION_POLL_MILLIS);
                final long now = System.nanoTime();
                if (cancellation.isCancellationRequested()) {
                    abandonRunningFiles("was given up on because the test run was cancelled");
                } else if (now - nextHangCheck >= 0) {
                    abandonHungFiles(now);
                    nextHangCheck = now + hangCheckNanos;
                }
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void abandonHungFiles(final long now) {
        for (final FileRun run : runs) {
            if (run.state.get() == State.RUNNING && now - run.lastActivityNanos > hangThreshold.toNanos()) {
                run.abandon("appears hung (no activity for " + format(hangThreshold) + ")");
            }
        }
    }

    /**
     * Every poll while the run is cancelled, not just the first: a file whose thread was about to start when the
     * cancellation was noticed is running by the next poll.
     */
    private void abandonRunningFiles(final String reason) {
        for (final FileRun run : runs) {
            if (run.state.get() == State.RUNNING) {
                run.abandon(reason);
            }
        }
    }

    private enum State { PENDING, RUNNING, DONE, ABANDONED }

    private final class FileRun implements Runnable {
        private final FileDescriptor file;
        private final EngineTestEvents events;
        private final AtomicReference<State> state = new AtomicReference<>(State.PENDING);
        private final CountDownLatch settled = new CountDownLatch(1);
        private volatile long lastActivityNanos = System.nanoTime();
        private volatile Thread thread;
        /** how long the file took, or -1 while it has not completed (an abandoned file never does) */
        private volatile long millis = -1;

        FileRun(final FileDescriptor file) {
            this.file = file;
            this.events = new EngineTestEvents(file, listener, this::touch);
        }

        private void touch() {
            lastActivityNanos = System.nanoTime();
        }

        void awaitSettled() throws InterruptedException {
            settled.await();
        }

        @Override
        public void run() {
            thread = Thread.currentThread();
            touch();
            state.set(State.RUNNING);
            final long startNanos = System.nanoTime();
            listener.executionStarted(file);

            Throwable failure = null;
            try {
                file.runner().run(events, brokerPool);
            } catch (final Throwable t) {
                failure = t;
            }

            // the watcher may have abandoned the file meanwhile, in which case everything has been reported
            if (state.compareAndSet(State.RUNNING, State.DONE)) {
                events.completeOutstanding(failure);
                millis = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
                listener.reportingEntryPublished(file, ReportEntry.from("file-time-ms", Long.toString(millis)));
                listener.executionFinished(file, failure == null ? TestExecutionResult.successful() : TestExecutionResult.failed(failure));
                settle();
            }
        }

        /**
         * @param reason what is said about the file in the failure, after "Test file "
         */
        void abandon(final String reason) {
            if (!state.compareAndSet(State.RUNNING, State.ABANDONED)) {
                return;
            }
            final List<String> running = events.runningTests();
            final AssertionError abandoned = new AssertionError("Test file " + reason + ": "
                    + file.runner().getSourcePath().toAbsolutePath()
                    + (running.isEmpty() ? "" : " while running " + running));
            events.completeOutstanding(abandoned);
            listener.executionFinished(file, TestExecutionResult.failed(abandoned));

            // stop the query if it can be stopped, then the thread, so that a stuck file does not keep using the database
            final Thread stuck = thread;
            for (final XQueryWatchDog watchDog : brokerPool.getProcessMonitor().getRunningXQueries()) {
                if (stuck != null && stuck.getName().equals(watchDog.getRunningThread())) {
                    watchDog.kill(0);
                }
            }
            if (stuck != null) {
                stuck.interrupt();
            }
            settle();
        }

        private void settle() {
            permits.release();
            settled.countDown();
        }
    }

    private static String format(final Duration duration) {
        final long seconds = duration.toSeconds();
        return seconds >= 120 ? (seconds / 60) + " minutes" : seconds + " seconds";
    }
}
