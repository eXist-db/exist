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

package org.exist.util.io;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.ExtendedResource;
import org.exist.xmldb.LocalBinaryResource;
import org.exist.xquery.value.BinaryValue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.CollectionManagementService;

import java.net.URISyntaxException;
import java.nio.file.Path;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;
public class FilterInputStreamCacheMonitorTest {

    @RegisterExtension
    public static ExistXmldbEmbeddedServer existXmldbEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    protected final static Logger LOG = LogManager.getLogger(FilterInputStreamCacheMonitorTest.class);

    private static String TEST_COLLECTION_NAME = "testFilterInputStreamCacheMonitor";

    @BeforeAll
    static void setup() throws XMLDBException, URISyntaxException {
        final FilterInputStreamCacheMonitor monitor = FilterInputStreamCacheMonitor.getInstance();
        int activeCount = monitor.getActive().size();
        if (activeCount != 0) {
            LOG.warn("FilterInputStreamCacheMonitor should have no active binaries, but found: {}.{}{}It is likely that a previous test or process within the same JVM is leaking file handles! This should be investigated...", activeCount, System.getProperty("line.separator"), monitor.dump());
        }
        monitor.clear();

        final Path icon = Path.of(FilterInputStreamCacheMonitorTest.class.getResource("icon.png").toURI());

        final Collection testCollection = existXmldbEmbeddedServer.createCollection(existXmldbEmbeddedServer.getRoot(), TEST_COLLECTION_NAME);
        try (final EXistResource resource = (EXistResource) testCollection.createResource("icon.png", BinaryResource.class)) {
            resource.setContent(icon);
            testCollection.storeResource(resource);
        }
        testCollection.close();
    }

    @AfterAll
    static void cleanup() throws XMLDBException {
        final CollectionManagementService cms = existXmldbEmbeddedServer.getRoot().getService(CollectionManagementService.class);
        cms.removeCollection(TEST_COLLECTION_NAME);
    }

    @Test
    void binaryResult() throws XMLDBException {
        final FilterInputStreamCacheMonitor monitor = FilterInputStreamCacheMonitor.getInstance();

        // assert no binaries in use yet
        int activeCount = monitor.getActive().size();
        if (activeCount != 0) {
            fail("FilterInputStreamCacheMonitor should have no active binaries, but found: " + activeCount + "." + System.getProperty("line.separator") + monitor.dump());
        }

        ResourceSet resourceSet = null;
        try {
            resourceSet = existXmldbEmbeddedServer.executeQuery(
                    "util:binary-doc('/db/" + TEST_COLLECTION_NAME + "/icon.png')");

            assertEquals(1, resourceSet.getSize());

            try (final EXistResource resource = (EXistResource) resourceSet.getResource(0)) {
                assertInstanceOf(LocalBinaryResource.class, resource);
                assertInstanceOf(BinaryValue.class, ((ExtendedResource) resource).getExtendedContent());

                // one active binary (as it is in the result set)
                assertEquals(1, monitor.getActive().size());
            }

            // assert no active binaries as we just closed the resource in the try-with-resources
            activeCount = monitor.getActive().size();
            if (activeCount != 0) {
                fail("FilterInputStreamCacheMonitor should again have no active binaries, but found: " + activeCount + "." + System.getProperty("line.separator") + monitor.dump());
            }

        } finally {
            resourceSet.clear();
        }
    }

    /**
     * The leak-direction guard for scope ownership: a binary value created in a scope and merely
     * passed through a user-defined function is released when that scope is left, exactly once.
     *
     * <p>A value is now released by the scope that created it, rather than by whichever scope happened
     * to hold the last reference. This test fails if that deferral leaks instead.</p>
     *
     * @see <a href="https://github.com/eXist-db/exist/issues/6725">Passing a binary value to a user-defined function closes it for the caller</a>
     */
    @Test
    void userDefinedFunctionCleanup() throws XMLDBException {
        final FilterInputStreamCacheMonitor monitor = FilterInputStreamCacheMonitor.getInstance();
        assertNoActiveBinaries(monitor, "before the query");

        ResourceSet resourceSet = null;
        try {
            resourceSet = existXmldbEmbeddedServer.executeQuery("""
                    declare function local:size($b) { string-length(util:binary-to-string($b)) };
                    let $b := util:binary-doc('/db/%s/icon.png')
                    return local:size($b)""".formatted(TEST_COLLECTION_NAME));

            assertEquals(1, resourceSet.getSize());
            try (final EXistResource resource = (EXistResource) resourceSet.getResource(0)) {
                assertFalse(resource instanceof LocalBinaryResource);
                assertNoActiveBinaries(monitor, "after the query");
            }
        } finally {
            resourceSet.clear();
        }
    }

    /**
     * Binary values created inside a loop and not returned by it must not accumulate for the whole
     * query.
     *
     * <p>Note what this does <em>not</em> claim: ForExpr opens one scope around the whole loop, so the
     * values are released when the loop ends rather than per iteration - the count during the loop is
     * not asserted here. Per-iteration release is a separate change.</p>
     */
    @Test
    void loopDoesNotAccumulateCaches() throws XMLDBException {
        final FilterInputStreamCacheMonitor monitor = FilterInputStreamCacheMonitor.getInstance();
        assertNoActiveBinaries(monitor, "before the query");

        ResourceSet resourceSet = null;
        try {
            resourceSet = existXmldbEmbeddedServer.executeQuery("""
                    sum(for $i in 1 to 20 return
                      string-length(util:binary-to-string(util:binary-doc('/db/%s/icon.png'))))""".formatted(TEST_COLLECTION_NAME));

            assertEquals(1, resourceSet.getSize());
            try (final EXistResource resource = (EXistResource) resourceSet.getResource(0)) {
                assertFalse(resource instanceof LocalBinaryResource);
                assertNoActiveBinaries(monitor, "after the query");
            }
        } finally {
            resourceSet.clear();
        }
    }

    /**
     * Hashing or comparing a binary value reads it through a stream that holds a reference to the
     * value's shared stream cache. Unless that stream is closed, the cache - and the blob it reads
     * from - stay open after the query has released the value, until the JVM stops.
     *
     * <p>One case per way an expression reaches {@code BinaryValue#compareTo} or
     * {@code BinaryValue#hashCode}. {@code $b} is read from the database, {@code $c} from a string,
     * so both kinds of stream-backed value are covered.</p>
     *
     * <p>Each case asserts that its own query leaves no more caches than it found, rather than none at
     * all, so that a leak is reported by the case that caused it and not by every case after it.</p>
     *
     * @see <a href="https://github.com/eXist-db/exist/issues/6943">Comparing or hashing a binary value leaks its stream cache and blob reader</a>
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "$b eq $c",
            "$b = $c",
            "deep-equal($b, $c)",
            "count(distinct-values(($b, $c)))",
            "count(index-of(($b, $c), $c))",
            "count(for $x in ($b, $c) order by $x return $x)",
            "count(for $x in ($b, $c) group by $k := $x return $k)",
            "count(sort(($b, $c)))",
            "switch ($b) case $c return 1 default return 0",
            "map:size(map { $b: 1, $c: 2 })",
            "map:contains(map { 'k': 1 }, $b)"
    })
    void hashingOrComparingReleasesCaches(final String expression) throws XMLDBException {
        final FilterInputStreamCacheMonitor monitor = FilterInputStreamCacheMonitor.getInstance();
        final int activeBefore = monitor.getActive().size();

        ResourceSet resourceSet = null;
        try {
            resourceSet = existXmldbEmbeddedServer.executeQuery("""
                    let $b := util:binary-doc('/db/%s/icon.png')
                    let $c := util:string-to-binary('x')
                    return %s""".formatted(TEST_COLLECTION_NAME, expression));

            assertEquals(1, resourceSet.getSize());
            try (final EXistResource resource = (EXistResource) resourceSet.getResource(0)) {
                assertFalse(resource instanceof LocalBinaryResource);
                final int leaked = monitor.getActive().size() - activeBefore;
                if (leaked != 0) {
                    fail("The query left " + leaked + " more active binaries than it found."
                            + System.getProperty("line.separator") + monitor.dump());
                }
            }
        } finally {
            resourceSet.clear();
        }
    }

    private static void assertNoActiveBinaries(final FilterInputStreamCacheMonitor monitor, final String when) {
        final int activeCount = monitor.getActive().size();
        if (activeCount != 0) {
            fail("FilterInputStreamCacheMonitor should have no active binaries " + when + ", but found: "
                    + activeCount + "." + System.getProperty("line.separator") + monitor.dump());
        }
    }

    @Test
    void enclosedExpressionCleanup() throws XMLDBException {
        final FilterInputStreamCacheMonitor monitor = FilterInputStreamCacheMonitor.getInstance();

        // assert no binaries in use yet
        int activeCount = monitor.getActive().size();
        if (activeCount != 0) {
            fail("FilterInputStreamCacheMonitor should have no active binaries, but found: " + activeCount + "." + System.getProperty("line.separator") + monitor.dump());
        }

        ResourceSet resourceSet = null;
        try {
            resourceSet = existXmldbEmbeddedServer.executeQuery("""
                    let $embedded := <logo><image>{util:binary-doc('/db/%1$s/icon.png')}</image></logo>
                    return xmldb:store('/db/%1$s', 'icon.xml', $embedded)""".formatted(TEST_COLLECTION_NAME));

            assertEquals(1, resourceSet.getSize());
            try (final EXistResource resource = (EXistResource) resourceSet.getResource(0)) {
                assertFalse(resource instanceof LocalBinaryResource);

                // assert still no active binaries (because they have been cleaned up)
                activeCount = monitor.getActive().size();
                if (activeCount != 0) {
                    fail("FilterInputStreamCacheMonitor should again have no active binaries, but found: " + activeCount + "." + System.getProperty("line.separator") + monitor.dump());
                }
            }

        } finally {
            resourceSet.clear();
        }
    }

    @Test
    void enclosedExpressionsCleanup() throws XMLDBException {
        final FilterInputStreamCacheMonitor monitor = FilterInputStreamCacheMonitor.getInstance();

        // assert no binaries in use yet
        int activeCount = monitor.getActive().size();
        if (activeCount != 0) {
            fail("FilterInputStreamCacheMonitor should have no active binaries, but found: " + activeCount + "." + System.getProperty("line.separator") + monitor.dump());
        }

        ResourceSet resourceSet = null;
        try {
            resourceSet = existXmldbEmbeddedServer.executeQuery("""
                    let $bin := util:binary-doc('/db/%1$s/icon.png')
                    let $embedded := <logo><image>{$bin}</image></logo>
                    let $embedded-2 := <other>{$bin}</other>
                    return xmldb:store('/db/%1$s', 'icon.xml', $embedded)""".formatted(TEST_COLLECTION_NAME));

            assertEquals(1, resourceSet.getSize());
            try (final EXistResource resource = (EXistResource) resourceSet.getResource(0)) {
                assertFalse(resource instanceof LocalBinaryResource);

                // assert still no active binaries (because they have been cleaned up)
                activeCount = monitor.getActive().size();
                if (activeCount != 0) {
                    fail("FilterInputStreamCacheMonitor should again have no active binaries, but found: " + activeCount + "." + System.getProperty("line.separator") + monitor.dump());
                }
            }

        } finally {
            resourceSet.clear();
        }
    }
}
