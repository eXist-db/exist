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

import org.exist.storage.BrokerPool;
import org.exist.util.XMLFilenameFilter;
import org.exist.util.XQueryFilenameFilter;

import javax.annotation.Nullable;
import java.nio.file.Path;

/**
 * Creates the runner for a file of XQSuite or XML tests.
 */
public final class TestRunners {

    private TestRunners() {
    }

    /**
     * @param path a file in a suite
     *
     * @return true if the file is an XQuery file of tests
     */
    public static boolean isXQueryTestFile(final Path path) {
        return XQueryFilenameFilter.asPredicate().test(path) && !"runTests.xql".equals(path.getFileName().toString());
    }

    /**
     * Creates the test runner for a file of tests.
     *
     * @param path the XQuery or XML test file
     * @param discoveryPool a running database to discover XQuery tests with, or null to compile the module instead
     *
     * @return the runner, or null if the file is not a test file
     *
     * @throws TestInitializationException if the file cannot be read as a test file
     */
    public static @Nullable AbstractTestRunner newTestRunner(final Path path, @Nullable final BrokerPool discoveryPool) throws TestInitializationException {
        if (XMLFilenameFilter.asPredicate().test(path)) {
            return new XMLTestRunner(path);
        } else if (isXQueryTestFile(path)) {
            return new XQueryTestRunner(path, discoveryPool);
        } else {
            return null;
        }
    }
}
