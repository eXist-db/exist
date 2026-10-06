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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static java.nio.file.Files.write;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the global methods of the {@link XMLTestRunner}.
 */
class XMLTestRunnerTest {

    @TempDir
    Path tempPath;

    private XMLTestRunner runnerFor(final String... lines) throws TestInitializationException, IOException {
        final Path xmlTestFile = tempPath.resolve("test.xml");
        write(xmlTestFile, List.of(lines));
        return new XMLTestRunner(xmlTestFile);
    }

    @Test
    void suiteAndTestNamesComeFromTheTestSet() throws TestInitializationException, IOException {
        final XMLTestRunner runner = runnerFor(
                "<TestSet>",
                "    <testName>demoTest</testName>",
                "    <description>description for the demo test</description>",
                "    <test id='testId'/>",
                "    <test>",
                "        <task>taskName</task>",
                "    </test>",
                "</TestSet>");

        assertEquals("xmlts.demoTest", runner.getSuiteName());
        assertEquals(List.of("testId", "taskName"), runner.getTestNames());
    }

    @Test
    void taskNameIsTheRawTextBecauseThatIsWhatTheRuntimeReports() throws TestInitializationException, IOException {
        final XMLTestRunner runner = runnerFor(
                "<TestSet>",
                "    <testName>demoTest</testName>",
                "    <test><task>trailing space </task></test>",
                "</TestSet>");

        assertEquals(List.of("trailing space "), runner.getTestNames());
    }
}
