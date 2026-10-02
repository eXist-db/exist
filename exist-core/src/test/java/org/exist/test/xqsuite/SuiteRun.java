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
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
 * Giving up on a file is reliable, stopping its thread is not. A thread that is sleeping or blocked ends when it
 * is interrupted, and eXist can stop a query at the points where it checks for being killed, but a query that is
 * spinning in code that does neither keeps running (which expressions check differs, and queries nested inside the
 * test runner do not always see the kill). Such a thread is left behind, and since it can stop the database from
 * shutting down the engine bounds that shutdown, see {@link XQSuiteTestEngine}.
 */
final class SuiteRun {
    private final SuiteDescriptor suite;
    private final EngineExecutionListener listener;
    private final BrokerPool brokerPool;
    private final XQSuiteSettings settings;

    private final List<FileRun> runs = new CopyOnWriteArrayList<>();
    private final Semaphore permits;
    private final Duration hangThreshold;

    SuiteRun(final SuiteDescriptor suite, final EngineExecutionListener listener, final BrokerPool brokerPool, final XQSuiteSettings settings) {
        this.suite = suite;
        this.listener = listener;
        this.brokerPool = brokerPool;
        this.settings = settings;
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
                final FileRun run = new FileRun((FileDescriptor) child);
                runs.add(run);
                threads.execute(run);
            }
            for (final FileRun run : runs) {
                run.awaitSettled();
            }
        } finally {
            watcher.interrupt();
            threads.shutdown();
            awaitAbandonedThreads();
        }
    }

    /**
     * Give the threads of abandoned files a chance to stop before the database is shut down under them.
     */
    private void awaitAbandonedThreads() throws InterruptedException {
        final long deadline = System.nanoTime() + settings.hangGrace().toNanos();
        for (final FileRun run : runs) {
            if (run.state.get() == State.ABANDONED && run.thread != null) {
                final long remainingMillis = Math.max(0, (deadline - System.nanoTime()) / 1_000_000);
                run.thread.join(remainingMillis);
            }
        }
    }

    private void watch() {
        final long intervalMillis = settings.hangWatcherInterval().toMillis();
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(intervalMillis);
                final long now = System.nanoTime();
                for (final FileRun run : runs) {
                    if (run.state.get() == State.RUNNING && now - run.lastActivityNanos > hangThreshold.toNanos()) {
                        run.abandon();
                    }
                }
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
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
                listener.executionFinished(file, failure == null ? TestExecutionResult.successful() : TestExecutionResult.failed(failure));
                settle();
            }
        }

        void abandon() {
            if (!state.compareAndSet(State.RUNNING, State.ABANDONED)) {
                return;
            }
            final List<String> running = events.runningTests();
            final AssertionError hung = new AssertionError("Test file appears hung (no activity for "
                    + format(hangThreshold) + "): " + file.runner().getSourcePath().toAbsolutePath()
                    + (running.isEmpty() ? "" : " while running " + running));
            events.completeOutstanding(hung);
            listener.executionFinished(file, TestExecutionResult.failed(hung));

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
