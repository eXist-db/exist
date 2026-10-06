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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests discovery of XQSuite tests, either by one discovery XQuery on a running database or by compiling the module.
 */
public class TestDiscoveryTest {

    private static final String RESOURCES = "src/test/resources/org/exist/test/runner/";

    @RegisterExtension
    public ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    void discoveryReturnsTestListForSingleTestFile() {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Path path = Path.of(RESOURCES + "single-test.xqm").toAbsolutePath();
        final XQueryTestRunner.XQueryTestInfo info = XQueryTestRunner.runDiscovery(pool, path);

        assertNotNull(info, "discovery XQuery should return test info");
        assertEquals("http://exist-db.org/xquery/single-test-module", info.namespace(), "namespace");
        assertEquals(1, info.testFunctions().size(), "one test function");
        assertEquals("f1", info.testFunctions().getFirst().localName(), "test name");
        assertEquals(0, info.testFunctions().getFirst().arity(), "test arity");
    }

    /**
     * Both ways of discovering tests must name them as the XQSuite runtime reports them, or a test
     * would be discovered under one name and reported under another.
     */
    @Test
    void discoveringWithTheDatabaseAndByCompilingGiveTheSameNames() throws TestInitializationException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        for (final String file : List.of("single-test.xqm", "hyphenated-prefix.xqm")) {
            final Path path = Path.of(RESOURCES + file).toAbsolutePath();
            final List<String> viaDatabase = new XQueryTestRunner(path, pool).getTestNames();
            final List<String> viaCompiling = new XQueryTestRunner(path, null).getTestNames();
            assertEquals(viaCompiling, viaDatabase, "test names discovered for " + file);
        }
    }
}
