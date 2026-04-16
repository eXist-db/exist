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
import org.exist.test.ExistEmbeddedServer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runners.model.InitializationError;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests discovery via a single XQuery per file.
 * Asserts that the discovery XQuery returns the expected test list for known files,
 * and that XQueryTestRunner uses discovery when the DB is up and runs the same tests.
 */
public class XSuiteDiscoveryTest {

    @Rule
    public ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    public void discoveryReturnsTestListForSingleTestFile() {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Path path = Path.of("src/test/resources/org/exist/test/runner/single-test.xqm").toAbsolutePath();
        final XQueryTestRunner.XQueryTestInfo info = XQueryTestRunner.runDiscovery(pool, path);
        assertNotNull("discovery XQuery should return test info", info);
        assertEquals("namespace", "http://exist-db.org/xquery/single-test-module", info.namespace());
        assertEquals("one test function", 1, info.testFunctions().size());
        assertEquals("test name", "f1", info.testFunctions().getFirst().localName());
        assertEquals("test arity", 0, info.testFunctions().getFirst().arity());
    }

    /**
     * When the DB is up (XSuite.EXIST_EMBEDDED_SERVER_CLASS_INSTANCE set), XQueryTestRunner should use
     * discovery and the runner's description should match the discovery result.
     */
    @Test
    public void runnerUsesDiscoveryWhenDbIsUp() throws InitializationError {
        final Path path = Path.of("src/test/resources/org/exist/test/runner/single-test.xqm").toAbsolutePath();
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQueryTestRunner.XQueryTestInfo discoveryInfo = XQueryTestRunner.runDiscovery(pool, path);
        assertNotNull("discovery must succeed in this test", discoveryInfo);

        final ExistEmbeddedServer previous = XSuite.EXIST_EMBEDDED_SERVER_CLASS_INSTANCE;
        try {
            XSuite.EXIST_EMBEDDED_SERVER_CLASS_INSTANCE = existEmbeddedServer;
            final XQueryTestRunner runner = new XQueryTestRunner(path, false);
            final Description description = runner.getDescription();
            final List<Description> children = description.getChildren();
            assertEquals("runner should have same number of tests as discovery", discoveryInfo.testFunctions().size(), children.size());
            final List<String> childNames = new ArrayList<>();
            for (final Description d : children) {
                childNames.add(d.getMethodName());
            }
            for (int i = 0; i < discoveryInfo.testFunctions().size(); i++) {
                assertEquals("test name from runner should match discovery", discoveryInfo.testFunctions().get(i).localName(), childNames.get(i));
            }
        } finally {
            XSuite.EXIST_EMBEDDED_SERVER_CLASS_INSTANCE = previous;
        }
    }
}
