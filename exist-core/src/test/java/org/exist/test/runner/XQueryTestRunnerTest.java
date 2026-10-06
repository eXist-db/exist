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

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the global methods of the {@link XQueryTestRunner}.
 */
class XQueryTestRunnerTest {

    private XQueryTestRunner runnerFor(final String resource) throws URISyntaxException, TestInitializationException {
        final URL queryUrl = getClass().getResource(resource);
        return new XQueryTestRunner(Path.of(queryUrl.toURI()), null);
    }

    @Test
    void suiteNameIsDerivedFromTheModuleNamespace() throws URISyntaxException, TestInitializationException {
        final XQueryTestRunner runner = runnerFor("single-test.xqm");
        assertEquals("xqts.org.exist-db.xquery.single-test-module", runner.getSuiteName());
        assertEquals(List.of("f1"), runner.getTestNames());
    }

    @Test
    void fileWithoutTestsIsNamedAfterTheFileAndHasNoTests() throws URISyntaxException, TestInitializationException {
        final XQueryTestRunner runner = runnerFor("no-tests.xqm");
        assertEquals("no-tests.xqm", runner.getSuiteName());
        assertTrue(runner.getTestNames().isEmpty());
    }

    @Test
    void testNamesFollowTheNamesTheXQSuiteRuntimeReports() throws URISyntaxException, TestInitializationException {
        // a prefix with a hyphen is kept, an explicit %test:name is used as is
        assertEquals(List.of("my-tests:f1", "explicitly named"), runnerFor("hyphenated-prefix.xqm").getTestNames());
    }

    @Test
    void runtimeTestNameDropsOnlyAPrefixOfWordCharacters() {
        assertEquals("f1", XQueryTestRunner.runtimeTestName("single", "f1"));
        assertEquals("f1", XQueryTestRunner.runtimeTestName("t1", "f1"));
        assertEquals("f1", XQueryTestRunner.runtimeTestName("", "f1"));
        assertEquals("my-tests:f1", XQueryTestRunner.runtimeTestName("my-tests", "f1"));
        // underscore is punctuation in XPath regular expressions, so it is not a word character
        assertEquals("my_tests:f1", XQueryTestRunner.runtimeTestName("my_tests", "f1"));
    }
}
