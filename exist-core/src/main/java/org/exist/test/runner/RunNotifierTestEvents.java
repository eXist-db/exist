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
package org.exist.test.runner;

import org.junit.AssumptionViolatedException;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;

/**
 * Adapts {@link TestEvents} to a JUnit 4 {@link RunNotifier}.
 */
final class RunNotifierTestEvents implements TestEvents {
    private final String suiteName;
    private final RunNotifier notifier;

    RunNotifierTestEvents(final String suiteName, final RunNotifier notifier) {
        this.suiteName = suiteName;
        this.notifier = notifier;
    }

    private Description description(final String testName) {
        return Description.createTestDescription(suiteName, testName);
    }

    @Override
    public void started(final String testName) {
        notifier.fireTestStarted(description(testName));
    }

    @Override
    public void finished(final String testName) {
        notifier.fireTestFinished(description(testName));
    }

    @Override
    public void ignored(final String testName) {
        notifier.fireTestIgnored(description(testName));
    }

    @Override
    public void failed(final String testName, final Throwable reason) {
        notifier.fireTestFailure(new Failure(description(testName), reason));
    }

    @Override
    public void assumptionFailed(final String testName, final String message) {
        final AssumptionViolatedException reason = new AssumptionViolatedException(message);
        // the Java stack trace would only point into the XQuery test runner
        reason.setStackTrace(new StackTraceElement[0]);
        notifier.fireTestAssumptionFailed(new Failure(description(testName), reason));
    }
}
