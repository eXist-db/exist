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

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.Description;
import org.junit.runners.model.InitializationError;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.nio.file.Files.write;
import static org.junit.Assert.*;

/**
 * Tests the global methods of the {@link XMLTestRunner}.
 */
public class XMLTestRunnerTest {

    @ClassRule
    public static final TemporaryFolder tempDir = new TemporaryFolder();

    protected XMLTestRunner runner;

    @Before
    public void prepare() throws InitializationError, IOException {
        final Path xmlTestFile = tempDir.newFile("test.xml").toPath();
        final List<String> lines = new ArrayList<>();
        lines.add("<TestSet>");
        lines.add("    <testName>demoTest</testName>");
        lines.add("    <description>description for the demo test</description>");
        lines.add("    <test id='testId'/>");
        lines.add("    <test>");
        lines.add("        <task>taskName</task>");
        lines.add("    </test>");
        lines.add("</TestSet>");
        write(xmlTestFile, lines);
        runner = new XMLTestRunner(xmlTestFile, false);
    }

    @Test
    public void testGetDescription() {
        final Description description = runner.getDescription();
        assertNotNull(description);
        assertTrue(description.isSuite());
        assertTrue(description.getAnnotations().isEmpty());
        assertEquals("xmlts.demoTest", description.getDisplayName());
        assertEquals(2, description.testCount());
        final ArrayList<Description> children = description.getChildren();
        assertChild(children.get(0), "testId(xmlts.demoTest)");
        assertChild(children.get(1), "taskName(xmlts.demoTest)");
    }

    private void assertChild(final Description description, final String expectedDisplayName) {
        assertFalse(description.isSuite());
        assertTrue(description.getAnnotations().isEmpty());
        assertEquals(expectedDisplayName, description.getDisplayName());
        assertEquals(1, description.testCount());
    }
}
