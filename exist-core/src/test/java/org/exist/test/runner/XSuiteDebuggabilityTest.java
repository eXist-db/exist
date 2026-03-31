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

import org.junit.jupiter.api.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for XSuite debuggability: failure stack traces should support
 * "navigate to XQuery" (test file:line) and "navigate to Java" (underlying Java code).
 */
class XSuiteDebuggabilityTest {

    @Test
    void failureFromAssertionIncludesTestFileInStackTraceOrMessage() {
        final List<Failure> failures = runSuiteAndCollectFailures();
        final Failure assertionFailure = failures.stream()
            .filter(f -> f.getException() instanceof org.junit.ComparisonFailure)
            .findFirst()
            .orElse(null);
        assertNotNull(assertionFailure, "expected one assertion failure (ComparisonFailure) from failing-both.xqm; collected failures: " + failures.size()
                + " types: " + failures.stream().map(f -> f.getException() == null ? "null" : f.getException().getClass().getName()).toList());

        final Throwable t = assertionFailure.getException();
        final String message = t.getMessage();
        final StackTraceElement[] stack = t.getStackTrace();

        boolean hasLocation = false;
        if (stack != null && stack.length > 0) {
            for (final StackTraceElement e : stack) {
                final String file = e.getFileName();
                if (file != null && file.contains("failing-both")) {
                    hasLocation = true;
                    break;
                }
            }
        }
        if (!hasLocation && message != null) {
            hasLocation = message.contains("failing-both");
        }
        assertTrue(hasLocation,
            "assertion failure should include test file (failing-both.xqm) in stack trace or message for IDE navigation (navigate to XQuery); stack.length=" + (stack != null ? stack.length : 0) + " message=" + message);
    }

    @Test
    void failureFromErrorIncludesOrgExistInStackTrace() {
        final List<Failure> failures = runSuiteAndCollectFailures();
        final Failure errorFailure = failures.stream()
            .filter(f -> f.getException() instanceof org.exist.xquery.XPathException)
            .findFirst()
            .orElse(null);
        assertNotNull(errorFailure, "expected one error failure (XPathException) from failing-both.xqm throwsUnexpectedError; xqsuite runs only functions with assert* annotations");
        final Throwable t = errorFailure.getException();
        final StackTraceElement[] stack = t.getStackTrace();

        boolean hasOrgExist = false;
        if (stack != null) {
            for (final StackTraceElement e : stack) {
                if (e.getClassName() != null && e.getClassName().startsWith("org.exist")) {
                    hasOrgExist = true;
                    break;
                }
            }
        }
        assertTrue(hasOrgExist,
            "error failure should include org.exist in stack trace for IDE navigation (navigate to Java); stack.length=" + (stack != null ? stack.length : 0));
    }

    private static List<Failure> runSuiteAndCollectFailures() {
        final List<Failure> collected = new ArrayList<>();
        final JUnitCore core = new JUnitCore();
        core.addListener(new RunListener() {
            @Override
            public void testFailure(final Failure failure) {
                collected.add(failure);
            }
        });
        final Result result = core.run(DebuggabilityNavigabilitySuite.class);
        assertFalse(result.wasSuccessful(), "suite is expected to have failures (failing-both.xqm)");
        assertTrue(collected.size() >= 2,
            "expected at least 2 failures (assertion + error) from failing-both.xqm; got " + collected.size()
                + " types: " + collected.stream().map(f -> f.getException() == null ? "null" : f.getException().getClass().getName()).toList());
        return collected;
    }
}
