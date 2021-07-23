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
 * Tests the global methods of the {@link XQueryTestRunner}.
 */
public class XQueryTestRunnerTest {
    @ClassRule
    public static final TemporaryFolder tempDir = new TemporaryFolder();

    protected XQueryTestRunner runner;

    @Before
    public void prepare() throws InitializationError, IOException {
        final Path xmlTestFile = tempDir.newFile("test.xq").toPath();
        final List<String> lines = new ArrayList<>();
        lines.add("xquery version \"3.1\";");
        lines.add("module namespace t = \"http://exist-db.org/xquery/some/test\";");
        lines.add("declare function t:testFunction() {};");
        write(xmlTestFile, lines);
        runner = new XQueryTestRunner(xmlTestFile, false);
    }

    @Test
    public void testGetDescription() {
        final Description description = runner.getDescription();
        assertNotNull(description);
        assertFalse(description.isSuite());
        assertEquals(true, description.getAnnotations().isEmpty());
        assertEquals("xqts.org.exist-db.xquery.some.test", description.getDisplayName());
        assertEquals(1, description.testCount());
    }
}
