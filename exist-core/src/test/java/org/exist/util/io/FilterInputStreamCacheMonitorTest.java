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
import java.nio.file.Paths;

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
            LOG.warn("FilterInputStreamCacheMonitor should have no active binaries, but found: {}.{}{}It is likely that a previous test or process within the same JVM is leaking file handles! This should be investigated...", activeCount, System.lineSeparator(), monitor.dump());
        }
        monitor.clear();

        final Path icon = Paths.get(FilterInputStreamCacheMonitorTest.class.getResource("icon.png").toURI());

        final Collection testCollection = existXmldbEmbeddedServer.createCollection(existXmldbEmbeddedServer.getRoot(), TEST_COLLECTION_NAME);
        try(final EXistResource resource = (EXistResource)testCollection.createResource("icon.png", BinaryResource.class)) {
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
            fail("FilterInputStreamCacheMonitor should have no active binaries, but found: " + activeCount + "." + System.lineSeparator() + monitor.dump());
        }

        ResourceSet resourceSet = null;
        try {
            resourceSet = existXmldbEmbeddedServer.executeQuery(
                    "util:binary-doc('/db/" + TEST_COLLECTION_NAME + "/icon.png')");

            assertEquals(1, resourceSet.getSize());

            try (final EXistResource resource = (EXistResource)resourceSet.getResource(0)) {
                assertTrue(resource instanceof LocalBinaryResource);
                assertTrue(((ExtendedResource)resource).getExtendedContent() instanceof BinaryValue);

                // one active binary (as it is in the result set)
                assertEquals(1, monitor.getActive().size());
            }

            // assert no active binaries as we just closed the resource in the try-with-resources
            activeCount = monitor.getActive().size();
            if (activeCount != 0) {
                fail("FilterInputStreamCacheMonitor should again have no active binaries, but found: " + activeCount + "."  + System.lineSeparator() + monitor.dump());
            }

        } finally {
            resourceSet.clear();
        }
    }

    @Test
    public void enclosedExpressionCleanup() throws XMLDBException {
        final FilterInputStreamCacheMonitor monitor = FilterInputStreamCacheMonitor.getInstance();

        // assert no binaries in use yet
        int activeCount = monitor.getActive().size();
        if (activeCount != 0) {
            fail("FilterInputStreamCacheMonitor should have no active binaries, but found: " + activeCount + "."  + System.lineSeparator() + monitor.dump());
        }

        ResourceSet resourceSet = null;
        try {
            resourceSet = existXmldbEmbeddedServer.executeQuery(
                    "let $embedded := <logo><image>{util:binary-doc('/db/" + TEST_COLLECTION_NAME + "/icon.png')}</image></logo>\n" +
                            "return xmldb:store('/db/" + TEST_COLLECTION_NAME + "', 'icon.xml', $embedded)");

            assertEquals(1, resourceSet.getSize());
            try (final EXistResource resource = (EXistResource)resourceSet.getResource(0)) {
                assertFalse(resource instanceof LocalBinaryResource);

                // assert still no active binaries (because they have been cleaned up)
                activeCount = monitor.getActive().size();
                if (activeCount != 0) {
                    fail("FilterInputStreamCacheMonitor should again have no active binaries, but found: " + activeCount + "."  + System.lineSeparator() + monitor.dump());
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
            fail("FilterInputStreamCacheMonitor should have no active binaries, but found: " + activeCount + "." + System.lineSeparator() + monitor.dump());
        }

        ResourceSet resourceSet = null;
        try {
            resourceSet = existXmldbEmbeddedServer.executeQuery(
                    "let $bin := util:binary-doc('/db/" + TEST_COLLECTION_NAME + "/icon.png')\n" +
                    "let $embedded := <logo><image>{$bin}</image></logo>\n" +
                    "let $embedded-2 := <other>{$bin}</other>\n" +
                    "return xmldb:store('/db/" + TEST_COLLECTION_NAME + "', 'icon.xml', $embedded)");

            assertEquals(1, resourceSet.getSize());
            try (final EXistResource resource = (EXistResource)resourceSet.getResource(0)) {
                assertFalse(resource instanceof LocalBinaryResource);

                // assert still no active binaries (because they have been cleaned up)
                activeCount = monitor.getActive().size();
                if (activeCount != 0) {
                    fail("FilterInputStreamCacheMonitor should again have no active binaries, but found: " + activeCount + "."  + System.lineSeparator() + monitor.dump());
                }
            }

        } finally {
            resourceSet.clear();
        }
    }
}
