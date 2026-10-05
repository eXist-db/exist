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
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;
import org.opentest4j.AssertionFailedError;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

class XQSuiteTestEngineTest {

    private static final String FIXTURES = "src/test/resources/org/exist/test/runner/";

    @XQSuite(value = FIXTURES + "single-test.xqm", fixture = true)
    static class SingleTest {
    }

    @XQSuite(value = FIXTURES + "failing-assertion.xqm", fixture = true)
    static class FailingAssertion {
    }

    @XQSuite(value = FIXTURES + "failing-both.xqm", fixture = true)
    static class FailingBoth {
    }

    @XQSuite(value = FIXTURES + "failing-serialization.xqm", fixture = true)
    static class FailingSerialization {
    }

    @XQSuite(value = FIXTURES + "no-tests.xqm", fixture = true)
    static class NoTests {
    }

    @XQSuite(value = FIXTURES + "hyphenated-prefix.xqm", fixture = true)
    static class HyphenatedPrefix {
    }

    @XQSuite(value = "src/test/resources/does/not/exist.xqm", fixture = true)
    static class MissingFile {
    }

    private static EngineExecutionResults run(final Class<?> suite) {
        return EngineTestKit.engine(XQSuiteTestEngine.ENGINE_ID).selectors(selectClass(suite))
                .configurationParameter(XQSuiteSettings.FIXTURES, "true").execute();
    }

    private static List<Throwable> failures(final EngineExecutionResults results) {
        return results.testEvents().failed().list().stream()
                .map(event -> event.getRequiredPayload(TestExecutionResult.class).getThrowable().orElseThrow())
                .toList();
    }

    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert") // assertion is delegated to assertStatistics, which asserts internally
    @Test
    void passingTestIsReportedAsSucceeded() {
        run(SingleTest.class).testEvents().assertStatistics(stats -> stats.started(1).succeeded(1).failed(0).skipped(0));
    }

    @Test
    void failingAssertionIsReportedWithExpectedAndActual() {
        final EngineExecutionResults results = run(FailingAssertion.class);
        results.testEvents().assertStatistics(stats -> stats.started(1).failed(1));

        final Throwable failure = failures(results).get(0);
        final AssertionFailedError assertionFailure = assertInstanceOf(AssertionFailedError.class, failure);
        assertTrue(assertionFailure.getExpected().getStringRepresentation().contains("expected"));
        assertEquals("actual", assertionFailure.getActual().getValue());
    }

    @Test
    void failureMessageCarriesExpectedAndActual() {
        // surefire keeps the message and drops the separate expected and actual values, so without this
        // a failed assertion in a report cannot be told apart from an empty result
        final String message = assertInstanceOf(AssertionFailedError.class, failures(run(FailingAssertion.class)).get(0)).getMessage();
        assertTrue(message.startsWith("XQuery failure: failing-assertion.xqm:"), message);
        assertTrue(message.contains(" ==> expected: <expected"), message);
        assertTrue(message.endsWith(" but was: <actual>"), message);
    }

    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert") // assertion is delegated to assertStatistics, which asserts internally
    @Test
    void assertionFailureAndUnexpectedErrorAreBothReported() {
        run(FailingBoth.class).testEvents().assertStatistics(stats -> stats.started(2).failed(2));
    }

    @Test
    void nodeResultIsNotEscapedIntoTheFailureMessage() {
        final AssertionFailedError failure = assertInstanceOf(AssertionFailedError.class, failures(run(FailingSerialization.class)).get(0));
        assertEquals("<doc a=\"1\">text</doc>", failure.getActual().getValue(),
                "a node-valued result should reach the failure message as markup, not XML-escaped");
    }

    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert") // assertion is delegated to assertStatistics, which asserts internally
    @Test
    void testsAreReportedUnderTheNamesTheyWereDiscoveredWith() {
        // if discovery and the XQSuite runtime disagreed on a name, a test would be reported twice:
        // once as passed under the runtime's name and once as failed because it was never reported
        run(HyphenatedPrefix.class).testEvents().assertStatistics(stats -> stats.started(2).succeeded(2).failed(0));
    }

    @Test
    void assertionFailureLeadsToTheXQueryTestFile() {
        final Throwable failure = failures(run(FailingBoth.class)).stream()
                .filter(AssertionFailedError.class::isInstance)
                .findFirst()
                .orElseThrow();

        final boolean inStackTrace = java.util.Arrays.stream(failure.getStackTrace())
                .anyMatch(e -> e.getFileName() != null && e.getFileName().contains("failing-both"));
        final boolean inMessage = failure.getMessage() != null && failure.getMessage().contains("failing-both");
        assertTrue(inStackTrace || inMessage, "the failure should name the XQuery test file so an IDE can navigate to it");
    }

    @Test
    void unexpectedErrorLeadsToTheJavaCode() {
        final Throwable failure = failures(run(FailingBoth.class)).stream()
                .filter(org.exist.xquery.XPathException.class::isInstance)
                .findFirst()
                .orElseThrow();

        assertTrue(java.util.Arrays.stream(failure.getStackTrace()).anyMatch(e -> e.getClassName().startsWith("org.exist")),
                "the error should keep its Java stack trace so an IDE can navigate to the code");
    }

    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert") // assertion is delegated to assertStatistics, which asserts internally
    @Test
    void fileWithoutTestsRunsNothing() {
        run(NoTests.class).testEvents().assertStatistics(stats -> stats.started(0));
    }

    @Test
    void fileTimeIsReportedForEachFile() {
        final List<Event> entries = run(SingleTest.class).allEvents().reportingEntryPublished().list();
        assertEquals(1, entries.size());
        final String millis = entries.get(0).getRequiredPayload(ReportEntry.class).getKeyValuePairs().get("file-time-ms");
        assertTrue(Long.parseLong(millis) >= 0);
    }

    @Test
    void suiteThatCannotBeDiscoveredFailsInsteadOfDisappearing() {
        final EngineExecutionResults results = run(MissingFile.class);
        results.testEvents().assertStatistics(stats -> stats.started(1).failed(1));
        final List<Event> failed = results.testEvents().failed().list();
        assertEquals(1, failed.size());
    }
}
