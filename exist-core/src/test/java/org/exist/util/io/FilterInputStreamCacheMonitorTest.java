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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.CollectionManagementService;

import java.net.URISyntaxException;
import java.nio.file.Path;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.*;

public class FilterInputStreamCacheMonitorTest {

    @ClassRule
    public static ExistXmldbEmbeddedServer existXmldbEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    protected final static Logger LOG = LogManager.getLogger(FilterInputStreamCacheMonitorTest.class);

    private static String TEST_COLLECTION_NAME = "testFilterInputStreamCacheMonitor";

    @BeforeClass
    public static void setup() throws XMLDBException, URISyntaxException {
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

    @AfterClass
    public static void cleanup() throws XMLDBException {
        final CollectionManagementService cms = existXmldbEmbeddedServer.getRoot().getService(CollectionManagementService.class);
        cms.removeCollection(TEST_COLLECTION_NAME);
    }

    @Test
    public void binaryResult() throws XMLDBException {
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
                assertTrue(resource instanceof LocalBinaryResource);
                assertTrue(((ExtendedResource) resource).getExtendedContent() instanceof BinaryValue);

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
    public void userDefinedFunctionCleanup() throws XMLDBException {
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
    public void loopDoesNotAccumulateCaches() throws XMLDBException {
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

    private static void assertNoActiveBinaries(final FilterInputStreamCacheMonitor monitor, final String when) {
        final int activeCount = monitor.getActive().size();
        if (activeCount != 0) {
            fail("FilterInputStreamCacheMonitor should have no active binaries " + when + ", but found: "
                    + activeCount + "." + System.getProperty("line.separator") + monitor.dump());
        }
    }

    @Test
    public void enclosedExpressionCleanup() throws XMLDBException {
        final FilterInputStreamCacheMonitor monitor = FilterInputStreamCacheMonitor.getInstance();

        // assert no binaries in use yet
        int activeCount = monitor.getActive().size();
        if (activeCount != 0) {
            fail("FilterInputStreamCacheMonitor should have no active binaries, but found: " + activeCount + "." + System.getProperty("line.separator") + monitor.dump());
        }

        ResourceSet resourceSet = null;
        try {
            resourceSet = existXmldbEmbeddedServer.executeQuery(
                    "let $embedded := <logo><image>{util:binary-doc('/db/" + TEST_COLLECTION_NAME + "/icon.png')}</image></logo>\n" +
                            "return xmldb:store('/db/" + TEST_COLLECTION_NAME + "', 'icon.xml', $embedded)");

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
    public void enclosedExpressionsCleanup() throws XMLDBException {
        final FilterInputStreamCacheMonitor monitor = FilterInputStreamCacheMonitor.getInstance();

        // assert no binaries in use yet
        int activeCount = monitor.getActive().size();
        if (activeCount != 0) {
            fail("FilterInputStreamCacheMonitor should have no active binaries, but found: " + activeCount + "." + System.getProperty("line.separator") + monitor.dump());
        }

        ResourceSet resourceSet = null;
        try {
            resourceSet = existXmldbEmbeddedServer.executeQuery(
                    "let $bin := util:binary-doc('/db/" + TEST_COLLECTION_NAME + "/icon.png')\n" +
                            "let $embedded := <logo><image>{$bin}</image></logo>\n" +
                            "let $embedded-2 := <other>{$bin}</other>\n" +
                            "return xmldb:store('/db/" + TEST_COLLECTION_NAME + "', 'icon.xml', $embedded)");

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
