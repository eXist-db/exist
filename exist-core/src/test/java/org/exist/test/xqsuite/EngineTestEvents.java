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

import org.exist.test.runner.TestEvents;
import org.opentest4j.TestAbortedException;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.TestExecutionResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Translates the outcome events of one test file into JUnit Platform execution events.
 * <p>
 * The Platform allows exactly one result per test, whereas the XQuery side may report
 * a failure before it reports that the test finished, so the result is held back
 * until {@link #finished(String)}.
 */
final class EngineTestEvents implements TestEvents {
    private final FileDescriptor file;
    private final EngineExecutionListener listener;
    /** called whenever the XQuery side reports something, to show that the file is still making progress */
    private final Runnable activity;
    /** once the file is complete or abandoned, nothing more is reported, even if its thread is still running */
    private boolean closed = false;
    /** how many tests of each name have been started, to match repeated names to occurrences in order */
    private final Map<String, Integer> startedCount = new HashMap<>();
    /** the test of each name that is currently running */
    private final Map<String, XQTestDescriptor> current = new HashMap<>();
    private final Map<XQTestDescriptor, TestExecutionResult> pending = new HashMap<>();
    private final Set<XQTestDescriptor> started = new HashSet<>();
    private final Set<XQTestDescriptor> done = new HashSet<>();

    EngineTestEvents(final FileDescriptor file, final EngineExecutionListener listener, final Runnable activity) {
        this.file = file;
        this.listener = listener;
        this.activity = activity;
    }

    /**
     * @return the names of the tests that have started but not finished
     */
    synchronized List<String> runningTests() {
        return started.stream().filter(t -> !done.contains(t)).map(XQTestDescriptor::getDisplayName).sorted().toList();
    }

    /**
     * Starts the next not yet started test of the given name.
     */
    private XQTestDescriptor start(final String testName) {
        final int index = startedCount.merge(testName, 1, Integer::sum) - 1;
        final List<XQTestDescriptor> occurrences = file.findTests(testName);
        final XQTestDescriptor test;
        if (index < occurrences.size()) {
            test = occurrences.get(index);
        } else {
            // reported by the XQuery side but not found by discovery
            test = file.addTest(testName);
            listener.dynamicTestRegistered(test);
        }
        current.put(testName, test);
        started.add(test);
        listener.executionStarted(test);
        return test;
    }

    /**
     * @return the running test of the given name, starting one if the XQuery side reports on a test it never started
     */
    private XQTestDescriptor running(final String testName) {
        final XQTestDescriptor test = current.get(testName);
        return test != null && !done.contains(test) ? test : start(testName);
    }

    @Override
    public synchronized void started(final String testName) {
        if (closed) {
            return;
        }
        activity.run();
        start(testName);
    }

    @Override
    public synchronized void finished(final String testName) {
        if (closed) {
            return;
        }
        activity.run();
        final XQTestDescriptor test = running(testName);
        done.add(test);
        final TestExecutionResult result = pending.remove(test);
        listener.executionFinished(test, result != null ? result : TestExecutionResult.successful());
    }

    @Override
    public synchronized void ignored(final String testName) {
        if (closed) {
            return;
        }
        activity.run();
        final int index = startedCount.merge(testName, 1, Integer::sum) - 1;
        final List<XQTestDescriptor> occurrences = file.findTests(testName);
        final XQTestDescriptor test = index < occurrences.size() ? occurrences.get(index) : file.addTest(testName);
        if (index >= occurrences.size()) {
            listener.dynamicTestRegistered(test);
        }
        started.add(test);
        done.add(test);
        listener.executionSkipped(test, "Ignored");
    }

    @Override
    public synchronized void failed(final String testName, final Throwable reason) {
        if (closed) {
            return;
        }
        activity.run();
        final XQTestDescriptor test = running(testName);
        final TestExecutionResult previous = pending.get(test);
        if (previous != null && previous.getStatus() == TestExecutionResult.Status.FAILED && previous.getThrowable().isPresent()) {
            previous.getThrowable().get().addSuppressed(reason);
        } else {
            pending.put(test, TestExecutionResult.failed(reason));
        }
    }

    @Override
    public synchronized void assumptionFailed(final String testName, final String message) {
        if (closed) {
            return;
        }
        activity.run();
        pending.putIfAbsent(running(testName), TestExecutionResult.aborted(new TestAbortedException(message)));
    }

    /**
     * Completes every test of the file that the XQuery side did not finish, so that none is silently lost.
     *
     * @param fileFailure the reason the whole file failed to run, or null
     */
    private static String reasonSuffix(final Throwable fileFailure) {
        return fileFailure != null && fileFailure.getMessage() != null ? ": " + fileFailure.getMessage() : "";
    }

    synchronized void completeOutstanding(final Throwable fileFailure) {
        if (closed) {
            return;
        }
        closed = true;
        for (final XQTestDescriptor test : file.tests()) {
            if (done.contains(test)) {
                continue;
            }
            done.add(test);
            if (started.contains(test)) {
                final TestExecutionResult result = pending.remove(test);
                listener.executionFinished(test, result != null ? result : TestExecutionResult.failed(
                        new AssertionError("Test started but did not finish" + reasonSuffix(fileFailure), fileFailure)));
            } else {
                started.add(test);
                listener.executionStarted(test);
                listener.executionFinished(test, TestExecutionResult.failed(new AssertionError(
                        "Test was discovered but never reported by the XQuery test runner" + reasonSuffix(fileFailure), fileFailure)));
            }
        }
    }
}
